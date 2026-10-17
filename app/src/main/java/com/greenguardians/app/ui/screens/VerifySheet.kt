package com.greenguardians.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenguardians.app.data.ApiException
import com.greenguardians.app.data.Json
import com.greenguardians.app.data.OnDevice
import com.greenguardians.app.data.QuestRepository
import com.greenguardians.app.model.L
import com.greenguardians.app.model.League
import com.greenguardians.app.model.Quest
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.model.Verification
import com.greenguardians.app.ui.ErrorLine
import com.greenguardians.app.ui.t
import com.greenguardians.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private typealias Runner = (suspend () -> List<String>) -> Unit

/** Verification bottom sheet: proofs are produced on-device and validated + rewarded by the server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifySheet(vm: MainViewModel, quest: Quest, p: UserProgress, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg by vm.config.collectAsStateWithLifecycle()
    val epBefore = remember { p.totalEP }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var awarded by remember { mutableStateOf<List<String>?>(null) }
    val submit: Runner = { block ->
        busy = true
        error = null
        scope.launch {
            runCatching { block() }.onSuccess { awarded = it }.onFailure { error = it }
            busy = false
        }
    }
    val complete: suspend (JSONObject) -> List<String> = { proof -> vm.completeQuest(quest.id, proof) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(quest.title.t(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(L("Verificare: ", "Verification: ").t() + quest.verification.label.t())

            val done = awarded
            if (done != null) {
                RewardSummary(done, epBefore)
                OutlinedButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("OK") }
                return@Column
            }

            when (quest.verification) {
                Verification.GPS -> TripProof(vm, quest, submit)
                Verification.RECEIPT_AI, Verification.BILL -> ImagePicker(1, L("📷 Alege poza bonului / documentului", "📷 Pick the receipt / document photo")) { (u) ->
                    submit {
                        val text = OnDevice.ocr(ctx, u)
                        val hash = OnDevice.sha256(ctx, u)
                        if (quest.sgr > 0) vm.submitReceipt(text, hash)
                        else complete(JSONObject().put("type", "ocr").put("text", text).put("imageHash", hash))
                    }
                }
                Verification.PHOTO, Verification.PHOTO_AI -> ImagePicker(1, L("📷 Alege poza dovadă", "📷 Pick the proof photo")) { (u) ->
                    submit {
                        complete(
                            JSONObject().put("type", "labels")
                                .put("labels", JSONArray(OnDevice.labels(ctx, u)))
                                .put("imageHash", OnDevice.sha256(ctx, u)),
                        )
                    }
                }
                Verification.BEFORE_AFTER -> ImagePicker(2, L("📷 Poza înainte, apoi după", "📷 Before photo, then after")) { uris ->
                    submit {
                        val labels = uris.flatMap { OnDevice.labels(ctx, it) }.distinct()
                        val hashes = uris.map { OnDevice.sha256(ctx, it) }
                        complete(JSONObject().put("type", "labels").put("labels", JSONArray(labels)).put("imageHashes", JSONArray(hashes)))
                    }
                }
                Verification.QR_PHOTO -> Button(onClick = {
                    submit {
                        val code = OnDevice.scanQr(ctx) ?: throw ApiException("cancelled", 0)
                        vm.checkin(code)
                    }
                }, Modifier.fillMaxWidth()) { Text(L("📱 Scanează codul QR al evenimentului", "📱 Scan the event QR code").t()) }
                Verification.TIMER -> TimerProof { seconds -> submit { complete(JSONObject().put("type", "timer").put("seconds", seconds)) } }
                Verification.CONFIRM, Verification.JOURNAL -> {
                    var checked by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, { checked = it })
                        Text(L("Confirm pe propria răspundere că am făcut această acțiune", "I confirm on my honour that I did this").t())
                    }
                    Button(enabled = checked, onClick = { submit { complete(JSONObject().put("type", "confirm")) } }, modifier = Modifier.fillMaxWidth()) {
                        Text(L("Confirmă și primește recompensa", "Confirm and claim reward").t())
                    }
                }
                Verification.AUTO -> Text(L("Se finalizează automat când atingi ținta.", "Completes automatically when you reach the target.").t())
            }

            if (busy) {
                Text(L("Green Guardians analizează dovada pe telefon...", "Green Guardians is analyzing the proof on your phone...").t())
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            error?.let { if ((it as? ApiException)?.code != "cancelled") ErrorLine(it) }
            if (cfg.demoMode) {
                TextButton(onClick = { submit { complete(JSONObject().put("type", "demo")) } }) {
                    Text(L("⚡ Finalizare demo (juriu)", "⚡ Demo completion (jury)").t())
                }
            }
        }
    }
}

@Composable
private fun RewardSummary(ids: List<String>, epBefore: Int) {
    val quests = ids.mapNotNull { QuestRepository.byId(it) }
    if (quests.isEmpty()) {
        Text(L("✔ Dovadă acceptată. Progresul misiunii a fost actualizat.", "✔ Proof accepted. Quest progress updated.").t(), fontWeight = FontWeight.Bold)
        return
    }
    val ep = quests.sumOf { it.ep }
    val co2 = quests.sumOf { it.co2Kg }
    Text("🎉 +$ep EP  ·  +${quests.sumOf { it.coins }} 🪙", style = MaterialTheme.typography.titleMedium)
    quests.forEach { Text("✔ " + it.title.t(), style = MaterialTheme.typography.bodySmall) }
    val newLeague = League.forEP(epBefore + ep)
    if (newLeague != League.forEP(epBefore)) {
        Text(L("🏆 Ai promovat în liga ${newLeague.displayName}!", "🏆 Promoted to ${newLeague.displayName} league!").t(), fontWeight = FontWeight.Bold, color = newLeague.color)
    }
    if (co2 > 0) {
        Text(L("🌍 -%.2f kg CO₂ evitați".format(co2), "🌍 -%.2f kg CO₂ avoided".format(co2)).t())
        Text(L("🧊 +%.1f cm² gheață arctică salvată".format(co2 * 30), "🧊 +%.1f cm² of Arctic ice saved".format(co2 * 30)).t())
        // 100 kg CO2 = 1 bear, so kg == % of a bear
        Text(L("🐻‍❄️ %.2f%% dintr-un urs polar salvat".format(co2), "🐻‍❄️ %.2f%% of a polar bear saved".format(co2)).t())
    }
}

@Composable
private fun ImagePicker(count: Int, label: L, onPicked: (List<Uri>) -> Unit) {
    val uris = remember { mutableStateListOf<Uri>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uris.add(uri)
            if (uris.size == count) {
                onPicked(uris.toList())
                uris.clear()
            }
        }
    }
    Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth()) {
        Text(label.t() + if (count > 1) " (${uris.size + 1}/$count)" else "")
    }
    Text(
        L("Textul și etichetele sunt extrase pe telefon (ML Kit); imaginea nu e trimisă.", "Text and labels are extracted on the phone (ML Kit); the image is not uploaded.").t(),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TimerProof(onDone: (Int) -> Unit) {
    val target = 180 // 3 minutes less in the shower
    var elapsed by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && elapsed < target) {
            delay(1000)
            elapsed++
        }
        if (elapsed >= target) running = false
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { elapsed / target.toFloat() }, Modifier.size(140.dp), strokeWidth = 10.dp)
        Text("%d:%02d".format(elapsed / 60, elapsed % 60), style = MaterialTheme.typography.headlineMedium)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { running = true }, enabled = elapsed < target) { Text("Start") }
        OutlinedButton(onClick = { running = false }) { Text(L("Pauză", "Pause").t()) }
        Button(onClick = { onDone(elapsed) }, enabled = elapsed >= target) { Text(L("Confirmă", "Confirm").t()) }
    }
}

private fun hasLocation(ctx: android.content.Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun TripProof(vm: MainViewModel, quest: Quest, submit: Runner) {
    val ctx = LocalContext.current
    val en = quest.title.en.lowercase()
    var mode by remember {
        mutableStateOf(if ("cycl" in en) "cycle" else if ("public" in en || "transport" in en) "transit" else "walk")
    }
    var granted by remember { mutableStateOf(hasLocation(ctx)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasLocation(ctx) }
    if (!granted) {
        Button(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
            Text(L("📍 Permite accesul la locație", "📍 Allow location access").t())
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("walk" to L("🚶 Pe jos", "🚶 Walk"), "cycle" to L("🚲 Bicicletă", "🚲 Bike"), "transit" to L("🚋 CTP", "🚋 Transit"))
            .forEach { (m, label) -> FilterChip(mode == m, { mode = m }, { Text(label.t()) }) }
    }
    if (mode == "transit") TransitFlow(vm, submit) else TrackFlow(vm, mode, submit)
}

@Composable
private fun TrackFlow(vm: MainViewModel, mode: String, submit: Runner) {
    val ctx = LocalContext.current
    var tracking by remember { mutableStateOf(false) }
    val points = remember { mutableStateListOf<Location>() }
    if (tracking) {
        LaunchedEffect(Unit) { OnDevice.track(ctx).collect { points.add(it) } }
    }
    val km = points.zipWithNext { a, b -> a.distanceTo(b) }.sum() / 1000f
    val minutes = if (points.size > 1) (points.last().time - points.first().time) / 60_000 else 0L
    Text(L("Distanță: %.2f km · %d min".format(km, minutes), "Distance: %.2f km · %d min".format(km, minutes)).t(), fontWeight = FontWeight.SemiBold)
    Text(
        L("Ține aplicația deschisă pe durata traseului. Serverul respinge vitezele de mașină.", "Keep the app open during the trip. The server rejects car speeds.").t(),
        style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = {
        if (!tracking) {
            points.clear()
            tracking = true
        } else {
            tracking = false
            val arr = JSONArray(points.map { JSONObject().put("lat", it.latitude).put("lon", it.longitude).put("t", it.time) })
            submit { vm.submitTrack(mode, arr) }
        }
    }, Modifier.fillMaxWidth()) {
        Text(if (tracking) L("⏹ Stop și trimite traseul", "⏹ Stop and submit trip").t() else L("▶ Pornește GPS", "▶ Start GPS").t())
    }
}

/** CTP Iasi: pick one of 3 nearby live routes, board next to a vehicle, alight next to one. */
@Composable
private fun TransitFlow(vm: MainViewModel, submit: Runner) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val boarding by vm.boarding.collectAsStateWithLifecycle()
    var routes by remember { mutableStateOf<List<JSONObject>?>(null) }
    var here by remember { mutableStateOf<Location?>(null) }
    var outside by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val locate: suspend () -> Location = { OnDevice.currentLocation(ctx) ?: throw ApiException("no_location", 0) }

    val b = boarding
    if (b != null) {
        Text(L("🚋 Ești în linia ${b.routeName}. Apasă când cobori.", "🚋 You're on line ${b.routeName}. Tap when you get off.").t(), fontWeight = FontWeight.Bold)
        Button(onClick = { submit { val l = locate(); vm.alight(l.latitude, l.longitude) } }, Modifier.fillMaxWidth()) {
            Text(L("Am coborât", "I got off").t())
        }
        TextButton(onClick = { vm.boarding.value = null }) { Text(L("Anulează cursa", "Cancel ride").t()) }
        return
    }
    Button(enabled = !loading, onClick = {
        loading = true
        error = null
        scope.launch {
            runCatching {
                val l = locate()
                val r = vm.nearbyRoutes(l.latitude, l.longitude)
                here = l
                outside = !r.getBoolean("inIasi")
                routes = Json.objects(r.getJSONArray("routes"))
            }.onFailure { error = it }
            loading = false
        }
    }, modifier = Modifier.fillMaxWidth()) { Text(L("🔎 Caută linii CTP lângă mine", "🔎 Find CTP lines near me").t()) }
    if (outside) {
        Text(L("Validarea transportului public e disponibilă momentan doar în Iași (CTP).", "Public transport validation is currently available only in Iași (CTP).").t())
    }
    val l = here
    routes?.forEach { r ->
        val name = r.getString("shortName")
        OutlinedButton(onClick = {
            if (l == null) return@OutlinedButton
            scope.launch { runCatching { vm.board(r.get("routeId"), name, l.latitude, l.longitude) }.onFailure { error = it } }
        }, Modifier.fillMaxWidth()) {
            Text("$name · ${r.optString("longName")} · ${r.getInt("nearestVehicleM")} m")
        }
    }
    if (routes?.isEmpty() == true && !outside) Text(L("Niciun vehicul CTP activ în apropiere.", "No active CTP vehicle nearby.").t())
    error?.let { ErrorLine(it) }
}
