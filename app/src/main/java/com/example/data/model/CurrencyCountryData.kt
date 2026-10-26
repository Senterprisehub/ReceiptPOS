package com.example.data.model

data class CountryCurrencyPreset(
    val countryCode: String,
    val countryName: String,
    val flagEmoji: String,
    val currencyCode: String,
    val currencySymbol: String,
    val defaultTaxPercent: Double,
    val taxName: String,
    val defaultReceiptHeader: String,
    val defaultReceiptFooter: String
)

object CurrencyCountryPresets {
    val PRESETS = listOf(
        CountryCurrencyPreset(
            countryCode = "US",
            countryName = "United States",
            flagEmoji = "🇺🇸",
            currencyCode = "USD",
            currencySymbol = "$",
            defaultTaxPercent = 8.25,
            taxName = "Sales Tax",
            defaultReceiptHeader = "THANK YOU FOR SHOPPING WITH US",
            defaultReceiptFooter = "Goods returnable within 30 days.\nHave a wonderful day!"
        ),
        CountryCurrencyPreset(
            countryCode = "GB",
            countryName = "United Kingdom",
            flagEmoji = "🇬🇧",
            currencyCode = "GBP",
            currencySymbol = "£",
            defaultTaxPercent = 20.0,
            taxName = "VAT (20%)",
            defaultReceiptHeader = "OFFICIAL VAT INVOICE / RECEIPT",
            defaultReceiptFooter = "VAT Reg No: GB984321098\nRefunds with valid receipt in 28 days."
        ),
        CountryCurrencyPreset(
            countryCode = "EU",
            countryName = "European Union (Eurozone)",
            flagEmoji = "🇪🇺",
            currencyCode = "EUR",
            currencySymbol = "€",
            defaultTaxPercent = 19.0,
            taxName = "MwSt / VAT (19%)",
            defaultReceiptHeader = "VIELEN DANK FÜR IHREN EINKAUF",
            defaultReceiptFooter = "USt-IdNr / TVA / CIF valid.\nVielen Dank für Ihren Besuch!"
        ),
        CountryCurrencyPreset(
            countryCode = "IN",
            countryName = "India",
            flagEmoji = "🇮🇳",
            currencyCode = "INR",
            currencySymbol = "₹",
            defaultTaxPercent = 18.0,
            taxName = "GST (18%)",
            defaultReceiptHeader = "TAX INVOICE - GST COMPLIANT",
            defaultReceiptFooter = "GSTIN: 27AAAAA0000A1Z5\nThank you for visiting! Please visit again."
        ),
        CountryCurrencyPreset(
            countryCode = "CA",
            countryName = "Canada",
            flagEmoji = "🇨🇦",
            currencyCode = "CAD",
            currencySymbol = "CA$",
            defaultTaxPercent = 13.0,
            taxName = "HST / GST (13%)",
            defaultReceiptHeader = "OFFICIAL PURCHASE RECEIPT",
            defaultReceiptFooter = "GST/HST: 123456789RT0001\nMerci pour votre visite / Thank you!"
        ),
        CountryCurrencyPreset(
            countryCode = "AU",
            countryName = "Australia",
            flagEmoji = "🇦🇺",
            currencyCode = "AUD",
            currencySymbol = "A$",
            defaultTaxPercent = 10.0,
            taxName = "GST (10%)",
            defaultReceiptHeader = "TAX INVOICE",
            defaultReceiptFooter = "ABN: 12 345 678 901\nIncludes 10% GST. Thanks for your custom!"
        ),
        CountryCurrencyPreset(
            countryCode = "JP",
            countryName = "Japan",
            flagEmoji = "🇯🇵",
            currencyCode = "JPY",
            currencySymbol = "¥",
            defaultTaxPercent = 10.0,
            taxName = "消費税 (10%)",
            defaultReceiptHeader = "毎度ありがとうございます (領収書)",
            defaultReceiptFooter = "登録番号: T1234567890123\nまたのご来店を心よりお待ちしております。"
        ),
        CountryCurrencyPreset(
            countryCode = "AE",
            countryName = "United Arab Emirates",
            flagEmoji = "🇦🇪",
            currencyCode = "AED",
            currencySymbol = "AED",
            defaultTaxPercent = 5.0,
            taxName = "VAT (5%)",
            defaultReceiptHeader = "TAX INVOICE / فاتورة ضريبية",
            defaultReceiptFooter = "TRN: 100234567800003\nThank you for shopping / شكرا لزيارتكم"
        ),
        CountryCurrencyPreset(
            countryCode = "SA",
            countryName = "Saudi Arabia",
            flagEmoji = "🇸🇦",
            currencyCode = "SAR",
            currencySymbol = "SAR",
            defaultTaxPercent = 15.0,
            taxName = "VAT (15%) - ضريبة",
            defaultReceiptHeader = "فاتورة ضريبية مبسطة (SIMPLIFIED TAX INVOICE)",
            defaultReceiptFooter = "الرقم الضريبي: 300123456700003\nZATCA Compliant Electronic Receipt"
        ),
        CountryCurrencyPreset(
            countryCode = "SG",
            countryName = "Singapore",
            flagEmoji = "🇸🇬",
            currencyCode = "SGD",
            currencySymbol = "S$",
            defaultTaxPercent = 9.0,
            taxName = "GST (9%)",
            defaultReceiptHeader = "TAX INVOICE",
            defaultReceiptFooter = "GST Reg: M91234567X\nThank you for dining / shopping with us!"
        ),
        CountryCurrencyPreset(
            countryCode = "MY",
            countryName = "Malaysia",
            flagEmoji = "🇲🇾",
            currencyCode = "MYR",
            currencySymbol = "RM",
            defaultTaxPercent = 8.0,
            taxName = "SST (8%)",
            defaultReceiptHeader = "SALES TAX INVOICE / RESIT",
            defaultReceiptFooter = "SST ID: W10-1808-12345678\nTerima Kasih / Thank you!"
        ),
        CountryCurrencyPreset(
            countryCode = "PH",
            countryName = "Philippines",
            flagEmoji = "🇵🇭",
            currencyCode = "PHP",
            currencySymbol = "₱",
            defaultTaxPercent = 12.0,
            taxName = "VAT (12%)",
            defaultReceiptHeader = "OFFICIAL RECEIPT / BIR COMPLIANT",
            defaultReceiptFooter = "TIN: 000-123-456-000\nSalamat po sa inyong pagtangkilik!"
        ),
        CountryCurrencyPreset(
            countryCode = "NZ",
            countryName = "New Zealand",
            flagEmoji = "🇳🇿",
            currencyCode = "NZD",
            currencySymbol = "NZ$",
            defaultTaxPercent = 15.0,
            taxName = "GST (15%)",
            defaultReceiptHeader = "TAX INVOICE",
            defaultReceiptFooter = "GST No: 123-456-789\nKia ora! Thanks for shopping with us."
        ),
        CountryCurrencyPreset(
            countryCode = "CH",
            countryName = "Switzerland",
            flagEmoji = "🇨🇭",
            currencyCode = "CHF",
            currencySymbol = "CHF",
            defaultTaxPercent = 8.1,
            taxName = "MWST (8.1%)",
            defaultReceiptHeader = "QUITTUNG / REÇU / SCONTRINO",
            defaultReceiptFooter = "CHE-123.456.789 MWST\nVielen Dank / Merci beaucoup!"
        ),
        CountryCurrencyPreset(
            countryCode = "MX",
            countryName = "Mexico",
            flagEmoji = "🇲🇽",
            currencyCode = "MXN",
            currencySymbol = "Mex$",
            defaultTaxPercent = 16.0,
            taxName = "IVA (16%)",
            defaultReceiptHeader = "COMPROBANTE SIMPLIFICADO DE VENTA",
            defaultReceiptFooter = "RFC: EKA980812K51\n¡Gracias por su compra!"
        ),
        CountryCurrencyPreset(
            countryCode = "BR",
            countryName = "Brazil",
            flagEmoji = "🇧🇷",
            currencyCode = "BRL",
            currencySymbol = "R$",
            defaultTaxPercent = 17.0,
            taxName = "ICMS / Impostos (17%)",
            defaultReceiptHeader = "CUPOM NÃO FISCAL / COMPROVANTE",
            defaultReceiptFooter = "CNPJ: 00.123.456/0001-00\nObrigado pela preferência!"
        ),
        CountryCurrencyPreset(
            countryCode = "ZA",
            countryName = "South Africa",
            flagEmoji = "🇿🇦",
            currencyCode = "ZAR",
            currencySymbol = "R",
            defaultTaxPercent = 15.0,
            taxName = "VAT (15%)",
            defaultReceiptHeader = "TAX INVOICE",
            defaultReceiptFooter = "VAT Reg: 4012345678\nThank you for your valued support!"
        ),
        CountryCurrencyPreset(
            countryCode = "ID",
            countryName = "Indonesia",
            flagEmoji = "🇮🇩",
            currencyCode = "IDR",
            currencySymbol = "Rp",
            defaultTaxPercent = 11.0,
            taxName = "PPN (11%)",
            defaultReceiptHeader = "STRUK PEMBELIAN RESMI",
            defaultReceiptFooter = "NPWP: 01.234.567.8-012.000\nTerima kasih atas kunjungan Anda!"
        ),
        CountryCurrencyPreset(
            countryCode = "TH",
            countryName = "Thailand",
            flagEmoji = "🇹🇭",
            currencyCode = "THB",
            currencySymbol = "฿",
            defaultTaxPercent = 7.0,
            taxName = "VAT (7%)",
            defaultReceiptHeader = "ใบเสร็จรับเงิน / TAX INVOICE",
            defaultReceiptFooter = "Tax ID: 0105551234567\nขอบคุณที่ใช้บริการ / Thank you!"
        ),
        CountryCurrencyPreset(
            countryCode = "VN",
            countryName = "Vietnam",
            flagEmoji = "🇻🇳",
            currencyCode = "VND",
            currencySymbol = "₫",
            defaultTaxPercent = 10.0,
            taxName = "VAT (10%)",
            defaultReceiptHeader = "HÓA ĐƠN BÁN LẺ",
            defaultReceiptFooter = "MST: 0101234567\nCảm ơn quý khách! Hẹn gặp lại."
        ),
        CountryCurrencyPreset(
            countryCode = "TR",
            countryName = "Turkey",
            flagEmoji = "🇹🇷",
            currencyCode = "TRY",
            currencySymbol = "₺",
            defaultTaxPercent = 20.0,
            taxName = "KDV (%20)",
            defaultReceiptHeader = "BİLGİ FİŞİ / SATIŞ MAKBUZU",
            defaultReceiptFooter = "VKN: 1234567890\nBizi tercih ettiğiniz için teşekkür ederiz."
        ),
        CountryCurrencyPreset(
            countryCode = "NG",
            countryName = "Nigeria",
            flagEmoji = "🇳🇬",
            currencyCode = "NGN",
            currencySymbol = "₦",
            defaultTaxPercent = 7.5,
            taxName = "VAT (7.5%)",
            defaultReceiptHeader = "OFFICIAL SALES RECEIPT",
            defaultReceiptFooter = "TIN: 12345678-0001\nThank you for your patronage!"
        )
    )

    fun findPresetByCountry(countryCode: String): CountryCurrencyPreset? {
        return PRESETS.firstOrNull { it.countryCode.equals(countryCode, ignoreCase = true) }
    }

    fun findPresetByCurrency(currencyCode: String): CountryCurrencyPreset? {
        return PRESETS.firstOrNull { it.currencyCode.equals(currencyCode, ignoreCase = true) }
    }

    fun findPresetBySymbol(symbol: String): CountryCurrencyPreset? {
        return PRESETS.firstOrNull { it.currencySymbol == symbol }
    }
}
