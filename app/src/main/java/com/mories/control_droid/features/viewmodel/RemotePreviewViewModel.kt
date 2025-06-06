package com.mories.control_droid.features.viewmodel

import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mories.control_droid.core.ConstantValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class RemotePreviewViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(RemotePreviewUiState())
    val uiState: StateFlow<RemotePreviewUiState> = _uiState

    private var pollingJob: Job? = null
    private val client = OkHttpClient()

    fun onEvent(event: RemotePreviewEvent) {
        when (event) {
            is RemotePreviewEvent.StartPolling -> startPolling(event.ip)
            is RemotePreviewEvent.StopPolling -> stopPolling()
        }
    }

    private fun startPolling(ip: String) {
        stopPolling()

        pollingJob = viewModelScope.launch {
            val url = "http://$ip:${ConstantValue.PORT_VALUE}/screenshot"
            Log.d("RemotePreviewViewModel", "Polling from $url")

            while (isActive) {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val request = Request.Builder().url(url).build()
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.bytes()?.let { bytes ->
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                }
                            } else {
                                _uiState.update {
                                    it.copy(error = "HTTP error: ${response.code}")
                                }
                                null
                            }
                        }
                    }

                    bitmap?.let { bmp ->
                        _uiState.update { state ->
                            state.copy(isLoading = false, bitmap = bmp, error = null)
                        }
                    } ?: run {
                        _uiState.update {
                            it.copy(error = "Failed to decode image")
                        }
                    }

                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(error = e.message ?: "Unknown error")
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