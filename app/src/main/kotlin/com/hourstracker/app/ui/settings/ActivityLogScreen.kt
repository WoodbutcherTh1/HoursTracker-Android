package com.hourstracker.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hourstracker.app.LocalAppContainer
import com.hourstracker.app.R
import com.hourstracker.app.domain.ActivityLogCsv
import com.hourstracker.app.ui.components.EmptyState
import com.hourstracker.app.ui.components.HistorySkeleton
import com.hourstracker.app.ui.export.FileSharing
import com.hourstracker.app.ui.nav.TabIcons
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.app.ui.theme.dsCard
import com.hourstracker.data.AuditAction
import com.hourstracker.data.db.AuditLogEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What changed on this phone, newest first, filterable by group and exportable as CSV. Holds no personal values by design. */
@Composable
fun ActivityLogScreen(onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val entries by container.audit.observe().collectAsState(initial = null)
    var category by remember { mutableStateOf<AuditAction.Category?>(null) }
    val shown = entries?.filter { category == null || category!!.matches(it.action) }
    val backLabel = stringResource(R.string.activity_back)
    BackHandler(onBack = onClose)

    Column(modifier = Modifier.fillMaxSize().background(Palette.background).statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "‹",
                style = DsText.titleScreen,
                color = Palette.textPrimary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = backLabel }.padding(Space.sm),
            )
            Text(text = stringResource(R.string.activity_title), style = DsText.titleSection, color = Palette.textPrimary)
            Text(
                text = stringResource(R.string.activity_export_csv),
                style = DsText.headline,
                color = if (entries.isNullOrEmpty()) Palette.textTertiary else Palette.accent,
                modifier = Modifier
                    .background(Palette.card, CircleShape)
                    .clickable(enabled = !entries.isNullOrEmpty(), role = Role.Button) {
                        val all = entries ?: return@clickable
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                FileSharing.cacheFile(context, "HoursTracker_activity_log.csv").also { it.writeText(ActivityLogCsv.build(all), Charsets.UTF_8) }
                            }
                            FileSharing.share(context, file, "text/csv")
                            container.audit.log(AuditAction.EXPORT_REPORT, metadata = mapOf("kind" to "activity_log", "format" to "csv", "rows" to all.size))
                        }
                    }
                    .padding(horizontal = Space.md, vertical = Space.xs),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.md, vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            FilterChip(stringResource(R.string.activity_filter_all), category == null) { category = null }
            AuditAction.Category.entries.forEach { option ->
                FilterChip(stringResource(categoryLabel(option)), category == option) { category = option }
            }
        }
        when {
            shown == null -> HistorySkeleton(modifier = Modifier.weight(1f).fillMaxWidth().padding(Space.md))
            shown.isEmpty() -> EmptyState(
                icon = TabIcons.History,
                title = stringResource(R.string.activity_empty_title),
                body = stringResource(R.string.activity_empty_body),
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.md), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                items(shown, key = { it.id }) { entry -> LogRow(entry, locale) }
                item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = Space.xl)) }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = DsText.sub,
        color = if (selected) Palette.ink else Palette.textPrimary,
        modifier = Modifier
            .background(if (selected) Palette.accent else Palette.raised, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

@Composable
private fun LogRow(entry: AuditLogEntity, locale: Locale) {
    val time = remember(entry.timestamp, locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()))
    }
    val detail = detailText(entry)
    Column(
        modifier = Modifier.fillMaxWidth().dsCard(Radius.md).padding(Space.md).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = stringResource(actionLabel(entry.action)), style = DsText.headline, color = Palette.textPrimary)
        Text(text = time, style = DsText.meta, color = Palette.textSecondary)
        if (detail != null) Text(text = detail, style = DsText.meta, color = Palette.textTertiary)
    }
}

@Composable
private fun detailText(entry: AuditLogEntity): String? {
    val json = runCatching { JSONObject(entry.metadata ?: return null) }.getOrNull() ?: return null
    json.optJSONArray("fields")?.let { array ->
        if (array.length() > 0) return stringResource(R.string.activity_fields, (0 until array.length()).joinToString(", ") { array.getString(it) })
    }
    if (json.has("removed")) return stringResource(R.string.activity_removed, json.optInt("removed"))
    return null
}

private fun categoryLabel(category: AuditAction.Category): Int = when (category) {
    AuditAction.Category.Settings -> R.string.activity_filter_settings
    AuditAction.Category.Shifts -> R.string.activity_filter_shifts
    AuditAction.Category.Exports -> R.string.activity_filter_exports
    AuditAction.Category.Privacy -> R.string.activity_filter_privacy
}

private fun actionLabel(action: String): Int = when (action) {
    AuditAction.SETTINGS_UPDATE -> R.string.action_settings_update
    AuditAction.SHIFT_CREATE -> R.string.action_shift_create
    AuditAction.SHIFT_UPDATE -> R.string.action_shift_update
    AuditAction.SHIFT_DELETE -> R.string.action_shift_delete
    AuditAction.EXPORT_REPORT -> R.string.action_export_report
    AuditAction.EXPORT_PERSONAL_DATA -> R.string.action_export_personal_data
    AuditAction.CONSENT_ACCEPTED -> R.string.action_consent_accepted
    AuditAction.RETENTION_CLEANUP -> R.string.action_retention_cleanup
    AuditAction.LOG_PURGE -> R.string.action_audit_purge
    AuditAction.ERASURE_COMPLETED -> R.string.action_erasure_completed
    else -> R.string.action_unknown
}
