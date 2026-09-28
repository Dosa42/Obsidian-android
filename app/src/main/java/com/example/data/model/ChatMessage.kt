package com.example.data.model

data class ToolExecutionResult(
    val action: String,
    val target: String,
    val details: String,
    val success: Boolean = true
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val citedNotes: List<String> = emptyList(),
    val isSynthesizing: Boolean = false,
    val executedTools: List<ToolExecutionResult> = emptyList()
)

enum class GeminiModel(val modelId: String, val displayName: String, val description: String) {
    FLASH_3_5("gemini-3.5-flash", "Gemini 3.5 Flash", "Fast, balanced general intelligence"),
    PRO_3_1("gemini-3.1-pro-preview", "Gemini 3.1 Pro", "Deep reasoning & complex synthesis"),
    FLASH_LITE("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite", "Ultra-fast low-latency responses"),
    FLASH_3_8("gemini-3.8-flash", "Gemini 3.8 Flash", "Cutting-edge multimodal intelligence")
}
