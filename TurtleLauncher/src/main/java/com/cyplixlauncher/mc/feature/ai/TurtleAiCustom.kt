package com.cyplixlauncher.mc.feature.ai

import com.cyplixlauncher.mc.feature.log.Logging
import com.cyplixlauncher.mc.setting.AllSettings
import com.cyplixlauncher.mc.utils.path.UrlManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Plugs any AI into the launcher: an OpenAI-compatible chat endpoint (OpenAI, OpenRouter, Groq,
 * Mistral, DeepSeek, Ollama, LM Studio, ...) with its own base URL, token and model name.
 * While it is switched on, the Assistant and the crash advisor use it for text answers instead
 * of Gemini. Images, voice and video stay on Gemini.
 */
object TurtleAiCustom {
    private const val TAG = "TurtleAiCustom"
    private const val TIMEOUT_SECONDS = 60L

    private fun read(block: () -> String): String = runCatching { block() }.getOrDefault("").trim()

    fun baseUrl(): String = read { AllSettings.aiCustomBaseUrl.getValue() }.trimEnd('/')
    fun apiKey(): String = read { AllSettings.aiCustomApiKey.getValue() }
    fun model(): String = read { AllSettings.aiCustomModel.getValue() }

    /** On, with a base URL and a model name. The token may be empty (local servers need none). */
    @JvmStatic
    fun isEnabled(): Boolean =
        runCatching { AllSettings.aiCustomEnabled.getValue() }.getOrDefault(false) &&
            baseUrl().isNotEmpty() && model().isNotEmpty()

    private fun endpoint(): String =
        if (baseUrl().endsWith("/chat/completions")) baseUrl() else baseUrl() + "/chat/completions"

    /** One chat turn. Returns the model's text, or null when the endpoint fails. */
    @JvmStatic
    fun chat(
        systemPrompt: String,
        userContent: String,
        history: List<TurtleAiGemini.Exchange>,
        temperature: Double,
        maxTokens: Int
    ): TurtleAiGemini.Answer? {
        val messages = JsonArray().apply {
            add(message("system", systemPrompt))
            history.takeLast(6).forEach { exchange ->
                val question = exchange.question.trim().take(240)
                val answer = exchange.answer.trim().take(700)
                if (question.isNotBlank() && answer.isNotBlank()) {
                    add(message("user", question))
                    add(message("assistant", answer))
                }
            }
            add(message("user", userContent))
        }
        val body = JsonObject().apply {
            addProperty("model", model())
            add("messages", messages)
            addProperty("temperature", temperature)
            addProperty("max_tokens", maxTokens)
        }
        val builder = UrlManager.createRequestBuilder(
            endpoint(),
            body.toString().toRequestBody("application/json".toMediaType())
        )
            .header("Accept", "application/json")
            .addHeader("User-Agent", "TurtleLauncher-TurtleAI/1.0")
        val token = apiKey()
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")

        val client = UrlManager.createOkHttpClientBuilder {
            it.callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
        }.build()

        return try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // The token is never logged; only the status and the start of the server message.
                    Logging.w(TAG, "Custom AI HTTP ${response.code}: ${text.take(300)}")
                    return null
                }
                val root = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
                    ?: return null
                val content = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")
                    ?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                if (content.isEmpty()) null
                else TurtleAiGemini.Answer(text = content, modelId = model())
            }
        } catch (t: Exception) {
            Logging.w(TAG, "Custom AI request failed", t)
            null
        }
    }

    private fun message(role: String, text: String): JsonObject = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", text)
    }
}
