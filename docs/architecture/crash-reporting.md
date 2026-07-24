# Crash reporting and the global error boundary

<!-- DOCS:TOC -->
## Contents
- [Crash reporting and the global error boundary](#crash-reporting-and-the-global-error-boundary)
- [The path a failure takes](#the-path-a-failure-takes)
- [Wiring](#wiring)
- [What reaches Crashlytics](#what-reaches-crashlytics)
- [PII](#pii)
- [Verifying it works](#verifying-it-works)
<!-- DOCS:END -->

Tracks [#78](https://github.com/georgeci/MoneySurfer/issues/78). Two problems,
one pipeline: an exception in a feature ViewModel used to be swallowed silently
by `MviViewModel.launch`, and the Firebase Crashlytics wiring that already
existed (Gradle plugins, SPM product, dSYM upload, `CrashReporter` bindings) was
never called from anywhere.

<!-- AI:SECTION id=crash-reporting task=telemetry,crashlytics,error-handling -->
## The path a failure takes

```
ViewModel coroutine throws
  └─ MviViewModel.launch catches (CancellationException rethrown — not an error)
       ├─ Logger.e(throwable)                      ← always, never swallowed
       │    └─ CrashReportingLogWriter             ← Kermit → Crashlytics
       │         ├─ crashReporter.log(...)         breadcrumb (Warn+)
       │         └─ crashReporter.record(...)      non-fatal (Error+ with throwable)
       └─ onError == null?
            ├─ yes → UnhandledErrors.report(e)     → SurferErrorBoundary in App.kt
            └─ no  → the screen renders it itself
```

`onError` decides *who renders the failure*, never *whether it is reported*.
Passing a handler means the screen owns the presentation (inline error state,
snackbar, dismiss); omitting it escalates to the app-level boundary.

## Wiring

| Piece | File |
| --- | --- |
| Platform-agnostic contract | [CrashReporter.kt](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/telemetry/CrashReporter.kt) |
| Kermit → Crashlytics bridge | [CrashReportingLogWriter.kt](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/telemetry/CrashReportingLogWriter.kt) |
| Install + user binding | [CrashReporting.kt](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/telemetry/CrashReporting.kt) |
| Called once per process | [KoinInit.kt](../../shared/src/commonMain/kotlin/com/georgeci/moneysurfer/di/KoinInit.kt) |
| Global error channel | [UnhandledErrors.kt](../../utils/src/commonMain/kotlin/com/georgeci/moneysurfer/utils/UnhandledErrors.kt) |
| UI fallback | [SurferErrorBoundary.kt](../../uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferErrorBoundary.kt) |
| Mounted around the nav graph | [App.kt](../../shared/src/commonMain/kotlin/com/georgeci/moneysurfer/App.kt) |

Firebase implementations of `CrashReporter` live in `data-remote` (androidMain /
iosMain, both on `dev.gitlive.firebase.crashlytics`); `jvmMain` and the offline
graph bind no-ops. `initKoin` resolves the binding with `getOrNull`, so graphs
without one — desktop, tests — simply run without crash reporting.

The boundary **overlays** the nav graph rather than replacing it: the content
stays composed underneath, so Retry returns the user to the screen they were on
with their back stack intact.

## What reaches Crashlytics

- **Breadcrumbs** — every Kermit line at `Warn` or above, as
  `SEVERITY/Tag: message`.
- **Non-fatals** — every `Error`/`Assert` line that carries a throwable.
- **User id** — the session's Firebase uid, passed to `installCrashReporting`
  from `SessionPointers.currentFirebaseUid` and mirrored for the lifetime of the
  process; cleared on sign-out. The collector lives on a single process scope
  behind the same idempotency latch as the install, so repeated `initKoin` calls
  (tests, previews) neither stack collectors nor leak scopes.
- **Native crashes** — via the Gradle plugin's
  `nativeSymbolUploadEnabled` on Android and the `Upload Crashlytics dSYMs`
  build phase on iOS.

Collection is **off in debug builds** (`installCrashReporting(reporter,
isDebug)`), so local crashes never reach the production dashboard.

## PII

The `Warn` floor is not arbitrary — it matches the release min-severity set by
[`configureLogging`](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/logging/LoggingConfig.kt).
Verbose/Debug/Info are the severities that historically carried invitee emails
and uids ([#154](https://github.com/georgeci/MoneySurfer/issues/154)); they are
dropped before the message lambda is evaluated, so they cannot become a
breadcrumb. Warn+ call sites still redact via `redactEmail` / `redactUid`.

The Crashlytics user id is the opaque Firebase uid. It never reaches logcat or
the iOS unified log — only the crash backend, which is tied to the same Firebase
project as the account.

## Verifying it works

Crashlytics only uploads from a **release** build with a real
`google-services.json` / `GoogleService-Info.plist`, and a non-fatal appears in
the console a few minutes later.

```bash
./gradlew :androidApp:assembleRelease
```

Then trigger any failing path (e.g. airplane mode during a sync push) and look
for the non-fatal under Crashlytics → Issues, filtered to "Non-fatals".

<!-- AI:END -->
