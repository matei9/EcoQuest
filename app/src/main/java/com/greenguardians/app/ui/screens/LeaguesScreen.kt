package com.greenguardians.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenguardians.app.data.Json
import com.greenguardians.app.model.L
import com.greenguardians.app.model.League
import com.greenguardians.app.model.Me
import com.greenguardians.app.ui.ErrorLine
import com.greenguardians.app.ui.components.BossBattleCard
import com.greenguardians.app.ui.components.LeagueIcon
import com.greenguardians.app.ui.components.LeagueProgress
import com.greenguardians.app.ui.components.LeaguesShowcaseCard
import com.greenguardians.app.ui.fmt
import com.greenguardians.app.ui.rememberApi
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun LeaguesScreen(vm: MainViewModel, me: Me, onGoMissions: () -> Unit) {
    val p = me.progress
    var tab by remember { mutableIntStateOf(0) }
    var verifiedOnly by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    val titles = listOf(L("👤 Individual", "👤 Individual"), L("🤝 Echipe", "🤝 Teams"), L("🎓 Școli", "🎓 Schools"), L("🏙️ Orașe", "🏙️ Cities"))
    val community by rememberApi(p.verifiedQuestEP) { vm.api.get("/api/community") }
    // Each tab loads from the server; keys refresh it after progress or team changes.
    val data by rememberApi(tab, verifiedOnly, p.verifiedQuestEP, me.user.teamId, reload) {
        when (tab) {
            0 -> vm.api.get("/api/leaderboard/individual?metric=" + if (verifiedOnly) "verified" else "total")
            1 -> vm.api.get("/api/teams")
            else -> JSONObject() // schools / cities load inside rankings()
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LeaguesShowcaseCard(p.league) }
        item { LeagueProgress(p) }
        item { BossBattleCard(bossKm(community), onContribute = onGoMissions) }
        item {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.t()) }) }
            }
        }
        val r = data
        when {
            r == null -> item { CircularProgressIndicator() }
            r.isFailure -> item { ErrorLine(r.exceptionOrNull()!!) }
            tab == 0 -> individual(r.getOrThrow(), verifiedOnly) { verifiedOnly = it }
            tab == 1 -> teamsTab(vm, me, r.getOrThrow()) { reload++ }
            else -> rankings(vm, tab)
        }
    }
}

private fun LazyListScope.individual(o: JSONObject, verifiedOnly: Boolean, setVerified: (Boolean) -> Unit) {
    val league = League.entries.firstOrNull { it.displayName == o.getString("league") } ?: League.BRONZE
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!verifiedOnly, { setVerified(false) }, { Text("Total EP") })
            FilterChip(verifiedOnly, { setVerified(true) }, { Text(L("EP Verificate", "Verified EP").t()) })
        }
        Text(
            L("Liga ${league.displayName}. „EP Verificate” exclude punctele autodeclarate din quiz.", "${league.displayName} league. “Verified EP” excludes self-declared quiz points.").t(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    val players = Json.objects(o.getJSONArray("players"))
    players.forEachIndexed { i, pl ->
        item {
            val value = if (verifiedOnly) pl.getInt("verified") else pl.getInt("total")
            RowCard("#${i + 1}  ${pl.getString("name")}", pl.getString("city"), "${fmt(value)} EP", highlight = pl.getBoolean("isYou"), league = league)
        }
    }
}

private fun LazyListScope.teamsTab(vm: MainViewModel, me: Me, o: JSONObject, changed: () -> Unit) {
    val goal = o.getInt("goal")
    val teams = Json.objects(o.getJSONArray("teams"))
    val mine = teams.firstOrNull { it.getInt("id") == me.user.teamId }
    item {
        val scope = rememberCoroutineScope()
        var dialog by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<Throwable?>(null) }
        val act: (suspend () -> Unit) -> Unit = { block ->
            scope.launch { runCatching { block() }.onSuccess { changed() }.onFailure { error = it } }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mine == null) {
                Button(onClick = { dialog = true }) { Text(L("Creează o echipă", "Create a team").t()) }
                if (teams.isNotEmpty()) Text(L("Sau alătură-te unei echipe:", "Or join a team:").t())
                teams.forEach { t ->
                    OutlinedButton(onClick = { act { vm.mutate("POST", "/api/teams/${t.getInt("id")}/join") } }, Modifier.fillMaxWidth()) {
                        Text("${t.getString("name")} · ${t.getString("city")} · ${t.getInt("members")} 👥")
                    }
                }
            } else {
                val done = mine.getInt("sgrThisWeek").coerceAtMost(goal)
                Text("🤝 ${mine.getString("name")}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { act { vm.mutate("POST", "/api/teams/leave") } }) { Text(L("Părăsește echipa", "Leave team").t()) }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🎯 Team Quest", fontWeight = FontWeight.Bold)
                        Text(L("Returnați împreună $goal de ambalaje SGR săptămâna aceasta", "Together, return $goal SGR packages this week").t())
                        LinearProgressIndicator(progress = { done / goal.toFloat() }, Modifier.fillMaxWidth())
                        Text("$done / $goal")
                    }
                }
                Text(L("Clasament echipe (SGR săptămâna asta)", "Team ranking (SGR this week)").t(), fontWeight = FontWeight.Bold)
                teams.forEachIndexed { i, t ->
                    RowCard("#${i + 1}  ${t.getString("name")}", "${t.getString("city")} · ${t.getInt("members")} 👥", "${t.getInt("sgrThisWeek")} ♻️", highlight = t == mine)
                }
            }
            error?.let { ErrorLine(it) }
        }
        if (dialog) {
            AlertDialog(
                onDismissRequest = { dialog = false },
                title = { Text(L("Echipă nouă", "New team").t()) },
                text = { OutlinedTextField(name, { name = it }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        dialog = false
                        act { vm.mutate("POST", "/api/teams", JSONObject().put("name", name.trim())) }
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { dialog = false }) { Text(L("Anulează", "Cancel").t()) } },
            )
        }
    }
}

/** Schools and cities endpoints return JSON arrays. */
private fun LazyListScope.rankings(vm: MainViewModel, tab: Int) {
    item {
        val rows by rememberApi(tab) {
            val path = if (tab == 2) "/api/leaderboard/schools" else "/api/leaderboard/cities"
            Json.objects(vm.api.getArray(path))
        }
        val r = rows
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 3) {
                Text(
                    L("Scor normalizat per participant activ (medie EP și kg CO₂) — competiție echitabilă.", "Score normalized per active participant (avg EP and kg CO₂) — fair competition.").t(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                r == null -> CircularProgressIndicator()
                r.isFailure -> ErrorLine(r.exceptionOrNull()!!)
                r.getOrThrow().isEmpty() -> Text(L("Încă nu există date.", "No data yet.").t())
                tab == 2 -> r.getOrThrow().forEachIndexed { i, s ->
                    RowCard(
                        "#${i + 1}  ${s.getString("name")}",
                        L("${s.getInt("users")} participanți · medie ${fmt(s.getInt("avgEP"))} EP", "${s.getInt("users")} participants · avg ${fmt(s.getInt("avgEP"))} EP").t(),
                        "${fmt(s.getInt("totalEP"))} EP",
                    )
                }
                else -> r.getOrThrow().forEachIndexed { i, c ->
                    RowCard(
                        "#${i + 1}  ${c.getString("name")}",
                        L(
                            "${c.getInt("active")}/${c.getInt("users")} activi · %.1f kg CO₂/participant".format(c.getDouble("avgCO2")),
                            "${c.getInt("active")}/${c.getInt("users")} active · %.1f kg CO₂/participant".format(c.getDouble("avgCO2")),
                        ).t(),
                        "${fmt(c.getInt("avgEP"))} EP",
                    )
                }
            }
        }
    }
}

@Composable
private fun RowCard(title: String, sub: String, value: String, highlight: Boolean = false, league: League? = null) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (league != null) LeagueIcon(league, 32.dp)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}
