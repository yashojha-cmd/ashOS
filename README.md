# ashOS

**You live. ashOS remembers.**

ashOS is a privacy-first, local-first life organizer for Android, built for Indian users and tuned for the Pixel 7. It turns the notifications you allow (bank SMS, UPI, Amazon, IRCTC, insurers, utilities) into structured records, keeps an encrypted document vault, and answers one question on the Home screen: *what needs my attention?*

ashOS has **no internet permission**. There is no cloud, no account, no analytics and no AI service. Everything is stored and processed on the phone.

## Status

V1 implements phases 1–11 of the plan: foundation, Home dashboard, encrypted database, notification intelligence, bills and transactions, document vault with OCR, reminders, vehicles and insurance, search, security hardening, and tests. Phase 12 (Play Store) needs your signing key and store assets (see below).

## Accounts (workspaces)

On every start, ashOS asks which account to open:

- **Yash**: your real data. Live notifications and imported SMS always go here, even while the demo is open.
- **Demo**: sample bills, payments, trips, insurance, vehicles and documents. On first open it fills itself through the real parser and OCR. Settings → Reset demo data starts it over.

Each account is a separate data space with its own SQLCipher database, its own database key and its own vault folder. The app runs one account per process, and switching restarts the app, so no screen can ever mix the two. Background work (notification capture, SMS import, reminder delivery, daily maintenance) uses an explicit per-account graph (`workspace/WorkspaceGraphs`), and reminder jobs are namespaced per account. Release builds (`DEMO_DATA = false`) skip the picker and run only the real account.

## Past data from SMS

Android keeps no notification history for apps, so earlier notifications cannot be recovered. Android does keep SMS: Settings → Account → **Import past SMS** asks for `READ_SMS` and reads the last 90 days of the inbox. Only business senders (DLT headers like `VM-HDFCBK`, or short codes) are read; messages from personal numbers are skipped without being parsed. Imported items keep their original dates, past deadlines are filed as handled, and uncertain matches are only logged. On Google Play, `READ_SMS` requires a permissions declaration (the "SMS-based money management" use case).

## Where your data lives

Everything is in the app's private storage on the phone. Android blocks other apps from reading it, and cloud backup is disabled.

| What | Path on the phone | Protection |
|---|---|---|
| Database (Yash) | `/data/data/app.ash.android/databases/ash.db` | SQLCipher AES-256 |
| Database (Demo) | `/data/data/app.ash.android/databases/ash-demo.db` | SQLCipher AES-256, separate key |
| Database keys | `/data/data/app.ash.android/no_backup/keys/db.key`, `db-demo.key` | Wrapped by Android Keystore (TEE, or StrongBox when available) |
| Documents | `/data/data/app.ash.android/files/vault/` (Yash), `files/vault-demo/` (Demo) | Tink AES-256-GCM-HKDF streaming encryption, keyset wrapped by Keystore |
| Logs | `/data/data/app.ash.android/files/logs/` | Personal data redacted before writing |
| Settings | `/data/data/app.ash.android/files/datastore/` | App-private |

Debug builds use the package `app.ash.android.debug` and staging builds `app.ash.android.staging`; each has its own data. Copying the database file off the phone gives unreadable data, because the key never leaves the Keystore. Android Studio's Database Inspector cannot open the database either, since it is SQLCipher-encrypted.

## Build

Requirements: the Android SDK (compileSdk 37.2) and a JDK 17+ (the JDK bundled with Android Studio works). Android Studio is optional.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 57 unit, Robolectric and Compose tests
./gradlew assembleRelease        # R8-minified, unsigned
```

Install on a connected Pixel 7 with USB debugging on:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Every build runs `verify<Variant>NoInternetPermission`, which fails the build if any library merges `INTERNET`, `ACCESS_NETWORK_STATE` or `QUERY_ALL_PACKAGES` into the manifest.

## Architecture

Single `:app` module with Clean Architecture packages:

```
app.ash
├── common/          time, money (Long paise, Indian grouping), dispatchers, results
├── logging/         AshLogger, redacting rotating file sink (5 × 1 MB), crash handler, export
├── security/        Keystore key manager, SQLCipher key, Tink vault crypto, backup crypto, app lock, biometrics
├── data/
│   ├── local/       Room entities (22 tables incl. FTS4), DAOs, SQLCipher database
│   ├── parser/      rule engine: 11 parsers, extractors, merchant and app dictionaries
│   └── repository/  repository implementations, settings (DataStore)
├── domain/          models, repository interfaces, use cases
├── intelligence/    IntelligenceEngine seam, rule engine, priority engine, OCR field extraction, search parser, subscription detection
├── notification/    NotificationListenerService and the ingestion pipeline
├── document/        encrypted vault storage, OCR (ML Kit bundled, offline), image tools
├── reminder/        planner, WorkManager/AlarmManager scheduler, notifications, boot and maintenance workers
├── search/          FTS-backed local search
├── backup/          encrypted export/import and delete-all
├── designsystem/    theme, Manrope type, spring motion, components, icons
├── navigation/      type-safe routes, nav host, app state
└── presentation/    onboarding, home, inbox, vault, life, reminders, search, settings, privacy, diagnostics
```

### Notification pipeline

`AshNotificationListener` → source allow-list (opt-in per app) → OTP/password filter (never stored) → duplicate check → `RuleBasedIntelligenceEngine` → confidence bands:

- **≥ 0.85**: the record is created automatically, with typed rows (transaction, bill, trip…) and reminders.
- **0.50–0.85**: the record goes to **Review**. ashOS shows "ashOS found something that may be important. Please confirm." Financial rows are only created after confirmation.
- **< 0.50**: only the notification log is kept.

### Priority (Home)

`score = urgency + deadline proximity + importance + overdue + action`. Each part is shown in the "Why is this here?" sheet (long-press a card). A score of 60 or more goes under *Needs attention* and 25–59 under *Coming up*. Items handled in the last 48 hours appear under *Recently handled*.

### Future on-device AI

`IntelligenceEngine` is bound in `di/AppModule.kt`. A future `OnDeviceAIEngine` can replace the binding without other changes. Any model must run locally and be deletable, and the rule engine stays as the fallback. Adding model downloads would mean revisiting the no-internet guarantee.

## Debug logs

Settings → Diagnostics → View logs, then Share to export a ZIP. Logs mask amounts, account, PAN, Aadhaar and vehicle numbers. "Detailed parser logs" records raw notification text for 24 hours to debug parser misses, then switches itself off.

## Tests

| Suite | Covers |
|---|---|
| `ParserGoldenTest` | 37 real-world notification formats: HDFC, SBI, ICICI, Axis, Hinglish, GPay, PhonePe, Paytm, Amazon, Flipkart, Swiggy, Zomato, Delhivery, IRCTC, IndiGo, redBus, ACKO, LIC, Star Health, BESCOM, Airtel, Jio, credit cards, Netflix, YouTube, Spotify, PUC, e-challan, EPFO, UIDAI, OTPs, promos |
| `ExtractorTest` | amounts, Indian date formats, references, merchants |
| `IntelligenceTest` | priority scoring, search queries, subscription detection, OCR fields, reminder planning |
| `CommonTest` | money formatting, log redaction, backup encryption (round trip, wrong password, tampering) |
| `NotificationPipelineTest` | end to end with a real Room database: allow-list, OTP skip, duplicates, bills and reminders, delivery merging, review/confirm, search, Home feed |
| `DatabaseTest` | seeding, FTS sync, soft delete and purge, tags |
| `ManifestSecurityTest` | no network/SMS/location permissions, backup off, only expected exported components |
| `UiTest`, `InboxViewModelTest` | accessibility semantics, ViewModel filtering and actions |

## Known limitations

- The notification listener only sees notifications posted after access is granted; there is no history import.
- Many banks send SMS only. Live SMS arrive through the Messages app notification (enable Messages as a source); older SMS can be imported with the optional `READ_SMS` permission.
- Parsers are deterministic rules. New notification formats need a dictionary or regex update, plus a golden test case.
- Camera scanning captures a photo without automatic edge detection or perspective correction (planned).
- OCR quality depends on the image. Users always confirm the extracted fields.
- Reminders default to WorkManager and can arrive a few minutes late under Doze. "Exact reminder times" uses exact alarms when Android allows it.
- A rooted or compromised phone, or someone holding the unlocked phone, can bypass app protections.
- Hindi strings are written (`docs/i18n/strings-hi.xml`) but not enabled in V1.
- Baseline profile generation needs a connected device (`profileinstaller` is included).

## Play Store checklist (phase 12)

- [ ] Create an upload key and configure `signingConfigs` (not committed).
- [ ] Host `docs/privacy.md` (for example on GitHub Pages) and link it in the Play Console.
- [ ] Data safety form: no data collected, no data shared.
- [ ] Prominent disclosure for notification access is already shown in onboarding.
- [ ] Screenshots on a Pixel 7 frame, store listing, content rating.
- [ ] Confirm the `app.ash.android` application ID (permanent once published).
