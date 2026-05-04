# Android E2E UI Tests — Plan

## Overview

**Phase 1 (current):** Maestro smoke flows against real debug Firebase (test account). Zero DI work, fast start.  
**Phase 2 (later):** Compose UI Test + JUnit4 with in-memory fakes for deterministic, CI-friendly coverage. Firebase Emulator Suite added then.

Tests live in `androidApp/src/androidTest/` (Phase 2). Maestro flows live in `scripts/maestro/`.

---

## Step 1 — Add dependencies to `gradle/libs.versions.toml`

**Versions:**
```toml
compose-ui-test = "1.10.3"   # match composeMultiplatform version
koin-test       = "4.2.1"    # match koin-bom
mockk           = "1.14.2"
# maestro = CLI only, no Gradle dep
```

**Libraries:**
```toml
compose-ui-test-junit4   = { module = "androidx.compose.ui:ui-test-junit4",  version.ref = "compose-ui-test" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest", version.ref = "compose-ui-test" }
koin-test                = { module = "io.insert-koin:koin-test",             version.ref = "koin-test" }
koin-test-android        = { module = "io.insert-koin:koin-test-android",     version.ref = "koin-test" }
mockk-android            = { module = "io.mockk:mockk-android",               version.ref = "mockk" }
```

> **Note:** For KMP/JetBrains Compose use `org.jetbrains.compose.ui:ui-test-junit4`. Verify exact artifact against JetBrains Compose BOM at `composeMultiplatform = "1.10.3"`.

---

## Step 2 — Wire dependencies in `androidApp/build.gradle.kts`

```kotlin
androidTestImplementation(libs.androidx.testExt.junit)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(libs.compose.ui.test.junit4)
androidTestDebugImplementation(libs.compose.ui.test.manifest)
androidTestImplementation(libs.koin.test)
androidTestImplementation(libs.koin.test.android)
androidTestImplementation(libs.mockk.android)
androidTestImplementation(libs.kotlinx.coroutines.test)
```

---

## Step 3 — Custom `TestApplication` + `TestRunner`

**Path:** `androidApp/src/androidTest/kotlin/com/georgeci/moneysurfer/`

- `E2ETestRunner.kt` — extends `AndroidJUnitRunner`, overrides `newApplication()` → `TestApplication::class.java`
- `TestApplication.kt` — extends `Application`, calls `initKoin { androidContext(this); modules(fakeDataModule) }` instead of real Koin graph. Replaces all Firebase-backed `@Single` bindings with in-memory fakes before any test starts.

Update `androidApp/build.gradle.kts`:
```kotlin
testInstrumentationRunner = "com.georgeci.moneysurfer.E2ETestRunner"
```

---

## Step 4 — Fake data layer

**Path:** `androidApp/src/androidTest/kotlin/com/georgeci/moneysurfer/fakes/`

In-memory fake implementations of all domain repository interfaces that hit Firebase:

| Fake class | Replaces |
|---|---|
| `FakeAuthRemoteRepository` | `AuthRemoteRepositoryImpl` (Firebase Auth) |
| `FakeWorkspaceRepository` | `WorkspaceRemoteRepositoryImpl` (Firestore) |
| `FakeAccountRepository` | Firestore account data |
| `FakeTransactionRepository` | Firestore transaction data |
| `FakeCategoryRepository` | Firestore category data |

Each fake:
- holds state in `MutableStateFlow`
- exposes `seed(data)` helper for per-test setup
- returns `Either<DomainError, T>` matching real contract

Assemble into `fakeDataModule: Module` Koin module.

---

## Step 5 — Screen-level Compose UI Tests

**Path:** `androidApp/src/androidTest/kotlin/com/georgeci/moneysurfer/screens/`

Rule: `createAndroidComposeRule<MainActivity>()`

| Test file | Screens exercised | Key assertions |
|---|---|---|
| `AuthFlowTest` | `SignInScreen` → `WorkspaceSelectorScreen` | Form validation, anonymous login, navigation |
| `WorkspaceSetupFlowTest` | `WorkspaceSelectorScreen` → `WorkspaceCreationScreen` | Create workspace, navigate to Dashboard |
| `DashboardTest` | `DashboardScreen` | Balance widget, accounts section, FAB, settings nav |
| `AccountManagementTest` | `AccountsManageScreen` → `AccountCreationScreen` → `AccountDetailsScreen` | Add/edit account, balance shown |
| `TransactionFlowTest` | `TransactionCreationScreen` → `DashboardScreen` | Create income/expense, amount validation, category picker |
| `TransactionListTest` | `TransactionsByAccountScreen` | Filter by account, tap → details |
| `CategoryCreationTest` | `CategoryCreationScreen` | Name input, type toggle, save |
| `SettingsTest` | `SettingsScreen` | Sign-out → `SignInScreen` |

---

## Step 6 — Key test scenarios

### Auth Flow (`AuthFlowTest`)
1. App launches → `SignInScreen` shown.
2. Submit empty fields → error shown.
3. Fill email + password → fake returns success → `WorkspaceSelectorScreen` shown.
4. Toggle Sign Up → submit → new-user flow navigates.
5. Anonymous login → fake stub UID → workspace selector shown.

### Workspace → Dashboard Onboarding (`WorkspaceSetupFlowTest`)
1. Seed logged-in user, no workspace.
2. App lands on `WorkspaceSelectorScreen`.
3. Tap "Create Workspace" → `WorkspaceCreationScreen`.
4. Enter name → Save → fake writes → navigate to Dashboard.
5. Assert toolbar shows workspace name.

### Dashboard Happy Path (`DashboardTest`)
1. Seed two accounts + three transactions.
2. Assert balance widget shows seeded total.
3. Assert both account names visible.
4. Assert three transactions in recent list.
5. Tap FAB → `TransactionCreationScreen`.
6. Tap settings icon → `SettingsScreen`.

### Add Transaction (`TransactionFlowTest`)
1. Pre-seed one account, one category.
2. Navigate to `TransactionCreationScreen`.
3. Enter title, amount, select account via `AccountChooserBottomSheet`, select category via `CategoryChooserBottomSheet`.
4. Tap Save → fake stores transaction → back on Dashboard, transaction appears.

### Sign-Out (`SettingsTest`)
1. Pre-seed logged-in state.
2. Navigate to `SettingsScreen`.
3. Tap "Sign out" → `FakeAuthRemoteRepository.signOut()` called → `AppLaunchViewModel` emits `Route.SignIn` → `SignInScreen` shown.

---

## Step 7 — Test infrastructure helpers

**Path:** `androidApp/src/androidTest/kotlin/com/georgeci/moneysurfer/helpers/`

- `ComposeTestExtensions.kt` — typed `onNodeWithStringRes(Res.string.foo)` helpers. References same string resource keys as UI; no hardcoded strings.
- `TestDataFactory.kt` — builder DSL for seeding fake `Account`, `Transaction`, `Category`, `Workspace` domain objects.
- `KoinTestRule.kt` — JUnit `TestRule` wrapping `startKoin`/`stopKoin` lifecycle per class; prevents Koin state leak between tests.

---

## Step 8 — Maestro smoke flows (Phase 1)

**Path:** `scripts/maestro/` ✅ created

Install: `brew install maestro`

| File | Flow | Status |
|---|---|---|
| `00_login.yaml` | Launch app with reused state and sign in only when needed | ✅ |
| `01_auth_signin.yaml` | Auth smoke wrapper around reusable login | ✅ |
| `02_workspace_create.yaml` | Create/select workspace after sign-in | ✅ |
| `03_dashboard_add_account.yaml` | Ensure baseline bank account exists from Dashboard | ✅ |
| `04_add_transaction.yaml` | Ensure baseline expense transaction exists | ✅ |
| `05_sign_out.yaml` | Navigate to Settings, sign out | ✅ |

Flows chain via `runFlow:` and reuse app/session state. `00_login.yaml` uses
`launchApp(clearState: false)` and only enters credentials when the login form is
visible, so full-suite runs avoid repeated clear-state + login cycles.

**Run locally:**
```bash
# Install Maestro
brew install maestro

# Build debug APK and install
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk

# Run all flows (provide test credentials)
maestro test --env TEST_EMAIL=test@example.com --env TEST_PASSWORD=secret123 --exclude-tags setup scripts/maestro/

# Run single flow
maestro test --env TEST_EMAIL=test@example.com --env TEST_PASSWORD=secret123 scripts/maestro/04_add_transaction.yaml
```

**Test account:** Use a dedicated Firebase test account (not production). Store credentials in local `.env` file excluded from Git.

---

## Step 9 — GitHub Actions CI

**Path:** `.github/workflows/android_e2e.yml`

```yaml
name: Android E2E Tests
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  maestro-e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'zulu' }
      - uses: gradle/actions/setup-gradle@v4

      - name: Build debug APK
        run: ./gradlew :androidApp:assembleDebug

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' \
            | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules && sudo udevadm trigger

      - name: Install Maestro
        run: curl -Ls "https://get.maestro.mobile.dev" | bash

      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          arch: x86_64
          profile: Nexus 6
          script: |
            adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk
            ~/.maestro/bin/maestro test \
              --env TEST_EMAIL=${{ secrets.TEST_EMAIL }} \
              --env TEST_PASSWORD=${{ secrets.TEST_PASSWORD }} \
              scripts/maestro/

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: maestro-results
          path: ~/.maestro/tests/
```

**Required GitHub Actions secrets:** `TEST_EMAIL`, `TEST_PASSWORD` (dedicated Firebase test account, not production).

---

## Step 10 — Gradle tasks

| Task | Purpose |
|---|---|
| `./gradlew :androidApp:connectedDebugAndroidTest` | Run all instrumented UI tests |
| `./gradlew :androidApp:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.georgeci.moneysurfer.screens.AuthFlowTest` | Single test class |
| `./gradlew :androidApp:connectedDebugAndroidTest --info` | Verbose output |
| `maestro test --exclude-tags setup scripts/maestro/` | All Maestro flows |
| `maestro test scripts/maestro/04_add_transaction.yaml` | Single Maestro flow |

---

## Open questions

_All resolved._

## Decisions

1. ✅ **Maestro first** — Phase 1 is Maestro smoke flows against real debug Firebase (test account). Compose UI Test in Phase 2.
2. ✅ **Split test locations (Phase 2):**
   - `composeApp/src/androidDeviceTest/` — isolated component tests (single screen, no navigation, no Koin app-graph)
   - `androidApp/src/androidTest/` — E2E flow tests through real `MainActivity` (full stack, navigation, Koin)
3. ✅ **No Firebase Emulator Suite for Phase 1** — add in Phase 2 together with Compose UI tests.
