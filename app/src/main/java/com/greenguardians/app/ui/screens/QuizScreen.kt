package com.greenguardians.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import com.greenguardians.app.ui.components.AdventureButton
import com.greenguardians.app.ui.components.EcoGlyph
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.greenguardians.app.ui.ErrorLine
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.greenguardians.app.data.QuizData
import com.greenguardians.app.model.L
import com.greenguardians.app.model.League
import com.greenguardians.app.ui.components.LangDarkToggles
import com.greenguardians.app.ui.components.LeagueIcon
import com.greenguardians.app.ui.fmt
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel

/** EcoBaseline onboarding quiz: 10 questions, result = raw * 25 EP (max 2.500). */
@Composable
fun QuizScreen(vm: MainViewModel) {
    var selected by remember { mutableIntStateOf(-1) }
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateListOf<Int>() }
    val questions = QuizData.questions
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var locality by remember { mutableStateOf(vm.session.value.me?.user?.locality ?: "urban") }
    var publicTransport by remember { mutableStateOf(vm.session.value.me?.user?.publicTransport ?: false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🌱 EcoQuest", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            LangDarkToggles(vm)
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(targetState = index, label = "quiz", modifier = Modifier.weight(1f)) { i ->
            if (i < questions.size) {
                val q = questions[i]
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("EcoBaseline · ${i + 1}/${questions.size + 2}", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(progress = { (i + 1f) / (questions.size + 2) }, Modifier.fillMaxWidth())
                    Text(QuizData.categories[q.category].name.t(), color = MaterialTheme.colorScheme.primary)
                    Text(q.text.t(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    q.options.forEachIndexed { oi, opt ->
                        Card(onClick = { if (i == index) selected = oi }, enabled = i == index, modifier = Modifier.fillMaxWidth().semantics { this.selected = selected == oi },
                            colors = CardDefaults.cardColors(containerColor = if(selected == oi) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                            border = BorderStroke(if(selected == oi) 2.dp else 1.dp, if(selected == oi) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                EcoGlyph(when(q.category) { 0 -> "transport"; 1 -> "energy"; 3 -> "sgr"; else -> "leaf" })
                                Text(opt.text.t(), Modifier.weight(1f))
                                Text(if(selected == oi) "✓" else "${oi + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                }
            } else if (i < questions.size + 2) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(progress = { (i + 1f) / (questions.size + 2) }, modifier = Modifier.fillMaxWidth())
                    Text(L("Misiuni potrivite pentru tine", "Quests that suit you").t(), style = MaterialTheme.typography.titleLarge)
                    if (i == questions.size) {
                        Text(L("Unde locuiești?", "Where do you live?").t())
                        listOf("urban" to L("Oraș / municipiu", "City / town"), "rural" to L("Sat / comună", "Village / rural commune")).forEach { (value, label) ->
                            OutlinedButton(onClick = { if(i == index) { locality = value; index++ } }, modifier = Modifier.fillMaxWidth()) { Text(label.t()) }
                        }
                    } else {
                        Text(L("Ai acces practic la transport public pentru deplasările zilnice?", "Is public transport practical for your daily trips?").t())
                        listOf(true to L("Da", "Yes"), false to L("Nu / foarte rar", "No / very rarely")).forEach { (value, label) ->
                            OutlinedButton(onClick = { if(i == index) { publicTransport = value; index++ } }, modifier = Modifier.fillMaxWidth()) { Text(label.t()) }
                        }
                    }
                }
            } else {
                val cat = IntArray(5)
                questions.forEachIndexed { qi, q -> cat[q.category] += q.options[answers[qi]].pts }
                val raw = cat.sum()
                val ep = raw * 25
                val league = League.forEP(ep)
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(L("Profilul tău ecologic", "Your eco profile").t(), style = MaterialTheme.typography.headlineSmall)
                    LeagueIcon(league, 120.dp, glow = true)
                    Text(league.displayName, color = league.color, style = MaterialTheme.typography.titleLarge)
                    Text("${fmt(ep)} EP ($raw/100)", fontWeight = FontWeight.Bold)
                    Text(
                        L(
                            "Obiceiurile tale îți dau punctele de start. CO₂ economisit pornește de la 0 kg și crește doar prin misiuni verificate.",
                            "Your habits give you starting points. CO₂ saved starts at 0 kg and grows only through verified quests.",
                        ).t(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    error?.let { ErrorLine(it) }
                    AdventureButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                runCatching { vm.submitQuiz(answers.toList(), locality, publicTransport) }.onFailure { error = it }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(L("Începe aventura", "Start the adventure").t())
                    }
                }
            }
        }
        if (index < questions.size) {
            AdventureButton(onClick = {
                if (selected in questions[index].options.indices) {
                    answers.add(selected)
                    selected = -1
                    index++
                }
            }, enabled = selected >= 0, modifier = Modifier.fillMaxWidth()) {
                Text(L("Continuă", "Continue").t())
            }
        }

    }
}
