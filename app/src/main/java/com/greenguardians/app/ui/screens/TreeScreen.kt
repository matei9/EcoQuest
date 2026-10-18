package com.greenguardians.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenguardians.app.data.ApiException
import com.greenguardians.app.data.Json
import com.greenguardians.app.model.GuardianTree
import com.greenguardians.app.model.L
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.ui.ErrorLine
import com.greenguardians.app.ui.components.TreeGarden
import com.greenguardians.app.ui.rememberApi
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.text.NumberFormat

private val treeStages = listOf(
    L("Sămânță", "Seed"), L("Germinație", "Germination"), L("Mlădiță", "Sprout"),
    L("Puiet", "Sapling"), L("Copac tânăr", "Young tree"), L("Copac în creștere", "Growing tree"), L("Copac matur", "Mature tree"),
)
private fun heightLabel(height: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(height) + " m"

@Composable
fun TreeScreen(vm: MainViewModel, p: UserProgress, onMissions: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val treeResult by rememberApi(refresh, p.completedKeys) {
        try {
            val response = vm.mutate("GET", "/api/tree")
            val tree = response.optJSONObject("progress")?.optJSONObject("tree") ?: throw ApiException("tree_server_outdated", 426)
            Json.tree(tree)
        } catch (e: ApiException) {
            if (e.status == 404) throw ApiException("tree_server_outdated", 426)
            throw e
        }
    }
    val leaderboard by rememberApi(refresh, p.completedKeys) {
        try { vm.api.get("/api/leaderboard/trees") }
        catch (e: ApiException) {
            if (e.status == 404) throw ApiException("tree_server_outdated", 426)
            throw e
        }
    }
    LaunchedEffect(Unit) {
        var day = LocalDate.now(ZoneId.of("Europe/Bucharest"))
        while (true) {
            delay(60_000)
            val current = LocalDate.now(ZoneId.of("Europe/Bucharest"))
            if (current != day) { day = current; refresh++ }
        }
    }
    // Capture both async responses for LazyColumn, including when a request is refreshed.
    val result = treeResult
    val ranks = leaderboard
    val tree = result?.getOrNull() ?: p.tree
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(L("Grădina ta", "Your garden").t(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(L("O misiune pe zi. Un copac care crește cu tine.", "One quest a day. A tree that grows with you.").t())
            OutlinedButton(onClick = { refresh++ }) { Text(L("Actualizează grădina", "Refresh garden").t()) }
            if (result == null) CircularProgressIndicator()
            result?.exceptionOrNull()?.let {
                ErrorLine(it)
                if (tree != null) Text(L("Afișăm ultima stare salvată; reconectează-te pentru evoluția de azi.", "Showing the last saved state; reconnect for today's growth.").t())
            }
        }
        if (tree != null) {
            item { GardenCard(tree, onMissions) }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(L("Cum îți îngrijești copacul", "How your tree grows").t(), fontWeight = FontWeight.Bold)
                    Text(L("Finalizează cel puțin o misiune pe zi. Mai multe misiuni în aceeași zi înseamnă o singură zi de creștere. Quiz-ul și trivia singure nu cresc copacul.", "Complete at least one quest a day. Multiple quests on the same day count as one growth day. Quiz and trivia alone do not grow the tree.").t())
                    Text(L("Sunt 7 etape. Avansezi după 3 zile consecutive de activitate în fiecare etapă: copacul devine matur la ziua 18, dacă nu ratezi zile.", "There are 7 stages. Advance after 3 consecutive active days in each stage: maturity arrives on day 18 if you miss no days.").t())
                    Text(L("Înainte de maturitate: prima zi ratată îl ofilește, iar la fiecare două zile ratate consecutiv coboară o etapă. Progresul de 3 zile al etapei se reia când revii.", "Before maturity: the first missed day causes wilting; every two consecutive missed days drop one stage. The stage's 3-day progress restarts when you return.").t())
                    Text(L("La maturitate pornește de la 7 m: +1 m pe zi activă și −1 m pe zi ratată, până la minimum 1 m. Rămâne matur. Peste 20 m vezi doar vârful.", "Maturity starts at 7 m: +1 m per active day and −1 m per missed day, down to a minimum of 1 m. It stays mature. Above 20 m, only the top is shown.").t())
                    Text(L("Zilele se schimbă la miezul nopții, ora României. Ziua de azi nu se pierde până nu se încheie.", "Days change at midnight in Romania. Today is not missed until it ends.").t(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text(L("🏆 Cei mai înalți copaci", "🏆 Tallest trees").t(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        when {
            ranks == null -> item { CircularProgressIndicator() }
            ranks.isFailure -> item { ErrorLine(ranks.exceptionOrNull()!!) }
            else -> {
                val o = ranks.getOrThrow()
                val players = Json.objects(o.getJSONArray("players"))
                if (players.isEmpty()) item { Text(L("Grădina comunității te așteaptă. Finalizează prima misiune ca să intri în clasament.", "The community garden awaits. Complete your first quest to enter the leaderboard.").t()) }
                players.forEach { player -> item(key = "tree-${player.getInt("id")}") { TreeRank(player) } }
                val mine = o.optJSONObject("mine")
                if (mine != null && players.none { it.getBoolean("isYou") }) item {
                    Text(L("Poziția ta", "Your position").t(), fontWeight = FontWeight.Bold)
                    TreeRank(mine)
                }
                if (mine == null && players.isNotEmpty()) item { Text(L("Finalizează o misiune pentru a intra și tu în clasament.", "Complete a quest to join the leaderboard.").t()) }
            }
        }
    }
}

@Composable
private fun GardenCard(tree: GuardianTree, onMissions: () -> Unit) {
    val stage = treeStages[(tree.stage - 1).coerceIn(0, 6)].t()
    val height = heightLabel(tree.heightMeters)
    val health = when (tree.health) {
        "wilting" -> L("Puțin ofilit", "Slightly wilted")
        "withered" -> L("Are nevoie de grijă", "Needs care")
        else -> L("Sănătos", "Healthy")
    }.t()
    Card {
        TreeGarden(tree, "$stage · $height · $health")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(height, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("$stage · ${tree.stage}/7 · $health", fontWeight = FontWeight.SemiBold)
            if (tree.topOnly) Text(L("☁️ E atât de înalt încât vezi doar vârful. Înălțimea de mai sus este cea totală.", "☁️ So tall that you can only see its top. The height above is the full height.").t())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..7).forEach { step ->
                    val selected = step <= tree.stage
                    androidx.compose.foundation.layout.Box(Modifier.size(32.dp).background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                        Text(if (step < tree.stage) "✓" else "$step", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!tree.mature) {
                LinearProgressIndicator(progress = { tree.stageDays / 3f }, modifier = Modifier.fillMaxWidth())
                Text(L("${tree.stageDays}/3 zile în această etapă · ${tree.daysToNextStage} până la următoarea", "${tree.stageDays}/3 days in this stage · ${tree.daysToNextStage} until the next").t())
            }
            Text(L("🔥 ${tree.streak} zile consecutive · ${tree.activeDays} zile active în total", "🔥 ${tree.streak} consecutive days · ${tree.activeDays} active days in total").t())
            Text(when {
                tree.activeToday -> L("✓ Copacul a primit deja creșterea de azi.", "✓ Your tree has already received today's growth.")
                tree.missedDays > 0 -> L("Ai ratat ${tree.missedDays} zile. Finalizează o misiune azi ca să își revină.", "You missed ${tree.missedDays} days. Complete a quest today to restore its health.")
                else -> L("Finalizează o misiune azi ca să-l ajuți să crească.", "Complete a quest today to help it grow.")
            }.t())
            Button(onClick = onMissions, modifier = Modifier.fillMaxWidth()) { Text(L("Mergi la misiuni", "Go to quests").t()) }
        }
    }
}

@Composable
private fun TreeRank(o: JSONObject) {
    Card(colors = CardDefaults.cardColors(containerColor = if (o.getBoolean("isYou")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#${o.getInt("rank")}", fontWeight = FontWeight.Bold)
            Column(Modifier.weight(1f)) {
                Text(o.getString("name") + if (o.getBoolean("isYou")) L(" (tu)", " (you)").t() else "", fontWeight = FontWeight.SemiBold)
                Text(o.getString("city"), style = MaterialTheme.typography.bodySmall)
            }
            Text(heightLabel(o.getDouble("heightMeters")), fontWeight = FontWeight.Bold)
        }
    }
}
