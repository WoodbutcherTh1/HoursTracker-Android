package com.hourstracker.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hourstracker.app.LocalAppContainer
import com.hourstracker.app.R
import com.hourstracker.app.ui.components.FormCard
import com.hourstracker.app.ui.components.RowDivider
import com.hourstracker.app.ui.components.SectionHeader
import com.hourstracker.app.ui.components.StepperRow
import com.hourstracker.app.ui.components.ToggleRow
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Space

/**
 * The three reminders, each with its own switch. They apply immediately and are not part of the Save button, like
 * the app language: they are choices about the app, not about pay.
 */
@Composable
fun NotificationSettingsSection() {
    val settings = LocalAppContainer.current.settings
    val context = LocalContext.current
    val breakEnabled by settings.breakRemindersEnabled.collectAsState()
    val breakMinutes by settings.breakReminderMinutesBefore.collectAsState()
    val shiftEnabled by settings.shiftReminderEnabled.collectAsState()
    val shiftMinutes by settings.shiftReminderMinutesBefore.collectAsState()
    val summaryEnabled by settings.shiftSummaryEnabled.collectAsState()
    val minus = stringResource(R.string.a11y_decrease)
    val plus = stringResource(R.string.a11y_increase)

    // Android 13 and later ask before any notification can show; turning a reminder on is the natural moment.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    SectionHeader(stringResource(R.string.settings_notifications_section))
    FormCard {
        ToggleRow(stringResource(R.string.settings_break_reminders), breakEnabled, {
            settings.saveBreakRemindersEnabled(it)
            if (it) ensureNotificationPermission()
        })
        if (breakEnabled) {
            RowDivider()
            StepperRow(
                label = stringResource(R.string.settings_break_reminders_minutes),
                value = breakMinutes,
                range = 1..30,
                onValueChange = settings::saveBreakReminderMinutesBefore,
                minusDescription = minus,
                plusDescription = plus,
            )
        }
        RowDivider()
        ToggleRow(stringResource(R.string.settings_shift_reminder), shiftEnabled, {
            settings.saveShiftReminderEnabled(it)
            if (it) ensureNotificationPermission()
        })
        if (shiftEnabled) {
            RowDivider()
            StepperRow(
                label = stringResource(R.string.settings_shift_reminder_minutes),
                value = shiftMinutes,
                range = 5..120,
                step = 5,
                onValueChange = settings::saveShiftReminderMinutesBefore,
                minusDescription = minus,
                plusDescription = plus,
            )
        }
        RowDivider()
        ToggleRow(stringResource(R.string.settings_shift_summary), summaryEnabled, {
            settings.saveShiftSummaryEnabled(it)
            if (it) ensureNotificationPermission()
        })
        Text(
            text = stringResource(R.string.settings_notifications_hint),
            style = DsText.meta,
            color = Palette.textSecondary,
            modifier = Modifier.padding(Space.md),
        )
    }
}
