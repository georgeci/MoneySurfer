# Store publication checklist (console-side)

Companion to [legal-policy-coverage-audit.md](legal-policy-coverage-audit.md) §7. Everything here
lives in the **Play Console / App Store Connect**, not in this repo — use the exact values below
when filling in the forms. In-repo counterparts (fastlane listing files, privacy manifests, in-app
links) were fixed by #211–#216.

**Canonical identity** (single source of truth):

| Field | Value |
|---|---|
| Policy host | `https://georgeci.github.io` |
| Privacy policy (online) | `https://georgeci.github.io/privacy-policy.html` |
| Privacy policy (offline) | `https://georgeci.github.io/privacy-policy-local.html` |
| Terms of service | `https://georgeci.github.io/terms.html` |
| Account & data deletion | `https://georgeci.github.io/account-deletion.html` |
| Support (offline) | `https://georgeci.github.io/support-local.html` |
| Contact email | `georgeci007+moneysurfer@gmail.com` |
| Website / source | `https://github.com/georgeci/MoneySurfer` |

> ⚠️ `terms.html` and `account-deletion.html` must be **published to the georgeci.github.io repo
> first** — the in-app About links and the values below point at them.

---

## Google Play Console — Money Surfer (online, `androidApp`)

- [ ] **App content → Privacy policy:** `https://georgeci.github.io/privacy-policy.html`
- [ ] **App content → Data safety form:**
  - Collects data: **yes**. Shares data: **no**. All data encrypted in transit: **yes**.
  - Deletion mechanism available: **yes** → `https://georgeci.github.io/account-deletion.html`
  - Declared types:
    | Data type | Collected | Purpose | Linked to user |
    |---|---|---|---|
    | Email address | yes | Account management | yes |
    | User IDs | yes | Account management | yes |
    | Other financial info (user-entered) | yes | App functionality | yes |
    | App interactions | yes (Firebase Analytics) | Analytics | no |
    | Crash logs | yes (Crashlytics) | App functionality | no |
    | Diagnostics | yes (Crashlytics) | App functionality | no |
- [ ] **App content → Account deletion:** in-app path not shipped yet (#213 follow-up) — declare the
      web link `https://georgeci.github.io/account-deletion.html`.
- [ ] **Store settings → Contact details:** email `georgeci007+moneysurfer@gmail.com`, website
      `https://github.com/georgeci/MoneySurfer` (mirrors `androidApp/src/main/play/`).
- [ ] Listing texts are pushed from fastlane files (`androidApp/src/main/play/listings/en-US/`) —
      graphics (icon, feature graphic, screenshots) still need to be added there or uploaded manually.

## Google Play Console — Money Surfer Offline (`androidApp-offline`)

- [ ] **Privacy policy:** `https://georgeci.github.io/privacy-policy-local.html`
- [ ] **Data safety form:** no data collected, no data shared (app has no INTERNET permission).
- [ ] **Account deletion:** n/a (no account creation → the section is not required).
- [ ] **Contact details:** same email; website already in fastlane files.

## App Store Connect — Money Surfer (online, `iosApp`)

- [ ] **App Privacy → Privacy policy URL:** `https://georgeci.github.io/privacy-policy.html`
- [ ] **App Privacy answers** (must match `iosApp/iosApp/PrivacyInfo.xcprivacy`):
  - *Data linked to you:* Email Address, User ID, Other Financial Info — purpose App Functionality.
  - *Data not linked to you:* Crash Data, Performance Data (App Functionality); Device ID,
    Product Interaction (Analytics).
  - Tracking (ATT): **no**.
- [ ] **Account deletion (App Review Guideline 5.1.1(v)):** apps with account creation must offer
      in-app account deletion — until the #213 in-app flow ships, App Review may reject; the interim
      web flow is `https://georgeci.github.io/account-deletion.html`.
- [ ] **Support URL:** `https://github.com/georgeci/MoneySurfer` (or add a support page to the site).

## App Store Connect — Money Surfer Offline (`iosAppOffline`)

- [ ] **Privacy policy URL:** `https://georgeci.github.io/privacy-policy-local.html`
- [ ] **App Privacy answers:** "Data not collected" (matches the empty
      `iosAppOffline/iosAppOffline/PrivacyInfo.xcprivacy`).
- [ ] **Support URL:** `https://georgeci.github.io/support-local.html`

---

## Open follow-ups

1. **Publish site pages** — `terms.html` + `account-deletion.html` (drafted alongside this branch),
   plus the contact-email fixes (`georgeci007+moneysurfer.com` → `…@gmail.com`, support page
   placeholder).
2. **In-app account deletion flow** (#213 remainder) — required by Apple 5.1.1(v) for the online iOS
   app and expected by Play; the deletion page is the interim answer.
3. **Play graphics for `androidApp`** — no icon/feature-graphic/screenshots in
   `androidApp/src/main/play/listings/en-US/graphics/` yet (offline variant has them).
