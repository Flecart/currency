# Economy rules

## Classification

| Match | Classification |
| --- | --- |
| Activity named `svago` | Leisure: spends credits |
| Activity named `cooking` or `travel` | Spends at 25% of svago, even in Work |
| Activity named `people` or `friends` | Spends at 5% of svago, even in Work |
| Activity named `sleep` | Conditional spending at 25% of svago, even in Work |
| Activity named `esplorazioni` or `sides` | Side activity: earns at 25% of the main rate, even in Work |
| Activity named `chores` | Earns at 10% of the main rate, even in Work |
| Activity named `thesis`, `oxford`, `zhijing`, or `aria` | Main work |
| Activity in the `Work` category | Main work |
| Everything else | Neutral |

Names and category matches ignore case and surrounding whitespace. These mappings reflect the initial personal experiment; there is no rule editor yet.

Work tagged `Pausa` earns nothing. The same tag never makes any spending activity free. `Admin` and `lab1/2/3` work tags do not receive different rates.

Known activity IDs keep their classification across renames and category edits. Newly discovered IDs use the rules above. Archiving an activity hides it from the main Activity and Rules lists but does not alter its accounting.

## Earning and spending

- Main work earns 1 C per 50 minutes.
- Side activities earn 1 C per 200 minutes.
- Chores earn 0.1 C per 50 minutes.
- Earnings are proportional to elapsed time, without requiring a complete 50-minute block.
- Credits can go negative. There is no interest, penalty, cap, expiry, or borrowing surcharge.

Concurrent intervals count once, with precedence: **svago → cooking / travel / chargeable sleep → friends → main work → side activity → chores**. Equal-price overlaps are attributed to the earliest-started session, then the lowest STT record ID. Free sleep is neutral and does not mask another activity. A paused work record does not suppress another eligible record. Durations are accumulated in milliseconds before credit amounts are rounded for display.

The Activity and Archived tabs show each session's allocated net credits. Its displayed duration includes overlapping time, while its credits only include the time attributed to it. Zero-credit sessions remain visible. Values smaller than 0.01 C are shown as `+<0.01 C` or `−<0.01 C`. Display rounding can make the sum of visible session amounts differ slightly from the full-precision balance. Lists show the latest 100 matching sessions; the balance includes all eligible sessions.

The dashboard's spending duration includes only chargeable time, including the chargeable portion of sleep. Its weekly summary separates svago from small costs.

The dashboard's work duration includes main work, side work, and chores at their actual elapsed durations. The deficit recovery estimate is in **main-work minutes**.

## Sleep

- **Afternoon:** 12:00 inclusive to 18:00 exclusive, charged at 25% of the svago rate.
- **Night:** 18:00 to the following noon. The first nine hours of recorded sleep are free; only excess time is charged at 25% of svago.
- Split sessions share the same nightly allowance, even with gaps. Overlapping sleep records count once toward that allowance. Afternoon sleep does not consume it.
- Sleep is measured in elapsed hours, including daylight-saving transitions. Local clock boundaries use the time zone saved when setting up or upgrading the app, shown in Rules. Changing the phone's zone does not reprice the history.
- Completed sleep before a reporting window or trial activation still contributes to the nine-hour allowance; it never incurs pre-trial debt. Future/unfinished records do not count toward the allowance.
- Sleep recorded simultaneously with another activity still counts toward its nightly allowance. Charges follow the single-count overlap precedence above.

At the provisional 50 svago minutes per credit, 50 minutes of cooking, travel, or chargeable sleep costs 0.25 C; 50 minutes with friends costs 0.05 C. Calibrated accounts retain these same relative prices.

## Calibration and trial

A new trial examines the 28 days before activation. Its fixed leisure price makes svago spending on that historical baseline equal to 105% of weighted work earnings. Chores, cooking, travel, friends, and sleep are excluded from calibration, including when they overlap work. Their earnings and costs are added to post-activation accounting. If either eligible earning time or leisure time is absent, the fallback is 50 leisure minutes per credit.

The trial starts at zero. Pre-trial records calibrate the price but create no credits or debt. A completed session crossing activation counts only its post-activation portion. Records ending in the future are excluded until they have ended.

The initial trial lasts 14 days. Rates continue unchanged afterward; there is no automated price adjustment or constitutional review UI.

## Corrections and upgrades

Accepted snapshots replace completed records atomically. Repeated imports do not mint twice; edits and deletions reconcile the existing account. Restarting the app or reconnecting a file does not recalibrate the trial.

Version 0.2 upgrades existing databases in place. It applies the named main-work and side-activity classifications to previously recorded trial sessions, so the balance can change. The original trial start and svago price remain fixed. Archive flags are imported again even when the source file's contents are unchanged.

Version 0.3 migrates existing activities named cooking, travel, friends, and sleep to the new classifications, including archived activities. Costs recalculate all completed sessions since trial activation. The baseline, svago price, records, and backup connection are preserved. The upgrade saves the device's current time zone for sleep accounting and dashboard day boundaries; existing session timestamps remain displayed in the device's viewing time zone.

Database version 4 reclassifies existing activities named chores, including archived activities. Chore earnings apply to completed sessions since trial activation; the baseline and svago price stay fixed.

Both dashboard summaries have independent, remembered periods: today, last N days (including today), this/last Monday-based week, and this/last calendar month. Earnings, spending, and net change are shown in credits. Dates use the trial time zone and only post-activation time counts. Completed sessions crossing a period boundary contribute only their overlapping time. Cooking, travel, and chargeable sleep now cost 25% of svago, recalculated since trial activation; friends remain at 5%.

The second summary includes a per-activity credit breakdown for its selected period, ordered by absolute credits. Bars share a common scale, with earnings in green and spending in orange; filters select all, earning, spending, or zero-credit activities. Recorded hours include overlapping and paused sessions; counted hours use the ledger allocation and exclude free/neutral time. Archived activities are included. Activity credits display four decimals (smaller nonzero amounts are marked explicitly), while account calculations retain full precision.

Database version 5 maps existing People activities to the 5% friends cost, including archived activities. Sessions since trial activation are recalculated; the saved calibration and trial dates are preserved. New activities named People or Friends use the same rate.
