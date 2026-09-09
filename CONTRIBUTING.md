# Contributing

Currency is currently a small personal experiment. For changes to earning rules, classification, or STT integration, open an issue explaining the intended behavior before investing in a large change.

## Local checks

Use a full JDK 21 and Android SDK 36:

```sh
./gradlew :core:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Run persistence, migration, and UI tests on a disposable emulator:

```sh
./gradlew :app:connectedDebugAndroidTest
```

## Project layout

- `core/`: backup parser, accounting, calibration, stable reads, and local CSV replay.
- `app/`: Compose screens, Android integration, Room persistence, and migrations.
- `app/schemas/`: exported Room schemas; keep existing versions for migration tests.
- `docs/`: economy and integration details.
- `artwork/`: original launcher artwork.

Use synthetic records in tests, screenshots, and bug reports. Do not attach personal STT exports, backups, database files, signing keys, or `local.properties`. Prefer a small sanitized fixture that reproduces the issue.

Changes to accounting should include tests for the relevant overlap and rounding behavior. Schema changes need an explicit migration that preserves an existing trial. Describe the user-visible change and the checks you ran in the pull request.
