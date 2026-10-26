package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ReceiptEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        SyncQueueEntity::class,
        SyncLogEntity::class,
        StoreConfigEntity::class,
        BusinessProfileEntity::class,
        UserAccountEntity::class,
        CreditNoteEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class POSDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun storeConfigDao(): StoreConfigDao
    abstract fun businessProfileDao(): BusinessProfileDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun creditNoteDao(): CreditNoteDao

    companion object {
        @Volatile
        private var INSTANCE: POSDatabase? = null

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE receipts ADD COLUMN returnedItemsJson TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE receipts ADD COLUMN appliedCreditNoteNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE receipts ADD COLUMN appliedCreditOriginalInvoice TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE receipts ADD COLUMN creditNoteAppliedAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `credit_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `businessId` INTEGER NOT NULL DEFAULT 1,
                        `creditNoteNumber` TEXT NOT NULL,
                        `originalReceiptId` INTEGER NOT NULL,
                        `originalInvoiceNumber` TEXT NOT NULL,
                        `customerName` TEXT NOT NULL,
                        `customerPhone` TEXT NOT NULL,
                        `returnedItemsJson` TEXT NOT NULL,
                        `totalCreditAmount` REAL NOT NULL,
                        `remainingBalance` REAL NOT NULL,
                        `status` TEXT NOT NULL,
                        `cashierName` TEXT NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): POSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    POSDatabase::class.java,
                    "pos_database.db"
                )
                    .addMigrations(MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .addCallback(POSDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class POSDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            suspend fun populateInitialData(database: POSDatabase) {
                val configDao = database.storeConfigDao()
                val businessDao = database.businessProfileDao()
                val productDao = database.productDao()

                val defaultBusiness = BusinessProfileEntity(
                    id = 1,
                    businessName = "Apex Mobile POS & Cafe",
                    tagline = "Artisan Roasts & Quality Essentials",
                    address = "450 Market St, Financial District",
                    phone = "+1 (415) 555-0198",
                    email = "orders@apexpos.dev",
                    taxId = "TAX-893041-US",
                    countryCode = "US",
                    countryName = "United States",
                    currencyCode = "USD",
                    currencySymbol = "$",
                    taxName = "Sales Tax",
                    defaultTaxPercent = 8.5,
                    receiptHeader = "★ APEX MOBILE POS ★\nFAST & SECURE CHECKOUT",
                    receiptFooter = "Thank you for shopping with us!\nScan QR code on receipt to verify.\n30-day returns with this slip.",
                    paperWidthMm = 58,
                    isDefault = true
                )

                val secondaryBusiness = BusinessProfileEntity(
                    id = 2,
                    businessName = "Apex Express Mart & Goods",
                    tagline = "Daily Groceries & Essentials",
                    address = "12 Brigade Road, Commercial District",
                    phone = "+91 80 2558 1234",
                    email = "support@apexexpress.in",
                    taxId = "GSTIN: 29AAAAA0000A1Z5",
                    countryCode = "IN",
                    countryName = "India",
                    currencyCode = "INR",
                    currencySymbol = "₹",
                    taxName = "GST (18%)",
                    defaultTaxPercent = 18.0,
                    receiptHeader = "★ APEX EXPRESS MART ★\nTAX INVOICE / GST COMPLIANT",
                    receiptFooter = "Thank you for visiting!\nNo return on perishable items.\nHave a wonderful day!",
                    paperWidthMm = 58,
                    isDefault = false
                )

                businessDao.insertBusinesses(listOf(defaultBusiness, secondaryBusiness))

                // Default Store Configuration
                configDao.saveStoreConfig(
                    StoreConfigEntity(
                        id = 1,
                        activeBusinessId = 1L,
                        businessName = "Apex Mobile POS & Cafe",
                        tagline = "Artisan Roasts & Quality Essentials",
                        address = "450 Market St, Financial District",
                        phone = "+1 (415) 555-0198",
                        email = "orders@apexpos.dev",
                        taxId = "TAX-893041-US",
                        countryCode = "US",
                        countryName = "United States",
                        currencyCode = "USD",
                        currencySymbol = "$",
                        taxName = "Sales Tax",
                        defaultTaxPercent = 8.5,
                        receiptHeader = "★ APEX MOBILE POS ★\nFAST & SECURE CHECKOUT",
                        receiptFooter = "Thank you for shopping with us!\nScan QR code on receipt to verify.\n30-day returns with this slip.",
                        paperWidthMm = 58,
                        showQrVerification = true,
                        showBarcode = true,
                        autoPrintOnSale = false,
                        masterPin = "1234",
                        isPinRequiredForRefund = true,
                        syncEndpointUrl = "",
                        autoSyncOnWifi = true,
                        syncConflictStrategy = "CLIENT_WINS"
                    )
                )

                // Seed Initial Retail & Cafe Catalog
                val initialProducts = listOf(
                    ProductEntity(
                        name = "Espresso Double Shot",
                        category = "Beverages",
                        price = 3.75,
                        costPrice = 0.80,
                        sku = "BEV-ESP-01",
                        barcode = "890123450001",
                        stockQuantity = 250,
                        taxRatePercent = 8.5,
                        colorHex = "#9333EA"
                    ),
                    ProductEntity(
                        name = "Caramel Oat Macchiato",
                        category = "Beverages",
                        price = 5.50,
                        costPrice = 1.20,
                        sku = "BEV-MAC-02",
                        barcode = "890123450002",
                        stockQuantity = 180,
                        taxRatePercent = 8.5,
                        colorHex = "#D97706"
                    ),
                    ProductEntity(
                        name = "Matcha Green Tea Latte",
                        category = "Beverages",
                        price = 5.25,
                        costPrice = 1.10,
                        sku = "BEV-MAT-03",
                        barcode = "890123450003",
                        stockQuantity = 95,
                        taxRatePercent = 8.5,
                        colorHex = "#059669"
                    ),
                    ProductEntity(
                        name = "Artisan Almond Croissant",
                        category = "Bakery",
                        price = 4.50,
                        costPrice = 1.50,
                        sku = "BAK-CRS-01",
                        barcode = "890123450004",
                        stockQuantity = 45,
                        taxRatePercent = 8.5,
                        colorHex = "#F59E0B"
                    ),
                    ProductEntity(
                        name = "Avocado Toast with Sourdough",
                        category = "Food",
                        price = 9.75,
                        costPrice = 3.20,
                        sku = "FOD-AVO-01",
                        barcode = "890123450005",
                        stockQuantity = 60,
                        taxRatePercent = 8.5,
                        colorHex = "#10B981"
                    ),
                    ProductEntity(
                        name = "Thermal Receipt Rolls (6-Pack)",
                        category = "Supplies",
                        price = 14.99,
                        costPrice = 6.00,
                        sku = "SUP-ROL-01",
                        barcode = "890123450006",
                        stockQuantity = 40,
                        taxRatePercent = 8.5,
                        colorHex = "#2563EB"
                    ),
                    ProductEntity(
                        name = "Bluetooth Thermal Printer 58mm",
                        category = "Hardware",
                        price = 49.99,
                        costPrice = 28.00,
                        sku = "HDW-PRN-58",
                        barcode = "890123450007",
                        stockQuantity = 15,
                        taxRatePercent = 8.5,
                        colorHex = "#0284C7"
                    ),
                    ProductEntity(
                        name = "Reusable Coffee Tumbler 16oz",
                        category = "Merchandise",
                        price = 18.00,
                        costPrice = 7.50,
                        sku = "MRC-TUM-01",
                        barcode = "890123450008",
                        stockQuantity = 30,
                        taxRatePercent = 8.5,
                        colorHex = "#EC4899"
                    )
                )

                productDao.insertProducts(initialProducts)

                // Seed Initial Customers
                val customerDao = database.customerDao()
                val initialCustomers = listOf(
                    CustomerEntity(
                        name = "Sarah Jenkins",
                        phone = "+1 (415) 555-2940",
                        email = "sarah.j@example.com",
                        address = "384 Sutter St, Apt 4B",
                        notes = "Preferred oat milk regular",
                        totalSpent = 68.50,
                        totalVisits = 6
                    ),
                    CustomerEntity(
                        name = "Michael Chang",
                        phone = "+1 (415) 555-8392",
                        email = "mchang.design@example.com",
                        address = "101 Montgomery St, Suite 1200",
                        notes = "Corporate account billing",
                        totalSpent = 142.25,
                        totalVisits = 11
                    ),
                    CustomerEntity(
                        name = "Emma Watson",
                        phone = "+1 (415) 555-7164",
                        email = "emma.w@example.com",
                        notes = "Frequent bakery purchaser",
                        totalSpent = 39.00,
                        totalVisits = 3
                    )
                )
                customerDao.insertCustomers(initialCustomers)
            }
        }
    }
}
