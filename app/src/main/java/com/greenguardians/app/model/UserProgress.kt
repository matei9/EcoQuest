package com.greenguardians.app.model

/** Notz & Stroeve: 1 t CO2 melts 3 m2 of summer Arctic sea ice -> 0.003 m2 per kg. */
object Impact {
    const val ICE_M2_PER_KG = 0.003
    const val KG_PER_BEAR = 100.0
    const val KG_PER_HABITAT_DAY = 5.0
    const val KG_PER_CAR_KM = 0.20
    const val KG_PER_TREE_YEAR = 22.0
    const val MAX_QUIZ_EP = 2_500
    const val LITERS_PER_KM = 0.07 // ~7 L/100 km average car
    const val COINS_PER_EP = 0.2 // PLACEHOLDER: 1 EcoCoin per 5 EP
}

/**
 * Quiz EP and verified-quest EP are kept apart; CO2 starts at 0 and grows only
 * from verified quests (never from the quiz).
 */
data class UserProgress(
    val quizDone: Boolean = false,
    val categoryScores: List<Int> = List(5) { 0 },
    val initialSelfDeclaredEP: Int = 0,
    val verifiedQuestEP: Int = 0,
    val verifiedCO2SavedKg: Double = 0.0,
    val ecoCoins: Int = 0,
    val sgrReturned: Int = 0,
    val dailyQuestIds: List<String> = emptyList(),
    val rankedWins: Int = 0,
    val rankedPlayed: Int = 0,
    val rankedDraws: Int = 0,
    val duelPoints: Int = 0,
    /** "questId@yyyy-MM-dd" for each completion. */
    val completedKeys: Set<String> = emptySet(),
    /** ISO dates with at least one completed quest (flexible 5-of-7 streak). */
    val activeDays: Set<String> = emptySet(),
    /** Server-computed (value, target) for auto quests: km tracked, SGR packages, daily counts... */
    val questProgress: Map<String, Pair<Double, Double>> = emptyMap(),
) {
    val totalEP: Int get() = initialSelfDeclaredEP + verifiedQuestEP
    val league: League get() = League.forEP(totalEP)

    val rawQuizScore: Int get() = initialSelfDeclaredEP / 25
    val improvementPotential: Int get() = if (quizDone) 100 - rawQuizScore else 100

    /** 0f..1f progress inside the current league toward the next one. */
    val leagueProgress: Float
        get() {
            val next = league.next ?: return 1f
            return (totalEP - league.minEP).toFloat() / (next.minEP - league.minEP)
        }
    val epToNextLeague: Int get() = league.next?.let { it.minEP - totalEP } ?: 0

    val arcticIceSavedM2: Double get() = verifiedCO2SavedKg * Impact.ICE_M2_PER_KG
    val arcticIceSavedCm2: Double get() = verifiedCO2SavedKg * 30.0
    val polarBearsSaved: Double get() = verifiedCO2SavedKg / Impact.KG_PER_BEAR
    val polarBearProgress: Float get() = (polarBearsSaved % 1.0).toFloat()
    val habitatDays: Double get() = verifiedCO2SavedKg / Impact.KG_PER_HABITAT_DAY
    val carKmAvoided: Double get() = verifiedCO2SavedKg / Impact.KG_PER_CAR_KM
    val fuelLitersSaved: Double get() = carKmAvoided * Impact.LITERS_PER_KM
    val treeEquivalents: Double get() = verifiedCO2SavedKg / Impact.KG_PER_TREE_YEAR
}
