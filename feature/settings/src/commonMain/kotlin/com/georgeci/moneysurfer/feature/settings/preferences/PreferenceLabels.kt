package com.georgeci.moneysurfer.feature.settings.preferences

import androidx.compose.runtime.Composable
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.preferences.AppLanguage
import com.georgeci.moneysurfer.domain.preferences.AppRegion
import com.georgeci.moneysurfer.domain.preferences.DefaultTransactionType
import com.georgeci.moneysurfer.domain.preferences.HourFormat
import com.georgeci.moneysurfer.domain.preferences.WeekStart
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_preferences_hour_12
import moneysurfer.feature.settings.generated.resources.settings_preferences_hour_24
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_georgia
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_germany
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_kazakhstan
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_poland
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_russia
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_ukraine
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_united_kingdom
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_united_states
import moneysurfer.feature.settings.generated.resources.settings_preferences_txn_expense
import moneysurfer.feature.settings.generated.resources.settings_preferences_txn_income
import moneysurfer.feature.settings.generated.resources.settings_preferences_value_system
import moneysurfer.feature.settings.generated.resources.settings_preferences_week_monday
import moneysurfer.feature.settings.generated.resources.settings_preferences_week_saturday
import moneysurfer.feature.settings.generated.resources.settings_preferences_week_sunday
import org.jetbrains.compose.resources.stringResource

/**
 * How a stored preference value is written out.
 *
 * These map the *value* to a label. The screen never renders a label without a value behind it,
 * which is the difference from the fixed `settings_preferences_*_pill` strings this replaced: a
 * translator can rename an option, but cannot change which option the user picked.
 */
@Composable
internal fun AppLanguage.label(): String = when (this) {
    AppLanguage.System -> stringResource(Res.string.settings_preferences_value_system)
    // Endonyms, deliberately not resources: a language has to be readable to the person looking
    // for it, so "Русский" stays "Русский" in the English UI and "English" stays "English" in the
    // Russian one.
    AppLanguage.English -> "English"
    AppLanguage.Russian -> "Русский"
}

@Composable
internal fun AppRegion.label(): String = stringResource(
    when (this) {
        AppRegion.System -> Res.string.settings_preferences_value_system
        AppRegion.Germany -> Res.string.settings_preferences_region_germany
        AppRegion.UnitedStates -> Res.string.settings_preferences_region_united_states
        AppRegion.UnitedKingdom -> Res.string.settings_preferences_region_united_kingdom
        AppRegion.Poland -> Res.string.settings_preferences_region_poland
        AppRegion.Ukraine -> Res.string.settings_preferences_region_ukraine
        AppRegion.Georgia -> Res.string.settings_preferences_region_georgia
        AppRegion.Kazakhstan -> Res.string.settings_preferences_region_kazakhstan
        AppRegion.Russia -> Res.string.settings_preferences_region_russia
    },
)

@Composable
internal fun WeekStart.label(): String = stringResource(
    when (this) {
        WeekStart.Monday -> Res.string.settings_preferences_week_monday
        WeekStart.Saturday -> Res.string.settings_preferences_week_saturday
        WeekStart.Sunday -> Res.string.settings_preferences_week_sunday
    },
)

@Composable
internal fun HourFormat.label(): String = stringResource(
    when (this) {
        HourFormat.System -> Res.string.settings_preferences_value_system
        HourFormat.TwelveHour -> Res.string.settings_preferences_hour_12
        HourFormat.TwentyFourHour -> Res.string.settings_preferences_hour_24
    },
)

@Composable
internal fun DefaultTransactionType.label(): String = stringResource(
    when (this) {
        DefaultTransactionType.Expense -> Res.string.settings_preferences_txn_expense
        DefaultTransactionType.Income -> Res.string.settings_preferences_txn_income
    },
)

/**
 * `EUR · €` when the code is one we stock, the bare code otherwise, and [UNKNOWN_VALUE] before the
 * stored code has arrived. A code outside the catalogue is a real possibility — the default comes
 * from the platform locale — and showing it plainly beats pretending the user picked something
 * else.
 */
internal fun currencyLabel(code: CurrencyCode?, catalogue: List<Currency>): String {
    if (code == null) return UNKNOWN_VALUE
    val known = catalogue.firstOrNull { it.code == code }
    return if (known == null) code.value else "${known.code.value} · ${known.symbol}"
}

/** Stand-in for a value the screen does not have yet — never for one it failed to store. */
internal const val UNKNOWN_VALUE: String = "—"
