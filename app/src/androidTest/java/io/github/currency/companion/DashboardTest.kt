package io.github.currency.companion

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.currency.companion.data.*
import io.github.currency.companion.ui.CurrencyApp
import io.github.currency.core.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setupExplainsConnectionAndStartsWithNoDailyInput() {
        compose.setContent { CurrencyApp(ScreenState(loading = false), {}, {}) }
        compose.onNodeWithText("Keep tracking.\nLet credits follow.").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Choose backup & start trial"))
        compose.onNodeWithText("Choose backup & start trial").assertIsDisplayed()
    }

    @Test fun negativeBalanceAndNeutralActivitiesAreVisible() {
        val now = System.currentTimeMillis()
        val trial = Trial(now - 6_000_000, now - 30_000_000, now - 6_000_000, Totals())
        val snapshot = Snapshot(listOf(Activity(1, "Research", setOf("Work")), Activity(2, "svago")),
            listOf(Record(1, 2, now - 3_000_000, now)), emptyMap())
        val stored = StoredState(snapshot, mappings(snapshot), trial,
            SyncRow(uri = "content://example/backup", documentName = "backup", hash = "hash", lastRead = now, lastChanged = now),
            listOf(ActivityRow(1, "Research", "WORK"), ActivityRow(2, "svago", "LEISURE")))
        compose.setContent { CurrencyApp(ScreenState(stored, loading = false), {}, {}) }
        compose.onNodeWithText("-1.00").assertIsDisplayed()
        compose.onNodeWithText("50 work minutes to return to zero.").assertIsDisplayed()
        compose.onNodeWithText("Rules").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Negative balances are allowed. No interest, penalties, expiry, or balance cap. Exercise and unlisted activities are neutral."))
        compose.onNodeWithText("Negative balances are allowed. No interest, penalties, expiry, or balance cap. Exercise and unlisted activities are neutral.").assertIsDisplayed()
    }
    @Test fun sessionRowsShowEarningsSmallCostsAndFreeSleep() {
        val now = java.time.LocalDate.now().atTime(11, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 86_400_000
        val activities = listOf(Activity(1, "Thesis"), Activity(2, "Cooking"), Activity(3, "Friends"), Activity(4, "Sleep"))
        val snapshot = Snapshot(activities, listOf(Record(1, 1, now - 9_000_000, now - 6_000_000),
            Record(2, 2, now - 6_000_000, now - 3_000_000), Record(3, 3, now - 3_000_000, now),
            Record(4, 4, now - 18_000_000, now - 12_000_000)), emptyMap())
        val kinds = mappings(snapshot)
        val stored = StoredState(snapshot, kinds, Trial(now - 20_000_000, 0, 0, Totals()), null,
            activities.map { ActivityRow(it.id, it.name, kinds.getValue(it.id).name) })
        compose.setContent { CurrencyApp(ScreenState(stored, loading = false), {}, {}) }
        compose.onNodeWithText("Activity").performClick()
        for (amount in listOf("−0.05 C", "−0.25 C", "+1.00 C", "0.00 C")) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(amount))
            compose.onNodeWithText(amount).assertIsDisplayed()
        }
    }

    @Test fun archivedSessionsAreHiddenFromActivityAndAvailableSeparately() {
        val now = System.currentTimeMillis()
        val snapshot = Snapshot(listOf(Activity(1, "Current"), Activity(2, "Old project", archived = true)),
            listOf(Record(1, 1, now - 2000, now - 1000), Record(2, 2, now - 1000, now)), emptyMap())
        val stored = StoredState(snapshot, mapOf(1L to Kind.WORK, 2L to Kind.WORK), Trial(now - 3000, 0, 0, Totals()), null,
            listOf(ActivityRow(1, "Current", "WORK"), ActivityRow(2, "Old project", "WORK", archived = true)))
        compose.setContent { CurrencyApp(ScreenState(stored, loading = false), {}, {}) }
        compose.onNodeWithText("Activity").performClick()
        compose.onNodeWithText("Current").assertIsDisplayed()
        compose.onNodeWithText("Old project").assertDoesNotExist()
        compose.onNodeWithText("Archived").performClick()
        compose.onNodeWithText("Archived sessions").assertIsDisplayed()
        compose.onAllNodesWithText("Old project").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Current").assertDoesNotExist()
        compose.onNodeWithText("Rules").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Current"))
        compose.onNodeWithText("Current").assertIsDisplayed()
        compose.onNodeWithText("Old project").assertDoesNotExist()
    }

    @Test fun summaryRangesAreIndependentValidatedAndRemembered() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("summary_ranges", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val now = System.currentTimeMillis()
        val stored = StoredState(Snapshot(emptyList(), emptyList(), emptyMap()), emptyMap(),
            Trial(now - 1000, 0, 0, Totals()), null, emptyList())
        compose.setContent { CurrencyApp(ScreenState(stored, loading = false), {}, {}) }
        compose.onNodeWithText("Today ▾").performScrollTo().performClick()
        compose.onNodeWithText("Last month").performClick()
        compose.onNodeWithText("Last month ▾").assertExists()
        compose.onNodeWithText("Last 7 days ▾").performScrollTo().performClick()
        compose.onNodeWithText("Last N days").performClick()
        compose.onNodeWithText("Number of days").performTextReplacement("0")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Number of days").performTextReplacement("14")
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Last 14 days ▾").assertExists()
        compose.runOnIdle {
            org.junit.Assert.assertEquals("LAST_MONTH", prefs.getString("overview", null))
            org.junit.Assert.assertEquals("LAST_DAYS", prefs.getString("breakdown", null))
            org.junit.Assert.assertEquals(14, prefs.getInt("breakdown.days", 0))
        }
        compose.onNodeWithText("Activity").performClick()
        compose.onNodeWithText("Summary").performClick()
        compose.onNodeWithText("Last month ▾").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Last 14 days ▾").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { prefs.edit().clear().commit() }
    }

    @Test fun breakdownShowsActivityHoursCreditsAndSpendingFilter() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("summary_ranges", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val now = System.currentTimeMillis()
        val activities = listOf(Activity(1, "Thesis"), Activity(2, "Cooking", archived = true))
        val snapshot = Snapshot(activities, listOf(Record(1, 1, now - 6_000_000, now),
            Record(2, 2, now - 3_000_000, now)), emptyMap())
        val kinds = mappings(snapshot)
        val stored = StoredState(snapshot, kinds, Trial(now - 6_000_000, 0, 0, Totals()), null,
            activities.map { ActivityRow(it.id, it.name, kinds.getValue(it.id).name, archived = it.archived) })
        compose.setContent { CurrencyApp(ScreenState(stored, loading = false), {}, {}) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("By activity"))
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("+1.0000 C"))
        compose.onNodeWithText("+1.0000 C").assertIsDisplayed()
        compose.onNodeWithText("1.67 h recorded · 0.83 h counted").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Cooking · Archived"))
        compose.onNodeWithText("−0.2500 C").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("By activity"))
        compose.onNode(hasText("Spent") and hasClickAction()).performClick()
        compose.onNodeWithText("Thesis").assertDoesNotExist()
        compose.onNodeWithText("Cooking · Archived").assertExists()
    }

}
