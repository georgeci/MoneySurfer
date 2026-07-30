# Firebase App Check

<!-- DOCS:TOC -->
## Contents
- [Firebase App Check](#firebase-app-check)
- [How each host attests](#how-each-host-attests)
- [Console setup (not in this repo)](#console-setup-not-in-this-repo)
- [Rollout order](#rollout-order)
<!-- DOCS:END -->

Firestore rules answer "who is asking" — they cannot answer "what is asking". Anyone who pulls
the API key out of an APK can talk to the project as a signed-in user from `curl`. App Check
closes that gap: each client attests to Google that it is a genuine build of this app, and the
backend rejects callers that cannot prove it.

<!-- AI:SECTION id=app-check-clients task=security,firebase,appcheck -->
## How each host attests

| Host | Release | Local / debug |
| --- | --- | --- |
| Android | Play Integrity | debug provider (secret printed to logcat) |
| iOS | App Attest | debug provider (Simulator and `DEBUG`) |
| Desktop (JVM) | — none exists — | debug secret from an env var |

**Android** installs the factory in
[MoneySurferApplication.onCreate](../../androidApp/src/main/kotlin/com/georgeci/moneysurfer/MoneySurferApplication.kt),
before Koin builds the graph that resolves Firestore. The provider is chosen by *variant source
set*, not by an `if`: `androidApp/src/debug` and `androidApp/src/release` each declare their own
`installAppCheckProvider()`, and the debug artifact is wired as `debugImplementation`. A release
binary therefore cannot fall back to minting tokens from a shared secret — the class is not in it.

**iOS** installs the factory in
[iOSApp.swift](../../iosApp/iosApp/iOSApp.swift) **before** `FirebaseApp.configure()`; Firebase
reads the factory during configuration and ignores one installed afterwards. Note that Firebase
ships `AppAttestProvider` but no factory for it, and there is no implicit default — with no
factory registered, `FIRAppCheck` logs "without a provider factory" and App Check is never
instantiated. The small `AppAttestProviderFactory` adapter in that file exists for exactly that
reason.

**Desktop** is the odd one out. gitlive's `firebase-java-sdk` ships the App Check core but not a
single provider, so the only thing a JVM client can present is a registered debug secret.
[DesktopAppCheck.jvm.kt](../../data-remote/src/jvmMain/kotlin/com/georgeci/moneysurfer/data/remote/DesktopAppCheck.jvm.kt)
trades `MS_FIREBASE_APPCHECK_DEBUG_TOKEN` for a token over the App Check REST API. That is
acceptable because desktop is a developer-only host with no shipped release — a debug secret is
exactly as trusted as the developer holding it. Without the variable nothing is installed and the
host behaves as before.

The REST path is keyed by project *number*, which is parsed from the second segment of the app id
(`1:<projectNumber>:android:<hash>`) rather than asking for a fourth environment variable.

**Against the emulator App Check is skipped on all three hosts.** The emulator does not verify
tokens and there is no project to register a secret with, so local development and every Maestro
or emulator-backed test are unaffected by any of this.
<!-- AI:END -->

<!-- AI:SECTION id=app-check-console task=security,firebase,appcheck,setup -->
## Console setup (not in this repo)

Attestation providers and enforcement live in the Firebase console, per project — nothing here
configures them. For **each** of `moneysurfer-dev` and `moneysurfer-release`:

1. **App Check → Apps** → register the Android app with **Play Integrity** and the iOS app with
   **App Attest**.
2. **Manage debug tokens** on the relevant app → add one entry per machine that needs a real
   project: your desktop, and the Simulator or Android emulator if you run them against dev
   rather than the local emulator suite.
   - Android prints its secret to logcat on first debug run.
   - iOS prints its secret to the Xcode console on first Simulator run.
   - Desktop does not print one: generate any UUID, register it, and export it as
     `MS_FIREBASE_APPCHECK_DEBUG_TOKEN`.
3. Enforcement stays **off** until the rollout below says otherwise.

Debug secrets are as good as a key: they belong in the console and in your shell, never in the
repository.
<!-- AI:END -->

<!-- AI:SECTION id=app-check-rollout task=security,firebase,appcheck,release -->
## Rollout order

Enforcement is a switch that starts rejecting unverified callers immediately. Flipping it before
clients ship locks out **every already-installed build**, and there is no client-side fix — users
would have to update before they could sync again. So the order is not negotiable:

1. Merge the client wiring with enforcement **off**. Nothing changes for anyone; clients merely
   start attaching tokens.
2. Ship the Android and iOS releases.
3. Watch **App Check → APIs** until the verified share is overwhelming. This takes weeks, not
   hours — it tracks how fast the installed base updates, and whatever is still unverified at
   flip time is what you are about to break.
4. Enforce per product, `moneysurfer-dev` first, then `moneysurfer-release`.
5. Rollback is turning enforcement back off in the console. No release required.

Desktop is worth a thought at step 4: once enforcement is on, a desktop host without a registered
debug token stops working against that project. Either register one or run it against the
emulator.
<!-- AI:END -->

Related: [firebase-emulator](../testing/firebase-emulator.md),
[supply-chain](supply-chain.md), [firestore-rules-bugs](../architecture/firestore-rules-bugs.md).
