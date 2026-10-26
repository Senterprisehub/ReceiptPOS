package com.example.data.model

import com.squareup.moshi.JsonClass

enum class PaymentMethod(val label: String, val iconName: String) {
    CASH("Cash", "attach_money"),
    CREDIT_CARD("Credit Card", "credit_card"),
    DEBIT_CARD("Debit Card", "payment"),
    NFC_CONTACTLESS("Contactless / NFC", "contactless"),
    UPI_QR("UPI / QR Code", "qr_code_2"),
    SPLIT("Split Payment", "call_split")
}

enum class ReceiptStatus(val label: String) {
    PAID("Paid"),
    REFUNDED("Refunded"),
    VOIDED("Voided")
}

enum class CreditNoteStatus(val label: String) {
    ACTIVE("Active"),
    PARTIALLY_USED("Partially Used"),
    REDEEMED("Redeemed")
}

enum class SyncStatus(val label: String) {
    PENDING("Pending Sync"),
    SYNCING("Syncing..."),
    SYNCED("Synced"),
    FAILED("Sync Failed")
}

enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE
}

enum class PaperWidth(val mm: Int, val charWidth: Int, val label: String) {
    MM_58(58, 32, "58mm (Small Thermal - 32 Chars)"),
    MM_80(80, 48, "80mm (Standard POS - 48 Chars)")
}

enum class ItemDiscountType(val label: String) {
    NONE("No Discount"),
    PERCENTAGE("Percentage (%)"),
    FLAT("Flat Amount")
}

@JsonClass(generateAdapter = true)
data class CartItem(
    val productId: Long = 0,
    val name: String,
    val unitPrice: Double, // Retail Price (Includes Tax)
    val costPrice: Double = 0.0, // Purchase / Cost Price
    val quantity: Int = 1,
    val taxRatePercent: Double = 0.0,
    val sku: String = "",
    val category: String = "General",
    val discountType: ItemDiscountType = ItemDiscountType.NONE,
    val discountValue: Double = 0.0
) {
    // Gross amount for this line item before discounts (Retail Price * Quantity)
    val grossTotal: Double
        get() = unitPrice * quantity

    // Total discount amount for this line item
    val itemDiscountAmount: Double
        get() = when (discountType) {
            ItemDiscountType.PERCENTAGE -> (grossTotal * (discountValue.coerceIn(0.0, 100.0) / 100.0))
            ItemDiscountType.FLAT -> (discountValue * quantity).coerceAtMost(grossTotal)
            ItemDiscountType.NONE -> 0.0
        }

    // Effective retail total after item-level discount
    val total: Double
        get() = (grossTotal - itemDiscountAmount).coerceAtLeast(0.0)

    // Net taxable base amount (after item discount)
    val taxableAmount: Double
        get() = if (taxRatePercent > 0.0) {
            total / (1.0 + (taxRatePercent / 100.0))
        } else {
            total
        }

    // Exact tax amount included inside the retail price
    val taxAmount: Double
        get() = total - taxableAmount

    // Subtotal represents the net taxable base
    val subtotal: Double
        get() = taxableAmount
}
