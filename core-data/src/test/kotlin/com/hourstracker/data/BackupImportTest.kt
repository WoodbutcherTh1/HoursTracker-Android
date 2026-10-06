package com.hourstracker.data

import com.hourstracker.data.backup.BackupFormatException
import com.hourstracker.data.backup.BackupImporter
import com.hourstracker.data.backup.BackupJson
import com.hourstracker.data.backup.BackupPayload
import com.hourstracker.data.backup.ImportOptions
import com.hourstracker.model.DayType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneId

/** A backup shaped like the iOS `user_backups.payload`: settings plus every shift, ISO-8601 dates in UTC. */
private fun backupText(
    sessionExtra: String = "",
    settingsExtra: String = "",
    hourlyRate: String = "55",
): String = """
{
  "settings": {
    "workplaceName": "Riverside Café", "contractorName": null, "workerFullName": "Alex Morgan",
    "workerIDNumber": "123456782", "employeeNumber": "EMP-2291",
    "hourlyRate": $hourlyRate, "dailyGasAllowance": 35, "standardDayHours": 8.6, "ot125HoursCap": 2,
    "locationLatitude": 32.0853, "locationLongitude": 34.7818, "locationRadiusMeters": 150,
    "maritalStatus": "married", "hasChildren": true, "numberOfChildren": 2, "spouseEmployed": false,
    "payrollStartDay": 10, "restDayWeekday": 7, "defaultBreakMinutes": 30, "breaksArePaid": false,
    "nightStandardDayHours": 7, "weeklyStandardHours": 42, "weeklyOvertimeCapHours": 12, "currencyCode": "ILS",
    "arrivalRemindersEnabled": false, "expectedShiftStartHour": 8, "expectedShiftStartMinute": 0,
    "leaveDays": [{"id": "6F9619FF-8B86-D011-B42D-00C04FC964FF", "date": "2026-07-19T21:00:00Z", "kind": "vacation"}],
    "additionalWorkplaces": [],
    "modifiedAt": "2026-09-28T09:30:00Z"$settingsExtra
  },
  "sessions": [
    {
      "id": "00000000-0000-0000-0000-000000000001", "date": "2026-07-14T21:00:00Z",
      "clockIn": "2026-07-15T05:00:00Z", "clockOut": "2026-07-15T13:30:00Z",
      "breaks": [{"start": "2026-07-15T09:00:00Z", "end": "2026-07-15T09:30:00Z"}],
      "dayType": "regular", "modifiedAt": "2026-07-15T13:31:00Z", "breakMinutes": 30,
      "isAIImported": false, "isNightShift": false, "isManualEntry": true$sessionExtra
    },
    {
      "id": "00000000-0000-0000-0000-000000000002", "date": "2026-07-15T21:00:00Z",
      "clockIn": "2026-07-16T19:00:00Z", "clockOut": null, "breaks": [],
      "dayType": "sick", "modifiedAt": "2026-07-16T19:00:00Z", "breakMinutes": 0,
      "isAIImported": true, "isNightShift": true, "isManualEntry": false, "notes": "flu"
    }
  ]
}
""".trimIndent()

private val jerusalem = ZoneId.of("Asia/Jerusalem")

@RunWith(RobolectricTestRunner::class)
class BackupImportTest {
    @Test
    fun `the iOS payload reads into the data model`() {
        val payload = BackupJson.parse(backupText())
        assertEquals("Riverside Café", payload.settings.workplaceName)
        assertNull(payload.settings.contractorName)
        assertEquals("123456782", payload.settings.workerIdNumber)
        assertEquals(55.0, payload.settings.hourlyRate, 0.0)
        assertEquals(32.0853, payload.settings.locationLatitude!!, 0.0)
        assertEquals(1, payload.settings.leaveDays.size)
        assertEquals(2, payload.sessions.size)
        val first = payload.sessions[0]
        assertEquals(Instant.parse("2026-07-15T05:00:00Z"), first.clockIn)
        assertEquals(1, first.breaks.size)
        assertEquals("regular", first.dayType)
        assertNull(payload.sessions[1].clockOut)
        assertEquals("flu", payload.sessions[1].notes)
        assertTrue(payload.sessions[1].isAIImported)
    }

    @Test
    fun `absent optional keys take the iOS defaults`() {
        val minimal = """
            {"settings": {"workplaceName": "W", "workerFullName": "N", "workerIDNumber": "", "employeeNumber": "",
              "hourlyRate": 50, "dailyGasAllowance": 20, "standardDayHours": 8.6, "ot125HoursCap": 2, "locationRadiusMeters": 150},
             "sessions": [{"id": "00000000-0000-0000-0000-000000000009", "date": "2026-07-14T21:00:00Z",
               "clockIn": "2026-07-15T05:00:00Z", "isManualEntry": false}]}
        """.trimIndent()
        val payload = BackupJson.parse(minimal)
        assertEquals("single", payload.settings.maritalStatus)
        assertEquals(42.0, payload.settings.weeklyStandardHours, 0.0)
        assertEquals(12.0, payload.settings.weeklyOvertimeCapHours, 0.0)
        assertEquals("ILS", payload.settings.currencyCode)
        assertEquals(7, payload.settings.restDayWeekday)
        assertEquals("regular", payload.sessions[0].dayType)
        assertTrue(payload.sessions[0].breaks.isEmpty())
        assertFalse(payload.sessions[0].isNightShift)
    }

    @Test
    fun `unknown keys are ignored so a full export is accepted too`() {
        val payload = BackupJson.parse(backupText(sessionExtra = ", \"somethingNew\": 1", settingsExtra = ", \"futureFlag\": true"))
        assertEquals(2, payload.sessions.size)
    }

    private fun assertRejected(text: String) {
        try {
            BackupJson.parse(text)
            fail("should have been rejected")
        } catch (_: BackupFormatException) {
            // expected
        }
    }

    @Test
    fun `a missing required key, a wrong type, a bad date or an unknown day type is rejected`() {
        assertRejected(backupText().replace("\"hourlyRate\": 55,", ""))
        assertRejected(backupText(hourlyRate = "\"55\""))
        assertRejected(backupText().replace("2026-07-15T05:00:00Z", "15/07/2026 08:00"))
        assertRejected(backupText().replace("\"regular\"", "\"vacationDay\""))
        assertRejected("not json")
        assertRejected("{\"sessions\": []}")
    }

    @Test
    fun `a malformed leave day does not make the whole backup unreadable`() {
        val broken = backupText().replace("\"kind\": \"vacation\"", "\"kind\": \"nonsense\"")
        assertTrue(BackupJson.parse(broken).settings.leaveDays.isEmpty())
    }

    @Test
    fun `a shift recorded in Israel stays on its day on a phone in Israel`() {
        val result = BackupImporter.import(BackupJson.parse(backupText()), ImportOptions(deviceZone = jerusalem))
        val date = result.shifts[0].session.date
        assertEquals(Instant.parse("2026-07-14T21:00:00Z"), date)
        assertEquals(java.time.LocalDate.of(2026, 7, 15), date.atZone(jerusalem).toLocalDate())
    }

    @Test
    fun `the day does not slip when the phone is in another zone`() {
        // A phone on UTC reads 2026-07-14T21:00:00Z as 14 July if it trusts the instant. The shift belongs to 15 July.
        val utc = ZoneId.of("UTC")
        val result = BackupImporter.import(BackupJson.parse(backupText()), ImportOptions(deviceZone = utc))
        assertEquals(Instant.parse("2026-07-15T00:00:00Z"), result.shifts[0].session.date)

        val newYork = ZoneId.of("America/New_York")
        val ny = BackupImporter.import(BackupJson.parse(backupText()), ImportOptions(deviceZone = newYork))
        assertEquals(java.time.LocalDate.of(2026, 7, 15), ny.shifts[0].session.date.atZone(newYork).toLocalDate())
    }

    @Test
    fun `a backup from another zone is read in the zone of the phone`() {
        // 2026-07-15T00:00:00Z is not a midnight in Israel, so it did not come from an Israeli phone.
        val text = backupText().replace("2026-07-14T21:00:00Z", "2026-07-15T00:00:00Z")
        val result = BackupImporter.import(BackupJson.parse(text), ImportOptions(deviceZone = ZoneId.of("UTC")))
        assertEquals(Instant.parse("2026-07-15T00:00:00Z"), result.shifts[0].session.date)
    }

    @Test
    fun `spring and autumn days in Israel keep their calendar day`() {
        // 2026-03-27 is the spring change (Friday); its midnight is still +02:00. 2026-10-25 is the autumn change.
        val spring = java.time.LocalDate.of(2026, 3, 27).atStartOfDay(jerusalem).toInstant()
        val autumn = java.time.LocalDate.of(2026, 10, 25).atStartOfDay(jerusalem).toInstant()
        val options = ImportOptions(deviceZone = ZoneId.of("UTC"))
        assertEquals(Instant.parse("2026-03-27T00:00:00Z"), BackupImporter.normalizeDate(spring, options))
        assertEquals(Instant.parse("2026-10-25T00:00:00Z"), BackupImporter.normalizeDate(autumn, options))
    }

    @Test
    fun `settings and shifts map to the app model and unsupported data is counted`() {
        val result = BackupImporter.import(BackupJson.parse(backupText()), ImportOptions(deviceZone = jerusalem))
        assertEquals(55.0, result.settings.hourlyRate, 0.0)
        assertEquals(10, result.settings.payrollStartDay)
        assertEquals(2, result.settings.numberOfChildren)
        assertEquals(DayType.Sick, result.shifts[1].session.dayType)
        assertTrue(result.shifts[1].session.isNightShift)
        assertEquals(1, result.unsupportedLeaveDays)
        assertEquals(0, result.unsupportedWorkplaces)
        assertEquals("EMP-2291", result.settingsBackup.employeeNumber)
    }

    @Test
    fun `export then import gives the same data`() {
        val original = BackupJson.parse(backupText())
        val imported = BackupImporter.import(original, ImportOptions(deviceZone = jerusalem))
        val exported = BackupPayload(original.settings, imported.shifts.map(BackupImporter::toBackupSession))
        val again = BackupJson.parse(BackupJson.write(exported))
        assertEquals(original.sessions, again.sessions)
        assertEquals(original.settings, again.settings)
    }

    @Test
    fun `export writes iOS compatible text`() {
        val payload = BackupJson.parse(backupText())
        val text = BackupJson.write(payload)
        // Whole-second ISO-8601 in UTC, as Swift's .iso8601 strategy reads it; ids upper-case like Swift's UUID text.
        assertTrue(text, text.contains("\"clockIn\":\"2026-07-15T05:00:00Z\""))
        assertTrue(text, text.contains("\"id\":\"00000000-0000-0000-0000-000000000001\""))
        assertTrue(text, text.contains("\"workerIDNumber\":\"123456782\""))
        assertFalse(text, text.contains("\"clockOut\":null"))
    }

    @Test
    fun `a shift survives the database and the backup together`() {
        // Import, store through the same mappers the repository uses, and write the backup again.
        val payload = BackupJson.parse(backupText())
        val shifts = BackupImporter.import(payload, ImportOptions(deviceZone = jerusalem)).shifts
        val rewritten = BackupJson.parse(BackupJson.write(BackupPayload(payload.settings, shifts.map(BackupImporter::toBackupSession))))
        assertEquals(payload.sessions.map { it.id }, rewritten.sessions.map { it.id })
        assertEquals(payload.sessions.map { it.breaks }, rewritten.sessions.map { it.breaks })
    }
}
