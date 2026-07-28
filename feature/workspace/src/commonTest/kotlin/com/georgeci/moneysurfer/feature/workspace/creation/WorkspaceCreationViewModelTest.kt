package com.georgeci.moneysurfer.feature.workspace.creation

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.RUB
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.userId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateWorkspaceUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.feature.workspace.FakeCategoryRepository
import com.georgeci.moneysurfer.feature.workspace.FakeCurrencyRepository
import com.georgeci.moneysurfer.feature.workspace.FakeWorkspaceMemberRepository
import com.georgeci.moneysurfer.feature.workspace.FakeWorkspaceRepository
import com.georgeci.moneysurfer.feature.workspace.FakeWorkspaceSyncer
import com.georgeci.moneysurfer.feature.workspace.RecordingUserRemoteRepository
import com.georgeci.moneysurfer.feature.workspace.aCurrency
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The create/edit workspace screen. One form serves both jobs: `init(workspaceId)` decides whether
 * Save runs `CreateWorkspaceUseCase` or a plain repository update.
 *
 * Wired against the real use case with fake repositories, so the `CreateWorkspaceError` →
 * `WorkspaceCreationError` mapping under test is driven by failures production can actually
 * produce rather than a hand-written `Left`. What the use case itself guarantees — category seeds,
 * remote refs, workspace pinning — belongs to `CreateWorkspaceUseCaseTest` and is not re-asserted
 * here; this spec covers what the *screen* does with the outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceCreationViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the form opens on the currency list with the first entry preselected" {
        val viewModel = newViewModel()

        val content = content(viewModel)
        content.currencies.map { it.code } shouldContainExactly listOf(EUR, USD, RUB)
        content.currency shouldBe EUR.value
        content.isEditing shouldBe false
        content.workspaceId shouldBe null
    }

    "an empty currency list still promotes the form out of Loading" {
        val viewModel = newViewModel(currencies = emptyList())

        content(viewModel).currency shouldBe ""
    }

    "init loads the existing workspace and switches the form to editing" {
        val existing = aWorkspace(
            id = workspaceId("ws-1"),
            name = "Family",
            description = "Shared",
            baseCurrency = USD,
        )
        val viewModel = newViewModel(repository = FakeWorkspaceRepository(listOf(existing)))

        viewModel.init(existing.id)

        val content = content(viewModel)
        content.workspaceId shouldBe existing.id
        content.name shouldBe "Family"
        content.description shouldBe "Shared"
        content.currency shouldBe USD.value
        content.isEditing shouldBe true
        // The currency list is fetched independently, so the edit load must not drop it.
        content.currencies.map { it.code } shouldContainExactly listOf(EUR, USD, RUB)
    }

    "init with no id leaves the blank creation form alone" {
        val viewModel = newViewModel()

        viewModel.init(null)

        content(viewModel).isEditing shouldBe false
        content(viewModel).name shouldBe ""
    }

    "re-initing on the id already on screen does not clobber an edit in progress" {
        val existing = aWorkspace(id = workspaceId("ws-1"), name = "Family")
        val viewModel = newViewModel(repository = FakeWorkspaceRepository(listOf(existing)))

        viewModel.init(existing.id)
        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Household"))
        viewModel.init(existing.id)

        content(viewModel).name shouldBe "Household"
    }

    "init for a workspace that is not in the store leaves the form untouched" {
        val viewModel = newViewModel()

        viewModel.init(workspaceId("missing"))

        content(viewModel).isEditing shouldBe false
    }

    "save with a blank name is a no-op — the Save button is the only way in" {
        val repository = FakeWorkspaceRepository()
        val viewModel = newViewModel(repository = repository)

        viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)

        repository.inserts shouldBe 0
        content(viewModel).isSaving shouldBe false
    }

    "a name of nothing but spaces counts as blank" {
        val repository = FakeWorkspaceRepository()
        val viewModel = newViewModel(repository = repository)

        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("   "))
        viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)

        repository.inserts shouldBe 0
    }

    "a successful create persists the trimmed form and navigates back" {
        val repository = FakeWorkspaceRepository()
        val viewModel = newViewModel(repository = repository)

        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("  Family  "))
        viewModel.onEvent(WorkspaceCreationEvent.OnDescriptionChanged("  Shared budget  "))
        viewModel.onEvent(WorkspaceCreationEvent.OnCurrencyChanged(USD.value))

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)
            awaitItem() shouldBe WorkspaceCreationEffect.NavigateBack
        }

        val saved = repository.snapshot().single()
        saved.name shouldBe "Family"
        saved.description shouldBe "Shared budget"
        saved.baseCurrency shouldBe USD
    }

    "a remote push failure keeps the user on the form with a retryable error" {
        val viewModel = newViewModel(
            session = InMemorySessionPointers(currentUserId = userId("u-1"), currentFirebaseUid = "fb-1"),
            syncer = FakeWorkspaceSyncer(failPush = true),
        )

        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Family"))
        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)
            expectNoEvents()
        }

        val content = content(viewModel)
        content.error shouldBe WorkspaceCreationError.RemoteSyncFailed
        content.isSaving shouldBe false
        // The typed name is still there, so a retry costs one tap rather than re-filling the form.
        content.name shouldBe "Family"
    }

    "sync being switched off reads as success, not as a sync failure" {
        val viewModel = newViewModel(
            session = InMemorySessionPointers(currentUserId = userId("u-1"), currentFirebaseUid = "fb-1"),
            syncer = FakeWorkspaceSyncer(pushed = false),
        )

        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Family"))
        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)
            awaitItem() shouldBe WorkspaceCreationEffect.NavigateBack
        }
    }

    "no signed-in user degrades to the generic error instead of navigating on" {
        val viewModel = newViewModel(session = InMemorySessionPointers(currentUserId = null))

        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Family"))
        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)
            expectNoEvents()
        }

        val content = content(viewModel)
        content.error shouldBe WorkspaceCreationError.Unknown
        content.isSaving shouldBe false
    }

    "a failed local write reports the generic error" {
        val viewModel = newViewModel(repository = FakeWorkspaceRepository(failOnInsert = true))

        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Family"))
        viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)

        content(viewModel).error shouldBe WorkspaceCreationError.Unknown
    }

    "editing updates the workspace in place instead of creating a second one" {
        val existing = aWorkspace(id = workspaceId("ws-1"), name = "Family", baseCurrency = USD)
        val repository = FakeWorkspaceRepository(listOf(existing))
        val viewModel = newViewModel(repository = repository)

        viewModel.init(existing.id)
        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("  Household  "))
        viewModel.onEvent(WorkspaceCreationEvent.OnCurrencyChanged(RUB.value))

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)
            awaitItem() shouldBe WorkspaceCreationEffect.NavigateBack
        }

        val saved = repository.snapshot().single()
        saved.id shouldBe existing.id
        saved.name shouldBe "Household"
        saved.baseCurrency shouldBe RUB
        repository.inserts shouldBe 0
        // Fields the form does not own ride through untouched.
        saved.ownerId shouldBe existing.ownerId
        saved.createdAt shouldBe existing.createdAt
    }

    "editing a workspace that vanished underneath the form reports the generic error" {
        val existing = aWorkspace(id = workspaceId("ws-1"))
        val repository = FakeWorkspaceRepository(listOf(existing))
        val viewModel = newViewModel(repository = repository)

        viewModel.init(existing.id)
        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Household"))
        repository.emit(emptyList())

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)
            expectNoEvents()
        }

        val content = content(viewModel)
        content.error shouldBe WorkspaceCreationError.Unknown
        content.isSaving shouldBe false
    }

    "an exception thrown by the edit write lands on the form rather than the error boundary" {
        val existing = aWorkspace(id = workspaceId("ws-1"))
        val viewModel = newViewModel(
            repository = FakeWorkspaceRepository(listOf(existing), failOnUpdate = true),
        )

        viewModel.init(existing.id)
        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Household"))
        viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)

        val content = content(viewModel)
        content.error shouldBe WorkspaceCreationError.Unknown
        content.isSaving shouldBe false
    }

    "a double tap on Save creates the workspace only once" {
        val repository = FakeWorkspaceRepository()
        val viewModel = newViewModel(repository = repository)

        viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Family"))
        viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)
        // `isSaving` stays set while the screen navigates away, so the second tap must not start
        // a second create behind it.
        viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)

        repository.inserts shouldBe 1
    }

    "editing any field clears the error left by the previous attempt" {
        listOf(
            WorkspaceCreationEvent.OnNameChanged("Household"),
            WorkspaceCreationEvent.OnDescriptionChanged("Shared"),
            WorkspaceCreationEvent.OnCurrencyChanged(USD.value),
        ).forEach { event ->
            val viewModel = newViewModel(session = InMemorySessionPointers(currentUserId = null))
            viewModel.onEvent(WorkspaceCreationEvent.OnNameChanged("Family"))
            viewModel.onEvent(WorkspaceCreationEvent.OnSaveClick)
            content(viewModel).error shouldBe WorkspaceCreationError.Unknown

            viewModel.onEvent(event)

            content(viewModel).error shouldBe null
        }
    }

    "the invite row stays inert until a members feed is wired up" {
        val viewModel = newViewModel()

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceCreationEvent.OnInviteMemberClick)
            expectNoEvents()
        }
    }

    "back navigates back" {
        val viewModel = newViewModel()

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceCreationEvent.OnBackClick)
            awaitItem() shouldBe WorkspaceCreationEffect.NavigateBack
        }
    }

    "the offline build carries its flag onto the form" {
        val viewModel = newViewModel(hostCapabilities = FakeHostCapabilities.offline())

        content(viewModel).isOffline shouldBe true
    }
})

private fun content(viewModel: WorkspaceCreationViewModel): WorkspaceCreationState.Content =
    viewModel.currentState.shouldBeInstanceOf<WorkspaceCreationState.Content>()

private val TEST_CURRENCIES = listOf(aCurrency(EUR, "€"), aCurrency(USD, "$"), aCurrency(RUB, "₽"))

@Suppress("LongParameterList")
private fun newViewModel(
    repository: FakeWorkspaceRepository = FakeWorkspaceRepository(),
    currencies: List<Currency> = TEST_CURRENCIES,
    session: InMemorySessionPointers = InMemorySessionPointers(currentUserId = userId("u-1")),
    syncer: FakeWorkspaceSyncer = FakeWorkspaceSyncer(),
    hostCapabilities: FakeHostCapabilities = FakeHostCapabilities(isOffline = false),
): WorkspaceCreationViewModel = WorkspaceCreationViewModel(
    workspaceRepository = repository,
    createWorkspace = CreateWorkspaceUseCase(
        workspaceRepository = repository,
        workspaceMemberRepository = FakeWorkspaceMemberRepository(),
        categoryRepository = FakeCategoryRepository(),
        userRemoteRepository = RecordingUserRemoteRepository(),
        workspaceSyncer = syncer,
        session = session,
        sessionMutator = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
    ),
    getCurrencies = GetCurrenciesUseCase(FakeCurrencyRepository(currencies)),
    hostCapabilities = hostCapabilities,
)
