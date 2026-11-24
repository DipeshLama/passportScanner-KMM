package io.github.dipeshlama.barcodescanner.internal

import androidx.compose.runtime.Composable

internal interface CameraPermission {
    val isGranted: Boolean

    fun request()
}

@Composable
internal expect fun rememberCameraPermission(): CameraPermission
