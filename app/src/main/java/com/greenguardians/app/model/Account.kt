package com.greenguardians.app.model

data class UserInfo(
    val id: Int,
    val email: String,
    val name: String,
    val city: String,
    val teamId: Int?,
    val refCode: String,
    val locality: String = "urban",
    val publicTransport: Boolean = false,
)

data class Me(val user: UserInfo, val progress: UserProgress)

/** token == null -> show login. me is the last /api/me payload (offline cache). */
data class Session(val loaded: Boolean = false, val token: String? = null, val me: Me? = null)

data class AppConfig(
    val demoMode: Boolean = false,
    val aiEnabled: Boolean = false,
    val transitEnabled: Boolean = false,
    val cities: List<String> = listOf("Iași", "Suceava", "Bacău", "Altul"),
)
