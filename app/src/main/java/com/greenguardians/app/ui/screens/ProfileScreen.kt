package com.greenguardians.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import com.greenguardians.app.model.Me
import com.greenguardians.app.ui.ErrorLine
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenguardians.app.data.QuestRepository
import com.greenguardians.app.data.QuizData
import com.greenguardians.app.model.Category
import com.greenguardians.app.model.L
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.ui.components.LangDarkToggles
import com.greenguardians.app.ui.components.LeagueProgress
import com.greenguardians.app.ui.components.LeaguesShowcaseCard
import com.greenguardians.app.ui.fmt
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel

private class Badge(val emoji: String, val name: L, val hint: L, val unlocked: (UserProgress, Set<String>) -> Boolean)

private val badges = listOf(
    Badge("♻️", L("Campion SGR", "SGR Champion"), L("Returnează 10 ambalaje SGR", "Return 10 SGR packages")) { p, _ -> p.sgrReturned >= 10 },
    Badge("🏙️", L("Eroul Iașiului", "Hero of Iași"), L("Finalizează 5 misiuni", "Complete 5 quests")) { _, ids -> ids.size >= 5 },
    Badge("🐻‍❄️", L("Salvatorul Urșilor Polari", "Polar Bear Saver"), L("Economisește 5 kg CO₂", "Save 5 kg CO₂")) { p, _ -> p.verifiedCO2SavedKg >= 5.0 },
    Badge("🍲", L("Chef Zero-Risipă", "Zero-Waste Chef"), L("Finalizează o misiune despre risipă", "Complete a food-waste quest")) { _, ids ->
        ids.any { QuestRepository.byId(it)?.category == Category.FOOD }
    },
    Badge("🕵️", L("Detectiv Eco", "Eco Detective"), L("Rezolvă misiunea misterioasă", "Solve the mystery quest")) { _, ids -> "mystery" in ids },
)

@Composable
fun ProfileScreen(vm: MainViewModel, me: Me) {
    LaunchedEffect(Unit) { vm.refresh() }
    val p = me.progress
    val u = me.user
    val ids = p.completedKeys.map { it.substringBefore("@") }.toSet()
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val monet by vm.monetColors.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text("👤 ${u.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${u.email} · ${u.city}", style = MaterialTheme.typography.bodySmall)
                Text(
                    L("Cod invitație prieteni: ${u.refCode}", "Friend invite code: ${u.refCode}").t(),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(L("Setări rapide", "Quick settings").t(), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                LangDarkToggles(vm)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(L("Culorile telefonului (Monet)", "Phone colors (Monet)").t(), fontWeight = FontWeight.SemiBold)
                    Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) L("Oprit: verde EcoQuest. Pornit: paleta sistemului.", "Off: EcoQuest green. On: system palette.").t()
                        else L("Disponibil de la Android 12. Folosim verdele EcoQuest.", "Available on Android 12+. Using EcoQuest green.").t(), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = monet, onCheckedChange = vm::setMonetColors, enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            }
        }
        item { LeaguesShowcaseCard(p.league) }
        item { LeagueProgress(p) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(L("⚔️ 1v1 clasat", "⚔️ Ranked 1v1").t(), fontWeight = FontWeight.Bold)
                    Text(L("${p.rankedWins} victorii · ${p.rankedDraws} egalități · ${p.rankedPlayed} meciuri", "${p.rankedWins} wins · ${p.rankedDraws} draws · ${p.rankedPlayed} matches").t())
                    Text(L("${p.duelPoints} puncte 1v1", "${p.duelPoints} 1v1 points").t())
                    Text(L("3 puncte/victorie, 1/egalitate. Amicalele cu prietenii nu se includ. Punctele 1v1 sunt separate de EcoPoints.", "3 points/win, 1/draw. Friendly matches are excluded. 1v1 points are separate from EcoPoints.").t(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(L("EcoPoints", "EcoPoints").t(), fontWeight = FontWeight.Bold)
                    Text(L("Total: ", "Total: ").t() + fmt(p.totalEP) + " EP")
                    Text(L("Quiz (autodeclarate): ", "Quiz (self-declared): ").t() + fmt(p.initialSelfDeclaredEP))
                    Text(L("Verificate (misiuni): ", "Verified (quests): ").t() + fmt(p.verifiedQuestEP))
                    Text("🪙 EcoCoins: ${p.ecoCoins}")
                }
            }
        }
        item {
            Text(L("Categorii EcoBaseline", "EcoBaseline categories").t(), fontWeight = FontWeight.Bold)
        }
        items(QuizData.categories.indices.toList()) { i ->
            val cat = QuizData.categories[i]
            val v by animateFloatAsState(p.categoryScores[i].toFloat() / cat.max, label = "cat")
            Column {
                Row {
                    Text(cat.name.t(), Modifier.weight(1f))
                    Text("${p.categoryScores[i]}/${cat.max}")
                }
                LinearProgressIndicator(progress = { v }, Modifier.fillMaxWidth())
            }
        }
        item {
            Text(
                L("Potențial de îmbunătățire: ", "Improvement potential: ").t() + "${p.improvementPotential}%",
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(onClick = { vm.retakeQuiz.value = true }) { Text(L("Refă quiz-ul", "Retake quiz").t()) }
            Text(L("Aici poți schimba și oraș/sat și accesul la transport. Noile răspunsuri se aplică selecției de mâine; misiunile de transport indisponibil se înlocuiesc imediat.", "You can also update city/village and transport access here. New answers apply to tomorrow's selection; unavailable transport quests are replaced immediately.").t(), style = MaterialTheme.typography.bodySmall)
        }
        item { Text(L("Badge-uri", "Badges").t(), fontWeight = FontWeight.Bold) }
        items(badges) { b ->
            val on = b.unlocked(p, ids)
            Card(Modifier.fillMaxWidth().alpha(if (on) 1f else 0.4f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (on) b.emoji else "🔒", style = MaterialTheme.typography.headlineSmall)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(b.name.t(), fontWeight = FontWeight.Bold)
                        Text(b.hint.t(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.logout() }) { Text(L("Deconectare", "Sign out").t()) }
                TextButton(onClick = { confirmDelete = true }) {
                    Text(L("Șterge contul", "Delete account").t(), color = MaterialTheme.colorScheme.error)
                }
            }
            error?.let { ErrorLine(it) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(L("Ștergi contul definitiv?", "Delete your account permanently?").t()) },
            text = { Text(L("Toate punctele, misiunile și voucherele tale vor fi șterse de pe server.", "All your points, quests and vouchers will be deleted from the server.").t()) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { runCatching { vm.deleteAccount() }.onFailure { error = it } }
                }) { Text(L("Șterge", "Delete").t(), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(L("Anulează", "Cancel").t()) } },
        )
    }
}
