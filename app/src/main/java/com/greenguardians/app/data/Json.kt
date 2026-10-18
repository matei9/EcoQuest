package com.greenguardians.app.data

import com.greenguardians.app.model.AppConfig
import com.greenguardians.app.model.Me
import com.greenguardians.app.model.UserInfo
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.model.GuardianTree
import org.json.JSONArray
import org.json.JSONObject

/** Parsers for server JSON (shapes defined in server/src/progress.js `me()`). */
object Json {
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    fun me(o: JSONObject): Me {
        val u = o.getJSONObject("user")
        val p = o.getJSONObject("progress")
        val qp = p.getJSONObject("questProgress")
        val cats = p.getJSONArray("categoryScores")
        return Me(
            user = UserInfo(
                id = u.getInt("id"),
                email = u.getString("email"),
                name = u.getString("name"),
                city = u.getString("city"),
                teamId = if (u.isNull("teamId")) null else u.getInt("teamId"),
                refCode = u.getString("refCode"),
                locality = u.optString("locality", "urban"),
                publicTransport = u.optBoolean("publicTransport"),
            ),
            progress = UserProgress(
                quizDone = p.getBoolean("quizDone"),
                categoryScores = (0 until cats.length()).map { cats.getInt(it) },
                initialSelfDeclaredEP = p.getInt("initialSelfDeclaredEP"),
                verifiedQuestEP = p.getInt("verifiedQuestEP"),
                verifiedCO2SavedKg = p.getDouble("verifiedCO2SavedKg"),
                ecoCoins = p.getInt("ecoCoins"),
                sgrReturned = p.getInt("sgrReturned"),
                dailyQuestIds = p.optJSONArray("dailyQuestIds")?.strings() ?: emptyList(),
                rankedWins = p.optJSONObject("duelStats")?.optInt("wins") ?: 0,
                rankedPlayed = p.optJSONObject("duelStats")?.optInt("played") ?: 0,
                rankedDraws = p.optJSONObject("duelStats")?.optInt("draws") ?: 0,
                duelPoints = p.optJSONObject("duelStats")?.optInt("points") ?: 0,
                tree = p.optJSONObject("tree")?.let(::tree),
                completedKeys = p.getJSONArray("completedKeys").strings().toSet(),
                activeDays = p.getJSONArray("activeDays").strings().toSet(),
                questProgress = qp.keys().asSequence().associateWith {
                    val a = qp.getJSONArray(it)
                    a.getDouble(0) to a.getDouble(1)
                },
            ),
        )
    }

    fun config(o: JSONObject) = AppConfig(
        demoMode = o.optBoolean("demoMode"),
        aiEnabled = o.optBoolean("aiEnabled"),
        transitEnabled = o.optBoolean("transitEnabled"),
        cities = o.getJSONArray("cities").strings(),
    )

    fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).map { a.getJSONObject(it) }

    fun tree(o: JSONObject) = GuardianTree(
        stage = o.getInt("stage"), stageDays = o.getInt("stageDays"), heightMeters = o.getDouble("heightMeters"),
        streak = o.getInt("streak"), missedDays = o.getInt("missedDays"), activeDays = o.getInt("activeDays"),
        activeToday = o.getBoolean("activeToday"), health = o.getString("health"),
        daysToNextStage = o.getInt("daysToNextStage"), mature = o.getBoolean("mature"),
        topOnly = o.getBoolean("topOnly"), serverDay = o.getString("serverDay"),
    )

    /** {"ro": "...", "en": "..."} -> L */
    fun l(o: JSONObject) = com.greenguardians.app.model.L(o.getString("ro"), o.getString("en"))
}
