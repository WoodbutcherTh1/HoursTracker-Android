package com.hourstracker.app.util

/** Israeli teudat zehut checksum (Luhn-like). Used for soft warnings only: it never blocks saving. */
object IsraeliIdValidator {
    /** Empty or non-digit input gives no warning. */
    fun shouldWarn(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        return digits.isNotEmpty() && !isChecksumValid(digits)
    }

    fun isChecksumValid(digits: String): Boolean {
        val padded = "0".repeat(maxOf(0, 9 - digits.length)) + digits
        if (padded.length != 9 || !padded.all { it.isDigit() }) return false
        var sum = 0
        padded.forEachIndexed { index, char ->
            var value = char.digitToInt() * (if (index % 2 == 0) 1 else 2)
            if (value > 9) value -= 9
            sum += value
        }
        return sum % 10 == 0
    }
}
