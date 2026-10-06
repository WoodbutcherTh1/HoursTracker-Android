package com.hourstracker.app.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UtilTest {
    @Test
    fun `a valid Israeli ID passes the checksum`() {
        assertTrue(IsraeliIdValidator.isChecksumValid("000000018"))
        assertTrue(IsraeliIdValidator.isChecksumValid("18"))
    }

    @Test
    fun `a wrong check digit warns, empty input does not`() {
        assertTrue(IsraeliIdValidator.shouldWarn("123456789"))
        assertFalse(IsraeliIdValidator.shouldWarn(""))
        assertFalse(IsraeliIdValidator.shouldWarn("abc"))
    }

    @Test
    fun `decimal text accepts a comma and rejects garbage`() {
        assertEquals(8.6, parseDecimal("8,6"))
        assertEquals(1234.5, parseDecimal(" 1 234.5 "))
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal(""))
    }

    @Test
    fun `editable numbers drop a trailing zero`() {
        assertEquals("55", formatDecimal(55.0))
        assertEquals("8.6", formatDecimal(8.6))
    }
}
