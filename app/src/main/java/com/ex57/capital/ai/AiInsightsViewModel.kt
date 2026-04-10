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
}
