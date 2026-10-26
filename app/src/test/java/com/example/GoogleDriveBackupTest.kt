package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.POSDatabase
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.ProductEntity
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.PaymentMethod
import com.example.data.model.ReceiptStatus
import com.example.data.sync.CloudRestSyncService
import com.example.data.sync.DriveOperationResult
import com.example.data.sync.GoogleDriveBackupService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleDriveBackupTest {

    private lateinit var context: Context
    private lateinit var database: POSDatabase
    private lateinit var cloudRestSyncService: CloudRestSyncService
    private lateinit var googleDriveBackupService: GoogleDriveBackupService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, POSDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        cloudRestSyncService = CloudRestSyncService(
            productDao = database.productDao(),
            receiptDao = database.receiptDao(),
            customerDao = database.customerDao(),
            storeConfigDao = database.storeConfigDao(),
            businessProfileDao = database.businessProfileDao()
        )

        googleDriveBackupService = GoogleDriveBackupService(
            context = context,
            cloudRestSyncService = cloudRestSyncService
        )

        // Ensure clean state before tests
        googleDriveBackupService.disconnectAccount()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `test email validation logic for google drive connection`() {
        assertFalse(googleDriveBackupService.isValidEmail(""))
        assertFalse(googleDriveBackupService.isValidEmail("invalid-email"))
        assertFalse(googleDriveBackupService.isValidEmail("user@"))
        assertFalse(googleDriveBackupService.isValidEmail("@gmail.com"))

        assertTrue(googleDriveBackupService.isValidEmail("Supriyogreat.das93@gmail.com"))
        assertTrue(googleDriveBackupService.isValidEmail("store.owner@gmail.com"))
        assertTrue(googleDriveBackupService.isValidEmail("pos.admin@company.co"))
    }

    @Test
    fun `test account connection and disconnection lifecycle`() {
        val initial = googleDriveBackupService.getAccountState()
        assertFalse(initial.isConnected)
        assertEquals("", initial.email)

        val connected = googleDriveBackupService.connectAccount("Supriyogreat.das93@gmail.com")
        assertTrue(connected)

        val afterConnect = googleDriveBackupService.getAccountState()
        assertTrue(afterConnect.isConnected)
        assertEquals("Supriyogreat.das93@gmail.com", afterConnect.email)
        assertEquals("AppData_Backup", afterConnect.folderName)
        assertEquals("app_backup_data.json", afterConnect.backupFileName)

        // Disconnect
        googleDriveBackupService.disconnectAccount()
        val afterDisconnect = googleDriveBackupService.getAccountState()
        assertFalse(afterDisconnect.isConnected)
        assertEquals("", afterDisconnect.email)
    }

    @Test
    fun `test upload backup to drive when disconnected returns error`() = runBlocking {
        googleDriveBackupService.disconnectAccount()
        val result = googleDriveBackupService.uploadBackupToDrive()
        assertTrue(result is DriveOperationResult.Error)
        val error = result as DriveOperationResult.Error
        assertTrue(error.isAuthError)
        assertTrue(error.message.contains("No Google account connected"))
    }

    @Test
    fun `test restore backup from drive when disconnected returns error`() = runBlocking {
        googleDriveBackupService.disconnectAccount()
        val result = googleDriveBackupService.restoreBackupFromDrive()
        assertTrue(result is DriveOperationResult.Error)
        val error = result as DriveOperationResult.Error
        assertTrue(error.isAuthError)
    }

    @Test
    fun `test full backup upload and in-place restoration flow`() = runBlocking {
        // 1. Seed test database
        val productDao = database.productDao()
        val receiptDao = database.receiptDao()
        val customerDao = database.customerDao()
        val configDao = database.storeConfigDao()

        productDao.insertProduct(
            ProductEntity(name = "Organic Espresso", category = "Beverages", price = 4.50, stockQuantity = 50)
        )
        customerDao.insertCustomer(
            CustomerEntity(name = "Alice Smith", email = "alice@example.com", phone = "+1555123456")
        )
        receiptDao.insertReceipt(
            ReceiptEntity(
                invoiceNumber = "INV-20260818-100001",
                customerName = "Alice Smith",
                itemsJson = "[{\"name\":\"Organic Espresso\",\"unitPrice\":4.50,\"quantity\":1}]",
                subtotal = 4.50,
                taxAmount = 0.38,
                totalAmount = 4.88,
                paymentMethod = PaymentMethod.CASH,
                status = ReceiptStatus.PAID
            )
        )
        configDao.saveStoreConfig(
            StoreConfigEntity(id = 1, businessName = "Apex Artisan Roasters", email = "Supriyogreat.das93@gmail.com")
        )

        // 2. Connect Account
        googleDriveBackupService.connectAccount("Supriyogreat.das93@gmail.com")

        // 3. Upload Backup to Drive (dedicate folder AppData_Backup/app_backup_data.json)
        val uploadResult = googleDriveBackupService.uploadBackupToDrive()
        assertTrue(uploadResult is DriveOperationResult.Success)

        val accountState = googleDriveBackupService.getAccountState()
        assertTrue(accountState.lastBackupTimestamp > 0)
        assertTrue(accountState.lastBackupSizeFormatted.isNotBlank())

        // 4. Wipe the local database to simulate new device or data loss
        productDao.getAllProductsList().forEach { productDao.deleteProduct(it) }
        receiptDao.getAllReceiptsList().forEach { receiptDao.deleteReceipt(it) }
        customerDao.getAllCustomers().forEach { customerDao.deleteCustomer(it) }

        assertEquals(0, productDao.getAllProductsList().size)
        assertEquals(0, receiptDao.getAllReceiptsList().size)
        assertEquals(0, customerDao.getAllCustomers().size)

        // 5. Restore from Drive
        val restoreResult = googleDriveBackupService.restoreBackupFromDrive()
        assertTrue(restoreResult is DriveOperationResult.Success)

        // 6. Verify all records restored accurately
        val restoredProducts = productDao.getAllProductsList()
        val restoredReceipts = receiptDao.getAllReceiptsList()
        val restoredCustomers = customerDao.getAllCustomers()

        assertEquals(1, restoredProducts.size)
        assertEquals("Organic Espresso", restoredProducts[0].name)
        assertEquals(1, restoredReceipts.size)
        assertEquals("INV-20260818-100001", restoredReceipts[0].invoiceNumber)
        assertEquals(1, restoredCustomers.size)
        assertEquals("Alice Smith", restoredCustomers[0].name)
    }

    @Test
    fun `test local JSON serialization and restoration parity`() = runBlocking {
        val productDao = database.productDao()
        productDao.insertProduct(ProductEntity(name = "Thermal Printer Roll", category = "Supplies", price = 12.0))

        val jsonString = cloudRestSyncService.generateCompleteBackupJson("admin@pos.dev")
        assertTrue(jsonString.contains("Thermal Printer Roll"))
        assertTrue(jsonString.contains("metadata"))
        assertTrue(jsonString.contains("products"))

        productDao.getAllProductsList().forEach { productDao.deleteProduct(it) }
        assertEquals(0, productDao.getAllProductsList().size)

        val restoredCount = cloudRestSyncService.restoreDatabaseFromJson(jsonString)
        assertTrue(restoredCount > 0)
        assertEquals(1, productDao.getAllProductsList().size)
    }
}
