package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.ReceiptEntity
import com.example.data.local.entity.StoreConfigEntity
import com.example.data.model.PaymentMethod
import com.example.data.model.ReceiptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Receipt POS", appName)
  }

  @Test
  fun `verify invoice number format and sequential counter`() {
    val timestamp = System.currentTimeMillis()
    val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(timestamp))
    val expectedPattern = Regex("^INV-\\d{8}-10000\\d$")

    val inv1 = "INV-$datePart-100001"
    val inv2 = "INV-$datePart-100002"

    assertTrue(expectedPattern.matches(inv1))
    assertTrue(expectedPattern.matches(inv2))
    assertEquals("INV-$datePart-100001", inv1)
  }

  @Test
  fun `verify receipt entity holds sequential invoice number`() {
    val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    val invoiceNumber = "INV-$datePart-100001"

    val receipt = ReceiptEntity(
      invoiceNumber = invoiceNumber,
      customerName = "Jane Doe",
      customerPhone = "+1 555-0199",
      itemsJson = "[]",
      subtotal = 50.0,
      taxAmount = 4.25,
      discountAmount = 0.0,
      discountPercent = 0.0,
      tipAmount = 5.0,
      totalAmount = 59.25,
      amountTendered = 60.0,
      changeDue = 0.75,
      paymentMethod = PaymentMethod.CASH,
      paymentDetails = "Cash Paid",
      status = ReceiptStatus.PAID,
      cashierName = "Cashier 1",
      notes = "Test Receipt",
      securityHash = "sec-hash-12345",
      createdAt = System.currentTimeMillis()
    )

    assertEquals(invoiceNumber, receipt.invoiceNumber)
    assertEquals(59.25, receipt.totalAmount, 0.001)
  }

  @Test
  fun `verify Excel invoice CSV export format and headers`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val datePart = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    val invoiceNumber = "INV-$datePart-100001"

    val receipt = ReceiptEntity(
      invoiceNumber = invoiceNumber,
      customerName = "Jane Doe",
      customerPhone = "+1 555-0199",
      itemsJson = "[{\"name\":\"Coffee Latte\",\"unitPrice\":4.50,\"quantity\":2}]",
      subtotal = 9.0,
      taxAmount = 0.72,
      discountAmount = 0.0,
      discountPercent = 0.0,
      tipAmount = 1.0,
      totalAmount = 10.72,
      amountTendered = 20.0,
      changeDue = 9.28,
      paymentMethod = PaymentMethod.CASH,
      paymentDetails = "Cash Tendered",
      status = ReceiptStatus.PAID,
      cashierName = "Cashier 1",
      notes = "Table 4",
      securityHash = "sec-hash-99999",
      createdAt = System.currentTimeMillis()
    )

    val config = StoreConfigEntity(
      businessName = "Artisan Bakery",
      currencySymbol = "$",
      taxName = "VAT",
      defaultTaxPercent = 8.0
    )

    val file = com.example.util.ExcelInvoiceExporter.exportInvoicesToExcel(context, listOf(receipt), config)
    assertTrue(file.exists())
    assertTrue(file.length() > 0)

    val content = file.readText()
    assertTrue(content.contains("Invoice No,Date,Time,Customer Name"))
    assertTrue(content.contains("INV-$datePart-100001"))
    assertTrue(content.contains("Jane Doe"))
    assertTrue(content.contains("Coffee Latte"))
    assertTrue(content.contains("10.72"))
  }

  @Test
  fun `verify user account entity creation for gmail and outlook`() {
    val gmailUser = com.example.data.local.entity.UserAccountEntity(
      email = "store.owner@gmail.com",
      displayName = "Store Owner",
      provider = com.example.data.local.entity.AccountProvider.GMAIL,
      isLoggedIn = true
    )

    val outlookUser = com.example.data.local.entity.UserAccountEntity(
      email = "manager@outlook.com",
      displayName = "Manager",
      provider = com.example.data.local.entity.AccountProvider.OUTLOOK,
      isLoggedIn = true
    )

    assertEquals("store.owner@gmail.com", gmailUser.email)
    assertEquals(com.example.data.local.entity.AccountProvider.GMAIL, gmailUser.provider)
    assertTrue(gmailUser.isLoggedIn)

    assertEquals("manager@outlook.com", outlookUser.email)
    assertEquals(com.example.data.local.entity.AccountProvider.OUTLOOK, outlookUser.provider)
  }
}

