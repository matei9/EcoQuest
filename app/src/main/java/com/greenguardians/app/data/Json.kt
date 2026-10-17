package com.greenguardians.app.data

import com.greenguardians.app.model.AppConfig
import com.greenguardians.app.model.Me
import com.greenguardians.app.model.UserInfo
import com.greenguardians.app.model.UserProgress
import org.json.JSONArray
import org.json.JSONObject

/** Parsers for server JSON (shapes defined in server/src/progress.js `me()`). */
object Json {
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
    private fun JSONObject.optStr(k: String) = if (isNull(k)) null else optString(k)

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
                school = u.optStr("school"),
                teamId = if (u.isNull("teamId")) null else u.getInt("teamId"),
                refCode = u.getString("refCode"),
            ),
            progress = UserProgress(
                quizDone = p.getBoolean("quizDone"),
                categoryScores = (0 until cats.length()).map { cats.getInt(it) },
                initialSelfDeclaredEP = p.getInt("initialSelfDeclaredEP"),
                verifiedQuestEP = p.getInt("verifiedQuestEP"),
                verifiedCO2SavedKg = p.getDouble("verifiedCO2SavedKg"),
                ecoCoins = p.getInt("ecoCoins"),
                sgrReturned = p.getInt("sgrReturned"),
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
        schools = o.getJSONArray("schools").strings(),
    )

    fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).map { a.getJSONObject(it) }

    /** {"ro": "...", "en": "..."} -> L */
    fun l(o: JSONObject) = com.greenguardians.app.model.L(o.getString("ro"), o.getString("en"))
}
