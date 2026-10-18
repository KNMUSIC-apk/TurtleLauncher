package com.cyplixlauncher.mc.ui.subassembly.chat

/** One chat message, either from the local user or from another participant. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)
