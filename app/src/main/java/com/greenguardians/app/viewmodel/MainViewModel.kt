package com.greenguardians.app.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.greenguardians.app.BuildConfig
import com.greenguardians.app.data.ApiClient
import com.greenguardians.app.data.ApiException
import com.greenguardians.app.data.Json
import com.greenguardians.app.data.PrefsRepository
import com.greenguardians.app.data.OnDevice
import com.greenguardians.app.data.TripStore
import com.greenguardians.app.data.TripTrackingService
import com.greenguardians.app.model.AppConfig
import com.greenguardians.app.model.Session
import com.greenguardians.app.model.UserProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import org.json.JSONArray
import org.json.JSONObject
import com.greenguardians.app.model.AiConversation
import com.greenguardians.app.model.AiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

/** Active CTP ride: issued by the server when a vehicle of the route was next to the user. */
data class Boarding(val token: String, val routeName: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PrefsRepository(app)
    private val trips = TripStore.get(app)
    val trip = trips.state

    val session: StateFlow<Session> = repo.session.stateIn(viewModelScope, SharingStarted.Eagerly, Session())
    val progress: StateFlow<UserProgress> =
        session.map { it.me?.progress ?: UserProgress() }.stateIn(viewModelScope, SharingStarted.Eagerly, UserProgress())
    val darkMode: StateFlow<Boolean?> = repo.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val monetColors = repo.monetColors.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _conversation = MutableStateFlow(AiConversation())
    val conversation: StateFlow<AiConversation> = _conversation
    private var chatJob: Job? = null
    private var chatGeneration = 0L

    val config = MutableStateFlow(AppConfig())
    val boarding = MutableStateFlow<Boarding?>(null)
    /** Home fun fact: loaded once per app start, "next" on demand (the server marks it as seen). */
    val fact = MutableStateFlow<JSONObject?>(null)

    fun nextFact() {
        viewModelScope.launch { runCatching { fact.value = api.get("/api/facts/next") } }
    }

    /** Profile "retake quiz" shows the quiz again until it is resubmitted. */
    val retakeQuiz = MutableStateFlow(false)

    val api = ApiClient(BuildConfig.API_URL) { session.value.token }

    init {
        viewModelScope.launch {
            session.map { if (it.loaded) it.me?.user?.id ?: 0 else null }.distinctUntilChanged().collect { owner ->
                if (owner != null && owner != trip.value.ownerId) {
                    stopTrip()
                    trips.bindOwner(owner)
                }
                if (owner != null && owner != _conversation.value.ownerId) clearConversation(owner.takeIf { it > 0 })
            }
        }
        viewModelScope.launch {
            session.first { it.loaded }
            runCatching { config.value = Json.config(api.get("/api/config")) }
            refresh()
        }
    }

    fun setDarkMode(dark: Boolean) {
        viewModelScope.launch { repo.setDarkMode(dark) }
    }
    fun setMonetColors(enabled: Boolean) {
        viewModelScope.launch { repo.setMonetColors(enabled) }
    }

    private fun clearConversation(owner: Int? = null) {
        chatGeneration++
        chatJob?.cancel()
        chatJob = null
        _conversation.value = AiConversation(ownerId = owner)
    }

    fun setAiDraft(text: String) {
        val owner = session.value.me?.user?.id ?: return
        if (_conversation.value.ownerId != owner) clearConversation(owner)
        _conversation.value = _conversation.value.copy(draft = text.take(4000))
    }

    /** Session memory belongs to the activity ViewModel, not the navigation destination. */
    fun sendAi(text: String, en: Boolean, fallback: String) {
        val owner = session.value.me?.user?.id ?: return
        if (_conversation.value.ownerId != owner) clearConversation(owner)
        val current = _conversation.value
        if (text.isBlank() || current.busy) return
        val messages = current.messages + AiMessage(true, text.take(4000))
        val history = messages.takeLast(10).map { it.fromUser to it.text }
        _conversation.value = current.copy(messages = messages + AiMessage(false, ""), draft = "", busy = true, thinking = false)
        val generation = ++chatGeneration
        chatJob = viewModelScope.launch {
            val answer = StringBuilder()
            fun update(text: String) {
                val state = _conversation.value
                if (state.ownerId == owner && generation == chatGeneration) _conversation.value = state.copy(messages = state.messages.dropLast(1) + AiMessage(false, text))
            }
            try {
                aiStream(history, if (en) "en" else "ro").collect { event ->
                    if (_conversation.value.ownerId != owner || generation != chatGeneration) return@collect
                    when(event.optString("type")) {
                        "thinking" -> _conversation.value = _conversation.value.copy(thinking = true)
                        "delta" -> { answer.append(event.getString("text")); update(answer.toString()) }
                        "error" -> throw ApiException(event.optString("error", "ai_failed"), 0)
                    }
                }
                if (answer.isEmpty()) throw ApiException("ai_failed", 0)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                update(if (answer.isEmpty()) fallback + if (en) "\n\n(offline mode)" else "\n\n(mod offline)" else "$answer …")
            } finally {
                if (_conversation.value.ownerId == owner && generation == chatGeneration) _conversation.value = _conversation.value.copy(busy = false, thinking = false)
            }
        }
    }

    /** RO is the default; AppCompat persists the locale and recreates the activity. */
    fun setLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun refresh() {
        if (session.value.token == null) return
        viewModelScope.launch { runCatching { mutate("GET", "/api/me") } }
    }

    /** Calls the API; responses that carry the `me` payload refresh the local cache. 401 logs out. */
    suspend fun mutate(method: String, path: String, body: JSONObject? = null, timeoutMs: Int = 15_000): JSONObject {
        try {
            val r = api.call(method, path, body, timeoutMs)
            if (r.has("progress")) repo.saveMe(r.toString())
            return r
        } catch (e: ApiException) {
            if (e.status == 401) repo.clearSession()
            throw e
        }
    }

    suspend fun signup(email: String, password: String, name: String, city: String, school: String?, refCode: String?) {
        val body = JSONObject()
            .put("email", email).put("password", password).put("name", name).put("city", city)
            .put("school", school ?: JSONObject.NULL)
        if (!refCode.isNullOrBlank()) body.put("refCode", refCode.trim())
        val r = api.post("/api/auth/signup", body)
        repo.saveSession(r.getString("token"), r.toString())
    }

    suspend fun login(email: String, password: String) {
        val r = api.post("/api/auth/login", JSONObject().put("email", email).put("password", password))
        repo.saveSession(r.getString("token"), r.toString())
    }

    fun logout() {
        clearConversation()
        discardTrip()
        viewModelScope.launch { repo.clearSession() }
    }

    suspend fun deleteAccount() {
        api.call("DELETE", "/api/me")
        clearConversation()
        discardTrip()
        repo.clearSession()
    }

    suspend fun submitQuiz(answers: List<Int>, locality: String, publicTransport: Boolean) {
        mutate("POST", "/api/quiz", JSONObject().put("answers", JSONArray(answers))
            .put("locality", locality).put("publicTransport", publicTransport))
        retakeQuiz.value = false
    }

    /** Returns quest ids awarded (the quest plus any auto quests it unlocked). */
    suspend fun completeQuest(id: String, proof: JSONObject): List<String> =
        awarded(mutate("POST", "/api/quests/$id/complete", JSONObject().put("proof", proof)))

    data class ReceiptSubmission(val awarded: List<String>, val count: Int, val day: String, val time: String)

    suspend fun submitReceipt(uri: Uri, count: Int? = null): ReceiptSubmission {
        val serverConfig = api.get("/api/config")
        if (serverConfig.optString("receiptOcr") != "server-v1" || serverConfig.optString("receiptValidation") != "datetime-v2") {
            throw ApiException("receipt_server_outdated", 426)
        }
        try {
            val r = api.uploadReceipt(OnDevice.receiptImage(getApplication(), uri), count)
            repo.saveMe(r.toString())
            return ReceiptSubmission(awarded(r), r.optInt("count"), r.optString("receiptDay"), r.optString("receiptTime"))
        } catch (e: ApiException) {
            if (e.status == 401) repo.clearSession()
            throw e
        }
    }

    fun startTrip(mode: String) {
        val owner = session.value.me?.user?.id ?: return
        if (trip.value.ownerId != owner) stopTrip()
        trips.bindOwner(owner)
        if (trip.value.hasDraft || trip.value.submitting) return
        val id = trips.begin(mode)
        try {
            ContextCompat.startForegroundService(getApplication(), Intent(getApplication(), TripTrackingService::class.java).putExtra("tripId", id))
        } catch (e: Exception) {
            trips.pause("gps_failed")
        }
    }

    fun stopTrip() {
        trips.pause()
        getApplication<Application>().stopService(Intent(getApplication(), TripTrackingService::class.java))
    }

    fun discardTrip() {
        if (trip.value.submitting) return
        stopTrip()
        trips.clear()
    }

    suspend fun submitSavedTrip(): List<String> {
        check(!trip.value.submitting)
        stopTrip()
        val saved = trip.value
        check(saved.hasDraft && saved.ownerId == session.value.me?.user?.id)
        val token = session.value.token ?: error("No active session")
        trips.setSubmitting(true)
        // Sending survives sheet dismissal. A stable clientId also makes retry safe after process death.
        return viewModelScope.async {
            try {
                val client = ApiClient(BuildConfig.API_URL) { token }
                val r = client.post("/api/trips", JSONObject().put("mode", saved.mode)
                    .put("points", saved.payload()).put("clientId", saved.id))
                if (session.value.token == token) repo.saveMe(r.toString())
                trips.clear(saved.id, saved.ownerId)
                awarded(r)
            } catch (e: ApiException) {
                if (e.status == 401 && session.value.token == token) repo.clearSession()
                throw e
            } finally {
                if (trip.value.id == saved.id) trips.setSubmitting(false)
            }
        }.await()
    }

    suspend fun nearbyRoutes(lat: Double, lon: Double): JSONObject =
        api.post("/api/transit/nearby", JSONObject().put("lat", lat).put("lon", lon))

    suspend fun board(routeId: Any, routeName: String, lat: Double, lon: Double) {
        val r = api.post("/api/transit/board", JSONObject().put("routeId", routeId).put("lat", lat).put("lon", lon))
        boarding.value = Boarding(r.getString("boardingToken"), routeName)
    }

    suspend fun alight(lat: Double, lon: Double): List<String> {
        val b = boarding.value ?: return emptyList()
        val end = JSONObject().put("lat", lat).put("lon", lon)
        val r = mutate("POST", "/api/trips", JSONObject().put("mode", "transit").put("boardingToken", b.token).put("end", end))
        boarding.value = null
        return awarded(r)
    }

    suspend fun checkin(code: String): List<String> =
        awarded(mutate("POST", "/api/events/checkin", JSONObject().put("code", code)))

    suspend fun redeem(voucherId: String): String = mutate("POST", "/api/vouchers/$voucherId/redeem").getString("code")

    /** Guardian AI answer as SSE events: {type: thinking | delta(text) | done | error(error)}. */
    fun aiStream(history: List<Pair<Boolean, String>>, lang: String): Flow<JSONObject> {
        val msgs = JSONArray()
        history.filter { it.second.isNotBlank() }.takeLast(10).forEach { (fromUser, text) ->
            msgs.put(JSONObject().put("role", if (fromUser) "user" else "assistant").put("content", text.take(1000)))
        }
        return api.stream("/api/ai/chat/stream", JSONObject().put("messages", msgs).put("lang", lang))
    }

    private fun awarded(r: JSONObject): List<String> =
        r.optJSONArray("awarded")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
}
