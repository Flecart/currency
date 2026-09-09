package io.github.currency.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.currency.companion.ScreenState
import io.github.currency.companion.data.StoredState
import io.github.currency.core.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Paper = Color(0xFFF5F4EF)
private val Forest = Color(0xFF173E35)
private val Lime = Color(0xFFD8EDB5)
private val Ink = Color(0xFF202C27)
private val Muted = Color(0xFF65716A)
private val Warm = Color(0xFFAF583A)

@Composable
fun CurrencyApp(state: ScreenState, onChooseFile: () -> Unit, onOpenStt: () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White,
        secondary = Forest, background = Paper, surface = Paper, onSurface = Ink,
        surfaceVariant = Color(0xFFE9ECE3), onSurfaceVariant = Muted)) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(containerColor = Paper) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().widthIn(max = 720.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = Forest, shape = RoundedCornerShape(12.dp)) {
                            Text("C", Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = Lime,
                                fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                        Column {
                            Text("Currency", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("WORK & LEISURE", fontSize = 10.sp, letterSpacing = 1.8.sp, color = Muted)
                        }
                    }
                    if (state.stored?.trial != null) TextButton(onClick = onChooseFile, enabled = !state.syncing) { Text("Reconnect") }
                }
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.stored?.trial == null -> Setup(state, onChooseFile, onOpenStt)
                    else -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Today", "Activity", "Archived", "Rules").forEachIndexed { index, title ->
                                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) })
                            }
                        }
                        if (state.syncing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Forest)
                        when (tab) {
                            0 -> Dashboard(state, onChooseFile)
                            1 -> ActivityLog(state.stored)
                            2 -> ActivityLog(state.stored, archived = true)
                            else -> Rules(state.stored, onOpenStt)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Setup(state: ScreenState, onChooseFile: () -> Unit, onOpenStt: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Text("Keep tracking.\nLet credits follow.", fontSize = 36.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Your work earns. Svago spends. Currency reads the records you already keep in Simple Time Tracker.", color = Muted, lineHeight = 24.sp)
        }
        item {
            Surface(color = Forest, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ONE-TIME CONNECTION", color = Lime, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text("No extra daily tracking", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your balance starts at zero. The last 28 days help set a modest starting challenge. All data stays on this device.", color = Color(0xFFD0DCD5), lineHeight = 23.sp)
                }
            }
        }
        item { SetupStep("1", "Choose a backup location in STT", "In STT’s settings, enable Automatic backup and choose a local file. Use the full backup, including records, rather than CSV export.") }
        item { SetupStep("2", "Open that same file here", "Give Currency read access once. Keep using this backup location in STT so the file stays up to date.") }
        item { SetupStep("3", "Track as usual", "Currency requests a fresh backup when opened and every minute while visible. Completed sessions count automatically.") }
        if (state.error != null) item { ErrorCard(state.error, null) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenStt, modifier = Modifier.fillMaxWidth()) { Text("Open Simple Time Tracker") }
                Button(onClick = onChooseFile, enabled = !state.syncing, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    if (state.syncing) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (state.syncing) "Reading your backup…" else "Choose backup & start trial")
                }
                Text("50 work minutes = 1 credit. Leisure pricing is calibrated once for a 14-day trial. Negative balances are allowed.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun SetupStep(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Color(0xFFE3E9DC), shape = RoundedCornerShape(10.dp)) {
            Text(number, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, color = Forest)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun Dashboard(state: ScreenState, onChooseFile: () -> Unit) {
    val stored = state.stored ?: return
    val trial = stored.trial ?: return
    val now = System.currentTimeMillis()
    val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val week = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val total = Economy.account(Economy.totals(stored.snapshot, stored.kinds, trial.activatedAt, now), trial)
    val todayTime = Economy.totals(stored.snapshot, stored.kinds, maxOf(today, trial.activatedAt), now)
    val todayAccount = Economy.account(todayTime, trial)
    val weekTime = Economy.totals(stored.snapshot, stored.kinds, maxOf(week, trial.activatedAt), now)
    val weekAccount = Economy.account(weekTime, trial)
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.error != null) item { ErrorCard(state.error, onChooseFile) }
        item {
            Surface(color = Forest, shape = RoundedCornerShape(26.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("YOUR BALANCE", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Lime)
                        Text(if (now < trial.endsAt) "14-day trial" else "Trial complete", fontSize = 12.sp, color = Lime)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(decimal(total.balance), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("credits", color = Color(0xFFC1D2C7), modifier = Modifier.padding(bottom = 9.dp))
                    }
                    HorizontalDivider(color = Color(0xFF426357))
                    Text(if (total.balance.signum() < 0) "${decimal(total.workMinutesToZero, 0, RoundingMode.CEILING)} work minutes to return to zero."
                        else if (total.balance.signum() > 0) "About ${decimal(total.balance.multiply(Economy.leisureMinutesPerCredit(trial)), 0, RoundingMode.FLOOR)} minutes of svago at your current rate."
                        else "Your next completed work session starts earning.", color = Color(0xFFDFE9E1), lineHeight = 22.sp)
                }
            }
        }
        item {
            SectionLabel("Today", LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Work", duration(todayTime.workMs + todayTime.sideMs), "+${decimal(todayAccount.earned)} C", Forest, Modifier.weight(1f))
                MetricCard("Svago", duration(todayTime.leisureMs), "−${decimal(todayAccount.spent)} C", Warm, Modifier.weight(1f))
            }
        }
        item {
            SectionLabel("Past 7 days", "Since trial start")
            Spacer(Modifier.height(12.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ValueRow("Work earned · ${duration(weekTime.workMs + weekTime.sideMs)}", "+${decimal(weekAccount.earned)} C", Forest)
                    ValueRow("Svago spent · ${duration(weekTime.leisureMs)}", "−${decimal(weekAccount.spent)} C", Warm)
                    HorizontalDivider(color = Paper)
                    ValueRow("Net change", "${decimal(weekAccount.balance)} C", Ink)
                }
            }
        }
        item {
            Surface(color = Color(0xFFE8ECDC), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A steady exchange", fontWeight = FontWeight.SemiBold)
                    Text("50 minutes of main work earns 1 C.\n200 minutes of esplorazioni or sides earns 1 C.\n${decimal(Economy.leisureMinutesPerCredit(trial), 1)} minutes of svago costs 1 C.", lineHeight = 24.sp)
                    Text(if (trial.provisional) "Provisional rate: there wasn’t enough work and leisure history to calibrate."
                        else "Calibrated once from your recent routine. Prices stay fixed during the trial.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                }
            }
        }
        item { SyncFooter(stored, state.syncing) }
    }
}

@Composable
private fun MetricCard(label: String, time: String, credits: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = Muted, fontSize = 13.sp)
            Text(time, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Text(credits, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ActivityLog(stored: StoredState, archived: Boolean = false) {
    val trial = stored.trial ?: return
    val activities = stored.snapshot.activities.associateBy { it.id }
    val records = stored.snapshot.records.filter { it.end > trial.activatedAt && it.end <= System.currentTimeMillis() && activities[it.activityId]?.archived == archived && (archived || stored.kinds[it.activityId] != Kind.NEUTRAL) }
        .sortedByDescending { it.end }.take(100)
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(if (archived) "Archived sessions" else "Your recent activity", fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(if (archived) "Sessions from activities archived in STT. Archiving hides them from the main lists; their credits still count." else "Completed work and svago since the trial began. Corrections in STT are reflected after the next successful refresh.", color = Muted, lineHeight = 22.sp)
        }
        if (archived) {
            items(stored.activities.filter { it.present && it.archived }.sortedBy { it.name }, key = { "archived-${it.id}" }) {
                ValueRow(it.name, "Archived in STT", Muted)
            }
        }
        if (records.isEmpty()) item {
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                Text(if (archived) "No archived sessions since the trial began." else "Your first completed session will appear here. Keep tracking in STT as usual.", Modifier.padding(24.dp), color = Muted, lineHeight = 24.sp)
            }
        }
        items(records, key = { it.id }) { record ->
            val paused = record.tags.any { stored.snapshot.tags[it]?.trim()?.equals("Pausa", true) == true }
            val kind = stored.kinds[record.activityId]
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(activities[record.activityId]?.name ?: "Activity", fontWeight = FontWeight.SemiBold)
                        Text(timestamp(record.end), fontSize = 12.sp, color = Muted)
                        Text(if (paused && kind in setOf(Kind.WORK, Kind.SIDE)) "Pause · neutral" else when (kind) { Kind.WORK -> "Work · earns"; Kind.SIDE -> "Side activity · earns 25%"; Kind.LEISURE -> "Svago · spends"; else -> "Neutral" },
                            fontSize = 12.sp, color = if (kind == Kind.LEISURE) Warm else Muted)
                    }
                    Text(duration(record.end - maxOf(record.start, trial.activatedAt)), fontWeight = FontWeight.Medium)
                }
            }
        }
        item { Text("Session durations are shown above. Overlaps count once: svago takes precedence, then main work, then side activities.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
    }
}

@Composable
private fun Rules(stored: StoredState, onOpenStt: () -> Unit) {
    val trial = stored.trial ?: return
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("Simple rules.\nAutomatic accounting.", fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ValueRow("Main work earns 1 C", "50 minutes", Forest)
                    ValueRow("Esplorazioni / sides earn 1 C", "200 minutes", Forest)
                    ValueRow("Svago costs 1 C", "${decimal(Economy.leisureMinutesPerCredit(trial), 1)} minutes", Warm)
                    HorizontalDivider(color = Paper)
                    Text("Negative balances are allowed. No interest, penalties, expiry, or balance cap. Exercise, social time, and other activities are neutral.", color = Muted, lineHeight = 23.sp)
                    Text("Thesis, oxford, zhijing, aria and Work-category activities earn at the main rate. Esplorazioni and sides earn 25%, even in Work. Lab tags are equivalent; Pausa earns nothing.", color = Muted, lineHeight = 23.sp)
                }
            }
        }
        item {
            SectionLabel("Your trial", "14 days")
            Spacer(Modifier.height(10.dp))
            Text("Started ${timestamp(trial.activatedAt)}. Initial trial ends ${timestamp(trial.endsAt)}. The same rates continue afterward until a future version supports a review.", color = Muted, lineHeight = 23.sp)
            Spacer(Modifier.height(8.dp))
            Text("Baseline: ${duration(trial.baseline.workMs)} main work, ${duration(trial.baseline.sideMs)} side activities and ${duration(trial.baseline.leisureMs)} svago over the preceding 28 days. ${if (trial.provisional) "A provisional 50:50 rate is in use." else "The leisure price makes baseline spending 5% higher than baseline earnings."}", color = Muted, lineHeight = 23.sp)
        }
        item { SectionLabel("Activity mapping", "Kept across renames") }
        items(stored.activities.filter { it.present && !it.archived }.sortedWith(compareBy({ it.kind }, { it.name })), key = { it.id }) { activity ->
            ValueRow(activity.name, when (Kind.valueOf(activity.kind)) { Kind.WORK -> "Earns"; Kind.SIDE -> "Earns 25%"; Kind.LEISURE -> "Spends"; Kind.NEUTRAL -> "Neutral" },
                when (Kind.valueOf(activity.kind)) { Kind.WORK, Kind.SIDE -> Forest; Kind.LEISURE -> Warm; Kind.NEUTRAL -> Muted })
        }
        item {
            Text("New activities in STT’s Work category earn automatically. Other new activities remain neutral. Mappings persist across renames. Archived activities appear in the Archived tab.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenStt, modifier = Modifier.fillMaxWidth()) { Text("Open Simple Time Tracker") }
        }
        item { SyncFooter(stored, false) }
    }
}

@Composable
private fun SectionLabel(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun ValueRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted, fontSize = 14.sp)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorCard(message: String, reconnect: (() -> Unit)?) {
    Surface(color = Color(0xFFF5E5DC), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Refresh needs attention", fontWeight = FontWeight.SemiBold, color = Warm)
            Text(message, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
            if (reconnect != null) TextButton(onClick = reconnect) { Text("Choose backup file") }
        }
    }
}

@Composable
private fun SyncFooter(stored: StoredState, syncing: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (syncing) "Checking your backup…" else "File last read ${stored.sync?.lastRead?.let(::timestamp) ?: "—"}", color = Muted, fontSize = 12.sp)
        Text(stored.sync?.documentName.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = Muted)
        Text("Completed records only. Active timers count after they finish. STT does not acknowledge backup requests, so the file may lag behind your latest activity.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

private fun decimal(value: BigDecimal, places: Int = 2, rounding: RoundingMode = RoundingMode.HALF_UP): String = value.setScale(places, rounding).toPlainString()
private fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return when { minutes == 0L && ms > 0 -> "<1m"; minutes < 60 -> "${minutes}m"; minutes % 60 == 0L -> "${minutes / 60}h"; else -> "${minutes / 60}h ${minutes % 60}m" }
}
private fun timestamp(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
