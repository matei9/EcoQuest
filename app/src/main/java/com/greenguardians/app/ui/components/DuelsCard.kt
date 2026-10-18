package com.greenguardians.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenguardians.app.data.Json
import com.greenguardians.app.model.L
import com.greenguardians.app.ui.ErrorLine
import com.greenguardians.app.ui.rememberApi
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DuelsCard(vm: MainViewModel) {
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val data by rememberApi(refresh) { vm.api.get("/api/duels") }
    val act: (suspend () -> Unit) -> Unit = { block ->
        if (!busy) scope.launch {
            busy = true
            error = null
            try { block(); refresh++; vm.refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e }
            finally { busy = false }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(L("⚔️ Dueluri 1v1", "⚔️ 1v1 duels").t(), style = MaterialTheme.typography.titleLarge)
            Text(L("7 zile de la start. Câștigă cine adună mai multe EP verificate în acest interval, inclusiv trivia. Punctele inițiale din quiz nu contează.", "7 days from the start. Earn the most verified EP during that time, including trivia, to win. Initial quiz points do not count.").t())
            Text(L("Clasat cu adversar aleatoriu: 3 puncte la victorie, 1 la egalitate. Amical: invită un prieten din Echipe → Prieteni; rezultatul nu intră în statisticile profilului. Un singur duel activ odată.", "Ranked random match: 3 points for a win, 1 for a draw. Friendly: invite a friend from Teams → Friends; results do not affect profile stats. One active duel at a time.").t())
            TextButton(enabled = !busy, onClick = { refresh++; vm.refresh() }) { Text(L("Actualizează duelurile", "Refresh duels").t()) }
            error?.let { ErrorLine(it) }
            val result = data
            when {
                result == null -> CircularProgressIndicator()
                result.isFailure -> ErrorLine(result.exceptionOrNull()!!)
                else -> {
                    val o = result.getOrThrow()
                    val stats = o.getJSONObject("stats")
                    Text(L("Clasate: ${stats.getInt("wins")} victorii · ${stats.getInt("points")} puncte", "Ranked: ${stats.getInt("wins")} wins · ${stats.getInt("points")} points").t())
                    val duels = Json.objects(o.getJSONArray("duels"))
                    val active = duels.any { it.getString("status") == "active" }
                    if (o.getBoolean("queued")) {
                        Text(L("Căutăm un adversar. Coada expiră după 30 de minute; actualizează lista pentru rezultat.", "Looking for an opponent. Queue expires after 30 minutes; refresh for the result.").t())
                        OutlinedButton(enabled = !busy, onClick = { act { vm.api.call("DELETE", "/api/duels/queue") } }) { Text(L("Anulează căutarea", "Cancel search").t()) }
                    } else {
                        Button(enabled = !busy && !active, onClick = { act { vm.api.post("/api/duels/queue") } }) { Text(L("Caută adversar aleatoriu", "Find random opponent").t()) }
                    }
                    if (duels.isEmpty()) Text(L("Încă nu ai dueluri. Invită un prieten sau caută un adversar.", "No duels yet. Invite a friend or find an opponent.").t())
                    duels.forEach { d ->
                        val id = d.getInt("id")
                        val status = d.getString("status")
                        val name = if (d.isNull("opponent")) L("Cont șters", "Deleted account").t() else d.getString("opponent")
                        val mode = if (d.getString("mode") == "ranked") L("Clasat", "Ranked") else L("Amical", "Friendly")
                        Text("${mode.t()} · $name", style = MaterialTheme.typography.titleMedium)
                        when (status) {
                            "pending" -> {
                                Text(if (d.getBoolean("incoming")) L("Te invită la un duel de 7 zile.", "Invites you to a 7-day duel.").t() else L("Așteaptă acceptarea prietenului.", "Waiting for your friend to accept.").t())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (d.getBoolean("incoming")) Button(enabled = !busy && !active, onClick = { act { vm.api.post("/api/duels/$id/accept") } }) { Text(L("Acceptă", "Accept").t()) }
                                    TextButton(enabled = !busy, onClick = { act { vm.api.call("DELETE", "/api/duels/$id") } }) { Text(L("Refuză / anulează", "Decline / cancel").t()) }
                                }
                            }
                            "active", "finished" -> {
                                Text(L("Tu: ${d.getInt("myScore")} EP · Adversar: ${d.getInt("opponentScore")} EP", "You: ${d.getInt("myScore")} EP · Opponent: ${d.getInt("opponentScore")} EP").t())
                                if (status == "active") {
                                    val end = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(d.getLong("endsAt")))
                                    Text(L("Se încheie: $end", "Ends: $end").t())
                                } else Text(when (d.getString("outcome")) {
                                    "win" -> L("🏆 Ai câștigat!", "🏆 You won!")
                                    "loss" -> L("Duel pierdut", "Duel lost")
                                    else -> L("Egalitate", "Draw")
                                }.t())
                            }
                            else -> Text(L("Invitație anulată / expirată", "Invitation cancelled / expired").t())
                        }
                    }
                }
            }
        }
    }
}
