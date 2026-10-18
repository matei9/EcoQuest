package com.greenguardians.app.data

import android.content.Context
import android.location.Location
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class TripPoint(val lat: Double, val lon: Double, val time: Long)

data class TripState(
    val ownerId: Int = 0,
    val id: String = "",
    val mode: String = "walk",
    val points: List<TripPoint> = emptyList(),
    val km: Double = 0.0,
    val running: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val hasDraft get() = id.isNotEmpty()
    val minutes get() = if (points.size > 1) (points.last().time - points.first().time) / 60_000 else 0L
    fun payload() = JSONArray(points.map { JSONObject().put("lat", it.lat).put("lon", it.lon).put("t", it.time) })
}

/** One recording at a time, independent of the quest sheet. Drafts are private to each account. */
class TripStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("gps_trip_drafts", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(TripState())
    val state = mutable.asStateFlow()

    fun bindOwner(ownerId: Int) {
        if (mutable.value.ownerId == ownerId) return
        val restored = runCatching {
            val o = JSONObject(prefs.getString("draft_$ownerId", null) ?: return@runCatching null)
            val a = o.getJSONArray("points")
            TripState(ownerId, o.getString("id"), o.getString("mode"), List(a.length()) { i ->
                val p = a.getJSONObject(i)
                TripPoint(p.getDouble("lat"), p.getDouble("lon"), p.getLong("t"))
            }, o.getDouble("km"))
        }.getOrNull()
        // After process death the saved route is stopped, never joined to an unrecorded gap.
        mutable.value = restored ?: TripState(ownerId = ownerId)
    }

    fun begin(mode: String): String {
        check(mutable.value.ownerId > 0 && mode in listOf("walk", "cycle"))
        check(!mutable.value.hasDraft && !mutable.value.submitting)
        mutable.value = TripState(ownerId = mutable.value.ownerId, id = UUID.randomUUID().toString(), mode = mode, running = true)
        persist()
        return mutable.value.id
    }

    fun append(location: Location) {
        val s = mutable.value
        if (!s.running || s.submitting) return
        if (s.points.size >= 20_000) {
            pause("track_limit")
            return
        }
        val last = s.points.lastOrNull()
        if (last != null && location.time <= last.time) return
        val distance = FloatArray(1)
        if (last != null) Location.distanceBetween(last.lat, last.lon, location.latitude, location.longitude, distance)
        mutable.value = s.copy(
            points = s.points + TripPoint(location.latitude, location.longitude, location.time),
            km = s.km + distance[0] / 1000.0,
        )
        persist()
    }

    fun pause(error: String? = null) {
        mutable.value = mutable.value.copy(running = false, error = error ?: mutable.value.error)
        persist()
    }

    fun setSubmitting(value: Boolean) { mutable.value = mutable.value.copy(submitting = value) }

    fun clear(id: String = mutable.value.id, ownerId: Int = mutable.value.ownerId) {
        if (mutable.value.ownerId == ownerId) {
            if (mutable.value.id != id) return
            prefs.edit().remove("draft_$ownerId").apply()
            mutable.value = TripState(ownerId = ownerId)
        } else {
            val storedId = runCatching { JSONObject(prefs.getString("draft_$ownerId", null) ?: "{}").optString("id") }.getOrNull()
            if (storedId == id) prefs.edit().remove("draft_$ownerId").apply()
        }
    }

    private fun persist() {
        val s = mutable.value
        if (!s.hasDraft) return
        prefs.edit().putString("draft_${s.ownerId}", JSONObject().put("id", s.id).put("mode", s.mode)
            .put("km", s.km).put("points", s.payload()).toString()).apply()
    }

    companion object {
        @Volatile private var instance: TripStore? = null
        fun get(context: Context): TripStore = instance ?: synchronized(this) {
            instance ?: TripStore(context.applicationContext).also { instance = it }
        }
    }
}
