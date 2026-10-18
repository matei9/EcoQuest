package com.greenguardians.app.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.greenguardians.app.data.Json
import com.greenguardians.app.model.L
import com.greenguardians.app.model.Me
import com.greenguardians.app.ui.ErrorLine
import com.greenguardians.app.ui.rememberApi
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun FriendsCard(vm: MainViewModel, me: Me, changed: () -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var notice by remember { mutableStateOf<L?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val data by rememberApi(refresh, me.user.teamId) { vm.api.get("/api/friends") }
    val act: (suspend () -> Unit) -> Unit = { block ->
        if (!busy) scope.launch {
            busy = true
            error = null
            notice = null
            try {
                block()
                refresh++
                changed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            } finally {
                busy = false
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(L("Prieteni și invitații", "Friends and invitations").t(), style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(L("Codul tău: ${me.user.refCode}", "Your code: ${me.user.refCode}").t()) }
            val shareText = L(
                "Adaugă-mă la prieteni în EcoQuest! Codul meu: ${me.user.refCode}. Îl introduci în Ligi → Echipe → Prieteni și invitații.",
                "Add me as a friend in EcoQuest! My code: ${me.user.refCode}. Enter it in Leagues → Teams → Friends and invitations.",
            ).t()
            OutlinedButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }, null))
            }) { Text(L("Distribuie codul", "Share code").t()) }
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(8) },
                label = { Text(L("Codul prietenului", "Friend's code").t()) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(enabled = !busy && code.trim().length == 8, onClick = {
                act {
                    vm.api.post("/api/friends", JSONObject().put("code", code.trim()))
                    code = ""
                    notice = L("Cerere de prietenie trimisă.", "Friend request sent.")
                }
            }) { Text(L("Adaugă prieten", "Add friend").t()) }
            TextButton(enabled = !busy, onClick = { refresh++ }) { Text(L("Actualizează lista", "Refresh list").t()) }
            if (busy) CircularProgressIndicator()
            notice?.let { Text(it.t()) }
            error?.let { ErrorLine(it) }
            val result = data
            when {
                result == null -> CircularProgressIndicator()
                result.isFailure -> ErrorLine(result.exceptionOrNull()!!)
                else -> {
                    val o = result.getOrThrow()
                    val incoming = Json.objects(o.getJSONArray("incoming"))
                    val outgoing = Json.objects(o.getJSONArray("outgoing"))
                    val friends = Json.objects(o.getJSONArray("friends"))
                    val invitations = Json.objects(o.getJSONArray("invitations"))
                    val sent = Json.objects(o.getJSONArray("sentInvitations")).map { it.getInt("userId") }.toSet()
                    incoming.forEach { person ->
                        Text(L("${person.getString("name")} vrea să fiți prieteni.", "${person.getString("name")} wants to be friends.").t())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = { act { vm.api.post("/api/friends/${person.getInt("id")}/accept") } }) {
                                Text(L("Acceptă", "Accept").t())
                            }
                            TextButton(enabled = !busy, onClick = { act { vm.api.call("DELETE", "/api/friends/${person.getInt("id")}") } }) {
                                Text(L("Refuză", "Decline").t())
                            }
                        }
                    }
                    invitations.forEach { invitation ->
                        Text(L(
                            "${invitation.getString("senderName")} te invită în ${invitation.getString("teamName")}.",
                            "${invitation.getString("senderName")} invites you to ${invitation.getString("teamName")}.",
                        ).t())
                        if (me.user.teamId != null) Text(L("Părăsește echipa actuală pentru a accepta.", "Leave your current team to accept.").t())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy && me.user.teamId == null, onClick = {
                                act { vm.mutate("POST", "/api/teams/invitations/${invitation.getInt("id")}/accept") }
                            }) { Text(L("Intră în echipă", "Join team").t()) }
                            TextButton(enabled = !busy, onClick = {
                                act { vm.api.call("DELETE", "/api/teams/invitations/${invitation.getInt("id")}") }
                            }) { Text(L("Refuză", "Decline").t()) }
                        }
                    }
                    if (friends.isEmpty()) Text(L("Nu ai încă prieteni. Trimite codul tău unei persoane care are cont.", "No friends yet. Share your code with someone who has an account.").t())
                    friends.forEach { friend ->
                        Text("${friend.getString("name")} · ${friend.getString("city")}", style = MaterialTheme.typography.titleSmall)
                        OutlinedButton(enabled = !busy, onClick = {
                            act {
                                vm.api.post("/api/duels/friends", JSONObject().put("userId", friend.getInt("id")))
                                notice = L("Invitație 1v1 amical trimisă. Acceptarea și scorul apar în Ligi → 1v1.", "Friendly 1v1 invitation sent. Accept and view scores in Leagues → 1v1.")
                            }
                        }) { Text(L("Provoacă la 1v1 amical", "Challenge to friendly 1v1").t()) }
                        if (me.user.teamId != null && friend.isNull("teamId")) {
                            OutlinedButton(enabled = !busy && friend.getInt("id") !in sent, onClick = {
                                act {
                                    vm.api.post("/api/teams/invitations", JSONObject().put("userId", friend.getInt("id")))
                                    notice = L("Invitație în echipă trimisă.", "Team invitation sent.")
                                }
                            }) { Text(if (friend.getInt("id") in sent) L("Invitație trimisă", "Invitation sent").t() else L("Invită în echipa mea", "Invite to my team").t()) }
                        } else if (!friend.isNull("teamId")) {
                            Text(if (friend.getInt("teamId") == me.user.teamId) L("În echipa ta", "In your team").t() else L("Are deja o echipă", "Already in a team").t())
                        }
                        TextButton(enabled = !busy, onClick = { act { vm.api.call("DELETE", "/api/friends/${friend.getInt("id")}") } }) {
                            Text(L("Elimină prieten", "Remove friend").t())
                        }
                    }
                    outgoing.forEach { person ->
                        Text(L("Cerere trimisă către ${person.getString("name")}", "Request sent to ${person.getString("name")}").t())
                        TextButton(enabled = !busy, onClick = { act { vm.api.call("DELETE", "/api/friends/${person.getInt("id")}") } }) {
                            Text(L("Anulează cererea", "Cancel request").t())
                        }
                    }
                }
            }
        }
    }
}
