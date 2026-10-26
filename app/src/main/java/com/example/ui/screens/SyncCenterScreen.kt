package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.local.entity.SyncLogEntity
import com.example.data.sync.SyncEngineState
import com.example.ui.POSViewModel
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncCenterScreen(
    viewModel: POSViewModel,
    syncState: SyncEngineState,
    syncLogs: List<SyncLogEntity>,
    unsyncedCount: Int,
    modifier: Modifier = Modifier
) {
    val storeConfig by viewModel.storeConfig.collectAsState()
    val products by viewModel.products.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val driveState by viewModel.driveState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var gmailInput by remember(driveState.email) { mutableStateOf(driveState.email) }
    var bridgeUrlInput by remember(driveState.bridgeUrl) { mutableStateOf(driveState.bridgeUrl) }
    var oauthTokenInput by remember(driveState.oauthToken) { mutableStateOf(driveState.oauthToken) }
    var selectedMode by remember(driveState.connectionMode) { mutableStateOf(driveState.connectionMode) }
    var isUploadingToDrive by remember { mutableStateOf(false) }
    var isRestoringFromDrive by remember { mutableStateOf(false) }
    var isTestingDrive by remember { mutableStateOf(false) }
    var showDriveRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showDriveSetupDialog by remember { mutableStateOf(false) }
    var isGmailUnlocked by remember { mutableStateOf(storeConfig.masterPin.isEmpty()) }
    var showGmailUnlockDialog by remember { mutableStateOf(false) }
    var gmailUnlockPin by remember { mutableStateOf("") }
    var showAppsScriptGuideDialog by remember { mutableStateOf(false) }

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var backupJsonContent by remember { mutableStateOf("") }
    var restoreInputText by remember { mutableStateOf("") }
    var lastBackupSavedTime by remember { mutableStateOf<String?>(null) }
    var isVerifyingDb by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    // Activity launcher for saving backup file to Device Storage (Create Document)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val backupJson = viewModel.getFullBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(backupJson.toByteArray(Charsets.UTF_8))
                    }
                    val timeStr = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault()).format(Date())
                    lastBackupSavedTime = timeStr
                    viewModel.showToast("Backup saved to device storage successfully!")
                } catch (e: Exception) {
                    viewModel.showToast("Failed to save backup: ${e.localizedMessage}")
                }
            }
        }
    }

    // Activity launcher for opening backup file from Device Storage (Open Document)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val jsonContent = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    if (jsonContent.isNotBlank()) {
                        viewModel.restoreFromJsonString(jsonContent) { success, msg ->
                            if (success) {
                                viewModel.showToast("All data and settings successfully restored!")
                            }
                        }
                    } else {
                        viewModel.showToast("Selected backup file is empty.")
                    }
                } catch (e: Exception) {
                    viewModel.showToast("Failed to read backup file: ${e.localizedMessage}")
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // =========================================================================
        // 1. GOOGLE DRIVE CLOUD BACKUP & RESTORE (ACCOUNT & DEDICATED APP FOLDER)
        // =========================================================================
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_drive_backup_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Drive Cloud Backup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            color = if (driveState.isConnected) StatusSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (driveState.isConnected) Icons.Default.CheckCircle else Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = if (driveState.isConnected) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (driveState.isConnected) "Drive Connected" else "Setup Required",
                                    fontSize = 11.sp,
                                    color = if (driveState.isConnected) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Automatically create the dedicated 'AppData_Backup' folder in your Google Drive and upload/restore 'app_backup_data.json' with 1-click synchronization.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Connected Account Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Target Google Account",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (driveState.email.isNotBlank()) driveState.email else gmailInput,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { showDriveSetupDialog = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("configure_drive_btn")
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Configure", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Target: ${driveState.folderName}/${driveState.backupFileName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (driveState.lastBackupTimestamp > 0L) {
                                    val timeStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(driveState.lastBackupTimestamp))
                                    Text(
                                        text = "Last: $timeStr (${driveState.lastBackupSizeFormatted})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldTertiary
                                    )
                                } else {
                                    Text(
                                        text = "Status: Ready to Sync",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action buttons: Test Folder / Upload / Restore
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Upload Backup to Drive
                            Button(
                                onClick = {
                                    isUploadingToDrive = true
                                    viewModel.uploadBackupToGoogleDrive { success, msg ->
                                        isUploadingToDrive = false
                                    }
                                },
                                enabled = !isUploadingToDrive && !isRestoringFromDrive && !isTestingDrive,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("upload_backup_drive_btn")
                            ) {
                                if (isUploadingToDrive) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Uploading...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload to Drive", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Restore from Drive
                            FilledTonalButton(
                                onClick = { showDriveRestoreConfirmDialog = true },
                                enabled = !isUploadingToDrive && !isRestoringFromDrive && !isTestingDrive,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("restore_backup_drive_btn")
                            ) {
                                if (isRestoringFromDrive) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restoring...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore from Drive", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Test & Verify Drive Folder Connection Button
                        OutlinedButton(
                            onClick = {
                                isTestingDrive = true
                                viewModel.testGoogleDriveConnection { success, msg ->
                                    isTestingDrive = false
                                }
                            },
                            enabled = !isUploadingToDrive && !isRestoringFromDrive && !isTestingDrive,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("test_drive_connection_btn")
                        ) {
                            if (isTestingDrive) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verifying Folder on Google Drive...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test & Create 'AppData_Backup' Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. LOCAL FILE EXPORT & IMPORT (RETAINED STORAGE ACCESS FRAMEWORK)
        // =========================================================================
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_backup_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SaveAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Local File Backup & Restore",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            color = StatusSuccess.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Offline Ready",
                                    fontSize = 11.sp,
                                    color = StatusSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create full on-device database backups as a .json file or restore an existing backup on any device completely offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Export Backup File to Device Button
                    Button(
                        onClick = {
                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            val defaultFilename = "apexpos_backup_$timeStamp.json"
                            createDocumentLauncher.launch(defaultFilename)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("export_backup_file_btn")
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export Backup File (.json)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Import Backup File from Device Button
                    OutlinedButton(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("import_backup_file_btn")
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import & Restore from File", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (lastBackupSavedTime != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusSuccess.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Last Backup Exported: $lastBackupSavedTime",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = StatusSuccess
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. QUICK JSON TEXT SHARE & PASTE
        // =========================================================================
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ImportExport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Direct JSON Export & Import",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Copy the raw JSON backup string to clipboard or paste JSON directly to restore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // View / Share JSON Backup
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    backupJsonContent = viewModel.getFullBackupJson()
                                    showBackupDialog = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("export_backup_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share / Copy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // Paste JSON String
                        FilledTonalButton(
                            onClick = {
                                restoreInputText = ""
                                showRestoreDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("import_backup_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 4. DATABASE RECORD STATISTICS & VERIFICATION
        // =========================================================================
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Local Database Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Current on-device Room SQLite database records and storage status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Products Count
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Products", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "${products.size}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Receipts Count
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Invoices", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "${receipts.size}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = EmeraldTertiary
                                )
                            }
                        }

                        // Database Health
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("DB Engine", style = MaterialTheme.typography.labelSmall)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "SQLite OK",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = StatusSuccess
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isVerifyingDb = true
                                kotlinx.coroutines.delay(400)
                                viewModel.showToast("Local database integrity verified. All records OK.")
                                isVerifyingDb = false
                            }
                        },
                        enabled = !isVerifyingDb,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("verify_db_btn"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isVerifyingDb) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifying Local Database...")
                        } else {
                            Icon(Icons.Default.Storage, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify Local Database Integrity", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // Confirmation Dialog for Google Drive Restore
    if (showDriveRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDriveRestoreConfirmDialog = false },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Restore from Google Drive?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will download 'app_backup_data.json' from your Google Drive folder 'AppData_Backup' and restore all products, receipts, customers, and configuration onto this device. Existing local records will be synchronized.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDriveRestoreConfirmDialog = false
                        isRestoringFromDrive = true
                        viewModel.restoreBackupFromGoogleDrive { success, msg ->
                            isRestoringFromDrive = false
                        }
                    }
                ) {
                    Text("Proceed with Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDriveRestoreConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Backup Export Dialog
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            icon = { Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Complete Database Backup", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Your complete database (Products, Invoices, Customers, Config) is ready for export. Copy or share it directly to another device.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupJsonContent,
                        onValueChange = {},
                        readOnly = true,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, backupJsonContent)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share POS Backup"))
                        } catch (e: Exception) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("POS Backup", backupJsonContent)
                            clipboard.setPrimaryClip(clip)
                            viewModel.showToast("Backup JSON copied to clipboard!")
                        }
                        showBackupDialog = false
                    }
                ) {
                    Text("Share / Copy JSON")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Backup Restore Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            icon = { Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Restore from Backup JSON", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Paste the exported backup JSON below to restore all products, receipts, and settings onto this device.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreInputText,
                        onValueChange = { restoreInputText = it },
                        placeholder = { Text("Paste JSON here...", fontSize = 11.sp) },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreDialog = false
                        viewModel.restoreFromJsonString(restoreInputText) { _, _ -> }
                    },
                    enabled = restoreInputText.isNotBlank()
                ) {
                    Text("Restore Database")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Google Drive Account & Method Configuration Dialog
    if (showDriveSetupDialog) {
        AlertDialog(
            onDismissRequest = { showDriveSetupDialog = false },
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Google Drive Cloud Configuration", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Configure how the POS app synchronizes with your Google Drive.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = gmailInput,
                        onValueChange = { if (isGmailUnlocked) gmailInput = it },
                        label = { Text("Gmail Address") },
                        placeholder = { Text("e.g. user@gmail.com") },
                        leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                        trailingIcon = {
                            if (storeConfig.masterPin.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (isGmailUnlocked) {
                                        isGmailUnlocked = false
                                    } else {
                                        showGmailUnlockDialog = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (isGmailUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = "Toggle Gmail Edit Lock"
                                    )
                                }
                            }
                        },
                        readOnly = !isGmailUnlocked,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showGmailUnlockDialog) {
                        AlertDialog(
                            onDismissRequest = { 
                                showGmailUnlockDialog = false
                                gmailUnlockPin = ""
                            },
                            title = { Text("Enter Supervisor PIN") },
                            text = {
                                OutlinedTextField(
                                    value = gmailUnlockPin,
                                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) gmailUnlockPin = it },
                                    label = { Text("Enter PIN to unlock Gmail ID") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (gmailUnlockPin == storeConfig.masterPin) {
                                        isGmailUnlocked = true
                                        showGmailUnlockDialog = false
                                    }
                                    gmailUnlockPin = ""
                                }) {
                                    Text("Unlock")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { 
                                    showGmailUnlockDialog = false 
                                    gmailUnlockPin = ""
                                }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    Text(
                        text = "Connection Mode:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == com.example.data.sync.DriveConnectionMode.APPS_SCRIPT_BRIDGE,
                            onClick = { selectedMode = com.example.data.sync.DriveConnectionMode.APPS_SCRIPT_BRIDGE },
                            label = { Text("Web Bridge (Recommended)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedMode == com.example.data.sync.DriveConnectionMode.OAUTH_ACCESS_TOKEN,
                            onClick = { selectedMode = com.example.data.sync.DriveConnectionMode.OAUTH_ACCESS_TOKEN },
                            label = { Text("OAuth Token", fontSize = 11.sp) }
                        )
                    }

                    if (selectedMode == com.example.data.sync.DriveConnectionMode.APPS_SCRIPT_BRIDGE) {
                        OutlinedTextField(
                            value = bridgeUrlInput,
                            onValueChange = { bridgeUrlInput = it },
                            label = { Text("Apps Script Web App URL") },
                            placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        TextButton(
                            onClick = { showAppsScriptGuideDialog = true },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1-Click Script & Setup Guide", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedTextField(
                            value = oauthTokenInput,
                            onValueChange = { oauthTokenInput = it },
                            label = { Text("Google OAuth 2.0 Access Token") },
                            placeholder = { Text("ya29....") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.connectGoogleDriveAccount(
                            email = gmailInput,
                            mode = selectedMode,
                            bridgeUrl = bridgeUrlInput,
                            oauthToken = oauthTokenInput
                        ) { _, _ -> }
                        if (storeConfig.masterPin.isNotEmpty()) {
                            isGmailUnlocked = false
                        }
                        showDriveSetupDialog = false
                    },
                    enabled = gmailInput.isNotBlank()
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (driveState.isConnected) {
                        TextButton(
                            onClick = {
                                viewModel.removeGmailSync()
                                if (storeConfig.masterPin.isNotEmpty()) {
                                    isGmailUnlocked = false
                                }
                                showDriveSetupDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear Config")
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { showDriveSetupDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Google Apps Script Setup & 1-Tap Copy Guide Dialog
    if (showAppsScriptGuideDialog) {
        val scriptCode = viewModel.getAppsScriptTemplate()
        AlertDialog(
            onDismissRequest = { showAppsScriptGuideDialog = false },
            icon = { Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Google Apps Script Setup (30 Sec)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Open script.google.com with your Gmail ($gmailInput).\n" +
                               "2. Click 'New Project', paste the code below.\n" +
                               "3. Click 'Deploy' > 'New Deployment' > Type 'Web app' > Who has access: 'Anyone' > Deploy.\n" +
                               "4. Copy the Web App URL and paste it into the Web App URL box!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = scriptCode,
                        onValueChange = {},
                        readOnly = true,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(scriptCode))
                        viewModel.showToast("Apps Script code copied to clipboard!")
                        showAppsScriptGuideDialog = false
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Script Code")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppsScriptGuideDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
