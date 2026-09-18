# Validation

Version 0.3.0 was validated with synthetic fixtures on 2026-09-09.

| Check | Result |
| --- | --- |
| JVM core tests | 37 passed |
| Android instrumentation tests | 9 passed on an Android 14 emulator |
| Debug app and test APK assembly | Passed |
| Android lint | No errors; 16 advisory warnings |
| APK signature verification | Passed |
| Installer | Installed and launched 0.3.0 on an Android 14 emulator; 0.2.0 was also tested on an Android 16 phone |

## Accounting and parsing

Tests cover small-cost rate weighting, afternoon sleep boundaries, nine-hour nightly allowances, split/overlapping sleep, daylight-saving transitions, reporting-window context, per-session credit allocation, proportional and reduced-rate earnings, project mappings, pauses, negative balances, overlap precedence, activation clipping, future records, weighted calibration, fallback pricing, renames, archive flags, malformed backups, broken references, duplicate IDs, stable-file retries, revoked access, cancellation, and CSV quoting/time zones.

## Persistence and UI

Device tests verify signed session earnings and costs, free sleep, setup, the balance screen, archive filtering, idempotent imports, record corrections/deletions, and mapping preservation.

Migration tests create databases from the committed version-1 and version-2 schemas and upgrade both to version 3. Records, trial activation, original leisure price, archive state, and document URI survive. New spending mappings and the saved accounting time zone are verified. The version-1 upgrade still reimports archive flags even when the source hash has not changed.

## STT integration

The official STT 1.59 APK was previously tested for the unchanged integration in an isolated Android 14 emulator using synthetic data:

1. Configure automatic backup in STT and select the same document in Currency.
2. Empty the test document; Currency's request causes STT to rewrite it and initialize a zero-credit trial.
3. Complete work and leisure timers in STT; Currency imports them and reflects earnings, spending, and a deficit.
4. Restart Currency; the trial and persistent document connection survive.

The current public CI workflow runs JVM tests, builds the app and instrumentation APK, and checks lint. It does not execute the device tests. Run those on a disposable emulator with `./gradlew :app:connectedDebugAndroidTest`.

## Installer

The installer was exercised with actual APK installation and launch. Additional smoke checks cover paths containing spaces, USB/default and explicit device selection, waiting, absent devices, failed installations, and invalid arguments.

## Known limits

See [integration details](docs/integration.md) for freshness limitations, active timers, document-provider behavior, and identifier reuse. Test fixtures contain synthetic data; personal tracking exports and local validation notes are excluded from the repository.

## September 18 changes

41 JVM tests passed; debug app and instrumentation APKs built successfully. Coverage now includes chores, People as an alias for Friends, calendar summary windows, historical boundary clipping, and per-activity reconciliation. The database version-5 upgrade was installed on the connected phone and verified to map People to FRIENDS, preserve the trial, and pass SQLite integrity checks. Instrumentation tests compiled, but emulator execution did not complete; the new UI checks are not yet runtime-validated.
