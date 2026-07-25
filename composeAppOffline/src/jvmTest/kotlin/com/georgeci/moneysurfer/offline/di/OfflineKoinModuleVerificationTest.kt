package com.georgeci.moneysurfer.offline.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.di.AppModule
import com.georgeci.moneysurfer.di.module
import com.georgeci.moneysurfer.domain.AppInfo
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
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationSeed
import com.georgeci.moneysurfer.navigation.GoalContributionMode
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Mirrors composeApp's `KoinModuleVerificationTest` for the offline graph:
 * walks every `@Single` / `@KoinViewModel` constructor signature and fails
 * fast if any binding is missing — catches the same crashes that surfaced
 * at runtime as `InstanceCreationException` for `LoginUseCase` /
 * `SyncCoordinator`.
 */
@OptIn(KoinExperimentalAPI::class)
class OfflineKoinModuleVerificationTest {

    @Test
    fun `offline app module graph resolves`() {
        val rootModule = module {
            includes(AppModule().module(), testPlatformModule)
            includes(*offlineWiring.toTypedArray())
        }
        rootModule.verify(
            extraTypes = listOf(
                WorkspaceId::class,
                UserId::class,
                WorkspaceInviteId::class,
                AccountId::class,
                CategoryId::class,
                TransactionId::class,
                // TransactionCreationScreen passes the transaction it opens on, plus whether it is
                // there to edit that row or to duplicate it.
                TransactionCreationSeed::class,
                CategoryType::class,
                // The category chooser passes which of the two picker layouts to open.
                CategoryPickerVariant::class,
                // Budget details/edit screens pass the budget id via parametersOf.
                BudgetId::class,
                // Goal screens pass the goal id, and the contribution screen its
                // add/withdraw mode, via parametersOf.
                GoalId::class,
                GoalContributionMode::class,
                Boolean::class,
                // AccountCreationScreen passes the onboarding's pre-selected AccountType.
                AccountType::class,
            ),
        )
    }
}

private val testPlatformModule = module {
    single<MoneySurferDatabase> { error("verify() must not instantiate this") }
    single<BackupStorageLocator> { error("verify() must not instantiate this") }
    single<DataStore<Preferences>> { error("verify() must not instantiate this") }
    single<DebugConfigSource> { DebugConfigSource.Empty }
    single { AppInfo(version = "test", versionCode = 1) }
}
