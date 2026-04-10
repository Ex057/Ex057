package com.ex57.capital.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashMap

class AiInsightsService(
    context: Context,
    private val promptComposer: AiPromptComposer = AiPromptComposer(),
    private val configStore: AiProviderConfigStore = AiProviderConfigStore(context),
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val responseCache = object : LinkedHashMap<String, AiInsightResponse>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AiInsightResponse>?): Boolean {
            return size > 24
        }
    }

    suspend fun requestInsight(request: AiInsightRequest): AiInsightResponse {
        val config = configStore.load()
        val prompt = promptComposer.compose(request, config.provider)
        synchronized(responseCache) {
            responseCache[prompt.requestHash]?.takeIf { !request.forceRefresh }?.let { cached ->
                return cached.copy(cached = true)
            }
        }

        val response = withTimeout(config.timeoutMillis) {
            when (config.provider) {
                AiProvider.MOCK -> MockAiInsightsService().request(prompt, request, config)
                AiProvider.OPENAI -> OpenAiInsightsService(httpClient).request(prompt, request, config)
                AiProvider.OLLAMA -> OllamaAiInsightsService(httpClient).request(prompt, request, config)
            }
        }.copy(
            provider = config.provider,
            action = request.action,
            cached = false,
            requestHash = prompt.requestHash,
            generatedAtEpochMillis = System.currentTimeMillis()
        )

        synchronized(responseCache) {
            responseCache[prompt.requestHash] = response
        }
        return response
    }
}

private interface AiProviderService {
    suspend fun request(
        prompt: AiComposedPrompt,
        request: AiInsightRequest,
        config: AiProviderConfig
    ): AiInsightResponse
}

class MockAiInsightsService : AiProviderService {
    override suspend fun request(
        prompt: AiComposedPrompt,
        request: AiInsightRequest,
        config: AiProviderConfig
    ): AiInsightResponse {
        val signal = request.context.signal
        val evidence = request.context.evidence
        val market = request.context.market
        val title = when (request.action) {
            AiInsightQuickAction.MARKET_DEEP_DIVE -> "Independent Market Read"
            AiInsightQuickAction.EXPLAIN_SIGNAL -> "Signal Explanation"
            AiInsightQuickAction.EXPLAIN_RISK -> "Risk Overview"
            AiInsightQuickAction.WHY_NOT_ELIGIBLE -> "Eligibility Explanation"
            AiInsightQuickAction.SUMMARIZE_TRADE_PLAN -> "Trade Plan Summary"
            AiInsightQuickAction.MANAGE_OPEN_TRADE -> "Open Trade Guidance"
        }
        val bullets = when (request.action) {
            AiInsightQuickAction.MARKET_DEEP_DIVE -> listOf(
                "Live price is ${market.livePrice ?: "unavailable"} with ${market.shortTrend.lowercase()} and ${market.momentumLabel.lowercase()}.",
                "Structure reads as ${market.structureLabel.lowercase()} with ${market.volatilityLabel.lowercase()} and ${market.rangePercent} total range in the loaded window.",
                "Nearest levels are support ${market.nearestSupport ?: "-"} and resistance ${market.nearestResistance ?: "-"}, so wait for cleaner expansion or rejection around those points."
            )
            AiInsightQuickAction.EXPLAIN_SIGNAL -> listOf(
                "Bias is ${signal?.bias?.lowercase() ?: "unavailable"} with ${signal?.confidence ?: 0}% confidence from the current confirmation stack.",
                "Decision is ${signal?.decision?.lowercase() ?: "unavailable"}, so the engine is treating this as ${if (signal?.approved == true) "actionable" else "non-actionable"} right now.",
                "Key trigger to watch: ${signal?.nextTrigger ?: "No trigger available"}"
            )
            AiInsightQuickAction.EXPLAIN_RISK -> listOf(
                signal?.riskNote ?: "Risk note is unavailable because engine analysis has not been run.",
                "Heuristic evidence: ${evidence?.heuristicSummary ?: "No heuristic evidence available."}",
                "Realized evidence: ${evidence?.realizedSummary ?: "No realized evidence available."}"
            )
            AiInsightQuickAction.WHY_NOT_ELIGIBLE -> listOf(
                signal?.rejectionReasons?.firstOrNull() ?: "The setup is already eligible under the current guardrails.",
                "Decision state is ${signal?.decision ?: "Unavailable"}.",
                "Trade candidate flag is ${if (signal?.tradeCandidate == true) "on" else "off"}."
            )
            AiInsightQuickAction.SUMMARIZE_TRADE_PLAN -> listOf(
                "Direction: ${signal?.bias ?: "Unavailable"}",
                "Entry / stop / target come from the current structured trade plan.",
                "Invalidation starts when the engine’s trigger and risk note no longer support the setup."
            )
            AiInsightQuickAction.MANAGE_OPEN_TRADE -> listOf(
                request.context.openTrade?.let { "Open trade is ${it.side.lowercase()} on ${it.timeframe} with management stage ${it.managementStage}." }
                    ?: "There is no open trade in context.",
                signal?.traderGuidance ?: "Run engine analysis before using management guidance.",
                "Do not treat this as a guarantee. Reassess if structure or risk changes."
            )
        }.take(4)

        return AiInsightResponse(
            provider = AiProvider.MOCK,
            action = request.action,
            title = title,
            summary = when (request.action) {
                AiInsightQuickAction.MARKET_DEEP_DIVE ->
                    "${request.context.symbolCode} shows ${market.shortTrend.lowercase()} conditions with ${market.momentumLabel.lowercase()} and ${market.structureLabel.lowercase()} structure."
                AiInsightQuickAction.WHY_NOT_ELIGIBLE ->
                    if (signal?.approved == true) "The setup currently passes the guardrails." else "The setup is being held back by one or more guardrails."
                else -> signal?.summary ?: "Engine analysis is not available for this request."
            },
            bullets = bullets,
            caution = when (request.action) {
                AiInsightQuickAction.MARKET_DEEP_DIVE ->
                    "Mock mode uses derived candle structure from the live market snapshot only. It is an independent read, not a guaranteed signal."
                else -> "Mock mode uses structured app context only. It is for development, not external model reasoning."
            },
            cached = false,
            requestHash = prompt.requestHash,
            generatedAtEpochMillis = System.currentTimeMillis()
        )
    }
}

class OpenAiInsightsService(
    private val httpClient: OkHttpClient
) : AiProviderService {
    override suspend fun request(
        prompt: AiComposedPrompt,
        request: AiInsightRequest,
        config: AiProviderConfig
    ): AiInsightResponse = withContext(Dispatchers.IO) {
        val apiKey = config.openAiApiKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("OpenAI provider is selected, but no runtime API key is configured.")

        val body = JSONObject()
            .put("model", config.openAiModel)
            .put("input", JSONArray()
                .put(
                    JSONObject()
                        .put("role", "system")
                        .put("content", JSONArray().put(JSONObject().put("type", "input_text").put("text", prompt.systemPrompt)))
                )
                .put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", JSONArray().put(JSONObject().put("type", "input_text").put("text", prompt.userPrompt)))
                )
            )
            .put("max_output_tokens", 320)

        val requestBody = body.toString().toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url("${config.openAiBaseUrl.trimEnd('/')}/v1/responses")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("OpenAI request failed with HTTP ${response.code}")
            }
            val payload = response.body?.string().orEmpty()
            val parsed = parseStructuredPayload(extractOpenAiText(payload), request.action)
            parsed.toResponse(AiProvider.OPENAI, request.action, prompt.requestHash)
        }
    }

    private fun extractOpenAiText(raw: String): String {
        val json = JSONObject(raw)
        val outputText = json.optString("output_text")
        if (outputText.isNotBlank()) return outputText

        val output = json.optJSONArray("output") ?: return raw
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val block = content.optJSONObject(contentIndex) ?: continue
                val text = block.optString("text")
                if (text.isNotBlank()) return text
            }
        }
        return raw
    }
}

class OllamaAiInsightsService(
    private val httpClient: OkHttpClient
) : AiProviderService {
    override suspend fun request(
        prompt: AiComposedPrompt,
        request: AiInsightRequest,
        config: AiProviderConfig
    ): AiInsightResponse = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", config.ollamaModel)
            .put("stream", false)
            .put("format", "json")
            .put("prompt", "${prompt.systemPrompt}\n\n${prompt.userPrompt}")

        val httpRequest = Request.Builder()
            .url("${config.ollamaBaseUrl.trimEnd('/')}/api/generate")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Ollama request failed with HTTP ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            val json = JSONObject(raw)
            val text = json.optString("response").ifBlank { raw }
            val parsed = parseStructuredPayload(text, request.action)
            parsed.toResponse(AiProvider.OLLAMA, request.action, prompt.requestHash)
        }
    }
}

class AiProviderConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_insights_config", Context.MODE_PRIVATE)

    fun load(): AiProviderConfig {
        val provider = prefs.getString("provider", null)
            ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
            ?: AiProvider.MOCK

        return AiProviderConfig(
            provider = provider,
            openAiApiKey = prefs.getString("openai_api_key", null),
            openAiModel = prefs.getString("openai_model", null).orEmpty().ifBlank { "gpt-4.1-mini" },
            openAiBaseUrl = prefs.getString("openai_base_url", null).orEmpty().ifBlank { "https://api.openai.com" },
            ollamaBaseUrl = prefs.getString("ollama_base_url", null).orEmpty().ifBlank { "http://10.0.2.2:11434" },
            ollamaModel = prefs.getString("ollama_model", null).orEmpty().ifBlank { "llama3.1:8b" },
            timeoutMillis = prefs.getLong("timeout_millis", 12_000L).coerceIn(4_000L, 30_000L)
        )
    }
}

private fun parseStructuredPayload(rawText: String, action: AiInsightQuickAction): AiInsightParsedPayload {
    val cleaned = rawText.trim()
    return runCatching {
        val json = JSONObject(cleaned)
        AiInsightParsedPayload(
            title = json.optString("title").ifBlank { action.label },
            summary = json.optString("summary").ifBlank { "No summary returned." },
            bullets = json.optJSONArray("bullets")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val value = array.optString(index)
                        if (value.isNotBlank()) add(value)
                    }
                }
            }?.take(4)?.ifEmpty { listOf("No bullets returned.") } ?: listOf("No bullets returned."),
            caution = json.optString("caution").ifBlank { "This insight is supportive analysis only, not a guarantee." }
        )
    }.getOrElse {
        AiInsightParsedPayload(
            title = action.label,
            summary = cleaned.take(280).ifBlank { "The provider returned an unreadable response." },
            bullets = listOf("Provider response was not valid JSON.", "Treat the output as unverified support text only."),
            caution = "This insight is supportive analysis only, not a guarantee."
        )
    }
}

private fun AiInsightParsedPayload.toResponse(
    provider: AiProvider,
    action: AiInsightQuickAction,
    requestHash: String
): AiInsightResponse {
    return AiInsightResponse(
        provider = provider,
        action = action,
        title = title,
        summary = summary,
        bullets = bullets,
        caution = caution,
        cached = false,
        requestHash = requestHash,
        generatedAtEpochMillis = System.currentTimeMillis()
    )
}
