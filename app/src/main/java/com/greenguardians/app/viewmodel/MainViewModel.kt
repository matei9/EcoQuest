package com.greenguardians.app.viewmodel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.greenguardians.app.BuildConfig
import com.greenguardians.app.data.ApiClient
import com.greenguardians.app.data.ApiException
import com.greenguardians.app.data.Json
import com.greenguardians.app.data.PrefsRepository
import com.greenguardians.app.model.AppConfig
import com.greenguardians.app.model.Session
import com.greenguardians.app.model.UserProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Active CTP ride: issued by the server when a vehicle of the route was next to the user. */
data class Boarding(val token: String, val routeName: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PrefsRepository(app)

    val session: StateFlow<Session> = repo.session.stateIn(viewModelScope, SharingStarted.Eagerly, Session())
    val progress: StateFlow<UserProgress> =
        session.map { it.me?.progress ?: UserProgress() }.stateIn(viewModelScope, SharingStarted.Eagerly, UserProgress())
    val darkMode: StateFlow<Boolean?> = repo.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
            session.first { it.loaded }
            runCatching { config.value = Json.config(api.get("/api/config")) }
            refresh()
        }
    }

    fun setDarkMode(dark: Boolean) {
        viewModelScope.launch { repo.setDarkMode(dark) }
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
        viewModelScope.launch { repo.clearSession() }
    }

    suspend fun deleteAccount() {
        api.call("DELETE", "/api/me")
        repo.clearSession()
    }

    suspend fun submitQuiz(answers: List<Int>) {
        mutate("POST", "/api/quiz", JSONObject().put("answers", JSONArray(answers)))
        retakeQuiz.value = false
    }

    /** Returns quest ids awarded (the quest plus any auto quests it unlocked). */
    suspend fun completeQuest(id: String, proof: JSONObject): List<String> =
        awarded(mutate("POST", "/api/quests/$id/complete", JSONObject().put("proof", proof)))

    suspend fun submitReceipt(text: String, imageHash: String): List<String> =
        awarded(mutate("POST", "/api/receipts", JSONObject().put("text", text).put("imageHash", imageHash)))

    suspend fun submitTrack(mode: String, points: JSONArray): List<String> =
        awarded(mutate("POST", "/api/trips", JSONObject().put("mode", mode).put("points", points)))

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
