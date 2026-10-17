package com.greenguardians.app.data

import com.greenguardians.app.model.Category
import com.greenguardians.app.model.Category.COMMUNITY
import com.greenguardians.app.model.Category.ENERGY
import com.greenguardians.app.model.Category.FOOD
import com.greenguardians.app.model.Category.PROGRESS
import com.greenguardians.app.model.Category.REUSE
import com.greenguardians.app.model.Category.SGR
import com.greenguardians.app.model.Category.TRANSPORT
import com.greenguardians.app.model.Difficulty
import com.greenguardians.app.model.Difficulty.EASY
import com.greenguardians.app.model.Difficulty.HARD
import com.greenguardians.app.model.Difficulty.MEDIUM
import com.greenguardians.app.model.L
import com.greenguardians.app.model.Quest
import com.greenguardians.app.model.QuestKind
import com.greenguardians.app.model.QuestKind.DAILY
import com.greenguardians.app.model.QuestKind.MONTHLY
import com.greenguardians.app.model.QuestKind.WEEKLY
import com.greenguardians.app.model.UserProgress
import com.greenguardians.app.model.Verification
import com.greenguardians.app.model.Verification.AUTO
import com.greenguardians.app.model.Verification.BEFORE_AFTER
import com.greenguardians.app.model.Verification.BILL
import com.greenguardians.app.model.Verification.CONFIRM
import com.greenguardians.app.model.Verification.GPS
import com.greenguardians.app.model.Verification.JOURNAL
import com.greenguardians.app.model.Verification.PHOTO
import com.greenguardians.app.model.Verification.PHOTO_AI
import com.greenguardians.app.model.Verification.QR_PHOTO
import com.greenguardians.app.model.Verification.RECEIPT_AI
import com.greenguardians.app.model.Verification.TIMER
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * Display catalog (titles RO/EN). Rewards are decided by the server catalog
 * (server/src/catalog.js): keep ids, EP and CO2 in sync with it.
 */
object QuestRepository {
    private fun q(
        id: String, kind: QuestKind, ro: String, en: String, d: Difficulty, ep: Int,
        v: Verification, co2: Double, cat: Category, sgr: Int = 0, km: Double = 3.0,
        autoOf: QuestKind? = null, autoTarget: Int = 0,
    ) = Quest(id, kind, L(ro, en), d, ep, v, co2, cat, sgr, km, autoOf, autoTarget)

    val daily = listOf(
        q("d1", DAILY, "Mergi 1 km pe jos în locul unui drum cu mașina", "Walk 1 km instead of driving", EASY, 25, GPS, 0.20, TRANSPORT, km = 1.0),
        q("d2", DAILY, "Mergi 3 km pe jos în locul mașinii", "Walk 3 km instead of driving", MEDIUM, 50, GPS, 0.60, TRANSPORT, km = 3.0),
        q("d3", DAILY, "Parcurge 10 km cu bicicleta în locul mașinii", "Cycle 10 km instead of driving", HARD, 100, GPS, 2.00, TRANSPORT, km = 10.0),
        q("d4", DAILY, "Folosește transportul public pentru o deplasare", "Use public transport for a trip", EASY, 25, GPS, 0.80, TRANSPORT),
        q("d5", DAILY, "Returnează 3 ambalaje SGR", "Return 3 SGR deposit packages", EASY, 25, RECEIPT_AI, 0.15, SGR, sgr = 3),
        q("d6", DAILY, "Returnează 10 ambalaje SGR", "Return 10 SGR deposit packages", MEDIUM, 50, RECEIPT_AI, 0.50, SGR, sgr = 10),
        q("d7", DAILY, "Returnează 20 de ambalaje SGR", "Return 20 SGR deposit packages", HARD, 100, RECEIPT_AI, 1.00, SGR, sgr = 20),
        q("d8", DAILY, "Folosește o sticlă reutilizabilă în locul uneia de unică folosință", "Use a reusable bottle", EASY, 25, CONFIRM, 0.10, REUSE),
        q("d9", DAILY, "Redu durata unui duș obișnuit cu 3 minute", "Reduce your usual shower time by 3 minutes", EASY, 25, TIMER, 0.45, ENERGY),
        q("d10", DAILY, "Stinge luminile în camerele nefolosite", "Turn off lights in unused rooms", EASY, 25, CONFIRM, 0.15, ENERGY),
        q("d11", DAILY, "Evită risipa unei porții de mâncare", "Avoid wasting a portion of food", EASY, 25, PHOTO, 0.90, FOOD),
        q("d12", DAILY, "Pregătește o masă folosind alimente care riscau să fie aruncate", "Cook a meal using food at risk of being wasted", MEDIUM, 50, PHOTO_AI, 1.50, FOOD),
        q("d13", DAILY, "Folosește o sacoșă reutilizabilă la cumpărături", "Use a reusable shopping bag", EASY, 25, CONFIRM, 0.08, REUSE),
        q("d14", DAILY, "Înlocuiește o deplasare auto de minimum 5 km cu transport public", "Replace a 5+ km car trip with public transport", MEDIUM, 50, GPS, 0.75, TRANSPORT, km = 5.0),
        q("d15", DAILY, "Petrece o zi fără deplasări cu mașina pe care le-ai fi făcut în mod normal", "Spend a car-free day", HARD, 100, JOURNAL, 3.20, TRANSPORT),
    )

    val weekly = listOf(
        q("w1a", WEEKLY, "Mergi 10 km pe jos în locul mașinii", "Walk 10 km instead of driving", EASY, 100, GPS, 2.0, TRANSPORT),
        q("w1b", WEEKLY, "Mergi 25 km pe jos în locul mașinii", "Walk 25 km instead of driving", MEDIUM, 200, GPS, 5.0, TRANSPORT),
        q("w1c", WEEKLY, "Mergi 50 km pe jos în locul mașinii", "Walk 50 km instead of driving", HARD, 350, GPS, 10.0, TRANSPORT),
        q("w2a", WEEKLY, "Folosește transportul public de 3 ori", "Use public transport 3 times", EASY, 100, GPS, 2.4, TRANSPORT),
        q("w2b", WEEKLY, "Folosește transportul public de 10 ori", "Use public transport 10 times", MEDIUM, 200, GPS, 8.0, TRANSPORT),
        q("w2c", WEEKLY, "Folosește transportul public în 5 zile diferite", "Use public transport on 5 different days", MEDIUM, 200, GPS, 5.0, TRANSPORT),
        q("w3a", WEEKLY, "Returnează 20 de ambalaje SGR", "Return 20 SGR packages", EASY, 100, RECEIPT_AI, 1.0, SGR, sgr = 20),
        q("w3b", WEEKLY, "Returnează 50 de ambalaje SGR", "Return 50 SGR packages", MEDIUM, 200, RECEIPT_AI, 2.5, SGR, sgr = 50),
        q("w3c", WEEKLY, "Returnează 100 de ambalaje SGR", "Return 100 SGR packages", HARD, 350, RECEIPT_AI, 5.0, SGR, sgr = 100),
        q("w4", WEEKLY, "Evită risipa alimentară în 3 zile", "Avoid food waste on 3 days", EASY, 100, JOURNAL, 2.70, FOOD),
        q("w5", WEEKLY, "Prepară 3 mese folosind resturi alimentare", "Cook 3 meals using food leftovers", MEDIUM, 200, PHOTO_AI, 4.50, FOOD),
        q("w6", WEEKLY, "Folosește o sticlă reutilizabilă în 5 zile", "Use a reusable bottle on 5 days", EASY, 100, CONFIRM, 0.50, REUSE),
        q("w7a", WEEKLY, "Mergi cu bicicleta minimum 30 km în locul mașinii", "Cycle at least 30 km instead of driving", MEDIUM, 200, GPS, 6.0, TRANSPORT),
        q("w7b", WEEKLY, "Mergi cu bicicleta minimum 70 km în locul mașinii", "Cycle at least 70 km instead of driving", HARD, 350, GPS, 14.0, TRANSPORT),
        q("w8a", WEEKLY, "Realizează 5 misiuni zilnice în 7 zile", "Complete 5 daily quests in 7 days", EASY, 100, AUTO, 0.0, PROGRESS, autoOf = DAILY, autoTarget = 5),
        q("w8b", WEEKLY, "Realizează 12 misiuni zilnice într-o săptămână", "Complete 12 daily quests in a week", HARD, 350, AUTO, 0.0, PROGRESS, autoOf = DAILY, autoTarget = 12),
        q("w9", WEEKLY, "Participă la o activitate organizată de curățenie în comunitate", "Join an organized community clean-up", MEDIUM, 200, QR_PHOTO, 3.00, COMMUNITY),
    )

    val monthly = listOf(
        q("m1a", MONTHLY, "Finalizează 20 de misiuni zilnice", "Complete 20 daily quests", EASY, 300, AUTO, 0.0, PROGRESS, autoOf = DAILY, autoTarget = 20),
        q("m1b", MONTHLY, "Finalizează 50 de misiuni zilnice", "Complete 50 daily quests", MEDIUM, 600, AUTO, 0.0, PROGRESS, autoOf = DAILY, autoTarget = 50),
        q("m1c", MONTHLY, "Finalizează 100 de misiuni zilnice", "Complete 100 daily quests", HARD, 1000, AUTO, 0.0, PROGRESS, autoOf = DAILY, autoTarget = 100),
        q("m2a", MONTHLY, "Returnează 100 de ambalaje SGR", "Return 100 SGR packages", EASY, 300, RECEIPT_AI, 5.0, SGR, sgr = 100),
        q("m2b", MONTHLY, "Returnează 250 de ambalaje SGR", "Return 250 SGR packages", MEDIUM, 600, RECEIPT_AI, 12.5, SGR, sgr = 250),
        q("m2c", MONTHLY, "Returnează 500 de ambalaje SGR", "Return 500 SGR packages", HARD, 1000, RECEIPT_AI, 25.0, SGR, sgr = 500),
        q("m3", MONTHLY, "Înlocuiește 50 km de deplasări auto cu mersul pe jos", "Replace 50 km of car trips with walking", MEDIUM, 600, GPS, 10.0, TRANSPORT),
        q("m4", MONTHLY, "Înlocuiește 100 km de deplasări auto cu transport public sau bicicleta", "Replace 100 km of car trips with public transport or cycling", HARD, 1000, GPS, 20.0, TRANSPORT),
        q("m5", MONTHLY, "Participă la 2 acțiuni ecologice comunitare", "Join 2 community eco actions", MEDIUM, 600, QR_PHOTO, 6.0, COMMUNITY),
        q("m6", MONTHLY, "Repară un obiect în loc să cumperi unul nou", "Repair an item instead of buying a new one", MEDIUM, 600, BEFORE_AFTER, 15.0, REUSE),
        q("m7", MONTHLY, "Cumpără un obiect second-hand în locul unuia nou planificat", "Buy a second-hand item instead of a planned new one", EASY, 300, RECEIPT_AI, 8.0, REUSE),
        q("m8", MONTHLY, "Redu consumul de energie față de o perioadă comparabilă", "Reduce energy use vs. a comparable period", HARD, 1000, BILL, 25.0, ENERGY),
        q("m9", MONTHLY, "Evită risipa alimentară în 15 zile diferite", "Avoid food waste on 15 different days", MEDIUM, 600, JOURNAL, 13.5, FOOD),
        q("m10", MONTHLY, "Completează toate cele 4 provocări săptămânale principale", "Complete all 4 main weekly challenges", HARD, 1000, AUTO, 0.0, PROGRESS, autoOf = WEEKLY, autoTarget = 4),
        q("m11", MONTHLY, "Ajută 3 prieteni să finalizeze prima lor misiune ecologică", "Help 3 friends finish their first eco quest", MEDIUM, 600, AUTO, 3.0, COMMUNITY),
    )

    /** Quest chain (3 steps, unlocked in order). */
    val chain = listOf(
        q("chain1", DAILY, "Mergi pe jos la magazin", "Walk to the store", EASY, 25, GPS, 0.20, TRANSPORT, km = 1.0),
        q("chain2", DAILY, "Folosește sacoșa reutilizabilă", "Use your reusable bag", EASY, 25, CONFIRM, 0.08, REUSE),
        q("chain3", DAILY, "Alege produse fără ambalaj plastic", "Choose plastic-free products", MEDIUM, 50, PHOTO_AI, 0.30, REUSE),
    )

    val mystery = q(
        "mystery", DAILY, "Misiune misterioasă: fotografiază cel mai apropiat punct de colectare SGR",
        "Mystery quest: photograph the nearest SGR collection point", MEDIUM, 50, PHOTO_AI, 0.50, SGR,
    )

    private val all = daily + weekly + monthly + chain + mystery
    private val byId = all.associateBy { it.id }

    fun byId(id: String): Quest? = byId[id]
    fun byKind(kind: QuestKind): List<Quest> = when (kind) {
        DAILY -> daily
        WEEKLY -> weekly
        MONTHLY -> monthly
    }

    // --- completion periods: a quest can be redone once per day / ISO week / month ---

    private fun period(kind: QuestKind, d: LocalDate): String = when (kind) {
        DAILY -> d.toString()
        WEEKLY -> "${d.get(IsoFields.WEEK_BASED_YEAR)}-W${d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
        MONTHLY -> "${d.year}-${d.monthValue}"
    }

    private fun parse(key: String): Pair<Quest, LocalDate>? {
        val quest = byId[key.substringBefore("@")] ?: return null
        val date = runCatching { LocalDate.parse(key.substringAfter("@")) }.getOrNull() ?: return null
        return quest to date
    }

    fun isDone(quest: Quest, keys: Set<String>, today: LocalDate): Boolean {
        val p = period(quest.kind, today)
        return keys.any { k -> parse(k)?.let { (qq, d) -> qq.id == quest.id && period(quest.kind, d) == p } == true }
    }

    /** Up to 3 easy/medium daily quests that target the weakest quiz categories. */
    fun recommended(p: UserProgress, today: LocalDate): List<Quest> {
        val map = listOf(listOf(TRANSPORT), listOf(ENERGY), listOf(FOOD), listOf(SGR, REUSE), listOf(REUSE))
        val order = QuizData.categories.indices.sortedBy { p.categoryScores[it].toFloat() / QuizData.categories[it].max }
        return order.flatMap { i ->
            daily.filter { it.category in map[i] && it.difficulty != HARD && !isDone(it, p.completedKeys, today) }.take(1)
        }.distinct().take(3)
    }
}
