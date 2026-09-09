# Simple Time Tracker integration

Currency is an independent companion app. It does not embed STT's application code or access its private database.

## Shared document

Configure STT's automatic-backup destination first. Currency then uses Android's system document picker to obtain persistent read access to the same document. It requests a fresh backup on opening and every 60 seconds while visible.

The explicit broadcast is:

```text
Package:  com.razeeman.util.simpletimetracker
Receiver: com.example.util.simpletimetracker.feature_notification.recevier.NotificationReceiver
Action:   com.razeeman.util.simpletimetracker.ACTION_EXTERNAL_AUTOMATIC_BACKUP
```

The spelling `recevier` is part of STT's component name. The receiver was checked against the official STT 1.59 APK.

## Snapshot handling

The importer accepts STT's tab-separated backup format, not its CSV export. It validates UTF-8, row shapes, identifiers, references, and supported section types. Settings rows are optional. Files above 32 MB are rejected.

The reader uses bounded retries and requires two identical validated reads one second apart. Accepted snapshots replace records in a Room transaction. Existing activity mappings and the original calibration are retained.

STT's activity archive flag determines which list shows its sessions. Archiving does not remove them from the balance. Both session lists show up to 100 recent completed records since trial activation.

## Limits

- The backup excludes active timers. Complete a session before expecting it to affect credits.
- STT exposes no write acknowledgement, snapshot generation number, or end marker through this route. A valid unchanged file may be stale; a stable, structurally valid prefix cannot always be identified as an interrupted write.
- STT writes to the document non-atomically. The validation and stable-read policy reduce risk but cannot make that interface transactional.
- A failed refresh keeps the previous accepted snapshot. Revoked permissions require reconnecting the document.
- STT restore/reset operations can reuse identifiers. Reconnect only to the same dataset during an existing trial.
- Use a local document provider. Behavior can vary by Android version and provider.

## Local data

Currency stores activity IDs, names and mappings; completed record IDs and timestamps; tags; the original calibration; and document metadata. It does not retain record comments or unrelated STT settings. The manifest has no internet permission, and Android cloud/device-transfer backups are excluded.

## Sources

- [STT backup serialization and activity archive flag](https://github.com/Razeeman/Android-SimpleTimeTracker/blob/v1.59/data_local/src/main/java/com/example/util/simpletimetracker/data_local/backup/BackupRepoImpl.kt)
- [STT notification receiver](https://github.com/Razeeman/Android-SimpleTimeTracker/blob/v1.59/features/feature_notification/src/main/java/com/example/util/simpletimetracker/feature_notification/recevier/NotificationReceiver.kt)
- [Android persistent document access](https://developer.android.com/training/data-storage/shared/documents-files)
