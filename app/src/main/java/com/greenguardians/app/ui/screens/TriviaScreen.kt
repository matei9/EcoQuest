package com.greenguardians.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenguardians.app.data.Json
import com.greenguardians.app.model.L
import com.greenguardians.app.ui.ErrorLine
import com.greenguardians.app.ui.rememberApi
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val levels = listOf(L("Ușor", "Easy"), L("Mediu", "Medium"), L("Greu", "Hard"))
private val Green = Color(0xFF10B981)
private val Red = Color(0xFFEF4444)

/**
 * Daily trivia from the fun facts: 10 questions, easy -> hard, no feedback until the end.
 * Grading and EP (10/10 = 100, 9 = 70, 8 = 50, < 8 = 0) are done by the server.
 */
@Composable
fun TriviaScreen(vm: MainViewModel, onClose: () -> Unit) {
    val data by rememberApi(Unit) { vm.api.get("/api/trivia") }
    val r = data
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(L("🧠 Quiz zilnic din fun facts", "🧠 Daily fun-fact quiz").t(), style = MaterialTheme.typography.titleLarge)
        Text(
            L("10/10 = 100 EP · 9/10 = 70 EP · 8/10 = 50 EP · o încercare pe zi", "10/10 = 100 EP · 9/10 = 70 EP · 8/10 = 50 EP · one try per day").t(),
            style = MaterialTheme.typography.bodySmall,
        )
        when {
            r == null -> CircularProgressIndicator()
            r.isFailure -> ErrorLine(r.exceptionOrNull()!!)
            r.getOrThrow().getBoolean("done") -> {
                val d = r.getOrThrow()
                Text(
                    L("Ai dat deja quiz-ul azi: ${d.getInt("score")}/10 (+${d.getInt("ep")} EP). Revino mâine!",
                        "You already took today's quiz: ${d.getInt("score")}/10 (+${d.getInt("ep")} EP). Come back tomorrow!").t(),
                )
                Button(onClick = onClose) { Text("OK") }
            }
            else -> Quiz(vm, r.getOrThrow(), onClose)
        }
    }
}

@Composable
private fun Quiz(vm: MainViewModel, data: JSONObject, onClose: () -> Unit) {
    val questions = remember(data) { Json.objects(data.getJSONArray("questions")) }
    val answers = remember { mutableStateListOf<Int>() }
    var index by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        busy = true
        error = null
        scope.launch {
            runCatching {
                vm.mutate("POST", "/api/trivia/submit", JSONObject().put("token", data.getString("token")).put("answers", JSONArray(answers.toList())))
            }.onSuccess { result = it }.onFailure { error = it }
            busy = false
        }
    }

    val res = result
    if (res != null) return Review(res, onClose)

    if (index < questions.size) {
        AnimatedContent(targetState = index, label = "trivia") { i ->
            val q = questions[i]
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${i + 1}/${questions.size} · ${levels[q.getInt("difficulty") - 1].t()}", style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress = { (i + 1f) / questions.size }, Modifier.fillMaxWidth())
                Text(Json.l(q.getJSONObject("question")).t(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val opts = q.getJSONArray("options")
                for (o in 0 until opts.length()) {
                    // No right/wrong feedback here: the review comes only after all 10 answers.
                    OutlinedButton(onClick = { answers.add(o); index = i + 1 }, Modifier.fillMaxWidth()) {
                        Text(Json.l(opts.getJSONObject(o)).t())
                    }
                }
            }
        }
    } else {
        Text(L("Ai răspuns la toate cele 10 întrebări.", "You answered all 10 questions.").t())
        error?.let { ErrorLine(it) }
        Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(L("Vezi rezultatul", "See the result").t())
        }
        TextButton(onClick = { answers.clear(); index = 0 }, enabled = !busy) { Text(L("Ia-o de la capăt", "Start over").t()) }
    }
}

@Composable
private fun Review(res: JSONObject, onClose: () -> Unit) {
    val results = Json.objects(res.getJSONArray("results"))
    val wrong = results.filter { !it.getBoolean("ok") }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("${res.getInt("score")}/${res.getInt("total")}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            val ep = res.getInt("ep")
            Text(
                if (ep > 0) L("🎉 +$ep EP · +${res.getInt("coins")} 🪙", "🎉 +$ep EP · +${res.getInt("coins")} 🪙").t()
                else L("Ai nevoie de minimum 8/10 pentru puncte. Citește fun facts și revino mâine!", "You need at least 8/10 for points. Read the fun facts and come back tomorrow!").t(),
                color = if (ep > 0) Green else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (wrong.isEmpty()) Text(L("Perfect, niciun răspuns greșit!", "Perfect, no wrong answers!").t())
            else Text(L("Ce ai greșit:", "What you got wrong:").t(), fontWeight = FontWeight.Bold)
        }
        items(wrong) { w ->
            val opts = w.getJSONArray("options")
            val chosen = w.getInt("chosen")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Json.l(w.getJSONObject("question")).t(), fontWeight = FontWeight.SemiBold)
                    if (chosen in 0 until opts.length()) {
                        Text("✗ " + Json.l(opts.getJSONObject(chosen)).t(), color = Red)
                    }
                    Text("✓ " + Json.l(opts.getJSONObject(w.getInt("correct"))).t(), color = Green, fontWeight = FontWeight.SemiBold)
                    Text("💡 " + Json.l(w.getJSONObject("fact")).t(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Button(onClick = onClose, Modifier.fillMaxWidth()) { Text(L("Înapoi la misiuni", "Back to quests").t()) } }
    }
}
