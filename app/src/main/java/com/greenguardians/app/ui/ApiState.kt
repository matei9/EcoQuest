package com.greenguardians.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import com.greenguardians.app.data.ApiException
import com.greenguardians.app.model.L
import kotlinx.coroutines.CancellationException

/** Loads once per key set; null while loading. */
@Composable
fun <T> rememberApi(vararg keys: Any?, load: suspend () -> T): State<Result<T>?> =
    // A changed request must not expose the previous request's result, even for one frame.
    key(keys.toList()) {
        produceState<Result<T>?>(null) {
            value = try {
                Result.success(load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

private val errors = mapOf(
    "tree_server_outdated" to L("Actualizează backendul pentru grădina copacului și clasamentul de înălțime.", "Update the backend to enable the tree garden and height leaderboard."),
    "quest_not_assigned" to L("Misiunea nu face parte din selecția de azi. Actualizează misiunile.", "This quest is not assigned today. Refresh your quests."),
    "quest_not_available" to L("Misiunea nu este disponibilă pentru localitatea și transportul ales.", "This quest is unavailable for your locality and transport settings."),
    "invalid_context" to L("Alege localitatea și accesul la transport public.", "Choose your locality and public transport access."),
    "duel_busy" to L("Tu sau adversarul aveți deja un duel activ. Așteaptă finalizarea lui.", "You or your opponent already have an active duel. Wait for it to finish."),
    "duel_invite_exists" to L("Există deja o invitație între voi. Verifică Ligi → 1v1.", "An invitation already exists between you. Check Leagues → 1v1."),
    "duel_invite_expired" to L("Invitația nu mai este disponibilă. Actualizează duelurile.", "This invitation is no longer available. Refresh duels."),
    "unknown_duel" to L("Duelul nu există sau nu îți aparține.", "Duel not found or you are not a participant."),
    "network" to L("Fără conexiune la server. Încearcă din nou.", "No connection to the server. Try again."),
    "bad_credentials" to L("Email sau parolă greșită.", "Wrong email or password."),
    "email_taken" to L("Există deja un cont cu acest email.", "An account with this email already exists."),
    "weak_password" to L("Parola trebuie să aibă minimum 8 caractere.", "Password must have at least 8 characters."),
    "invalid_email" to L("Email invalid.", "Invalid email."),
    "invalid_name" to L("Nume invalid (2–40 caractere).", "Invalid name (2–40 characters)."),
    "invalid_ref_code" to L("Cod de invitație inexistent.", "Unknown invite code."),
    "already_done" to L("Misiunea e deja finalizată în perioada curentă.", "Quest already completed this period."),
    "locked" to L("Finalizează mai întâi pasul anterior.", "Finish the previous step first."),
    "proof_already_used" to L("Această dovadă sau un bon cu aceeași dată și oră a mai fost folosit.", "This proof or a receipt with the same date and time was already used."),
    "receipt_already_used" to L("Bonul sau un bon cu aceeași dată și oră a fost deja acceptat. Progresul nu se acordă de două ori.", "This receipt or one with the same date and time was already accepted. Progress cannot be credited twice."),
    "receipt_count_invalid" to L("Lasă Ambalaje gol sau introdu un număr între 1 și 200.", "Leave Packages empty or enter a number from 1 to 200."),
    "labels_mismatch" to L("Poza nu pare să arate ce cere misiunea.", "The photo doesn't seem to show what the quest asks."),
    "not_sgr_receipt" to L("Serverul folosește verificarea veche pentru bonuri. Actualizează backendul.", "The server uses the old receipt validation. Update the backend."),
    "receipt_datetime_missing" to L("Nu am putut citi o dată și o oră valide. Include și partea de jos a bonului într-o poză clară.", "Couldn't read a valid date and time. Include the bottom of the receipt in a clear photo."),
    "receipt_server_outdated" to L("Serverul API trebuie actualizat și repornit pentru citirea bonurilor pe server.", "Update and restart the API server to enable receipt OCR."),
    "receipt_upgrade_required" to L("Actualizează aplicația pentru a trimite fotografia bonului către server.", "Update the app to upload receipt photos to the server."),
    "receipt_ocr_unavailable" to L("OCR-ul nu este instalat complet pe server. Rulează instalarea backendului cu dependențele OCR.", "Server OCR is not fully installed. Run the backend installer with its OCR dependencies."),
    "receipt_ocr_busy" to L("Serverul citește alt bon. Încearcă din nou în câteva secunde.", "The server is reading another receipt. Try again in a few seconds."),
    "receipt_ocr_failed" to L("Serverul nu a putut citi imaginea. Încearcă o fotografie clară a bonului.", "The server couldn't read the image. Try a clear receipt photo."),
    "receipt_image_invalid" to L("Imaginea nu a putut fi citită. Alege o fotografie JPG sau PNG clară.", "Couldn't read the image. Choose a clear JPG or PNG photo."),
    "too_large" to L("Imaginea este prea mare. Alege o fotografie mai mică.", "The image is too large. Choose a smaller photo."),
    "receipt_count_missing" to L("Data și ora sunt valide, dar nu am citit numărul de ambalaje. Completează câmpul Ambalaje (1–200) și alege poza din nou.", "The date and time are valid, but the package count wasn't readable. Enter Packages (1–200) and select the photo again."),
    "camera_unavailable" to L("Nu am putut deschide camera. Poți alege o poză din galerie.", "Couldn't open the camera. You can select a photo from the gallery."),
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
    "friend_self" to L("Acesta este codul tău. Introdu codul unui prieten.", "This is your code. Enter a friend's code."),
    "friend_exists" to L("Sunteți deja prieteni sau există o cerere. Actualizează lista.", "Already friends or a request exists. Refresh the list."),
    "unknown_request" to L("Cererea nu mai este disponibilă. Actualizează lista.", "This request is no longer available. Refresh the list."),
    "not_friends" to L("Cererea de prietenie trebuie acceptată mai întâi.", "The friend request must be accepted first."),
    "no_team" to L("Creează sau intră într-o echipă mai întâi.", "Create or join a team first."),
    "team_already_joined" to L("Persoana are deja o echipă. Trebuie să o părăsească mai întâi.", "This person already has a team and must leave it first."),
    "invite_exists" to L("Există deja o invitație pentru această echipă.", "An invitation to this team already exists."),
    "invite_expired" to L("Invitația nu mai este valabilă. Cere una nouă.", "This invitation is no longer valid. Ask for a new one."),
    "invalid_user" to L("Utilizator invalid.", "Invalid user."),
    "trivia_done_today" to L("Ai dat deja quiz-ul azi. Revino mâine!", "You already took today's quiz. Come back tomorrow!"),
    "bad_trivia_token" to L("Quiz-ul a expirat. Deschide-l din nou.", "The quiz expired. Open it again."),
    "no_location" to L("Nu am putut obține locația GPS. Activează locația.", "Couldn't get a GPS fix. Turn on location."),
    "gps_failed" to L("Înregistrarea GPS s-a oprit. Verifică permisiunea și locația; traseul colectat este păstrat.", "GPS recording stopped. Check location and permission; recorded points are saved."),
    "track_limit" to L("Traseul a atins limita de puncte. Trimite traseul păstrat și începe unul nou.", "Trip point limit reached. Submit the saved trip and start a new one."),
    "bad_trip_id" to L("Identificatorul traseului este invalid.", "Invalid trip identifier."),
    "bad_points" to L("Nu există suficiente puncte GPS valide. Traseul este păstrat.", "Not enough valid GPS points. Your trip is saved."),
    "bad_timestamps" to L("Orele punctelor GPS nu sunt valide. Traseul este păstrat.", "GPS point timestamps are invalid. Your trip is saved."),
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
