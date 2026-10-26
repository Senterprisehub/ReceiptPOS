package com.example.data.sync

import android.content.Context
import android.util.Log
import com.example.data.local.dao.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class DriveOperationResult {
    data class Success(
        val message: String,
        val timestamp: Long = System.currentTimeMillis(),
        val fileId: String? = null,
        val folderId: String? = null
    ) : DriveOperationResult()
    data class Error(
        val message: String,
        val isAuthError: Boolean = false,
        val detailedLog: String = ""
    ) : DriveOperationResult()
}

enum class DriveConnectionMode {
    APPS_SCRIPT_BRIDGE,
    OAUTH_ACCESS_TOKEN,
    SIMULATED_SANDBOX
}

data class DriveAccountState(
    val email: String = "",
    val isConnected: Boolean = false,
    val connectionMode: DriveConnectionMode = DriveConnectionMode.APPS_SCRIPT_BRIDGE,
    val bridgeUrl: String = "",
    val oauthToken: String = "",
    val folderName: String = "AppData_Backup",
    val backupFileName: String = "app_backup_data.json",
    val folderId: String = "",
    val fileId: String = "",
    val lastBackupTimestamp: Long = 0L,
    val lastBackupSizeFormatted: String = "",
    val lastSyncStatus: String = ""
)

/**
 * Enterprise Google Drive Backup & Restoration Service.
 * Supports:
 * 1. Dedicated Google Apps Script Web App Bridge (zero-auth expiration, creates AppData_Backup automatically)
 * 2. Direct Google Drive REST API v3 with OAuth Access Token
 * 3. Fallback Offline Drive Cache
 */
class GoogleDriveBackupService(
    private val context: Context,
    private val cloudRestSyncService: CloudRestSyncService
) {
    private val TAG = "GoogleDriveBackupService"
    private val PREFS_NAME = "google_drive_pos_backup_prefs"
    private val KEY_GMAIL = "connected_gmail_account"
    private val KEY_MODE = "drive_connection_mode"
    private val KEY_BRIDGE_URL = "drive_bridge_webhook_url"
    private val KEY_OAUTH_TOKEN = "drive_oauth_token"
    private val KEY_FOLDER_ID = "drive_folder_id"
    private val KEY_FILE_ID = "drive_file_id"
    private val KEY_LAST_BACKUP_TIME = "last_backup_timestamp"
    private val KEY_LAST_BACKUP_SIZE = "last_backup_size"
    private val KEY_LAST_STATUS = "last_sync_status"

    val FOLDER_NAME = "AppData_Backup"
    val BACKUP_FILENAME = "app_backup_data.json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Retrieves current Google Drive account configuration.
     */
    fun getAccountState(): DriveAccountState {
        val email = prefs.getString(KEY_GMAIL, "") ?: ""
        val modeStr = prefs.getString(KEY_MODE, DriveConnectionMode.APPS_SCRIPT_BRIDGE.name)
        val mode = try { DriveConnectionMode.valueOf(modeStr ?: DriveConnectionMode.APPS_SCRIPT_BRIDGE.name) } catch (e: Exception) { DriveConnectionMode.APPS_SCRIPT_BRIDGE }
        val bridgeUrl = prefs.getString(KEY_BRIDGE_URL, "") ?: ""
        val token = prefs.getString(KEY_OAUTH_TOKEN, "") ?: ""
        val folderId = prefs.getString(KEY_FOLDER_ID, "") ?: ""
        val fileId = prefs.getString(KEY_FILE_ID, "") ?: ""
        val lastTime = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
        val size = prefs.getString(KEY_LAST_BACKUP_SIZE, "") ?: ""
        val status = prefs.getString(KEY_LAST_STATUS, "") ?: ""

        val isConnected = email.isNotBlank() && (bridgeUrl.isNotBlank() || token.isNotBlank() || mode == DriveConnectionMode.SIMULATED_SANDBOX)

        return DriveAccountState(
            email = email,
            isConnected = isConnected,
            connectionMode = mode,
            bridgeUrl = bridgeUrl,
            oauthToken = token,
            folderName = FOLDER_NAME,
            backupFileName = BACKUP_FILENAME,
            folderId = folderId,
            fileId = fileId,
            lastBackupTimestamp = lastTime,
            lastBackupSizeFormatted = size,
            lastSyncStatus = status
        )
    }

    /**
     * Connects with a Google Apps Script Web App Bridge URL or OAuth Access Token.
     */
    fun connectAccount(
        email: String,
        mode: DriveConnectionMode = DriveConnectionMode.APPS_SCRIPT_BRIDGE,
        bridgeUrl: String = "",
        oauthToken: String = ""
    ): Boolean {
        val trimmedEmail = email.trim()
        if (!isValidEmail(trimmedEmail)) {
            return false
        }

        prefs.edit()
            .putString(KEY_GMAIL, trimmedEmail)
            .putString(KEY_MODE, mode.name)
            .putString(KEY_BRIDGE_URL, bridgeUrl.trim())
            .putString(KEY_OAUTH_TOKEN, oauthToken.trim())
            .apply()

        return true
    }

    /**
     * Disconnects Google Drive.
     */
    fun disconnectAccount() {
        prefs.edit()
            .remove(KEY_GMAIL)
            .remove(KEY_MODE)
            .remove(KEY_BRIDGE_URL)
            .remove(KEY_OAUTH_TOKEN)
            .remove(KEY_FOLDER_ID)
            .remove(KEY_FILE_ID)
            .remove(KEY_LAST_BACKUP_TIME)
            .remove(KEY_LAST_BACKUP_SIZE)
            .remove(KEY_LAST_STATUS)
            .apply()
    }

    fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        return email.isNotBlank() && email.matches(Regex(emailRegex))
    }

    /**
     * Tests connection and creates Google Drive folder AppData_Backup if not present.
     */
    suspend fun testDriveConnection(): DriveOperationResult = withContext(Dispatchers.IO) {
        val state = getAccountState()
        if (!state.isConnected) {
            return@withContext DriveOperationResult.Error(
                "Please configure a Google Apps Script Web App URL or OAuth Access Token for your Gmail account.",
                isAuthError = true
            )
        }

        if (state.connectionMode == DriveConnectionMode.APPS_SCRIPT_BRIDGE && state.bridgeUrl.isNotBlank()) {
            return@withContext testAppsScriptBridge(state.bridgeUrl)
        } else if (state.connectionMode == DriveConnectionMode.OAUTH_ACCESS_TOKEN && state.oauthToken.isNotBlank()) {
            return@withContext testOAuthDriveConnection(state.oauthToken)
        } else {
            return@withContext DriveOperationResult.Success("Connected in Sandbox Mode (Local offline cloud mirror active).")
        }
    }

    /**
     * Uploads the complete POS database directly into Google Drive folder 'AppData_Backup/app_backup_data.json'.
     */
    suspend fun uploadBackupToDrive(): DriveOperationResult = withContext(Dispatchers.IO) {
        val state = getAccountState()
        if (state.email.isBlank()) {
            return@withContext DriveOperationResult.Error(
                "Please enter and save your Gmail address.",
                isAuthError = true
            )
        }

        try {
            // 1. Generate full database backup JSON
            val backupJson = cloudRestSyncService.generateCompleteBackupJson(state.email)
            val sizeKb = String.format(Locale.getDefault(), "%.1f KB", backupJson.toByteArray(Charsets.UTF_8).size / 1024.0)

            // 2. Perform live upload based on configured connection mode
            val result: DriveOperationResult = when {
                state.connectionMode == DriveConnectionMode.APPS_SCRIPT_BRIDGE && state.bridgeUrl.isNotBlank() -> {
                    uploadViaAppsScriptBridge(state.bridgeUrl, backupJson, state.email)
                }
                state.connectionMode == DriveConnectionMode.OAUTH_ACCESS_TOKEN && state.oauthToken.isNotBlank() -> {
                    uploadViaOAuthRestApi(state.oauthToken, backupJson)
                }
                else -> {
                    // Fallback to local offline cloud mirror
                    saveToOfflineDriveCache(backupJson)
                    val simId = "cached_${System.currentTimeMillis()}"
                    DriveOperationResult.Success("Backup saved to Drive Offline Mirror ($sizeKb).", fileId = simId)
                }
            }

            if (result is DriveOperationResult.Success) {
                val now = System.currentTimeMillis()
                val statusMsg = "Backed up $sizeKb on ${SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(now))}"
                prefs.edit()
                    .putLong(KEY_LAST_BACKUP_TIME, now)
                    .putString(KEY_LAST_BACKUP_SIZE, sizeKb)
                    .putString(KEY_LAST_STATUS, statusMsg)
                    .apply()

                if (result.fileId != null) prefs.edit().putString(KEY_FILE_ID, result.fileId).apply()
                if (result.folderId != null) prefs.edit().putString(KEY_FOLDER_ID, result.folderId).apply()

                saveToOfflineDriveCache(backupJson)
            }

            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadBackupToDrive", e)
            return@withContext DriveOperationResult.Error("Upload failed: ${e.localizedMessage ?: "Network error"}")
        }
    }

    /**
     * Restores database from Google Drive 'AppData_Backup/app_backup_data.json'.
     */
    suspend fun restoreBackupFromDrive(): DriveOperationResult = withContext(Dispatchers.IO) {
        val state = getAccountState()
        if (state.email.isBlank()) {
            return@withContext DriveOperationResult.Error("Please enter your connected Gmail address.", isAuthError = true)
        }

        try {
            val downloadResult: String = when {
                state.connectionMode == DriveConnectionMode.APPS_SCRIPT_BRIDGE && state.bridgeUrl.isNotBlank() -> {
                    downloadViaAppsScriptBridge(state.bridgeUrl)
                }
                state.connectionMode == DriveConnectionMode.OAUTH_ACCESS_TOKEN && state.oauthToken.isNotBlank() -> {
                    downloadViaOAuthRestApi(state.oauthToken)
                }
                else -> {
                    loadFromOfflineDriveCache()
                }
            }

            if (downloadResult.isBlank()) {
                val fallbackCache = loadFromOfflineDriveCache()
                if (fallbackCache.isNotBlank()) {
                    val count = cloudRestSyncService.restoreDatabaseFromJson(fallbackCache)
                    return@withContext DriveOperationResult.Success("Restored $count records from offline backup cache.")
                }
                return@withContext DriveOperationResult.Error("No backup data found in Google Drive folder '$FOLDER_NAME'.")
            }

            val restoredCount = cloudRestSyncService.restoreDatabaseFromJson(downloadResult)
            if (restoredCount > 0) {
                return@withContext DriveOperationResult.Success(
                    "Successfully restored $restoredCount database records & store profile from Google Drive!"
                )
            } else {
                return@withContext DriveOperationResult.Error("The Google Drive backup payload contains 0 records.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in restoreBackupFromDrive", e)
            return@withContext DriveOperationResult.Error("Restore failed: ${e.localizedMessage ?: "Parsing error"}")
        }
    }

    // =========================================================================
    // 1. GOOGLE APPS SCRIPT DRIVE BRIDGE IMPLEMENTATION
    // =========================================================================
    private fun testAppsScriptBridge(url: String): DriveOperationResult {
        return try {
            val testPayload = JSONObject().apply {
                put("action", "test")
                put("folderName", FOLDER_NAME)
                put("fileName", BACKUP_FILENAME)
            }.toString()

            val req = Request.Builder()
                .url(url)
                .post(testPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""
            if (res.isSuccessful || body.contains("success") || body.contains("AppData_Backup")) {
                DriveOperationResult.Success("Connected to Google Drive! Folder '$FOLDER_NAME' is ready.")
            } else {
                DriveOperationResult.Error("Google Apps Script returned HTTP ${res.code}: $body")
            }
        } catch (e: Exception) {
            DriveOperationResult.Error("Failed to reach Apps Script Web App: ${e.localizedMessage}")
        }
    }

    private fun uploadViaAppsScriptBridge(url: String, backupJson: String, email: String): DriveOperationResult {
        return try {
            val wrapper = JSONObject().apply {
                put("action", "upload")
                put("folderName", FOLDER_NAME)
                put("fileName", BACKUP_FILENAME)
                put("userEmail", email)
                put("data", JSONObject(backupJson))
            }.toString()

            val req = Request.Builder()
                .url(url)
                .post(wrapper.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string() ?: ""

            if (res.isSuccessful) {
                var fileId = ""
                var folderId = ""
                try {
                    val resJson = JSONObject(body)
                    fileId = resJson.optString("fileId", "")
                    folderId = resJson.optString("folderId", "")
                } catch (_: Exception) {}

                DriveOperationResult.Success(
                    message = "Successfully uploaded to Google Drive in folder '$FOLDER_NAME/$BACKUP_FILENAME'!",
                    fileId = fileId,
                    folderId = folderId
                )
            } else {
                DriveOperationResult.Error("Drive Upload failed (HTTP ${res.code}): $body")
            }
        } catch (e: Exception) {
            DriveOperationResult.Error("Google Drive upload error: ${e.localizedMessage}")
        }
    }

    private fun downloadViaAppsScriptBridge(url: String): String {
        return try {
            val targetUrl = if (url.contains("?")) "$url&action=download&folderName=$FOLDER_NAME&fileName=$BACKUP_FILENAME"
            else "$url?action=download&folderName=$FOLDER_NAME&fileName=$BACKUP_FILENAME"

            val req = Request.Builder()
                .url(targetUrl)
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string() ?: ""
                try {
                    val jsonObj = JSONObject(body)
                    if (jsonObj.has("data")) {
                        return jsonObj.getJSONObject("data").toString()
                    }
                } catch (_: Exception) {}
                body
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in downloadViaAppsScriptBridge", e)
            ""
        }
    }

    // =========================================================================
    // 2. DIRECT GOOGLE DRIVE REST API v3 IMPLEMENTATION
    // =========================================================================
    private fun testOAuthDriveConnection(token: String): DriveOperationResult {
        return try {
            val queryUrl = "https://www.googleapis.com/drive/v3/files?q=name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false&fields=files(id,name)"
            val req = Request.Builder()
                .url(queryUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            if (res.code == 401) {
                return DriveOperationResult.Error("Google OAuth Access Token is invalid or expired. Please refresh your token.", isAuthError = true)
            }

            val body = res.body?.string() ?: ""
            if (res.isSuccessful) {
                val files = JSONObject(body).optJSONArray("files")
                val folderId = if (files != null && files.length() > 0) files.getJSONObject(0).optString("id") else "New (will create on upload)"
                DriveOperationResult.Success("OAuth Connected! Folder: $FOLDER_NAME ($folderId)")
            } else {
                DriveOperationResult.Error("Google Drive API error (${res.code}): $body")
            }
        } catch (e: Exception) {
            DriveOperationResult.Error("Network error contacting Google Drive API: ${e.localizedMessage}")
        }
    }

    private fun uploadViaOAuthRestApi(token: String, backupJson: String): DriveOperationResult {
        return try {
            // Step 1: Ensure dedicated folder AppData_Backup exists
            var folderId = prefs.getString(KEY_FOLDER_ID, "") ?: ""
            if (folderId.isBlank()) {
                val queryUrl = "https://www.googleapis.com/drive/v3/files?q=name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false&fields=files(id,name)"
                val queryReq = Request.Builder()
                    .url(queryUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val queryRes = httpClient.newCall(queryReq).execute()
                if (queryRes.code == 401) {
                    return DriveOperationResult.Error("Google Drive OAuth Token expired. Please provide a fresh token.", isAuthError = true)
                }

                val queryBody = queryRes.body?.string() ?: ""
                val filesArr = JSONObject(queryBody).optJSONArray("files")
                if (filesArr != null && filesArr.length() > 0) {
                    folderId = filesArr.getJSONObject(0).getString("id")
                } else {
                    // Create folder
                    val createFolderJson = JSONObject().apply {
                        put("name", FOLDER_NAME)
                        put("mimeType", "application/vnd.google-apps.folder")
                    }.toString()

                    val createFolderReq = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files")
                        .addHeader("Authorization", "Bearer $token")
                        .post(createFolderJson.toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    val createRes = httpClient.newCall(createFolderReq).execute()
                    val createBody = createRes.body?.string() ?: ""
                    folderId = JSONObject(createBody).optString("id", "")
                }
                if (folderId.isNotBlank()) {
                    prefs.edit().putString(KEY_FOLDER_ID, folderId).apply()
                }
            }

            // Step 2: Check if app_backup_data.json exists inside folder
            val searchFileUrl = "https://www.googleapis.com/drive/v3/files?q=name='$BACKUP_FILENAME' and '$folderId' in parents and trashed=false&fields=files(id,name)"
            val searchReq = Request.Builder()
                .url(searchFileUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val searchRes = httpClient.newCall(searchReq).execute()
            val searchBody = searchRes.body?.string() ?: ""
            val searchFilesArr = JSONObject(searchBody).optJSONArray("files")
            val existingFileId = if (searchFilesArr != null && searchFilesArr.length() > 0) {
                searchFilesArr.getJSONObject(0).getString("id")
            } else {
                null
            }

            // Step 3: Overwrite in-place (PATCH) or Create (POST)
            val uploadReq = if (existingFileId != null) {
                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
                    .addHeader("Authorization", "Bearer $token")
                    .patch(backupJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            } else {
                val metadata = JSONObject().apply {
                    put("name", BACKUP_FILENAME)
                    put("parents", JSONArray().put(folderId))
                }.toString()

                val boundary = "-------314159265358979323846"
                val delimiter = "\r\n--$boundary\r\n"
                val closeDelimiter = "\r\n--$boundary--"

                val multipartBody = StringBuilder()
                    .append(delimiter)
                    .append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    .append(metadata)
                    .append(delimiter)
                    .append("Content-Type: application/json\r\n\r\n")
                    .append(backupJson)
                    .append(closeDelimiter)
                    .toString()

                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                    .addHeader("Authorization", "Bearer $token")
                    .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                    .build()
            }

            val uploadRes = httpClient.newCall(uploadReq).execute()
            if (uploadRes.isSuccessful) {
                val body = uploadRes.body?.string() ?: ""
                val uploadedId = JSONObject(body).optString("id", existingFileId ?: "")
                prefs.edit().putString(KEY_FILE_ID, uploadedId).apply()
                return DriveOperationResult.Success(
                    message = "Backup uploaded to Google Drive ($FOLDER_NAME/$BACKUP_FILENAME)!",
                    fileId = uploadedId,
                    folderId = folderId
                )
            } else if (uploadRes.code == 401) {
                return DriveOperationResult.Error("Google Drive authentication token expired.", isAuthError = true)
            } else {
                return DriveOperationResult.Error("Google Drive upload failed with HTTP ${uploadRes.code}: ${uploadRes.message}")
            }
        } catch (e: Exception) {
            DriveOperationResult.Error("REST API Upload failed: ${e.localizedMessage}")
        }
    }

    private fun downloadViaOAuthRestApi(token: String): String {
        return try {
            var fileId = prefs.getString(KEY_FILE_ID, "") ?: ""
            if (fileId.isBlank()) {
                val searchUrl = "https://www.googleapis.com/drive/v3/files?q=name='$BACKUP_FILENAME' and trashed=false&fields=files(id,name)"
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val searchRes = httpClient.newCall(searchReq).execute()
                val searchBody = searchRes.body?.string() ?: ""
                val filesArr = JSONObject(searchBody).optJSONArray("files")
                if (filesArr != null && filesArr.length() > 0) {
                    fileId = filesArr.getJSONObject(0).getString("id")
                    prefs.edit().putString(KEY_FILE_ID, fileId).apply()
                }
            }

            if (fileId.isBlank()) return ""

            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val downloadReq = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val downloadRes = httpClient.newCall(downloadReq).execute()
            if (downloadRes.isSuccessful) {
                downloadRes.body?.string() ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in downloadViaOAuthRestApi", e)
            ""
        }
    }

    private fun saveToOfflineDriveCache(json: String) {
        try {
            val file = File(context.filesDir, "drive_cached_backup.json")
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving offline drive cache", e)
        }
    }

    private fun loadFromOfflineDriveCache(): String {
        return try {
            val file = File(context.filesDir, "drive_cached_backup.json")
            if (file.exists()) file.readText(Charsets.UTF_8) else ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Returns the 1-click Google Apps Script template for creating AppData_Backup folder and managing files in Google Drive.
     */
    fun getAppsScriptTemplateCode(): String {
        return """
// =========================================================================
// GOOGLE APPS SCRIPT: POS GOOGLE DRIVE BACKUP & RESTORE BRIDGE
// =========================================================================
// 1. Open https://script.google.com with your Gmail account.
// 2. Click 'New project', replace the code with this script, and click Save.
// 3. Click 'Deploy' -> 'New deployment'.
// 4. Select type: 'Web app'.
// 5. Set 'Execute as': 'Me' (your email).
// 6. Set 'Who has access': 'Anyone'.
// 7. Click 'Deploy' and copy the Web App URL into the POS App!
// =========================================================================

function doPost(e) {
  try {
    var request = JSON.parse(e.postData.contents);
    var folderName = request.folderName || "AppData_Backup";
    var fileName = request.fileName || "app_backup_data.json";
    
    // 1. Ensure folder exists in Google Drive
    var folders = DriveApp.getFoldersByName(folderName);
    var folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(folderName);
    
    if (request.action === "test") {
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        folderId: folder.getId(),
        message: "Drive folder '" + folderName + "' is ready."
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    // 2. Upload / In-place Overwrite backup data
    var contentString = typeof request.data === "object" ? JSON.stringify(request.data, null, 2) : String(request.data);
    var files = folder.getFilesByName(fileName);
    var file;
    if (files.hasNext()) {
      file = files.next();
      file.setContent(contentString);
    } else {
      file = folder.createFile(fileName, contentString, MimeType.PLAIN_TEXT);
    }
    
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      fileId: file.getId(),
      folderId: folder.getId(),
      url: file.getUrl(),
      size: file.getSize(),
      updated: new Date().toISOString()
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doGet(e) {
  try {
    var folderName = (e.parameter && e.parameter.folderName) || "AppData_Backup";
    var fileName = (e.parameter && e.parameter.fileName) || "app_backup_data.json";
    
    var folders = DriveApp.getFoldersByName(folderName);
    if (!folders.hasNext()) {
      return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Folder not found" })).setMimeType(ContentService.MimeType.JSON);
    }
    var folder = folders.next();
    var files = folder.getFilesByName(fileName);
    if (!files.hasNext()) {
      return ContentService.createTextOutput(JSON.stringify({ status: "error", message: "Backup file not found" })).setMimeType(ContentService.MimeType.JSON);
    }
    var file = files.next();
    return ContentService.createTextOutput(file.getBlob().getDataAsString()).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() })).setMimeType(ContentService.MimeType.JSON);
  }
}
""".trimIndent()
    }
}
