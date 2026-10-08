package com.hourstracker.app.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class DataTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `an ID number survives an encryption round trip`() {
        val key = newKey()
        val cipher = IdCipher { key }
        val stored = cipher.encrypt("123456782")
        assertEquals("123456782", cipher.decrypt(stored))
    }

    @Test
    fun `the stored text does not contain the ID and differs every time`() {
        val key = newKey()
        val cipher = IdCipher { key }
        val first = cipher.encrypt("123456782")
        val second = cipher.encrypt("123456782")
        assertFalse(first.contains("123456782"))
        assertNotEquals(first, second) // a fresh IV each time
    }

    @Test
    fun `another key or damaged text cannot be decrypted`() {
        val stored = IdCipher { newKey() }.encrypt("123456782")
        assertNull(IdCipher { newKey() }.decrypt(stored))
        val key = newKey()
        val cipher = IdCipher { key }
        val good = cipher.encrypt("123456782")
        val flipped = good.dropLast(4) + if (good.takeLast(4) == "AAAA") "BBBB" else "AAAA"
        assertNull(cipher.decrypt(flipped))
        assertNull(cipher.decrypt("not base64 !!"))
        assertNull(cipher.decrypt(""))
    }

    @Test
    fun `Hebrew is right to left and English is not`() {
        assertEquals(true, AppLanguage.Hebrew.isRtl)
        assertEquals(false, AppLanguage.English.isRtl)
        assertNull(AppLanguage.System.isRtl)
    }

    @Test
    fun `right to left detection accepts full tags and the legacy Hebrew code`() {
        assertTrue(AppLanguage.isRtlLanguage("he-IL"))
        assertTrue(AppLanguage.isRtlLanguage("iw"))
        assertTrue(AppLanguage.isRtlLanguage("ar_EG"))
        assertFalse(AppLanguage.isRtlLanguage("ru-RU"))
        assertFalse(AppLanguage.isRtlLanguage("en"))
    }

    @Test
    fun `the language tags map to the shipped resource folders`() {
        assertEquals("iw", AppLanguage.Hebrew.tag)
        assertEquals("en", AppLanguage.English.tag)
        assertEquals(listOf("System", "English", "Hebrew", "Arabic", "Russian"), AppLanguage.entries.map { it.name })
    }
}
