package com.portfolioos.mobile.util

import java.text.NumberFormat
import java.util.Locale

fun formatInr(valNum: Double, showDecimals: Boolean = false): String {
    val locale = Locale("en", "IN")
    val formatter = NumberFormat.getCurrencyInstance(locale).apply {
        maximumFractionDigits = if (showDecimals) 2 else 0
        minimumFractionDigits = if (showDecimals) 2 else 0
    }
    val formatted = formatter.format(valNum)
    return if (formatted.startsWith("INR")) {
        formatted.replace("INR", "₹").trim()
    } else {
        formatted
    }
}

fun formatInrStr(valStr: String?): String {
    if (valStr.isNullOrBlank()) return "₹0"
    val cleaned = valStr.replace("₹", "").replace(",", "").trim()
    val dbl = cleaned.toDoubleOrNull() ?: return valStr
    return formatInr(dbl, showDecimals = false)
}
