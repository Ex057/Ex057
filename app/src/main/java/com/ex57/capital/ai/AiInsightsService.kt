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

        val providerResponse = runCatching {
            withTimeout(config.timeoutMillis) {
                when (config.provider) {
                    AiProvider.MOCK -> MockAiInsightsService().request(prompt, request, config)
                    AiProvider.OPENAI -> OpenAiInsightsService(httpClient).request(prompt, request, config)
                    AiProvider.OLLAMA -> OllamaAiInsightsService(httpClient).request(prompt, request, config)
                    AiProvider.LOCALAI -> LocalAiInsightsService(httpClient).request(prompt, request, config)
                }
            }
        }.getOrElse { throwable ->
            if (config.provider == AiProvider.LOCALAI) {
                MockAiInsightsService().request(prompt, request, config).copy(
                    caution = "LocalAI was unavailable (${throwable.message ?: "request failed"}). Showing mock fallback context only."
                )
            } else {
                throw throwable
            }
        }

        val response = providerResponse.copy(
            provider = if (config.provider == AiProvider.LOCALAI && providerResponse.provider == AiProvider.MOCK) AiProvider.MOCK else config.provider,
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
        val symbol = request.context.symbolCode
        val timeframe = request.context.timeframe
        val directionalRead = deriveDirectionalRead(market.shortTrend, market.momentumLabel, market.structureLabel)
        val setupRead = deriveSetupRead(market.structureLabel, market.volatilityLabel, market.rangePercent)
        val triggerRead = deriveTriggerRead(market.nearestSupport, market.nearestResistance, directionalRead)
        val invalidationRead = deriveInvalidationRead(market.nearestSupport, market.nearestResistance, directionalRead)
        val title = when (request.action) {
            AiInsightQuickAction.MARKET_DEEP_DIVE -> "Independent Market Read • $symbol $timeframe"
            AiInsightQuickAction.EXPLAIN_SIGNAL -> "Signal Explanation"
            AiInsightQuickAction.EXPLAIN_RISK -> "Risk Overview"
            AiInsightQuickAction.WHY_NOT_ELIGIBLE -> "Eligibility Explanation"
            AiInsightQuickAction.SUMMARIZE_TRADE_PLAN -> "Trade Plan Summary"
            AiInsightQuickAction.MANAGE_OPEN_TRADE -> "Open Trade Guidance"
        }
        val bullets = when (request.action) {
            AiInsightQuickAction.MARKET_DEEP_DIVE -> listOf(
                "$directionalRead Setup quality: $setupRead",
                triggerRead,
                invalidationRead,
                "Levels: support ${market.nearestSupport ?: "-"}, resistance ${market.nearestResistance ?: "-"}; volatility ${market.volatilityLabel.lowercase()}."
            )
            AiInsightQuickAction.EXPLAIN_SIGNAL -> listOf(
                "Bias ${signal?.bias?.lowercase() ?: "unavailable"} at ${signal?.confidence ?: 0}%: this is driven by current structure + momentum alignment on $timeframe.",
                "Decision ${signal?.decision?.lowercase() ?: "unavailable"} means ${if (signal?.approved == true) "conditions are close to executable" else "conditions are still incomplete"} under current guardrails.",
                "Trader trigger: ${signal?.nextTrigger ?: triggerRead}",
                "Invalidation: ${signal?.riskNote ?: invalidationRead}"
            )
            AiInsightQuickAction.EXPLAIN_RISK -> listOf(
                "Regime risk: ${market.volatilityLabel.lowercase()} volatility with ${market.structureLabel.lowercase()} structure can break setup quality quickly.",
                signal?.riskNote ?: "Risk note is unavailable because engine analysis has not been run.",
                "Evidence check: ${evidence?.heuristicSummary ?: "No heuristic evidence available."}",
                "Execution risk: spread/slippage sensitivity increases near level tests (${market.nearestSupport ?: "-"} / ${market.nearestResistance ?: "-"})"
            )
            AiInsightQuickAction.WHY_NOT_ELIGIBLE -> listOf(
                signal?.rejectionReasons?.firstOrNull() ?: "The setup is already eligible under the current guardrails.",
                "Decision state is ${signal?.decision ?: "Unavailable"}.",
                "Trade candidate flag is ${if (signal?.tradeCandidate == true) "on" else "off"}.",
                "What to wait for: ${signal?.nextTrigger ?: triggerRead}"
            )
            AiInsightQuickAction.SUMMARIZE_TRADE_PLAN -> listOf(
                "Direction: ${signal?.bias ?: "Unavailable"} (${signal?.decision ?: "Unknown"}).",
                "Entry / stop / target come from the structured trade plan and should be treated as scenario levels, not certainty.",
                "Trigger: ${signal?.nextTrigger ?: triggerRead}",
                "Invalidation: ${signal?.riskNote ?: invalidationRead}"
            )
            AiInsightQuickAction.MANAGE_OPEN_TRADE -> listOf(
                request.context.openTrade?.let { "Open trade is ${it.side.lowercase()} on ${it.timeframe} with management stage ${it.managementStage}." }
                    ?: "There is no open trade in context.",
                signal?.traderGuidance ?: "Run engine analysis before using management guidance.",
                "If price action loses structure around key level, reduce risk or exit rather than averaging.",
                "Reassess immediately if volatility regime shifts."
            )
        }.take(4)

        return AiInsightResponse(
            provider = AiProvider.MOCK,
            action = request.action,
            title = title,
            summary = when (request.action) {
                AiInsightQuickAction.MARKET_DEEP_DIVE ->
                    "$symbol $timeframe: ${market.structureLabel.lowercase()} with ${market.momentumLabel.lowercase()} and ${market.shortTrend.lowercase()} flow. Bias: ${directionalRead.substringAfter("Bias: ").substringBefore(".")}."
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

private fun deriveDirectionalRead(
    shortTrend: String,
    momentum: String,
    structure: String
): String {
    val trend = shortTrend.lowercase()
    val mom = momentum.lowercase()
    val struct = structure.lowercase()
    return when {
        (trend.contains("up") || trend.contains("bull")) && !struct.contains("range") ->
            "Bias: long-leaning. Trend and structure are aligned with continuation pressure."
        (trend.contains("down") || trend.contains("bear")) && !struct.contains("range") ->
            "Bias: short-leaning. Trend and structure are aligned with downside pressure."
        struct.contains("range") || mom.contains("flat") ->
            "Bias: wait / no trade. Market is range-like or momentum is too flat."
        else ->
            "Bias: conditional. Direction exists but structure is mixed, so wait for cleaner confirmation."
    }
}

private fun deriveSetupRead(
    structure: String,
    volatility: String,
    rangePercent: String?
): String {
    val struct = structure.lowercase()
    val vol = volatility.lowercase()
    return when {
        struct.contains("break") && !vol.contains("high") ->
            "breakout continuation is viable if follow-through holds."
        struct.contains("trend") && vol.contains("low") ->
            "trend continuation setup is constructive with controlled volatility."
        struct.contains("range") && vol.contains("high") ->
            "setup quality is weak: noisy range and fakeout risk are elevated."
        else ->
            "setup quality is moderate; needs cleaner trigger at key level."
    } + (rangePercent?.let { " Window range: $it." } ?: "")
}

private fun deriveTriggerRead(
    support: String?,
    resistance: String?,
    directionalRead: String
): String {
    return when {
        directionalRead.contains("long-leaning") ->
            "Trigger: accept long only after bullish reaction above ${support ?: "support"} or clean break/retest through ${resistance ?: "resistance"}."
        directionalRead.contains("short-leaning") ->
            "Trigger: accept short only after bearish rejection below ${resistance ?: "resistance"} or break/retest through ${support ?: "support"}."
        else ->
            "Trigger: wait for decisive break from ${support ?: "support"} / ${resistance ?: "resistance"} with momentum confirmation."
    }
}

private fun deriveInvalidationRead(
    support: String?,
    resistance: String?,
    directionalRead: String
): String {
    return when {
        directionalRead.contains("long-leaning") ->
            "Invalidation: long thesis weakens if price closes back below ${support ?: "support"} or fails to hold breakout."
        directionalRead.contains("short-leaning") ->
            "Invalidation: short thesis weakens if price reclaims ${resistance ?: "resistance"} or breakdown fails."
        else ->
            "Invalidation: no-trade stance ends only when structure resolves cleanly away from ${support ?: "support"} / ${resistance ?: "resistance"}."
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

class LocalAiInsightsService(
    private val httpClient: OkHttpClient
) : AiProviderService {
    override suspend fun request(
        prompt: AiComposedPrompt,
        request: AiInsightRequest,
        config: AiProviderConfig
    ): AiInsightResponse = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("model", config.localAiModel)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", prompt.systemPrompt))
                .put(JSONObject().put("role", "user").put("content", prompt.userPrompt))
            )
            .put("temperature", 0.2)
            .put("max_tokens", 360)

        val httpRequest = Request.Builder()
            .url("${config.localAiBaseUrl.trimEnd('/')}/v1/chat/completions")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("LocalAI request failed with HTTP ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices")
            val text = choices
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .ifBlank { raw }
            val parsed = parseStructuredPayload(text, request.action)
            parsed.toResponse(AiProvider.LOCALAI, request.action, prompt.requestHash)
        }
    }
}

class AiProviderConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_insights_config", Context.MODE_PRIVATE)

    fun load(): AiProviderConfig {
        val provider = prefs.getString("provider", null)
            ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
            ?: AiProvider.LOCALAI

        return AiProviderConfig(
            provider = provider,
            openAiApiKey = prefs.getString("openai_api_key", null),
            openAiModel = prefs.getString("openai_model", null).orEmpty().ifBlank { "gpt-4.1-mini" },
            openAiBaseUrl = prefs.getString("openai_base_url", null).orEmpty().ifBlank { "https://api.openai.com" },
            ollamaBaseUrl = prefs.getString("ollama_base_url", null).orEmpty().ifBlank { "http://10.0.2.2:11434" },
            ollamaModel = prefs.getString("ollama_model", null).orEmpty().ifBlank { "llama3.1:8b" },
            localAiBaseUrl = prefs.getString("localai_base_url", null).orEmpty().ifBlank { "http://10.0.2.2:8080" },
            localAiModel = prefs.getString("localai_model", null).orEmpty().ifBlank { "local-model" },
            timeoutMillis = prefs.getLong("timeout_millis", 12_000L).coerceIn(4_000L, 30_000L)
        )
    }

    fun saveProvider(provider: AiProvider) {
        prefs.edit().putString("provider", provider.name).apply()
    }

    fun saveOpenAiApiKey(apiKey: String) {
        prefs.edit().putString("openai_api_key", apiKey.trim()).apply()
    }

    fun saveOpenAiModel(model: String) {
        prefs.edit().putString("openai_model", model.trim()).apply()
    }

    fun saveLocalAiBaseUrl(baseUrl: String) {
        prefs.edit().putString("localai_base_url", baseUrl.trim()).apply()
    }

    fun saveLocalAiModel(model: String) {
        prefs.edit().putString("localai_model", model.trim()).apply()
    }

    fun saveOllamaBaseUrl(baseUrl: String) {
        prefs.edit().putString("ollama_base_url", baseUrl.trim()).apply()
    }

    fun saveOllamaModel(model: String) {
        prefs.edit().putString("ollama_model", model.trim()).apply()
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
