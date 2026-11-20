package io.github.dipeshlama.passportscanner.internal

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
internal actual fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    var granted by remember(context) { mutableStateOf(context.hasCameraPermission()) }
    var hasRequested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasCameraPermission() }

    return object : CameraPermission {
        override val isGranted: Boolean get() = granted

        override fun request() {
            val activity = context.findActivity()
            val canPrompt = !hasRequested || (activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA))
            if (canPrompt) {
                hasRequested = true
                launcher.launch(Manifest.permission.CAMERA)
            } else {
                context.openAppSettings()
            }
        }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    if (findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
