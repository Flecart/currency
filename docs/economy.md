# Economy rules

## Classification

| Match | Classification |
| --- | --- |
| Activity named `svago` | Leisure: spends credits |
| Activity named `esplorazioni` or `sides` | Side activity: earns at 25% of the main rate, even in Work |
| Activity named `thesis`, `oxford`, `zhijing`, or `aria` | Main work |
| Activity in the `Work` category | Main work |
| Everything else | Neutral |

Names and category matches ignore case and surrounding whitespace. These mappings reflect the initial personal experiment; there is no rule editor yet.

Work tagged `Pausa` earns nothing. The same tag never makes leisure free. `Admin` and `lab1/2/3` work tags do not receive different rates.

Known activity IDs keep their classification across renames and category edits. Newly discovered IDs use the rules above. Archiving an activity hides it from the main Activity and Rules lists but does not alter its accounting.

## Earning and spending

- Main work earns 1 C per 50 minutes.
- Side activities earn 1 C per 200 minutes.
- Earnings are proportional to elapsed time, without requiring a complete 50-minute block.
- Credits can go negative. There is no interest, penalty, cap, expiry, or borrowing surcharge.

Concurrent intervals count once, with precedence: **leisure → main work → side activity**. A paused work record does not suppress another eligible record. Durations are accumulated in milliseconds before credit amounts are rounded for display.

The dashboard's work duration includes main and side work at their actual elapsed durations. The deficit recovery estimate is in **main-work minutes**.

## Calibration and trial

A new trial examines the 28 days before activation. Its fixed leisure price makes spending on that historical baseline equal to 105% of weighted work earnings. If either eligible earning time or leisure time is absent, the fallback is 50 leisure minutes per credit.

The trial starts at zero. Pre-trial records calibrate the price but create no credits or debt. A completed session crossing activation counts only its post-activation portion. Records ending in the future are excluded until they have ended.

The initial trial lasts 14 days. Rates continue unchanged afterward; there is no automated price adjustment or constitutional review UI.

## Corrections and upgrades

Accepted snapshots replace completed records atomically. Repeated imports do not mint twice; edits and deletions reconcile the existing account. Restarting the app or reconnecting a file does not recalibrate the trial.

Version 0.2 upgrades existing databases in place. It applies the named main-work and side-activity classifications to previously recorded trial sessions, so the balance can change. The original trial start and svago price remain fixed. Archive flags are imported again even when the source file's contents are unchanged.
