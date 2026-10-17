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
 * Plugs any AI into the launcher with just its API token. The service is detected from the token's
 * format, and each service has a default endpoint and model. While this is on, the Assistant and the
 * crash advisor use it for text answers instead of Gemini. Images, voice and video stay on Gemini.
 */
object TurtleAiCustom {
    private const val TAG = "TurtleAiCustom"
    private const val TIMEOUT_SECONDS = 60L

    /** An OpenAI-compatible service, its endpoint and the model used when only the token is given. */
    private class Provider(val name: String, val baseUrl: String, val model: String)

    /** Token prefix -> services that issue such tokens, tried in order. The longest matching prefix wins. */
    private val PROVIDERS: Map<String, List<Provider>> = mapOf(
        "sk-ant-" to listOf(Provider("Anthropic", "https://api.anthropic.com/v1", "claude-haiku-5-5")),
        "sk-or-" to listOf(Provider("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini")),
        "sk-" to listOf(
            Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            Provider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat")
        ),
        "gsk_" to listOf(Provider("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile")),
        "xai-" to listOf(Provider("xAI", "https://api.x.ai/v1", "grok-3-mini")),
        "AIza" to listOf(Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"))
    )

    private fun apiKey(): String = runCatching { AllSettings.aiCustomApiKey.getValue() }.getOrDefault("").trim()

    /** The services to try for this token, most likely first. Empty when the token format is unknown. */
    private fun candidates(token: String): List<Provider> {
        val prefix = PROVIDERS.keys.filter { token.startsWith(it) }.maxByOrNull { it.length } ?: return emptyList()
        return PROVIDERS.getValue(prefix)
    }

    /** On, and the saved token is in a format the launcher recognises. */
    @JvmStatic
    fun isEnabled(): Boolean =
        runCatching { AllSettings.aiCustomEnabled.getValue() }.getOrDefault(false) &&
            candidates(apiKey()).isNotEmpty()

    /** One chat turn. Returns the model's text, or null when every candidate service fails. */
    @JvmStatic
    fun chat(
        systemPrompt: String,
        userContent: String,
        history: List<TurtleAiGemini.Exchange>,
        temperature: Double,
        maxTokens: Int
    ): TurtleAiGemini.Answer? {
        val token = apiKey()
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
        for (provider in candidates(token)) {
            val answer = request(provider, token, messages, temperature, maxTokens)
            if (answer != null) return answer
        }
        return null
    }

    private fun request(
        provider: Provider,
        token: String,
        messages: JsonArray,
        temperature: Double,
        maxTokens: Int
    ): TurtleAiGemini.Answer? {
        val body = JsonObject().apply {
            addProperty("model", provider.model)
            add("messages", messages)
            addProperty("temperature", temperature)
            addProperty("max_tokens", maxTokens)
        }
        val builder = UrlManager.createRequestBuilder(
            provider.baseUrl + "/chat/completions",
            body.toString().toRequestBody("application/json".toMediaType())
        )
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .addHeader("User-Agent", "TurtleLauncher-TurtleAI/1.0")

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
                    // The token is never logged; only the service, the status and the start of the message.
                    Logging.w(TAG, "${provider.name} HTTP ${response.code}: ${text.take(300)}")
                    return null
                }
                val root = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
                    ?: return null
                val content = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")
                    ?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                if (content.isEmpty()) null
                else TurtleAiGemini.Answer(text = content, modelId = provider.model)
            }
        } catch (t: Exception) {
            Logging.w(TAG, "${provider.name} request failed", t)
            null
        }
    }

    private fun message(role: String, text: String): JsonObject = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", text)
    }
}
