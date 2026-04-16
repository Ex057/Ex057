package com.ex57.capital.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiInsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val service = AiInsightsService(application)
    private val configStore = AiProviderConfigStore(application)
    private var lastRequest: AiInsightRequest? = null

    private val _uiState = MutableStateFlow(
        AiInsightsUiState(provider = configStore.load().provider)
    )
    val uiState: StateFlow<AiInsightsUiState> = _uiState.asStateFlow()
    private val _config = MutableStateFlow(configStore.load())
    val config: StateFlow<AiProviderConfig> = _config.asStateFlow()

    fun requestInsight(
        action: AiInsightQuickAction,
        context: AiInsightContext,
        forceRefresh: Boolean = false
    ) {
        val provider = configStore.load().provider
        val request = AiInsightRequest(
            action = action,
            context = context,
            forceRefresh = forceRefresh
        )
        lastRequest = request
        _uiState.value = _uiState.value.copy(
            provider = provider,
            activeAction = action,
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            runCatching { service.requestInsight(request) }
                .onSuccess { response ->
                    _uiState.value = AiInsightsUiState(
                        provider = response.provider,
                        activeAction = response.action,
                        isLoading = false,
                        response = response,
                        errorMessage = null
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        provider = provider,
                        activeAction = action,
                        isLoading = false,
                        errorMessage = throwable.message ?: "AI insight request failed."
                    )
                }
        }
    }

    fun retry() {
        val request = lastRequest ?: return
        requestInsight(
            action = request.action,
            context = request.context,
            forceRefresh = true
        )
    }

    fun clear() {
        _uiState.value = AiInsightsUiState(provider = configStore.load().provider)
    }

    fun updateProvider(provider: AiProvider) {
        configStore.saveProvider(provider)
        _config.value = configStore.load()
        _uiState.value = AiInsightsUiState(provider = provider)
    }

    fun updateOpenAiApiKey(apiKey: String) {
        configStore.saveOpenAiApiKey(apiKey)
        _config.value = configStore.load()
    }

    fun updateOpenAiModel(model: String) {
        configStore.saveOpenAiModel(model)
        _config.value = configStore.load()
    }

    fun updateLocalAiBaseUrl(baseUrl: String) {
        configStore.saveLocalAiBaseUrl(baseUrl)
        _config.value = configStore.load()
    }

    fun updateLocalAiModel(model: String) {
        configStore.saveLocalAiModel(model)
        _config.value = configStore.load()
    }

    fun updateOllamaBaseUrl(baseUrl: String) {
        configStore.saveOllamaBaseUrl(baseUrl)
        _config.value = configStore.load()
    }

    fun updateOllamaModel(model: String) {
        configStore.saveOllamaModel(model)
        _config.value = configStore.load()
    }
}
