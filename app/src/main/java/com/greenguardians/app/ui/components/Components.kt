package com.greenguardians.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenguardians.app.model.L
import com.greenguardians.app.model.League
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.ui.LocalIsEn
import com.greenguardians.app.ui.fmt
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel

/** RO/EN + Dark/Light switches (top bar and profile). */
@Composable
fun LangDarkToggles(vm: MainViewModel) {
    val darkPref by vm.darkMode.collectAsStateWithLifecycle()
    val isDark = darkPref ?: isSystemInDarkTheme()
    val isEn = LocalIsEn.current
    Row {
        TextButton(onClick = { vm.setLanguage(if (isEn) "ro" else "en") }) {
            Text(if (isEn) "EN 🇬🇧" else "RO 🇷🇴")
        }
        TextButton(onClick = { vm.setDarkMode(!isDark) }) { Text(if (isDark) "☀️" else "🌙") }
    }
}

/** Dark card with the 8 leagues in a 4x2 grid; current league glows. */
@Composable
fun LeaguesShowcaseCard(current: League, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F14)),
        border = BorderStroke(1.dp, Color(0xFF263040)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            League.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { l ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            LeagueIcon(l, 58.dp, glow = l == current)
                            Text(l.displayName, color = l.color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${fmt(l.minEP)}+ EP", color = Color(0xFF9AA4B2), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeagueProgress(p: UserProgress, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(p.leagueProgress, label = "league")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LeagueIcon(p.league, 36.dp)
            Text(
                " ${p.league.displayName} · ${fmt(p.totalEP)} EP",
                fontWeight = FontWeight.Bold,
                color = p.league.color,
            )
        }
        LinearProgressIndicator(progress = { anim }, Modifier.fillMaxWidth(), color = p.league.color)
        val next = p.league.next
        Text(
            if (next == null) L("Ligă maximă atinsă! 🌍", "Max league reached! 🌍").t()
            else L("Încă ${fmt(p.epToNextLeague)} EP până la ${next.displayName}", "${fmt(p.epToNextLeague)} EP to ${next.displayName}").t(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Community boss: verified km (walk, bike, CTP) of all users since BOSS_START, from /api/community. */
@Composable
fun BossBattleCard(communityKm: Double?, onContribute: () -> Unit, modifier: Modifier = Modifier) {
    val goal = 10_000.0
    val km = communityKm ?: 0.0
    val bears = km * 0.20 / 100.0 // 0.2 kg CO2 per car km avoided, 100 kg per bear
    val anim by animateFloatAsState((km / goal).toFloat().coerceAtMost(1f), label = "boss")
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("👾 Community Boss Battle", fontWeight = FontWeight.Bold)
            Text(
                L(
                    "Smogzilla Carburantului — Evitați împreună 10.000 km de deplasări cu mașina!",
                    "Fuel Smogzilla — together, avoid 10,000 km of car trips!",
                ).t(),
            )
            LinearProgressIndicator(progress = { anim }, Modifier.fillMaxWidth(), color = Color(0xFFEF4444))
            Text("${fmt(km.toInt())} / 10.000 km", fontWeight = FontWeight.SemiBold)
            Text(L("🐻‍❄️ Urși polari salvați prin acest Boss: %.2f".format(bears), "🐻‍❄️ Polar bears saved by this Boss: %.2f".format(bears)).t())
            Button(onClick = onContribute) { Text(L("Contribuie cu misiuni de transport verde", "Contribute with green transport quests").t()) }
        }
    }
}
