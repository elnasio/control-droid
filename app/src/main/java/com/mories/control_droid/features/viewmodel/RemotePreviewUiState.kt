package com.mories.control_droid.features.viewmodel

import android.graphics.Bitmap

data class RemotePreviewUiState(
    val isLoading: Boolean = true, val bitmap: Bitmap? = null, val error: String? = null
)