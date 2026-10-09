# ASH

**You live. ASH remembers.**

ASH is a privacy-first, local-first life organizer for Android, built for Indian users and tuned for the Pixel 7. It turns the notifications you allow (bank SMS, UPI, Amazon, IRCTC, insurers, utilities) into structured records, keeps an encrypted document vault, and answers one question on the Home screen: *what needs my attention?*

ASH has **no internet permission**. There is no cloud, no account, no analytics and no AI service. Everything is stored and processed on the phone.

## Status

V1 implements phases 1–11 of the plan: foundation, Home dashboard, encrypted database, notification intelligence, bills and transactions, document vault with OCR, reminders, vehicles and insurance, search, security hardening, and tests. Phase 12 (Play Store) needs your signing key and store assets (see below).

## Where your data lives

Everything is in the app's private storage on the phone. Android blocks other apps from reading it, and cloud backup is disabled.

| What | Path on the phone | Protection |
|---|---|---|
| Database | `/data/data/app.ash.android/databases/ash.db` | SQLCipher AES-256 |
| Database key | `/data/data/app.ash.android/no_backup/keys/db.key` | Wrapped by Android Keystore (TEE, or StrongBox when available) |
| Documents | `/data/data/app.ash.android/files/vault/*.bin` | Tink AES-256-GCM-HKDF streaming encryption, keyset wrapped by Keystore |
| Logs | `/data/data/app.ash.android/files/logs/` | Personal data redacted before writing |
| Settings | `/data/data/app.ash.android/files/datastore/` | App-private |

Debug builds use the package `app.ash.android.debug`. Copying the database file off the phone gives unreadable data, because the key never leaves the Keystore. Android Studio's Database Inspector cannot open the database either, since it is SQLCipher-encrypted.

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

Every build runs `verify<Variant>NoInternetPermission`, which fails the build if any library merges `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_SMS` or `QUERY_ALL_PACKAGES` into the manifest.

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
- **0.50–0.85**: the record goes to **Review**. ASH shows "ASH found something that may be important. Please confirm." Financial rows are only created after confirmation.
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
- Many banks send SMS only. ASH reads those through the Messages app notification, so Messages must be enabled as a source. ASH never requests `READ_SMS`.
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
