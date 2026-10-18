package com.greenguardians.app.model

data class GuardianTree(
    val stage: Int,
    val stageDays: Int,
    val heightMeters: Double,
    val streak: Int,
    val missedDays: Int,
    val activeDays: Int,
    val activeToday: Boolean,
    val health: String,
    val daysToNextStage: Int,
    val mature: Boolean,
    val topOnly: Boolean,
    val serverDay: String,
)
