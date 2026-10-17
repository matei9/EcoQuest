package com.greenguardians.app.model

import androidx.compose.ui.graphics.Color

/** The 8 official leagues. Icons (Canvas) are drawn in the Leagues/Profile tasks. */
enum class League(val displayName: String, val minEP: Int, val color: Color) {
    BRONZE("Bronze", 0, Color(0xFFCD7F32)),
    SILVER("Silver", 500, Color(0xFF94A3B8)),
    GOLD("Gold", 1_000, Color(0xFFEAB308)),
    PLATINUM("Platinum", 2_000, Color(0xFF2DD4BF)),
    DIAMOND("Diamond", 3_500, Color(0xFF3B82F6)),
    MASTER("Master", 6_000, Color(0xFFA855F7)),
    GRANDMASTER("Grandmaster", 10_000, Color(0xFFF87171)),
    ECO_LEGEND("Eco Legend", 20_000, Color(0xFF10B981));

    val next: League? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun forEP(totalEP: Int): League = entries.last { totalEP >= it.minEP }
    }
}
