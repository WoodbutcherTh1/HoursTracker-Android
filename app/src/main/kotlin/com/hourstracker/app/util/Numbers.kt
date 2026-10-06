package com.hourstracker.app.util

/** Parses a number typed by the user: accepts a decimal comma as well as a point, and ignores spaces. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()

/** Formats a number for an editable field: no trailing ".0", always a point as separator. */
fun formatDecimal(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
