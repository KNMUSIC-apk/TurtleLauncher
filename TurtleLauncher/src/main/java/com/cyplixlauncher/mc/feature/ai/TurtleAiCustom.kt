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
 * format. Each service has an endpoint and a list of models; a model the service rejects is skipped,
 * and a service that rejects the token (or is out of quota) is skipped in favour of the next one.
 * While this is on, the Assistant and the crash advisor use it for text answers instead of Gemini.
 * Images, voice and video stay on Gemini.
 */
object TurtleAiCustom {
    private const val TAG = "TurtleAiCustom"
    private const val TIMEOUT_SECONDS = 60L

    private class Provider(val name: String, val baseUrl: String, val models: List<String>)

    private sealed class Outcome {
        class Ok(val answer: TurtleAiGemini.Answer) : Outcome()
        object NextModel : Outcome()
        object NextProvider : Outcome()
    }

    /** Token prefix -> services that issue such tokens, tried in order. The longest matching prefix wins. */
    private val PROVIDERS: Map<String, List<Provider>> = mapOf(
        "sk-ant-" to listOf(
            Provider("Anthropic", "https://api.anthropic.com/v1", listOf("claude-haiku-5-5", "claude-sonnet-5-5"))
        ),
        "sk-or-" to listOf(
            Provider("OpenRouter", "https://openrouter.ai/api/v1", listOf("openai/gpt-4o-mini", "meta-llama/llama-3.3-70b-instruct"))
        ),
        "sk-" to listOf(
            Provider("OpenAI", "https://api.openai.com/v1", listOf("gpt-4o-mini", "gpt-4.1-mini")),
            Provider("DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat"))
        ),
        "gsk_" to listOf(
            Provider("Groq", "https://api.groq.com/openai/v1", listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"))
        ),
        "xai-" to listOf(Provider("xAI", "https://api.x.ai/v1", listOf("grok-3-mini"))),
        "AIza" to listOf(
            Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", listOf("gemini-2.5-flash", "gemini-2.5-flash-lite"))
        )
    )

    @Volatile
    private var lastFailure: String? = null

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

    /** Name of the service the saved token looks like, for the settings screen. */
    @JvmStatic
    fun detectedProvider(): String? = candidates(apiKey()).firstOrNull()?.name

    /** Why the last request failed, in words the user can act on. */
    @JvmStatic
    fun failureReason(): String? = lastFailure?.let { "Trợ lý AI tùy chỉnh: $it" }

    /** One chat turn. Returns the model's text, or null when every service and model failed. */
    @JvmStatic
    fun chat(
        systemPrompt: String,
        userContent: String,
        history: List<TurtleAiGemini.Exchange>,
        temperature: Double,
        maxTokens: Int
    ): TurtleAiGemini.Answer? {
        val token = apiKey()
        lastFailure = null
        val providers = candidates(token)
        if (providers.isEmpty()) {
            lastFailure = "token không đúng định dạng của nhà cung cấp nào được hỗ trợ"
            return null
        }
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
        for (provider in providers) {
            for (model in provider.models) {
                when (val outcome = request(provider, model, token, messages, temperature, maxTokens)) {
                    is Outcome.Ok -> return outcome.answer
                    Outcome.NextModel -> continue
                    Outcome.NextProvider -> break
                }
            }
        }
        return null
    }

    private fun request(
        provider: Provider,
        model: String,
        token: String,
        messages: JsonArray,
        temperature: Double,
        maxTokens: Int
    ): Outcome {
        val body = JsonObject().apply {
            addProperty("model", model)
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
                    // The token is never logged; only the service, model, status and the start of the message.
                    Logging.w(TAG, "${provider.name} $model HTTP ${response.code}: ${text.take(300)}")
                    lastFailure = "${provider.name}: ${describe(response.code)}"
                    return@use when (response.code) {
                        400, 404, 422 -> Outcome.NextModel
                        else -> Outcome.NextProvider
                    }
                }
                val root = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
                if (root == null) {
                    lastFailure = "${provider.name} trả về dữ liệu không đọc được"
                    return@use Outcome.NextProvider
                }
                val content = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")
                    ?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                if (content.isEmpty()) {
                    lastFailure = "${provider.name} ($model) trả về câu trả lời rỗng"
                    Outcome.NextModel
                } else {
                    lastFailure = null
                    Outcome.Ok(TurtleAiGemini.Answer(text = content, modelId = model))
                }
            }
        } catch (t: Exception) {
            Logging.w(TAG, "${provider.name} request failed", t)
            lastFailure = "${provider.name}: không kết nối được (${t.javaClass.simpleName})"
            Outcome.NextProvider
        }
    }

    private fun describe(code: Int): String = when (code) {
        401, 403 -> "token bị từ chối (sai hoặc đã hết hạn)"
        402 -> "tài khoản hết tiền hoặc chưa có gói trả phí"
        429 -> "vượt hạn mức hoặc gửi quá nhanh"
        400, 404, 422 -> "mô hình không hợp lệ (HTTP $code)"
        in 500..599 -> "máy chủ đang lỗi (HTTP $code)"
        else -> "HTTP $code"
    }

    private fun message(role: String, text: String): JsonObject = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", text)
    }
}
