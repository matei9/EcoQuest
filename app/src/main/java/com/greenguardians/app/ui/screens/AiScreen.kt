package com.greenguardians.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import com.greenguardians.app.data.ApiException
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenguardians.app.data.QuestRepository
import com.greenguardians.app.model.L
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.ui.LocalIsEn
import com.greenguardians.app.ui.t
import java.time.LocalDate

private class Msg(val fromUser: Boolean, val text: String)

private val quickPrompts = listOf(
    L("Câți litri de carburant am economisit?", "How many liters of fuel did I save?"),
    L("Câte ambalaje SGR am reciclat?", "How many SGR packages did I recycle?"),
    L("Cum ajut urșii polari?", "How do I help polar bears?"),
    L("Ce misiune să fac azi?", "What quest should I do today?"),
)

/** Offline rule-based "Guardian AI": explains the user's real impact from local progress. */
private fun reply(q: String, p: UserProgress, en: Boolean): String {
    val s = q.lowercase()
    fun r(ro: String, e: String) = if (en) e else ro
    return when {
        listOf("litri", "carbur", "fuel", "liter").any { it in s } -> r(
            "Ai evitat ~%.1f km cu mașina, adică ~%.1f litri de carburant (7 L/100 km) și %.2f kg CO₂.".format(p.carKmAvoided, p.fuelLitersSaved, p.verifiedCO2SavedKg),
            "You avoided ~%.1f km by car, about %.1f liters of fuel (7 L/100 km) and %.2f kg CO₂.".format(p.carKmAvoided, p.fuelLitersSaved, p.verifiedCO2SavedKg),
        )
        listOf("sgr", "ambalaj", "package", "recicl", "recycl").any { it in s } -> r(
            "Ai returnat ${p.sgrReturned} ambalaje SGR prin misiuni verificate. Fiecare ambalaj returnat ajunge din nou în circuit în loc să polueze.",
            "You returned ${p.sgrReturned} SGR packages through verified quests. Each returned package re-enters the loop instead of polluting.",
        )
        listOf("urs", "bear", "gheat", "ice", "polar").any { it in s } -> r(
            "1 kg CO₂ economisit protejează 0,003 m² (30 cm²) de gheață arctică. Tu ai salvat %.1f cm² și %.2f urși polari; la 100 kg CO₂ salvezi un urs întreg. Continuă cu misiunile de transport verde!".format(p.arcticIceSavedCm2, p.polarBearsSaved),
            "1 kg of CO₂ saved protects 0.003 m² (30 cm²) of Arctic ice. You saved %.1f cm² and %.2f polar bears; at 100 kg CO₂ you save a whole bear. Keep going with green transport quests!".format(p.arcticIceSavedCm2, p.polarBearsSaved),
        )
        listOf("misiun", "quest", "azi", "today").any { it in s } -> {
            val rec = QuestRepository.recommended(p, LocalDate.now()).firstOrNull()
            if (rec == null) r("Ai terminat recomandările de azi. Bravo!", "You finished today's recommendations. Well done!")
            else r("Îți recomand: „${rec.title.ro}” (+${rec.ep} EP, -%.2f kg CO₂).".format(rec.co2Kg), "I recommend: “${rec.title.en}” (+${rec.ep} EP, -%.2f kg CO₂).".format(rec.co2Kg))
        }
        else -> r(
            "Sunt Guardian AI 🌱. Întreabă-mă despre carburant, ambalaje SGR, urși polari sau misiuni.",
            "I'm Guardian AI 🌱. Ask me about fuel, SGR packages, polar bears or quests.",
        )
    }
}

@Composable
fun AiScreen(vm: MainViewModel, p: UserProgress) {
    val en = LocalIsEn.current
    val msgs = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val greeting = L(
        "Salut! Sunt Guardian AI 🌱 Pentru verificarea cu foto/bon deschide o misiune din tab-ul Misiuni.",
        "Hi! I'm Guardian AI 🌱 To verify with a photo/receipt, open a quest from the Quests tab.",
    ).t()

    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var modelThinking by remember { mutableStateOf(false) }
    // Shown from the moment the question is sent; models often think for a while before writing.
    val typing = if (modelThinking) L("🤔 Guardian AI se gândește…", "🤔 Guardian AI is thinking…").t()
    else L("🤔 Guardian AI pregătește răspunsul…", "🤔 Guardian AI is preparing an answer…").t()

    // Server AI (Kilo gateway, streamed word by word); offline rule-based answer if it fails before any text.
    fun send(text: String) {
        if (text.isBlank() || busy) return
        msgs.add(Msg(true, text))
        input = ""
        busy = true
        modelThinking = false
        val history = msgs.map { it.fromUser to it.text }
        msgs.add(Msg(false, ""))
        val idx = msgs.lastIndex
        scope.launch {
            val sb = StringBuilder()
            try {
                vm.aiStream(history, if (en) "en" else "ro").collect { ev ->
                    when (ev.optString("type")) {
                        "thinking" -> modelThinking = true
                        "delta" -> {
                            sb.append(ev.getString("text"))
                            msgs[idx] = Msg(false, sb.toString())
                        }
                        "error" -> throw ApiException(ev.optString("error", "ai_failed"), 0)
                    }
                }
                if (sb.isEmpty()) throw ApiException("ai_failed", 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                msgs[idx] = Msg(
                    false,
                    if (sb.isEmpty()) reply(text, p, en) + if (en) "\n\n(offline mode)" else "\n\n(mod offline)"
                    else "$sb …",
                )
            } finally {
                busy = false
                modelThinking = false
            }
        }
    }
    // Follow the answer while it grows (+1: the greeting is the first list item).
    LaunchedEffect(msgs.size, msgs.lastOrNull()?.text?.length) {
        if (msgs.isNotEmpty()) listState.scrollToItem(msgs.size)
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🤖 Guardian AI", style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Bubble(false, greeting) }
            items(msgs.size) { Bubble(msgs[it].fromUser, msgs[it].text.ifEmpty { typing }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quickPrompts.forEach { qp ->
                val label = qp.t()
                AssistChip(onClick = { send(label) }, label = { Text(label) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true)
            Button(onClick = { send(input) }) { Text(L("Trimite", "Send").t()) }
        }
    }
}

@Composable
private fun Bubble(fromUser: Boolean, text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ) { Text(text, Modifier.padding(12.dp)) }
    }
}
