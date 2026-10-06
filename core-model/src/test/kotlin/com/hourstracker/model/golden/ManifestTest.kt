package com.hourstracker.model.golden

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The data must be the authoritative Mac output, and every file listed must exist (and no other). */
class ManifestTest {
    private val manifest = Golden.read("manifest.json")

    @Test
    fun `data comes from a Mac and is authoritative`() {
        assertEquals("darwin", manifest.str("platform"))
        assertTrue(manifest.bool("authoritative"))
        assertEquals(1, manifest["formatVersion"]!!.let { manifest.optInt("formatVersion") })
        assertTrue(manifest.str("iosCommit").matches(Regex("[0-9a-f]{40}")))
    }

    @Test
    fun `listed files match the files on disk`() {
        val listed = manifest["files"]!!.jsonArray.map { it.jsonObject.str("name") }.sorted()
        val onDisk = Golden.dir.list().orEmpty().filter { it.endsWith(".json") && it != "manifest.json" }.sorted()
        assertEquals(onDisk, listed)
    }

    @Test
    fun `every file records the pinned environment it was produced in`() {
        val expected = mapOf(
            "il" to Triple("Asia/Jerusalem", 1, 1),
            "ru" to Triple("Europe/Moscow", 2, 4),
            "utc" to Triple("UTC", 2, 4),
        )
        manifest["files"]!!.jsonArray.forEach { entry ->
            val name = entry.jsonObject.str("name")
            val root = Golden.read(name)
            val env = GoldenEnv(root["environment"]!!.jsonObject)
            val pinned = expected.getValue(env.id)
            assertEquals(pinned, Triple(env.timeZone, env.firstWeekday, env.minimumDaysInFirstWeek), name)
            assertEquals("darwin", env.platform, name)
        }
    }
}
