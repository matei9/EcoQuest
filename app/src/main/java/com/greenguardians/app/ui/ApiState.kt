package com.greenguardians.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.greenguardians.app.data.ApiException
import com.greenguardians.app.model.L

/** Loads once per key set; null while loading. */
@Composable
fun <T> rememberApi(vararg keys: Any?, load: suspend () -> T): State<Result<T>?> =
    produceState<Result<T>?>(null, *keys) { value = runCatching { load() } }

private val errors = mapOf(
    "network" to L("Fără conexiune la server. Încearcă din nou.", "No connection to the server. Try again."),
    "bad_credentials" to L("Email sau parolă greșită.", "Wrong email or password."),
    "email_taken" to L("Există deja un cont cu acest email.", "An account with this email already exists."),
    "weak_password" to L("Parola trebuie să aibă minimum 8 caractere.", "Password must have at least 8 characters."),
    "invalid_email" to L("Email invalid.", "Invalid email."),
    "invalid_name" to L("Nume invalid (2–40 caractere).", "Invalid name (2–40 characters)."),
    "invalid_ref_code" to L("Cod de invitație inexistent.", "Unknown invite code."),
    "already_done" to L("Misiunea e deja finalizată în perioada curentă.", "Quest already completed this period."),
    "locked" to L("Finalizează mai întâi pasul anterior.", "Finish the previous step first."),
    "proof_already_used" to L("Această dovadă a mai fost folosită.", "This proof was already used."),
    "labels_mismatch" to L("Poza nu pare să arate ce cere misiunea.", "The photo doesn't seem to show what the quest asks."),
    "not_sgr_receipt" to L("Nu am găsit ambalaje SGR pe bon.", "No SGR packages found on the receipt."),
    "ocr_mismatch" to L("Textul documentului nu corespunde misiunii.", "The document text doesn't match the quest."),
    "too_fast" to L("Viteza indică deplasare cu mașina — traseu respins.", "Speed suggests a car trip — route rejected."),
    "too_short" to L("Traseu prea scurt.", "Route too short."),
    "no_vehicle_nearby" to L("Niciun vehicul CTP al liniei nu e lângă tine acum.", "No vehicle of this CTP line is near you right now."),
    "outside_iasi" to L("Validarea CTP funcționează doar în Iași.", "CTP validation only works in Iași."),
    "transit_unavailable" to L("Serviciul CTP Iași nu e disponibil acum.", "CTP Iași service is unavailable right now."),
    "demo_disabled" to L("Modul demo e dezactivat pe server.", "Demo mode is disabled on the server."),
    "unknown_event" to L("Cod QR necunoscut sau expirat.", "Unknown or expired QR code."),
    "already_checked_in" to L("Ai fost deja înregistrat la acest eveniment.", "Already checked in to this event."),
    "not_enough_coins" to L("Nu ai destule EcoCoins.", "Not enough EcoCoins."),
    "team_exists" to L("Există deja o echipă cu acest nume.", "A team with this name already exists."),
    "trivia_done_today" to L("Ai dat deja quiz-ul azi. Revino mâine!", "You already took today's quiz. Come back tomorrow!"),
    "bad_trivia_token" to L("Quiz-ul a expirat. Deschide-l din nou.", "The quiz expired. Open it again."),
    "no_location" to L("Nu am putut obține locația GPS. Activează locația.", "Couldn't get a GPS fix. Turn on location."),
    "ai_unavailable" to L("Guardian AI nu e configurat pe server.", "Guardian AI is not configured on the server."),
)

@Composable
fun errorText(e: Throwable): String {
    val api = e as? ApiException ?: return L("Eroare: ", "Error: ").t() + (e.message ?: "?")
    val text = errors[api.code]?.t() ?: (L("Eroare: ", "Error: ").t() + api.code)
    return if (api.detail != null) "$text\n(${api.detail})" else text
}

@Composable
fun ErrorLine(e: Throwable) {
    Text(errorText(e), color = MaterialTheme.colorScheme.error)
}
