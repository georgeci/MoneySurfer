package com.georgeci.moneysurfer.feature.settings.preferences

import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.preferences.AppLanguage
import com.georgeci.moneysurfer.domain.preferences.AppRegion
import com.georgeci.moneysurfer.domain.preferences.DefaultTransactionType
import com.georgeci.moneysurfer.domain.preferences.HourFormat
import com.georgeci.moneysurfer.domain.preferences.NumberFormatStyle
import com.georgeci.moneysurfer.domain.preferences.WeekStart
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.repositories.CurrencyRepository
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private val Catalogue = listOf(
    Currency(CurrencyCode("EUR"), symbol = "€", displayName = "Euro"),
    Currency(CurrencyCode("PLN"), symbol = "zł", displayName = "Polish Zloty"),
)

private class FakeCurrencyRepository(private val currencies: List<Currency>) : CurrencyRepository {
    override fun getAll(): Flow<List<Currency>> = flowOf(currencies)
}

private fun viewModel(
    preferences: FakeUiPreferences = FakeUiPreferences(),
    currencies: List<Currency> = Catalogue,
) = PreferencesViewModel(preferences, GetCurrenciesUseCase(FakeCurrencyRepository(currencies)))

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the screen opens on the stored values, not on the defaults" {
        val preferences = FakeUiPreferences(
            appLanguage = AppLanguage.Russian,
            appRegion = AppRegion.Poland,
            defaultCurrency = CurrencyCode("PLN"),
            numberFormat = NumberFormatStyle.SpaceGroupCommaDecimal,
            weekStart = WeekStart.Sunday,
            hourFormat = HourFormat.TwelveHour,
            defaultTransactionType = DefaultTransactionType.Income,
            hideAmounts = true,
            autoCategorize = false,
            roundUpSavings = true,
        )

        val state = viewModel(preferences).currentState

        state.language shouldBe AppLanguage.Russian
        state.region shouldBe AppRegion.Poland
        state.currency shouldBe CurrencyCode("PLN")
        state.numberFormat shouldBe NumberFormatStyle.SpaceGroupCommaDecimal
        state.weekStart shouldBe WeekStart.Sunday
        state.hourFormat shouldBe HourFormat.TwelveHour
        state.defaultTransactionType shouldBe DefaultTransactionType.Income
        state.hideAmounts shouldBe true
        state.autoCategorize shouldBe false
        state.roundUp shouldBe true
    }

    "picking a value writes it through — it is still there for the next screen instance" {
        val preferences = FakeUiPreferences()
        val first = viewModel(preferences)

        first.onEvent(PreferencesEvent.OnWeekStartSelect(WeekStart.Saturday))

        preferences.weekStart.flow.first() shouldBe WeekStart.Saturday
        viewModel(preferences).currentState.weekStart shouldBe WeekStart.Saturday
    }

    "each picker writes to its own preference and to no other" {
        // All seven branches in one pass, each set to something other than its default. A branch
        // wired to the wrong handle leaves its own pref at the default *and* overwrites a
        // neighbour, and only asserting them together catches that — the per-event tests below
        // would each still pass.
        val preferences = FakeUiPreferences()
        val screen = viewModel(preferences)

        screen.onEvent(PreferencesEvent.OnLanguageSelect(AppLanguage.Russian))
        screen.onEvent(PreferencesEvent.OnRegionSelect(AppRegion.Georgia))
        screen.onEvent(PreferencesEvent.OnCurrencySelect(CurrencyCode("PLN")))
        screen.onEvent(PreferencesEvent.OnNumberFormatSelect(NumberFormatStyle.DotGroupCommaDecimal))
        screen.onEvent(PreferencesEvent.OnWeekStartSelect(WeekStart.Sunday))
        screen.onEvent(PreferencesEvent.OnHourFormatSelect(HourFormat.TwelveHour))
        screen.onEvent(PreferencesEvent.OnDefaultTransactionTypeSelect(DefaultTransactionType.Income))

        preferences.appLanguage.flow.first() shouldBe AppLanguage.Russian
        preferences.appRegion.flow.first() shouldBe AppRegion.Georgia
        preferences.defaultCurrency.flow.first() shouldBe CurrencyCode("PLN")
        preferences.numberFormat.flow.first() shouldBe NumberFormatStyle.DotGroupCommaDecimal
        preferences.weekStart.flow.first() shouldBe WeekStart.Sunday
        preferences.hourFormat.flow.first() shouldBe HourFormat.TwelveHour
        preferences.defaultTransactionType.flow.first() shouldBe DefaultTransactionType.Income
    }

    "every toggle reaches the store, so navigating away and back does not reset it" {
        val preferences = FakeUiPreferences()
        val screen = viewModel(preferences)

        screen.onEvent(PreferencesEvent.OnHideAmountsToggle(enabled = true))
        screen.onEvent(PreferencesEvent.OnAutoCategorizeToggle(enabled = false))
        screen.onEvent(PreferencesEvent.OnRoundUpToggle(enabled = true))

        preferences.hideAmounts.flow.first() shouldBe true
        preferences.autoCategorize.flow.first() shouldBe false
        preferences.roundUpSavings.flow.first() shouldBe true

        val reopened = viewModel(preferences).currentState
        reopened.hideAmounts shouldBe true
        reopened.autoCategorize shouldBe false
        reopened.roundUp shouldBe true
    }

    "a picker opens on tap and closes on the pick" {
        val screen = viewModel()

        screen.onEvent(PreferencesEvent.OnPickerOpen(PreferencePicker.Region))
        screen.currentState.activePicker shouldBe PreferencePicker.Region

        screen.onEvent(PreferencesEvent.OnRegionSelect(AppRegion.Ukraine))
        screen.currentState.activePicker shouldBe null
        screen.currentState.region shouldBe AppRegion.Ukraine
    }

    "dismissing a picker changes nothing but the sheet" {
        val preferences = FakeUiPreferences(appLanguage = AppLanguage.English)
        val screen = viewModel(preferences)

        screen.onEvent(PreferencesEvent.OnPickerOpen(PreferencePicker.Language))
        screen.onEvent(PreferencesEvent.OnPickerDismiss)

        screen.currentState.activePicker shouldBe null
        preferences.appLanguage.flow.first() shouldBe AppLanguage.English
    }

    "the currency picker is fed from the catalogue, and the pill resolves against it" {
        val screen = viewModel(FakeUiPreferences(defaultCurrency = CurrencyCode("PLN")))

        screen.currentState.currencies.map { it.code.value } shouldContainExactly listOf("EUR", "PLN")
        screen.currentState.selectedCurrency?.symbol shouldBe "zł"
    }

    "a stored currency the catalogue does not stock resolves to no entry rather than the wrong one" {
        // The default comes from the platform locale, so a code outside the short catalogue is a
        // real state — the row has to show the bare code instead of silently picking a neighbour.
        val screen = viewModel(FakeUiPreferences(defaultCurrency = CurrencyCode("CHF")))

        screen.currentState.selectedCurrency shouldBe null
        screen.currentState.currency shouldBe CurrencyCode("CHF")
    }

    "back is still a navigation side effect" {
        val screen = viewModel()

        screen.onEvent(PreferencesEvent.OnBackClick)

        screen.sideEffects.effectFlow.first() shouldBe PreferencesEffect.NavigateBack
    }
})
