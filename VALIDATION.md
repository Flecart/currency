# Validation

Version 0.2.0 was validated with synthetic fixtures on 2026-09-09.

| Check | Result |
| --- | --- |
| JVM core tests | 25 passed |
| Android instrumentation tests | 7 passed on an Android 14 emulator |
| Debug app and test APK assembly | Passed |
| Android lint | No errors; 16 advisory warnings |
| APK signature verification | Passed |
| Installer | Installed and launched the app on an emulator and an Android 16 phone |

## Accounting and parsing

Tests cover proportional and reduced-rate earnings, project mappings, pauses, negative balances, overlap precedence, activation clipping, future records, weighted calibration, fallback pricing, renames, archive flags, malformed backups, broken references, duplicate IDs, stable-file retries, revoked access, cancellation, and CSV quoting/time zones.

## Persistence and UI

Device tests verify setup, the balance screen, archive filtering, idempotent imports, record corrections/deletions, and mapping preservation.

The migration test creates a database from the committed version-1 schema and opens it through the version-2 migration. Records, trial activation, original leisure price, and document URI survive. Named activity mappings update and archive flags are reimported even when the source hash has not changed.

## STT integration

The official STT 1.59 APK was tested in an isolated Android 14 emulator using synthetic data:

1. Configure automatic backup in STT and select the same document in Currency.
2. Empty the test document; Currency's request causes STT to rewrite it and initialize a zero-credit trial.
3. Complete work and leisure timers in STT; Currency imports them and reflects earnings, spending, and a deficit.
4. Restart Currency; the trial and persistent document connection survive.

The current public CI workflow runs JVM tests, builds the app and instrumentation APK, and checks lint. It does not execute the device tests. Run those on a disposable emulator with `./gradlew :app:connectedDebugAndroidTest`.

## Installer

The installer was exercised with actual APK installation and launch. Additional smoke checks cover paths containing spaces, USB/default and explicit device selection, waiting, absent devices, failed installations, and invalid arguments.

## Known limits

See [integration details](docs/integration.md) for freshness limitations, active timers, document-provider behavior, and identifier reuse. Test fixtures contain synthetic data; personal tracking exports and local validation notes are excluded from the repository.
