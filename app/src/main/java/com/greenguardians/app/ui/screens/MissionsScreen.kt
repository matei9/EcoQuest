package com.greenguardians.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.greenguardians.app.ui.components.AdventureButton
import com.greenguardians.app.ui.components.EcoGlyph
import com.greenguardians.app.ui.components.glyph
import com.greenguardians.app.ui.fmt
import java.time.ZoneId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenguardians.app.ui.rememberApi
import com.greenguardians.app.data.Json
import androidx.compose.runtime.LaunchedEffect
import org.json.JSONObject
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.greenguardians.app.data.QuestRepository
import com.greenguardians.app.model.L
import com.greenguardians.app.model.Quest
import com.greenguardians.app.model.QuestKind
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.model.Verification
import com.greenguardians.app.ui.components.BossBattleCard
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.delay

@Composable
fun MissionsScreen(vm: MainViewModel, p: UserProgress, onOpenTrivia: () -> Unit) {
    var today by remember { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Bucharest"))) }
    var tab by remember { mutableStateOf(QuestKind.DAILY) }
    var verifying by remember { mutableStateOf<Quest?>(null) }
    val keys = p.completedKeys
    val chain = QuestRepository.chain
    val chainDone = chain.map { QuestRepository.isDone(it, keys, today) }
    val nextChain = chainDone.indexOfFirst { !it }
    val user = vm.session.collectAsStateWithLifecycle().value.me?.user
    val transitAvailable = user?.locality == "urban" && user.publicTransport && user.city == "Iași"
    val transitIds = setOf("d4", "d14", "w2a", "w2b", "w2c", "m4")
    val visibleQuests = QuestRepository.byKind(tab).filter { q ->
        (tab != QuestKind.DAILY || q.id in p.dailyQuestIds) && (transitAvailable || q.id !in transitIds)
    }
    val community by rememberApi(p.verifiedQuestEP) { vm.api.get("/api/community") }
    var extras by rememberSaveable { mutableStateOf(false) }
    val dailyDone = p.dailyQuestIds.count { id -> QuestRepository.byId(id)?.let { QuestRepository.isDone(it, keys, today) } == true }
    val receiptQuest = QuestRepository.daily.first { it.sgr > 0 }

    val fact by vm.fact.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (vm.fact.value == null) vm.nextFact() }
    LaunchedEffect(Unit) {
        vm.refresh()
        while (true) {
            delay(60_000)
            val day = LocalDate.now(ZoneId.of("Europe/Bucharest"))
            if (day != today) {
                today = day
                vm.refresh()
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "daily-summary") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("🔥", L("${p.tree?.streak ?: 0} zile", "${p.tree?.streak ?: 0} days").t(), Modifier.weight(1f))
                    StatPill("★", "${fmt(p.totalEP)} EP", Modifier.weight(1f))
                    StatPill("●", "${fmt(p.ecoCoins)}", Modifier.weight(1f))
                }
                Text(L("Un pas mic. Un viitor mai verde.", "One small step. A greener future.").t(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .25f))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(L("AVENTURA DE AZI", "TODAY'S ADVENTURE").t(), style = MaterialTheme.typography.labelMedium)
                                Text(L("$dailyDone din ${p.dailyQuestIds.size} misiuni", "$dailyDone of ${p.dailyQuestIds.size} quests").t(), style = MaterialTheme.typography.titleLarge)
                            }
                            EcoGlyph(if (dailyDone == p.dailyQuestIds.size && dailyDone > 0) "progress" else "leaf", Modifier.size(46.dp))
                        }
                        LinearProgressIndicator(progress = { if (p.dailyQuestIds.isEmpty()) 0f else dailyDone.toFloat() / p.dailyQuestIds.size }, modifier = Modifier.fillMaxWidth().height(10.dp))
                        Text(if (dailyDone == p.dailyQuestIds.size && dailyDone > 0) L("Toate misiunile de azi sunt gata. Bravo!", "All today's quests are done. Well done!").t()
                            else L("Alese pentru tine. Fiecare acțiune contează.", "Picked for you. Every action counts.").t(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { verifying = receiptQuest }, modifier = Modifier.weight(1f)) { Text(L("Scanează bon", "Scan receipt").t()) }
                    OutlinedButton(onClick = onOpenTrivia, modifier = Modifier.weight(1f)) { Text(L("Quiz zilnic", "Daily quiz").t()) }
                }
            }
        }
        item {
            TabRow(selectedTabIndex = tab.ordinal) {
                QuestKind.entries.forEach { k ->
                    Tab(selected = tab == k, onClick = { tab = k }, text = { Text(k.label.t()) })
                }
            }
        }
        if (tab == QuestKind.DAILY) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(L("Misiunile tale de azi", "Your quests today").t(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = vm::refresh) { Text(L("Actualizează", "Refresh").t()) }
            }
            if (p.dailyQuestIds.isEmpty()) Text(L("Nu am putut încărca selecția. Verifică conexiunea și încearcă din nou.", "Could not load your selection. Check your connection and try again.").t())
        }
        items(visibleQuests, key = { it.id }) { q -> QuestCard(q, p, today) { verifying = q } }
        item(key = "chain-path") {
            SectionCard(L("Drumul tău verde", "Your green path"), L("Trei pași care se deblochează pe rând.", "Three steps unlocked one at a time."))
            ChainPath(chainDone, nextChain) { i -> verifying = chain[i] }
        }
        item(key = "more-toggle") {
            OutlinedButton(onClick = { extras = !extras }, modifier = Modifier.fillMaxWidth()) {
                Text(if (extras) L("Mai puține detalii ▴", "Fewer details ▴").t() else L("Comunitate, streak și surprize ▾", "Community, streak and surprises ▾").t())
            }
        }
        if (extras) {
            item { StreakWidget(p.activeDays, today) }
            item { BossBattleCard(bossKm(community), onContribute = { tab = QuestKind.DAILY }) }
            item { FunFactCard(fact, onNext = vm::nextFact, onOpenTrivia = onOpenTrivia) }
            item { QuestCard(QuestRepository.mystery, p, today) { verifying = QuestRepository.mystery } }
        }

    }

    verifying?.let { q ->
        key(q.id) {
            VerifySheet(vm, q, p, onDismiss = { verifying = null })
        }
    }
}

private fun num(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)

/** Community km toward the boss, null while loading or offline. */
fun bossKm(community: Result<JSONObject>?): Double? =
    community?.getOrNull()?.getJSONObject("boss")?.getDouble("km")

/** "Did you know" card; the daily trivia questions come from these facts. */
@Composable
private fun FunFactCard(fact: JSONObject?, onNext: () -> Unit, onOpenTrivia: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("💡 Știai că…", "💡 Did you know…").t(), fontWeight = FontWeight.Bold)
            if (fact == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Text(Json.l(fact.getJSONObject("text")).t())
                Text(
                    L("Fun facts văzute: ${fact.getInt("seen")}/${fact.getInt("total")}", "Fun facts seen: ${fact.getInt("seen")}/${fact.getInt("total")}").t(),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNext) { Text(L("Alt fun fact", "Another fact").t()) }
                Button(onClick = onOpenTrivia) { Text(L("🧠 Quiz zilnic", "🧠 Daily quiz").t()) }
            }
        }
    }
}

@Composable
private fun SectionCard(title: L, sub: L) {
    Column {
        Text(title.t(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(sub.t(), style = MaterialTheme.typography.bodySmall)
    }
}

/** Flexible streak: any 5 active days out of the current Mon–Sun week keep it alive. */
@Composable
private fun StreakWidget(activeDays: Set<String>, today: LocalDate) {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val active = days.count { it.toString() in activeDays }
    val labels = L("LMMJVSD", "MTWTFSS").t()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔥 Streak ${minOf(active, 5)}/5 " + L("(5 din 7 zile)", "(5 of 7 days)").t(), fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEachIndexed { i, d ->
                    val on = d.toString() in activeDays
                    Box(
                        Modifier.size(36.dp).background(if (on) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(labels[i].toString(), color = if (on) Color.White else MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
    }
}

@Composable
private fun QuestCard(q: Quest, p: UserProgress, today: LocalDate, locked: Boolean = false, onVerify: () -> Unit) {
    val done = QuestRepository.isDone(q, p.completedKeys, today)
    val metric = p.questProgress[q.id]
    var details by rememberSaveable(q.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (done) colors.primaryContainer.copy(alpha = .55f) else colors.surface),
        border = BorderStroke(2.dp, if (done) colors.primary.copy(alpha = .45f) else colors.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(colors.primaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    EcoGlyph(if (done) "progress" else q.category.glyph(), Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(q.title.t(), style = MaterialTheme.typography.titleMedium)
                    Text("${q.difficulty.label.t()} · +${q.ep} EP · +${q.coins} EcoCoins", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
            }
            if (metric != null && !done) {
                val (value, target) = metric
                LinearProgressIndicator(progress = { if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(8.dp))
                Text("${num(value)} / ${num(target)}", style = MaterialTheme.typography.labelMedium)
            }
            when {
                done -> Text(L("✓ Finalizat. Ai făcut diferența!", "✓ Complete. You made a difference!").t(), fontWeight = FontWeight.Bold, color = colors.primary)
                locked -> Text(L("Finalizează mai întâi pasul anterior", "Finish the previous step first").t())
                q.verification == Verification.AUTO -> Text(L("Se finalizează automat la atingerea țintei", "Completes automatically at the target").t(), style = MaterialTheme.typography.bodySmall)
                else -> AdventureButton(onClick = onVerify, modifier = Modifier.fillMaxWidth()) {
                    Text(when(q.verification) {
                        Verification.GPS -> L("Pornește traseul", "Start trip")
                        Verification.RECEIPT_AI -> if(q.sgr > 0) L("Scanează bonul", "Scan receipt") else L("Verifică misiunea", "Verify quest")
                        Verification.QR_PHOTO -> L("Scanează QR", "Scan QR")
                        else -> L("Începe misiunea", "Start quest")
                    }.t())
                }
            }
            TextButton(onClick = { details = !details }, contentPadding = PaddingValues(0.dp)) {
                Text(if(details) L("Ascunde detaliile ▴", "Hide details ▴").t() else L("Impact și verificare ▾", "Impact and verification ▾").t())
            }
            if(details) {
                Text(q.verification.label.t(), style = MaterialTheme.typography.bodySmall)
                if(q.co2Kg > 0) Text(L("%.2f kg CO₂ evitați · %.0f cm² gheață arctică salvată".format(q.co2Kg,q.iceCm2), "%.2f kg CO₂ avoided · %.0f cm² Arctic ice saved".format(q.co2Kg,q.iceCm2)).t(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatPill(icon: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Text("$icon $value", Modifier.padding(horizontal = 10.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun ChainPath(done: List<Boolean>, next: Int, onOpen: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(310.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val route = Path().apply {
                moveTo(size.width * .25f, 48.dp.toPx())
                cubicTo(size.width * .25f, 110.dp.toPx(), size.width * .75f, 90.dp.toPx(), size.width * .75f, 148.dp.toPx())
                cubicTo(size.width * .75f, 210.dp.toPx(), size.width * .25f, 190.dp.toPx(), size.width * .25f, 248.dp.toPx())
            }
            drawPath(route, colors.outlineVariant, style = Stroke(8.dp.toPx()))
        }
        Column {
            listOf(L("Mergi la magazin", "Walk to the store"), L("Ia sacoșa reutilizabilă", "Bring your reusable bag"), L("Alege fără plastic", "Choose plastic-free")).forEachIndexed { i, title ->
                val locked = next != -1 && i > next
                val nodeLabel = title.t() + when {
                    done[i] -> L(" — finalizat", " — completed").t()
                    locked -> L(" — blocat", " — locked").t()
                    else -> L(" — începe", " — start").t()
                }
                Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.CenterVertically) {
                    if(i == 1) Box(Modifier.weight(1f))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(onClick = { onOpen(i) }, enabled = !locked && !done[i], shape = CircleShape, modifier = Modifier.size(64.dp).semantics { contentDescription = nodeLabel }, contentPadding = PaddingValues(0.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = if(done[i] || i == next) colors.primaryContainer else colors.surface)) {
                            if(locked) Text("🔒") else EcoGlyph(if(done[i]) "progress" else listOf("transport","leaf","leaf")[i])
                        }
                        Text(title.t(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    if(i != 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}
