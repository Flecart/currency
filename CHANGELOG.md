# Changelog

## 0.4.0 — 2026-10-31

- Work with STT 1.61, which moved external backup requests to a new receiver; Currency now resolves the receiver instead of naming it.
- Add **Specific day** (with previous/next) and **Date range** options to both dashboard summaries.
- Add a one-time **Recalibrate** action in Rules that re-anchors the svago price on all data since the trial began. Sessions before recalibration keep their price, so the balance is unchanged.
- Upgrade the database to version 6 without changing the trial or original price.

## 0.3.0 — 2026-09-09

- Charge cooking and travel at 10% of the svago rate, and friends at 5%.
- Charge afternoon sleep (12:00–18:00) and the portion above nine hours per night at 10% of svago.
- Share nightly allowances across split sessions, deduplicate overlapping sleep, and preserve a fixed accounting time zone.
- Show signed credits on recent and archived session rows, using the same overlap allocation as the balance.
- Separate svago and small costs in the weekly summary.
- Upgrade existing accounts without changing their original svago price; new costs apply from trial activation.

## 0.2.0 — 2026-09-09

- Add an Archived tab and hide archived STT activities from the main lists.
- Award `esplorazioni` and `sides` 25% of the main-work rate.
- Explicitly classify `thesis`, `oxford`, `zhijing`, and `aria` as main work.
- Preserve existing trials and leisure prices while migrating activity mappings.
- Add `install-debug.sh` with device selection, connection waiting, and a fast `--no-build` option.

## 0.1.0 — 2026-09-08

- Introduce local work/leisure accounting from completed STT sessions.
- Calibrate a fixed leisure price from the preceding 28 days.
- Add Today, Activity, and Rules views with automatic foreground refresh.
- Reconcile complete snapshots and preserve the account on failed reads.
- Add adaptive and themed launcher icons.
