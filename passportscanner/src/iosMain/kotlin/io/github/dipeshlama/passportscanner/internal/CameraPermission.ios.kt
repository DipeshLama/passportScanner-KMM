package io.github.dipeshlama.passportscanner.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import platform.AVFoundation.AVAuthorizationStatus
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
internal actual fun rememberCameraPermission(): CameraPermission {
    var status by remember { mutableStateOf(currentStatus()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = currentStatus() }

    return object : CameraPermission {
        override val isGranted: Boolean get() = status == AVAuthorizationStatusAuthorized

        override fun request() {
            if (status == AVAuthorizationStatusNotDetermined) {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                    dispatch_async(dispatch_get_main_queue()) { status = currentStatus() }
                }
            } else {
                NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                    UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any?>(), completionHandler = null)
                }
            }
        }
    }
}

private fun currentStatus(): AVAuthorizationStatus =
    AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
