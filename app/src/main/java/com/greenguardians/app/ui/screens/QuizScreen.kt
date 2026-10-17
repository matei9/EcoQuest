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
    var index by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateListOf<Int>() }
    val questions = QuizData.questions
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("🌱 Green Guardians", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
                    Text("EcoBaseline · ${i + 1}/${questions.size}", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(progress = { (i + 1f) / questions.size }, Modifier.fillMaxWidth())
                    Text(QuizData.categories[q.category].name.t(), color = MaterialTheme.colorScheme.primary)
                    Text(q.text.t(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    q.options.forEachIndexed { oi, opt ->
                        OutlinedButton(
                            onClick = {
                                answers.add(oi) // server scores option indices
                                index = i + 1
                            },
                            Modifier.fillMaxWidth(),
                        ) { Text(opt.text.t()) }
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
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                runCatching { vm.submitQuiz(answers.toList()) }.onFailure { error = it }
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
    }
}
