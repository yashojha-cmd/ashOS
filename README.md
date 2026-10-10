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

## Today, Money, Vault, Life

The app has four tabs. Search and Messages (the inbox) open from the Today top bar.

- **Today** is one glanceable dashboard:
  - **Life in order**: a 0–100 score. Points come off for overdue payments, expired documents, things due in 3 days or expiring this week, messages waiting for confirmation, and unfinished setup. Each reason is capped. Tap the score to see exactly where every point went.
  - **Money left this cycle**: a ring for the share left, a sparkline of the running balance, and a safe amount per day. The amount stays hidden ("₹ • • • •") until you unlock with fingerprint or PIN, the same rule as the Money tab.
  - **Today**: anything overdue or due today.
  - **Next 7 days**: a week strip with coloured markers; tap a day to see what's due.
  - **Setup checklist** (real account only): notification access, apps to read, past-SMS import, first document, app lock.
  - Recent payments.
- **Money**: the salary statement (described below), with a category donut chart. The top-right button opens all transactions, bills and subscriptions.
- **Every card shows meaning, not the message.** `RecordSummarizer` turns a record into a subject, one headline and up to three facts:
  - Example: "ACKO · Vehicle insurance · Expires in 12 days · Policy AB123" rather than "ACKO / ACKO / pasted SMS / Insurance · Insurance".
  - The source app is shown only when it adds information.
  - The original message is one tap away in the detail view ("Show original message").

## Money tab (statements)

A salary-to-salary statement built from your bank and UPI messages:

- **Salary starts a cycle.** A credit with a salary marker ("NEFT-SALARY", "Salary credited", "SAL CR", "Payroll", "Stipend") opens a new statement, and its amount is the opening balance. If your salary SMS has no marker, open the credit and tap **Mark as salary**; ashOS remembers the payer and recognises next month's credit (at least half the last amount).
- **Running balance.** Every payment subtracts and every credit or refund adds, newest first, with the balance after each line. Self-transfers are listed but not counted.
- **Insights.** Remaining amount, spent and received, days until the next expected salary (median gap between salaries) with a safe amount per day, where the money went by category, top payees, and the biggest category changes against the previous cycle. When an SMS quotes the bank's own "Avl Bal", it is shown as a cross-check.
- **History.** Arrows step through earlier cycles. Before any salary is known, the statement covers the current month.

## How ashOS structures what it reads

ashOS does not dump every message into a list. It links and files what it captures:

- **One payment, one entry.** The bank SMS and the UPI app notification for the same payment (same amount and direction, from two apps, within 15 minutes) are merged into one transaction and enriched with details from both.
- **Bills settle themselves.** A payment that matches an open bill (amount within ₹1, due 30 days before to 15 days after the payment, biller name preferred) marks the bill paid and cancels its reminders.
- **Updates merge.** Delivery updates with the same order ID and trip updates with the same PNR update one record instead of creating new ones.
- **It learns your categories.** Correct a merchant's category once; future payments to that merchant use it.
- **Inbox shows what needs you.** Routine payments live in Balance, not the Inbox. The Inbox opens on *Needs you* (review, action required, new) and groups items by Today, Yesterday, Earlier this week and Earlier.
- **History is filed, not flagged.** Imported SMS keep their original dates; past deadlines are filed as handled; uncertain old messages are only logged.

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
./gradlew testDebugUnitTest      # 77 unit, Robolectric and Compose tests
./gradlew assembleStaging        # R8-optimised build signed with the debug key, for testing on a phone
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

`AshNotificationListener` → source check ("Read all apps" is on by default; any app can be switched off) → OTP/password filter (never stored) → duplicate check → `RuleBasedIntelligenceEngine` → nothing understood: counted and dropped (no text stored or logged) → confidence bands:

- **≥ 0.85**: the record is created automatically, with typed rows (transaction, bill, trip…) and reminders.
- **0.50–0.85**: the record goes to **Review**. ashOS shows "ashOS found something that may be important. Please confirm." Financial rows are only created after confirmation.
- **< 0.50**: only a notification log row is kept (text only if "keep original text" is on).

### Priority (Home)

`score = urgency + deadline proximity + importance + overdue + action`. Each part is shown in the "Why is this here?" sheet (long-press a card). A score of 60 or more goes under *Needs attention* and 25–59 under *Coming up*. Items handled in the last 48 hours appear under *Recently handled*.

### Future on-device AI

`IntelligenceEngine` is bound in `di/AppModule.kt`. A future `OnDeviceAIEngine` can replace the binding without other changes. Any model must run locally and be deletable, and the rule engine stays as the fallback. Adding model downloads would mean revisiting the no-internet guarantee.

## Debug logs

Settings → Diagnostics → View logs, then Share to export a ZIP. Logs mask amounts, account, PAN, Aadhaar and vehicle numbers. "Detailed parser logs" records raw notification text for 24 hours to debug parser misses, then switches itself off.

Debug and staging builds also mirror the log to logcat (`LOGCAT_MIRROR`), so a connected phone can be read with `adb logcat -s ashOS`. Release builds keep logs in the app only. Logged: startup (version, build type, account), each screen opened (name only), notification parse results with timing, OCR timing, SMS import totals, duplicate merges, bills settled by payments, and maintenance results.

Before installing a new build on the test phone: read the device log and frame stats (`adb shell dumpsys gfxinfo app.ash.android.staging`), review and fix what they show, and install only after the owner confirms.

## Tests

| Suite | Covers |
|---|---|
| `ParserGoldenTest` | 43 real-world notification formats (incl. salary credits, Rs:500, INR500, "debited by", ATM, Avl/Avbl Bal): HDFC, SBI, ICICI, Axis, Hinglish, GPay, PhonePe, Paytm, Amazon, Flipkart, Swiggy, Zomato, Delhivery, IRCTC, IndiGo, redBus, ACKO, LIC, Star Health, BESCOM, Airtel, Jio, credit cards, Netflix, YouTube, Spotify, PUC, e-challan, EPFO, UIDAI, OTPs, promos |
| `ExtractorTest` | amounts, Indian date formats, references, merchants |
| `IntelligenceTest` | priority scoring, search queries, subscription detection, OCR fields, reminder planning |
| `DashboardTest` | record summaries (insurance, bills, salary, trips, fallback sentence), Life-in-order score and caps, compact Indian money (K/L/Cr) |
| `CommonTest` | money formatting, log redaction, backup encryption (round trip, wrong password, tampering) |
| `NotificationPipelineTest` | end to end with a real Room database: allow-list, OTP skip, duplicates, bills and reminders, delivery merging, review/confirm, search, Home feed, SMS history import, workspace isolation, cross-app payment merge, salary detection and learning, bill settled by payment, category learning, inbox filtering |
| `StatementCalculatorTest` | salary cycles, running balance, reset on new salary, previous cycles, expected salary and daily budget, insights |
| `MigrationTest` | real v1 database upgraded to v2 without losing transactions |
| `DatabaseTest` | seeding, FTS sync, soft delete and purge, tags |
| `ManifestSecurityTest` | no network, SMS send/receive or location permissions (READ_SMS is opt-in), backup off, only expected exported components |
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
