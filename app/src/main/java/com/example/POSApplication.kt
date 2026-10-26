package com.example

import android.app.Application
import com.example.data.local.POSDatabase
import com.example.data.repository.POSRepository
import com.example.data.sync.CloudRestSyncService
import com.example.data.sync.FirestoreSyncService
import com.example.data.sync.OfflineSyncManager
import com.example.printer.BluetoothPrinterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class POSApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { POSDatabase.getDatabase(this, applicationScope) }

    val printerManager by lazy {
        BluetoothPrinterManager(this, applicationScope)
    }

    val firestoreSyncService by lazy {
        FirestoreSyncService(
            productDao = database.productDao(),
            receiptDao = database.receiptDao(),
            customerDao = database.customerDao(),
            storeConfigDao = database.storeConfigDao(),
            businessProfileDao = database.businessProfileDao()
        )
    }

    val cloudRestSyncService by lazy {
        CloudRestSyncService(
            productDao = database.productDao(),
            receiptDao = database.receiptDao(),
            customerDao = database.customerDao(),
            storeConfigDao = database.storeConfigDao(),
            businessProfileDao = database.businessProfileDao(),
            creditNoteDao = database.creditNoteDao()
        )
    }

    val googleDriveBackupService by lazy {
        com.example.data.sync.GoogleDriveBackupService(
            context = this,
            cloudRestSyncService = cloudRestSyncService
        )
    }

    val syncManager by lazy {
        OfflineSyncManager(
            context = this,
            receiptDao = database.receiptDao(),
            productDao = database.productDao(),
            syncQueueDao = database.syncQueueDao(),
            syncLogDao = database.syncLogDao(),
            userAccountDao = database.userAccountDao(),
            firestoreSyncService = firestoreSyncService,
            cloudRestSyncService = cloudRestSyncService,
            scope = applicationScope
        )
    }

    val repository by lazy {
        POSRepository(
            receiptDao = database.receiptDao(),
            productDao = database.productDao(),
            customerDao = database.customerDao(),
            creditNoteDao = database.creditNoteDao(),
            syncQueueDao = database.syncQueueDao(),
            syncLogDao = database.syncLogDao(),
            storeConfigDao = database.storeConfigDao(),
            businessProfileDao = database.businessProfileDao(),
            userAccountDao = database.userAccountDao(),
            printerManager = printerManager,
            syncManager = syncManager,
            cloudRestSyncService = cloudRestSyncService,
            googleDriveBackupService = googleDriveBackupService
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        printerManager.cleanup()
        syncManager.cleanup()
    }
}
