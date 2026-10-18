package com.greenguardians.app.ui.screens

import androidx.compose.foundation.background
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
    var today by remember { mutableStateOf(LocalDate.now()) }
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
    val receiptQuest = QuestRepository.daily.first { it.sgr > 0 }

    val fact by vm.fact.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (vm.fact.value == null) vm.nextFact() }
    LaunchedEffect(Unit) {
        vm.refresh()
        while (true) {
            delay(60_000)
            val day = LocalDate.now()
            if (day != today) {
                today = day
                vm.refresh()
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FunFactCard(fact, onNext = vm::nextFact, onOpenTrivia = onOpenTrivia) }
        item { StreakWidget(p.activeDays, today) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(L("🧾 Bonuri SGR", "🧾 SGR receipts").t(), fontWeight = FontWeight.Bold)
                    Text(L("Scanează bonul de returnare. Ambalajele se adună la progresul misiunilor SGR.", "Scan your return receipt. Packages count toward your SGR quests.").t())
                    Button(onClick = { verifying = receiptQuest }) {
                        Text(L("Scanează bon SGR", "Scan SGR receipt").t())
                    }
                }
            }
        }
        item { BossBattleCard(bossKm(community), onContribute = { tab = QuestKind.DAILY }) }

        item {
            SectionCard(L("🔗 Lanț de misiuni", "🔗 Quest chain"), L(
                "Pas cu pas: jos la magazin → sacoșă reutilizabilă → fără plastic.",
                "Step by step: walk to store → reusable bag → plastic-free.",
            ))
        }
        itemsIndexed3(chain) { i, c ->
            QuestCard(c, p, today, locked = nextChain != -1 && i > nextChain) { verifying = c }
        }

        item {
            SectionCard(L("🎁 Misiune misterioasă", "🎁 Mystery quest"), L(
                "Finalizeaz-o ca să deblochezi badge-ul „Detectiv Eco”.",
                "Finish it to unlock the “Eco Detective” badge.",
            ))
        }
        item { QuestCard(QuestRepository.mystery, p, today) { verifying = QuestRepository.mystery } }

        item {
            TabRow(selectedTabIndex = tab.ordinal) {
                QuestKind.entries.forEach { k ->
                    Tab(selected = tab == k, onClick = { tab = k }, text = { Text(k.label.t()) })
                }
            }
        }
        if (tab == QuestKind.DAILY) item {
            SectionCard(L("Misiunile tale de azi", "Your quests today"), L(
                "Șase misiuni alese după răspunsurile tale și localitate. Se schimbă zilnic, nu la fiecare deschidere.",
                "Six quests based on your answers and locality. They change daily, not every time you open the app.",
            ))
            if (p.dailyQuestIds.isEmpty()) Text(L("Actualizează backendul și reîncarcă profilul pentru misiunile personalizate.", "Update the backend and refresh your profile for personalized quests.").t())
            OutlinedButton(onClick = vm::refresh) { Text(L("Actualizează misiunile", "Refresh quests").t()) }
        }
        items(visibleQuests, key = { it.id }) { q -> QuestCard(q, p, today) { verifying = q } }
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

/** LazyListScope.itemsIndexed wrapper kept local to avoid an extra import clash with items(). */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed3(
    list: List<Quest>,
    content: @Composable (Int, Quest) -> Unit,
) {
    items(list.size, key = { list[it].id }) { content(it, list[it]) }
}

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
    // Server-tracked progress (km, SGR packages, check-ins, quest counts) for auto-completing quests.
    val metric = p.questProgress[q.id]
    val auto = q.verification == Verification.AUTO
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(q.title.t(), fontWeight = FontWeight.SemiBold)
            Text(
                "${q.difficulty.label.t()} · ${q.verification.label.t()} · ${q.kind.label.t()}",
                style = MaterialTheme.typography.labelMedium,
            )
            Text("+${q.ep} EP   +${q.coins} 🪙", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (q.co2Kg > 0) {
                Text(
                    L(
                        "+%.2f kg CO₂ evitați · +%.0f cm² gheață arctică salvată pentru urșii polari 🐻‍❄️".format(q.co2Kg, q.iceCm2),
                        "+%.2f kg CO₂ avoided · +%.0f cm² Arctic ice saved for polar bears 🐻‍❄️".format(q.co2Kg, q.iceCm2),
                    ).t(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (metric != null && !done) {
                val (value, target) = metric
                LinearProgressIndicator(progress = { (value / target).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text("%s / %s".format(num(value), num(target)), style = MaterialTheme.typography.labelMedium)
            }
            when {
                done -> Text("✔ " + L("Finalizat", "Completed").t(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                locked -> Text("🔒 " + L("Finalizează pasul anterior", "Finish the previous step").t())
                auto -> Text(L("Se finalizează automat", "Completes automatically").t(), style = MaterialTheme.typography.labelMedium)
                else -> Button(onClick = onVerify) {
                    Text(
                        when (q.verification) {
                            Verification.GPS -> L("📍 Pornește traseul", "📍 Start trip")
                            Verification.RECEIPT_AI -> if (q.sgr > 0) L("🧾 Scanează bon SGR", "🧾 Scan SGR receipt") else L("Verifică misiunea", "Verify quest")
                            Verification.QR_PHOTO -> L("📱 Scanează QR", "📱 Scan QR")
                            else -> L("Verifică misiunea", "Verify quest")
                        }.t(),
                    )
                }
            }
        }
    }
}
