package com.example.security

import com.example.data.local.entity.ReceiptEntity
import java.security.MessageDigest

object SecurityManager {
    /**
     * Generates a tamper-proof SHA-256 hash for the receipt
     */
    fun generateReceiptHash(
        invoiceNumber: String,
        createdAt: Long,
        totalAmount: Double,
        cashierName: String,
        itemsJson: String
    ): String {
        val raw = "$invoiceNumber|$createdAt|${String.format("%.2f", totalAmount)}|$cashierName|$itemsJson"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validates if a receipt's tamper hash matches its current financial data
     */
    fun verifyReceiptIntegrity(receipt: ReceiptEntity): Boolean {
        if (receipt.securityHash.isEmpty()) return true
        val computed = generateReceiptHash(
            receipt.invoiceNumber,
            receipt.createdAt,
            receipt.totalAmount,
            receipt.cashierName,
            receipt.itemsJson
        )
        return computed.equals(receipt.securityHash, ignoreCase = true)
    }

    /**
     * Verifies user-entered PIN against master PIN
     */
    fun verifyPin(enteredPin: String, masterPin: String): Boolean {
        return enteredPin.trim() == masterPin.trim()
    }
}
