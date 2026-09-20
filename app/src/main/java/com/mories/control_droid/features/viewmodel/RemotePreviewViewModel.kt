package com.mories.control_droid.features.viewmodel

import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mories.control_droid.core.networking.DeviceControlClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RemotePreviewViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RemotePreviewUiState())
    val uiState: StateFlow<RemotePreviewUiState> = _uiState

    private var pollingJob: Job? = null
    fun onEvent(event: RemotePreviewEvent) {
        when (event) {
            is RemotePreviewEvent.StartPolling -> startPolling(event.client)
            is RemotePreviewEvent.StopPolling -> stopPolling()
        }
    }

    private fun startPolling(client: DeviceControlClient) {
        stopPolling()
        _uiState.update { RemotePreviewUiState() }

        pollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val response = client.fetchScreenshot()
                    if (response.statusCode !in 200..299) {
                        _uiState.update {
                            it.copy(isLoading = false, error = "HTTP error: ${response.statusCode}")
                        }
                        delay(2000)
                        continue
                    }

                    val bitmap = response.bytes?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap == null) {
                        _uiState.update {
                            it.copy(isLoading = false, error = "Failed to decode image")
                        }
                    } else {
                        _uiState.update { state ->
                            state.copy(isLoading = false, bitmap = bitmap, error = null)
                        }
                    }

                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Unknown error")
                    }
                }

                delay(2000)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        Log.d("RemotePreviewViewModel", "Polling stopped")
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}
