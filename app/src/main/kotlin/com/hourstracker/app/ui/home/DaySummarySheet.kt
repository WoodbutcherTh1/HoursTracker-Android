package com.hourstracker.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.hourstracker.app.R
import com.hourstracker.app.ui.components.PayCard
import com.hourstracker.app.ui.components.PayCardRow
import com.hourstracker.app.ui.components.PayTierSegment
import com.hourstracker.app.ui.components.PrimaryButton
import com.hourstracker.app.ui.theme.DsText
import com.hourstracker.app.ui.theme.Palette
import com.hourstracker.app.ui.theme.Radius
import com.hourstracker.app.ui.theme.Space
import com.hourstracker.model.HistoryPeriodHelper
import com.hourstracker.model.PayFormatter
import java.util.Locale

/**
 * What the shift just closed earned: the hero amount, the hours as pay tiers, and the deductions behind net pay.
 * The amount follows the same gross/net choice as the live pay on Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySummarySheet(summary: DaySummary, showNet: Boolean, onShowNet: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val breakdown = summary.breakdown
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    var deductionsOpen by remember { mutableStateOf(false) }
    fun money(value: Double) = PayFormatter.string(value, breakdown.currencyCode, locale)
    fun hours(value: Double) = HistoryPeriodHelper.formatHoursClock(value)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Palette.background) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.lg).padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Text(text = stringResource(R.string.sum_title), style = DsText.titleScreen, color = Palette.textPrimary)

            val rows = buildList {
                add(PayCardRow(stringResource(R.string.sum_row_regular), "${money(breakdown.basePay)} · ${hours(breakdown.regularHours)}", Palette.accent))
                if (breakdown.ot125Hours > 0) add(PayCardRow(stringResource(R.string.sum_row_overtime, "125"), "${money(breakdown.ot125Pay)} · ${hours(breakdown.ot125Hours)}", Palette.ot125))
                if (breakdown.ot150Hours > 0) add(PayCardRow(stringResource(R.string.sum_row_overtime, "150"), "${money(breakdown.ot150Pay)} · ${hours(breakdown.ot150Hours)}", Palette.ot150))
                if (summary.breakMinutes > 0) add(PayCardRow(stringResource(R.string.sum_row_breaks), stringResource(R.string.settings_notifications_minutes, summary.breakMinutes.toString())))
                if (breakdown.gasAllowance > 0) add(PayCardRow(stringResource(R.string.shift_gas), money(breakdown.gasAllowance)))
            }
            PayCard(
                amount = breakdown.totalPay,
                currencyCode = breakdown.currencyCode,
                caption = stringResource(R.string.pay_gross),
                note = stringResource(R.string.sum_note_gross),
                segments = listOf(
                    PayTierSegment(breakdown.regularHours, Palette.accent),
                    PayTierSegment(breakdown.ot125Hours, Palette.ot125),
                    PayTierSegment(breakdown.ot150Hours, Palette.ot150),
                ),
                rows = rows,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                SummaryChoice(stringResource(R.string.pay_net), money(breakdown.netPay), showNet, Modifier.weight(1f)) { onShowNet(true) }
                SummaryChoice(stringResource(R.string.pay_gross), money(breakdown.totalPay), !showNet, Modifier.weight(1f)) { onShowNet(false) }
            }

            val deductions = breakdown.incomeTax + breakdown.nationalInsurance + breakdown.healthTax
            Text(
                text = stringResource(R.string.sum_deductions, money(deductions)),
                style = DsText.callout,
                color = Palette.textSecondary,
                modifier = Modifier.fillMaxWidth().clickable { deductionsOpen = !deductionsOpen },
            )
            if (deductionsOpen) {
                listOf(
                    R.string.tax_income_tax to breakdown.incomeTax,
                    R.string.tax_national_insurance to breakdown.nationalInsurance,
                    R.string.tax_health_tax to breakdown.healthTax,
                ).forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(label), style = DsText.sub, color = Palette.textSecondary)
                        Text(money(value), style = DsText.sub.copy(fontFeatureSettings = "tnum"), color = Palette.textPrimary)
                    }
                }
            }
            PrimaryButton(text = stringResource(R.string.summary_done), onClick = onDismiss)
        }
    }
}

@Composable
private fun SummaryChoice(label: String, value: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .background(if (selected) Palette.raised else Palette.card, androidx.compose.foundation.shape.RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick)
            .padding(Space.md),
    ) {
        Text(text = label, style = DsText.meta, color = Palette.textSecondary)
        Text(text = value, style = DsText.headline.copy(fontFeatureSettings = "tnum"), color = if (selected) Palette.accent else Palette.textPrimary, textAlign = TextAlign.Start)
    }
}
