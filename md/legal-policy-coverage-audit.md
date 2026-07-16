# Legal-policy coverage audit (in-app / in-repo)

**Issue:** [#142 — Audit presence of required policies in-app and externally](https://github.com/georgeci/MoneySurfer/issues/142)
**Scope of this audit:** in-repo / in-app coverage only — which policy documents and Settings/About links exist in the codebase and where they point. External-URL liveness and Play/App Store console checks are **out of scope** and handed back to the maintainer (see [§7](#7-handback-external-checks-not-performed)).
**Variants covered:** `androidApp`, `androidApp-offline`, `iosApp`, `iosAppOffline`.
**Date:** 2026-07-16.

---

## 1. Executive summary

All four app variants share a single Kotlin Multiplatform UI: both `composeApp` and `composeAppOffline` depend on `shared → feature:settings + feature:login`, and both iOS apps embed the corresponding Compose framework. **In-app legal behaviour is therefore identical across all four variants**; the only per-variant differences are in the native shells (Play listing files, iOS privacy manifests), covered in [§5](#5-store--platform-metadata-in-repo-portion).

Two in-app legal surfaces exist:

1. **Login "Terms & Privacy" screen** ([`LegalScreen.kt`](feature/login/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/login/legal/LegalScreen.kt)) — real inline Terms + Privacy prose, reachable **only from the pre-login Sign-in screen**.
2. **Settings "About & legal" screen** ([`AboutScreen.kt`](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt)) — **renders no legal links at all** despite advertising itself as "About & legal".

Headline gaps:

- **Terms / Privacy / OSS-licenses rows are missing from the About UI** even though the strings, ViewModel events, and effects for them all exist. They are dead code.
- **No OSS-licenses screen and no license-generation tooling** exist anywhere; the licenses action is a no-op.
- **No account/data-deletion policy or flow** exists (the existing "Delete account" dialog deletes a *financial* account, not the user account/data).
- **`iosApp` (online) has no `PrivacyInfo.xcprivacy`** while `iosAppOffline` does — the wrong way round, given the online variant is the one that syncs data to the cloud.
- **Policy URLs are inconsistent** across the codebase (`moneysurfer.app` vs `georgeci.com` vs `MoneySurfer2026`) and the Legal text is still flagged pending legal review (`TODO(#65)`).

---

## 2. Inventory table

| Required policy | Document exists in repo? | In-app link present? | Where it points |
|---|---|---|---|
| **Privacy policy** | Partial — inline prose in login `LegalScreen` (`legal_privacy_body`), flagged `TODO(#65)` pending legal review | **Login only.** About screen string + ViewModel URL exist but **row is not rendered** | About ViewModel: `https://moneysurfer.app/legal/privacy` (unverified) |
| **Terms of service** | Partial — inline prose in login `LegalScreen` (`legal_terms_body`), flagged `TODO(#65)` | **Login only.** About row **not rendered** | About ViewModel: `https://moneysurfer.app/legal/terms` (unverified) |
| **Open-source licenses** | **Missing** — no license screen, no generation tooling (no AboutLibraries/licensee) | **No.** String says "34 packages" but action `NavigateToLicenses` is a **no-op** (`-> Unit`) | Nowhere |
| **Account / data-deletion policy** | **Missing** — no document, no in-app deletion flow for the user account/data | **No** | Nowhere |

Legend: "Login only" = reachable solely from the pre-login Sign-in screen, not from Settings after sign-in.

---

## 3. In-app surface #1 — Login "Terms & Privacy" screen

- **File:** [`feature/login/.../legal/LegalScreen.kt`](feature/login/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/login/legal/LegalScreen.kt); strings in [`feature/login/.../values/strings.xml`](feature/login/src/commonMain/composeResources/values/strings.xml).
- **Reachability:** Sign-in screen renders `SignInTerms` ("By continuing you agree to the Terms & Privacy") → `OnTermsClick` → `NavigateToLegal` → `LegalScreen` ([`SignInScreen.kt:524`](feature/login/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/login/SignInScreen.kt), [`LoginNavGraph.kt:18`](feature/login/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/login/LoginNavGraph.kt)). This is a **pre-login** entry point only.
- **Content:** real Terms of Use + Privacy Policy prose (offline-first framing, no ads, anonymous crash reports, optional cloud sync). Footer: "Last updated: May 2026."
- **Caveats:**
  - The strings file carries `TODO(#65)`: *"plain-language Terms/Privacy text — final wording pending legal review before public release."* Treat the current text as placeholder.
  - No hyperlinks to hosted canonical versions; content is inline only.
  - **No OSS-licenses and no account/data-deletion content** on this screen.
  - After sign-in (or in offline use past this screen) there is **no way to re-open** Terms/Privacy in-app, because the About screen (below) does not surface them.

---

## 4. In-app surface #2 — Settings "About & legal" screen  ⚠️ primary gap

- **Files:** [`AboutScreen.kt`](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutScreen.kt), [`AboutViewModel.kt`](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/about/AboutViewModel.kt), strings in [`feature/settings/.../values/strings.xml`](feature/settings/src/commonMain/composeResources/values/strings.xml). Reachable via [`SettingsNavGraph.kt:73`](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsNavGraph.kt) from the "About & legal" settings row.
- **What the UI actually renders** (`AboutContent`, lines 77–119): app-identity hero (icon, brand, version, "MIT License" chip), a single **GitHub link row**, and a centered copyright line. **That is all.**
- **What exists but is NOT rendered:**
  - Strings for a **Legal** section: `settings_about_section_legal`, `settings_about_terms_title`, `settings_about_privacy_title` (+ supporting), `settings_about_licenses_title` ("Open-source licenses"), `settings_about_licenses_supporting` ("34 packages").
  - ViewModel **events** `OnTermsClick` / `OnPrivacyClick` / `OnLicensesClick` and **effects** `OpenUrl(URL_TERMS)` / `OpenUrl(URL_PRIVACY)` / `NavigateToLicenses` (`AboutViewModel.kt:18–20`).
  - A Help section (`OnHelpCenterClick`, `OnContactClick`, `OnRateClick`, region, diagnostics) — also unrendered.
- **Consequence:** the screen titled **"About & legal"** exposes **no privacy, no terms, and no license links**. The wiring is half-built: strings + ViewModel exist, the composable rows were never added.
- **Licenses no-op:** even the effect handler treats `NavigateToLicenses` as `-> Unit` (`AboutScreen.kt:68`), so there is nothing to navigate to. No license screen or generator (AboutLibraries / licensee / OSS-licenses Gradle plugin) exists in the build — confirmed absent from `libs.versions.toml` and all `*.gradle.kts`. The "34 packages" figure is fictional.

### URL / identity inconsistencies (unverified — see §7)
| Reference | Value | Source |
|---|---|---|
| Terms URL | `https://moneysurfer.app/legal/terms` | `AboutViewModel.kt:31` |
| Privacy URL | `https://moneysurfer.app/legal/privacy` | `AboutViewModel.kt:32` |
| Help URL | `https://moneysurfer.app/help` | `AboutViewModel.kt:33` |
| Contact email (ViewModel) | `support@moneysurfer.app` | `AboutViewModel.kt:35` |
| Contact email (string) | `hello@georgeci.com` | `settings_about_contact_supporting` |
| GitHub | `github.com/georgeci/MoneySurfer2026` | `settings_about_github_supporting` |

Three different identities (`moneysurfer.app`, `georgeci.com`, `MoneySurfer2026`) are used for what should be one canonical policy host. None were fetched (out of scope).

---

## 5. Store / platform metadata (in-repo portion)

### Android — Play fastlane listings
- [`androidApp-offline/src/main/play/`](androidApp-offline/src/main/play): complete — `full-description.txt` (privacy-forward "fully offline… data stays on your device… no network access"), `short-description.txt`, `contact-email.txt`, `contact-website.txt`, graphics, release notes.
- [`androidApp/src/main/play/`](androidApp/src/main/play): **nearly empty** — only `title.txt` + `default-language.txt`. No full/short description, **no `contact-email.txt`, no `contact-website.txt`.**
- **Neither variant** carries a privacy-policy URL file. (Play's privacy-policy URL and Data-safety form live in the Play Console, not in these fastlane files — see §7.)
- No `<meta-data>` privacy/policy references in either `AndroidManifest.xml` (none expected, noted for completeness).

### iOS — privacy manifests
- [`iosAppOffline/iosAppOffline/PrivacyInfo.xcprivacy`](iosAppOffline/iosAppOffline/PrivacyInfo.xcprivacy): present — `NSPrivacyTracking=false`, empty tracking domains, **empty `NSPrivacyCollectedDataTypes`**, empty accessed-API types.
- [`iosApp/iosApp/`](iosApp/iosApp): **no `PrivacyInfo.xcprivacy` at all** — only `Info.plist`.
- **Asymmetry is backwards:** the **online** variant (`iosApp`), which syncs user financial data to a hosting provider, is the one **missing** the privacy manifest; the offline variant (which collects nothing) has one. If/when the online variant collects data, its `NSPrivacyCollectedDataTypes` would also need to declare it.
- Neither `Info.plist` contains privacy usage-description keys (none currently required by the observed feature set; noted for completeness).

---

## 6. Per-variant coverage matrix

| Surface | androidApp | androidApp-offline | iosApp | iosAppOffline |
|---|---|---|---|---|
| Login Legal (Terms+Privacy) screen | ✅ shared | ✅ shared | ✅ shared | ✅ shared |
| About "Legal" links (Terms/Privacy/Licenses) | ❌ not rendered | ❌ not rendered | ❌ not rendered | ❌ not rendered |
| OSS-licenses screen | ❌ absent | ❌ absent | ❌ absent | ❌ absent |
| Account/data-deletion policy | ❌ absent | ❌ absent | ❌ absent | ❌ absent |
| Play listing privacy content | ⚠️ listing near-empty | ✅ present | n/a | n/a |
| iOS privacy manifest | n/a | n/a | ❌ missing | ✅ present |

The four in-app rows are identical because the UI is shared code (§1).

---

## 7. Handback — external checks NOT performed

Per the task scope, the following acceptance-criteria items were **not** performed here and must be completed by the maintainer:

1. **Verify hosted policy URLs return 200** and that their content matches what the app links to — for `https://moneysurfer.app/legal/terms`, `/legal/privacy`, `/help`. (Also resolve the `moneysurfer.app` vs `georgeci.com` vs `MoneySurfer2026` identity split first.)
2. **Google Play Console:** confirm the privacy-policy URL is set for each variant and the **Data safety** form is complete/accurate — especially for the online `androidApp` (cloud sync).
3. **App Store Connect:** confirm **App Privacy** details for `iosApp` / `iosAppOffline`, and **account-deletion** disclosure for the online variant.

None of these are represented in the repo and cannot be audited from the code.

---

## 8. Gaps → follow-up issues

| # | Gap | Severity | Follow-up issue |
|---|---|---|---|
| G1 | About "About & legal" screen renders no Terms/Privacy/Licenses rows (strings + ViewModel exist; rows never added) | High | [#211](https://github.com/georgeci/MoneySurfer/issues/211) |
| G2 | No OSS-licenses screen or license-generation tooling; `NavigateToLicenses` is a no-op | High | [#212](https://github.com/georgeci/MoneySurfer/issues/212) |
| G3 | No account/data-deletion policy or in-app user-account/data-deletion flow (existing dialog deletes a financial account only) | High | [#213](https://github.com/georgeci/MoneySurfer/issues/213) |
| G4 | `iosApp` (online) missing `PrivacyInfo.xcprivacy`; collected-data types undeclared for cloud sync | Medium | [#214](https://github.com/georgeci/MoneySurfer/issues/214) |
| G5 | Inconsistent policy identity/URLs (`moneysurfer.app` / `georgeci.com` / `MoneySurfer2026`); Legal text still `TODO(#65)` pending legal review | Medium | [#215](https://github.com/georgeci/MoneySurfer/issues/215) |
| G6 | `androidApp` (online) Play listing incomplete — no full/short description, no contact email/website | Low | [#216](https://github.com/georgeci/MoneySurfer/issues/216) |
