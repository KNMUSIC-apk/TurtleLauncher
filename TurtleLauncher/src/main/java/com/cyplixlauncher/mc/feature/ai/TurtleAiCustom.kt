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

    /**
     * Token prefix -> services that issue such tokens, tried in order. The longest matching prefix wins.
     * "sk-" is shared by several services (OpenAI, DeepSeek, Moonshot, Qwen, SiliconFlow), so each one
     * is tried in turn until one accepts the token.
     */
    private val PROVIDERS: Map<String, List<Provider>> = mapOf(
        "sk-ant-" to listOf(
            Provider("Anthropic", "https://api.anthropic.com/v1", listOf("claude-haiku-5-5", "claude-sonnet-5-5"))
        ),
        "sk-or-" to listOf(
            Provider("OpenRouter", "https://openrouter.ai/api/v1", listOf("openai/gpt-4o-mini", "meta-llama/llama-3.3-70b-instruct"))
        ),
        "sk-" to listOf(
            Provider("OpenAI", "https://api.openai.com/v1", listOf("gpt-4o-mini", "gpt-4.1-mini")),
            Provider("DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat")),
            Provider("Moonshot", "https://api.moonshot.cn/v1", listOf("moonshot-v1-8k")),
            Provider("Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", listOf("qwen-plus", "qwen-turbo")),
            Provider("SiliconFlow", "https://api.siliconflow.cn/v1", listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"))
        ),
        "gsk_" to listOf(
            Provider("Groq", "https://api.groq.com/openai/v1", listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"))
        ),
        "xai-" to listOf(Provider("xAI", "https://api.x.ai/v1", listOf("grok-3-mini"))),
        "AIza" to listOf(
            Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", listOf("gemini-2.5-flash", "gemini-2.5-flash-lite"))
        )
    )

    /** Every failed attempt of the last request, in order, e.g. "OpenAI: token bị từ chối ...". */
    @Volatile
    private var lastFailures: List<String> = emptyList()

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

    /** Services the saved token will be tried against, in order, for the settings screen. */
    @JvmStatic
    fun detectedProvider(): String? =
        candidates(apiKey()).takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name }

    /** Why the last request failed: every service that was tried and what it answered. */
    @JvmStatic
    fun failureReason(): String? =
        lastFailures.takeIf { it.isNotEmpty() }?.let { "Trợ lý AI tùy chỉnh: " + it.joinToString(" | ") }

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
        val failures = mutableListOf<String>()
        val providers = candidates(token)
        if (providers.isEmpty()) {
            lastFailures = listOf("token không đúng định dạng của nhà cung cấp nào được hỗ trợ")
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
                when (val outcome = request(provider, model, token, messages, temperature, maxTokens, failures)) {
                    is Outcome.Ok -> {
                        lastFailures = emptyList()
                        return outcome.answer
                    }
                    Outcome.NextModel -> continue
                    Outcome.NextProvider -> break
                }
            }
        }
        lastFailures = failures.toList()
        return null
    }

    private fun request(
        provider: Provider,
        model: String,
        token: String,
        messages: JsonArray,
        temperature: Double,
        maxTokens: Int,
        failures: MutableList<String>
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
                    failures.add("${provider.name}: ${describe(response.code)}${errorDetail(text, token)}")
                    return@use when (response.code) {
                        400, 404, 422 -> Outcome.NextModel
                        else -> Outcome.NextProvider
                    }
                }
                val root = JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
                if (root == null) {
                    failures.add("${provider.name} trả về dữ liệu không đọc được")
                    return@use Outcome.NextProvider
                }
                val content = root.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")?.get("content")
                    ?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                if (content.isEmpty()) {
                    failures.add("${provider.name} ($model) trả về câu trả lời rỗng")
                    Outcome.NextModel
                } else {
                    Outcome.Ok(TurtleAiGemini.Answer(text = content, modelId = model))
                }
            }
        } catch (t: Exception) {
            Logging.w(TAG, "${provider.name} request failed", t)
            failures.add("${provider.name}: không kết nối được (${t.javaClass.simpleName})")
            Outcome.NextProvider
        }
    }

    /** The service's own error message, e.g. " (Authentication Fails)". The token is masked if echoed back. */
    private fun errorDetail(text: String, token: String): String {
        val message = runCatching {
            val error = JsonParser.parseString(text).asJsonObject.get("error")
            when {
                error == null -> null
                error.isJsonObject -> error.asJsonObject.get("message")?.asString
                error.isJsonPrimitive -> error.asString
                else -> null
            }
        }.getOrNull()?.replace(token, "***")?.trim()
        return if (message.isNullOrEmpty()) "" else " ($message)".take(160)
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
