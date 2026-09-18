package io.github.currency.companion.ui

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
                            listOf("Summary", "Activity", "Archived", "Rules").forEachIndexed { index, title ->
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
    val zone = ZoneId.of(trial.zoneId)
    val total = Economy.account(Economy.totals(stored.snapshot, stored.kinds, trial.activatedAt, now, zone), trial)
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
            val (from, until) = SummaryRange("overview", SummaryPeriod.TODAY, now, zone)
            val todayTime = Economy.totals(stored.snapshot, stored.kinds, maxOf(from, trial.activatedAt), until, zone, now)
            val todayAccount = Economy.account(todayTime, trial)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Earned", duration(todayTime.workMs + todayTime.sideMs + todayTime.choresMs), "+${decimal(todayAccount.earned)} C", Forest, Modifier.weight(1f))
                MetricCard("Spent", duration(todayTime.spendingMs), "−${decimal(todayAccount.spent)} C", Warm, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            ValueRow("Net change", "${decimal(todayAccount.balance)} C", Ink)
        }
        item {
            val (from, until) = SummaryRange("breakdown", SummaryPeriod.LAST_DAYS, now, zone)
            val ledger = Economy.ledger(stored.snapshot, stored.kinds, maxOf(from, trial.activatedAt), until, zone, now)
            val weekTime = ledger.totals
            val weekAccount = Economy.account(weekTime, trial)
            Spacer(Modifier.height(12.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ValueRow("Work earned · ${duration(weekTime.workMs + weekTime.sideMs + weekTime.choresMs)}", "+${decimal(weekAccount.earned)} C", Forest)
                    ValueRow("Svago · ${duration(weekTime.leisureMs)}", "−${decimal(Economy.account(Totals(leisureMs = weekTime.leisureMs), trial).spent)} C", Warm)
                    ValueRow("Small costs · ${duration(weekTime.spendingMs - weekTime.leisureMs)}", "−${decimal(Economy.account(weekTime.copy(leisureMs = 0), trial).spent)} C", Warm)
                    HorizontalDivider(color = Paper)
                    ValueRow("Net change", "${decimal(weekAccount.balance)} C", Ink)
                }
            }
            Spacer(Modifier.height(20.dp))
            ActivityBreakdownChart(stored, activityBreakdown(stored.snapshot, ledger, trial, from, until, now))
        }
        item {
            Surface(color = Color(0xFFE8ECDC), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A steady exchange", fontWeight = FontWeight.SemiBold)
                    Text("50 minutes of main work earns 1 C.\n200 minutes of esplorazioni or sides earns 1 C.\n50 minutes of chores earns 0.1 C.\n${decimal(Economy.leisureMinutesPerCredit(trial), 1)} minutes of svago costs 1 C.\nCooking, travel and chargeable sleep cost 25% of svago; People / friends cost 5%.", lineHeight = 24.sp)
                    Text(if (trial.provisional) "Provisional rate: there wasn’t enough work and leisure history to calibrate."
                        else "Calibrated once from your recent routine. Prices stay fixed during the trial.", fontSize = 12.sp, color = Muted, lineHeight = 18.sp)
                }
            }
        }
        item { SyncFooter(stored, state.syncing) }
    }
}

@Composable
private fun ActivityBreakdownChart(stored: StoredState, rows: List<ActivityBreakdown>) {
    var filter by rememberSaveable { mutableStateOf("All") }
    val activities = stored.activities.associateBy { it.id }
    val visible = rows.filter {
        when (filter) {
            "Earned" -> it.account.balance.signum() > 0
            "Spent" -> it.account.balance.signum() < 0
            "Zero" -> it.account.balance.signum() == 0
            else -> true
        }
    }
    val largest = rows.maxOfOrNull { it.account.balance.abs() } ?: BigDecimal.ZERO
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("By activity", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Hours and credits for the period above. Green earns; orange spends. Bars compare credit amounts on the same scale.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Earned", "Spent", "Zero").forEach { option ->
                FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
            }
        }
        if (visible.isEmpty()) Text("No completed activities for this filter and period.", color = Muted)
        visible.forEach { row ->
            val credits = row.account.balance
            val color = when (credits.signum()) { -1 -> Warm; 1 -> Forest; else -> Muted }
            val amount = if (credits.signum() != 0 && credits.abs() < BigDecimal("0.0001")) "<0.0001"
                else decimal(credits.abs(), 4)
            val sign = when (credits.signum()) { -1 -> "−"; 1 -> "+"; else -> "" }
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val activity = activities[row.activityId]
                    Text((activity?.name ?: "Activity ${row.activityId}") + if (activity?.archived == true) " · Archived" else "",
                        fontWeight = FontWeight.SemiBold)
                    ValueRow("Credits", "$sign$amount C", color)
                    Box(Modifier.fillMaxWidth().height(8.dp).background(Paper, RoundedCornerShape(4.dp))) {
                        if (credits.signum() != 0 && largest.signum() > 0) Box(Modifier
                            .fillMaxWidth(credits.abs().divide(largest, java.math.MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
                    }
                    Text("${hours(row.recordedMs)} h recorded · ${hours(row.countedMs)} h counted",
                        color = Muted, fontSize = 13.sp)
                }
            }
        }
        Text("Recorded hours can overlap. Counted hours exclude pauses, masked overlaps, neutral activities and free sleep. Archived activities are included. Credits show four decimal places; totals use full precision.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

private fun hours(ms: Long): String = BigDecimal(ms).divide(BigDecimal(3_600_000), 2, RoundingMode.HALF_UP).toPlainString()

@Composable
private fun SummaryRange(key: String, default: SummaryPeriod, now: Long, zone: ZoneId): Pair<Long, Long> {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("summary_ranges", Context.MODE_PRIVATE) }
    var period by rememberSaveable(key) {
        mutableStateOf(SummaryPeriod.entries.firstOrNull { it.name == preferences.getString(key, null) } ?: default)
    }
    var days by rememberSaveable(key) { mutableIntStateOf(preferences.getInt("$key.days", 7).coerceIn(1, 3650)) }
    var expanded by remember { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    fun select(value: SummaryPeriod, count: Int = days) {
        period = value
        days = count
        preferences.edit().putString(key, value.name).putInt("$key.days", count).apply()
    }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(if (period == SummaryPeriod.LAST_DAYS) "Last $days days ▾" else "${period.label} ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SummaryPeriod.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                    expanded = false
                    if (option == SummaryPeriod.LAST_DAYS) {
                        input = days.toString()
                        custom = true
                    } else select(option)
                })
            }
        }
    }
    if (custom) {
        val count = input.toIntOrNull()?.takeIf { it in 1..3650 }
        AlertDialog(onDismissRequest = { custom = false }, title = { Text("Last N days") },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                    label = { Text("Number of days") }, isError = count == null,
                    supportingText = { Text("1–3650 days, including today") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = { TextButton(enabled = count != null, onClick = {
                select(SummaryPeriod.LAST_DAYS, count!!)
                custom = false
            }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } })
    }
    val window = period.window(now, zone, days)
    val format = DateTimeFormatter.ofPattern("d MMM yyyy")
    val start = Instant.ofEpochMilli(window.first).atZone(zone).format(format)
    val end = Instant.ofEpochMilli(if (period in setOf(SummaryPeriod.LAST_WEEK, SummaryPeriod.LAST_MONTH)) window.second - 1 else window.second)
        .atZone(zone).format(format)
    Text("$start – $end · Since trial start only", color = Muted, fontSize = 11.sp)
    return window
}

@Composable
private fun MetricCard(label: String, time: String, credits: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = Muted, fontSize = 13.sp)
            Text(credits, color = color, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            Text(time, color = Muted)
        }
    }
}

@Composable
private fun ActivityLog(stored: StoredState, archived: Boolean = false) {
    val trial = stored.trial ?: return
    val activities = stored.snapshot.activities.associateBy { it.id }
    val now = System.currentTimeMillis()
    val ledger = remember(stored, now) {
        Economy.ledger(stored.snapshot, stored.kinds, trial.activatedAt, now, ZoneId.of(trial.zoneId))
    }
    val records = stored.snapshot.records.filter { it.end > trial.activatedAt && it.end <= now && activities[it.activityId]?.archived == archived && (archived || stored.kinds[it.activityId] != Kind.NEUTRAL) }
        .sortedByDescending { it.end }.take(100)
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(if (archived) "Archived sessions" else "Your recent activity", fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(8.dp))
            Text(if (archived) "Sessions from activities archived in STT. Archiving hides them from the main lists; their credits still count." else "Completed earning and spending sessions since the trial began. Corrections in STT are reflected after the next successful refresh.", color = Muted, lineHeight = 22.sp)
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
            val credits = Economy.account(ledger.sessions[record.id] ?: Totals(), trial).balance
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(activities[record.activityId]?.name ?: "Activity", fontWeight = FontWeight.SemiBold)
                        Text(timestamp(record.end), fontSize = 12.sp, color = Muted)
                        Text(if (paused && kind in setOf(Kind.WORK, Kind.SIDE, Kind.CHORES)) "Pause · neutral" else when (kind) { Kind.WORK -> "Work · earns"; Kind.SIDE -> "Side activity · earns 25%"; Kind.CHORES -> "Chores · earns 10%"; Kind.LEISURE -> "Svago · spends"; Kind.COOKING, Kind.TRAVEL -> "Spends 25% of svago"; Kind.FRIENDS -> "Spends 5% of svago"; Kind.SLEEP -> "Afternoon / excess night sleep · 25%"; else -> "Neutral" },
                            fontSize = 12.sp, color = if (credits.signum() < 0) Warm else Muted)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sessionCredits(credits), fontWeight = FontWeight.SemiBold,
                            color = when (credits.signum()) { -1 -> Warm; 1 -> Forest; else -> Muted })
                        Text(duration(record.end - maxOf(record.start, trial.activatedAt)), fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }
        item { Text("Credits reflect each session’s contribution after overlaps. Spending takes precedence over earning; higher prices win. Ties go to the earliest-started session, then its STT ID. Zero can mean free sleep, paused work, or overlapping time. Amounts are rounded for display; the balance uses full precision.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp) }
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
                    ValueRow("Chores earn 0.1 C", "50 minutes", Forest)
                    ValueRow("Svago costs 1 C", "${decimal(Economy.leisureMinutesPerCredit(trial), 1)} minutes", Warm)
                    ValueRow("Cooking / travel / charged sleep", "25% of svago", Warm)
                    ValueRow("People / friends", "5% of svago", Warm)
                    HorizontalDivider(color = Paper)
                    Text("Negative balances are allowed. No interest, penalties, expiry, or balance cap. Exercise and unlisted activities are neutral.", color = Muted, lineHeight = 23.sp)
                    Text("Sleep from 12:00 to 18:00 costs 25% of svago. Each night runs from 18:00 to noon: only recorded sleep above nine hours costs credits. Split sessions share the allowance; overlapping sleep counts once. Afternoon sleep is separate. Time zone: ${trial.zoneId}.", color = Muted, lineHeight = 23.sp)
                    Text("Small costs and chore earnings apply to all sessions since the trial began, including earlier sessions. Your original svago rate stays unchanged.", color = Muted, lineHeight = 23.sp)
                    Text("Thesis, oxford, zhijing, aria and Work-category activities earn at the main rate. Esplorazioni and sides earn 25%; chores earn 10%, even in Work. Lab tags are equivalent; Pausa earns nothing.", color = Muted, lineHeight = 23.sp)
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
            ValueRow(activity.name, when (Kind.valueOf(activity.kind)) { Kind.WORK -> "Earns"; Kind.SIDE -> "Earns 25%"; Kind.CHORES -> "Earns 10%"; Kind.LEISURE -> "Spends"; Kind.COOKING, Kind.TRAVEL -> "Spends 25%"; Kind.FRIENDS -> "Spends 5%"; Kind.SLEEP -> "Conditional 25%"; Kind.NEUTRAL -> "Neutral" },
                when (Kind.valueOf(activity.kind)) { Kind.WORK, Kind.SIDE, Kind.CHORES -> Forest; Kind.LEISURE, Kind.COOKING, Kind.TRAVEL, Kind.FRIENDS, Kind.SLEEP -> Warm; Kind.NEUTRAL -> Muted })
        }
        item {
            Text("Named activities use the rates above, regardless of category. Other new activities in STT’s Work category earn automatically; the rest remain neutral. Mappings persist across renames. Archived activities appear in the Archived tab.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
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

private fun sessionCredits(value: BigDecimal): String {
    if (value.signum() == 0) return "0.00 C"
    val amount = if (value.abs() < BigDecimal("0.01")) "<0.01" else decimal(value.abs())
    return (if (value.signum() > 0) "+" else "−") + amount + " C"
}
private fun decimal(value: BigDecimal, places: Int = 2, rounding: RoundingMode = RoundingMode.HALF_UP): String = value.setScale(places, rounding).toPlainString()
private fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return when { minutes == 0L && ms > 0 -> "<1m"; minutes < 60 -> "${minutes}m"; minutes % 60 == 0L -> "${minutes / 60}h"; else -> "${minutes / 60}h ${minutes % 60}m" }
}
private fun timestamp(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault()))
