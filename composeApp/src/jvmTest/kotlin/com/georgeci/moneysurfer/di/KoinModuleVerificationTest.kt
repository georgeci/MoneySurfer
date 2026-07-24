package com.georgeci.moneysurfer.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.backup.AppRestarter
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.feature.category.picker.CategoryPickerVariant
import com.georgeci.moneysurfer.navigation.GoalContributionMode
import com.georgeci.moneysurfer.sync.db.SyncDatabase
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Walks the full DI graph at test-time and fails the build if any `@Single` /
 * `@KoinViewModel` constructor parameter has no matching binding.
 *
 * Catches the same class of bug that produced the runtime
 * `InstanceCreationException: SignInViewModel → LoginUseCase` we hit when the
 * `WorkspaceInviteDao` factory was missing from `DataModule` — it now surfaces
 * here as a pre-merge test failure instead of a crash on the SignIn screen.
 *
 * Implementation note: koin-compiler 1.0.0-RC1's `compileSafety = true` would
 * catch this at compile time, but doesn't see cross-module annotations and
 * false-flags every `koinViewModel<X>()` call site whose VM lives in another
 * Gradle module. Re-evaluate when the plugin stabilises.
 *
 * Unlike `checkModules`, [verify] only walks signatures via reflection — it
 * never actually instantiates anything, so platform-specific bindings like
 * Room / DataStore don't need real implementations, just typed placeholders.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinModuleVerificationTest {

    @Test
    fun `app module graph resolves`() {
        val rootModule = module {
            includes(AppModule().module(), OnlineKoinApp().module(), testPlatformModule)
        }
        rootModule.verify(
            extraTypes = listOf(
                // parametersOf(...) types — verify() can't see them at the
                // declaration site, so we list them explicitly.
                WorkspaceId::class,
                UserId::class,
                WorkspaceInviteId::class,
                AccountId::class,
                CategoryId::class,
                TransactionId::class,
                CategoryType::class,
                // The category chooser passes which of the two picker layouts to open.
                CategoryPickerVariant::class,
                // Budget details/edit screens pass the budget id via parametersOf.
                BudgetId::class,
                // Goal screens pass the goal id, and the contribution screen its
                // add/withdraw mode, via parametersOf.
                GoalId::class,
                GoalContributionMode::class,
                // WorkspaceSelectorScreen passes a Boolean (`showActions`) via parametersOf.
                Boolean::class,
                // AccountCreationScreen passes the onboarding's pre-selected AccountType.
                AccountType::class,
            ),
        )
    }
}

/**
 * Stubs for platform singletons that real `platformModule` actuals build with
 * Android / iOS / Room / DataStore APIs unavailable on plain JVM. `verify()` only
 * checks the shape of the graph — it never calls these factories.
 */
private val testPlatformModule = module {
    single<MoneySurferDatabase> { error("verify() must not instantiate this") }
    single<SyncDatabase> { error("verify() must not instantiate this") }
    single<DataStore<Preferences>> { error("verify() must not instantiate this") }
    single { AppInfo(version = "test", versionCode = 1) }
    single<BackupStorageLocator> { error("verify() must not instantiate this") }
    single<AppRestarter> { error("verify() must not instantiate this") }
}
