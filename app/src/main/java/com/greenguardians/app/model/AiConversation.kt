package com.greenguardians.app.model

data class AiMessage(val fromUser: Boolean, val text: String)
data class AiConversation(
    val ownerId: Int? = null,
    val messages: List<AiMessage> = emptyList(),
    val draft: String = "",
    val busy: Boolean = false,
    val thinking: Boolean = false,
)
