package com.mapp.app.session

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException

/**
 * Runtime ARCore availability checks. Depth API is never required; this only validates install/ARCore support.
 */
object ArCoreSupport {

    enum class InstallState {
        SUPPORTED_INSTALLED,
        SUPPORTED_APK_TOO_OLD,
        SUPPORTED_NOT_INSTALLED,
        UNSUPPORTED_DEVICE_NOT_CAPABLE,
        UNKNOWN_CHECKING
    }

    /**
     * @return true if the app may proceed to request a [com.google.ar.core.Session].
     */
    fun ensureInstalled(context: Context): InstallState {
        return try {
            when (ArCoreApk.getInstance().requestInstall(context, true)) {
                ArCoreApk.InstallStatus.INSTALLED -> InstallState.SUPPORTED_INSTALLED
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> InstallState.UNKNOWN_CHECKING
            }
        } catch (e: UnavailableException) {
            when (e) {
                is UnavailableUserDeclinedInstallationException ->
                    InstallState.SUPPORTED_NOT_INSTALLED
                is UnavailableDeviceNotCompatibleException ->
                    InstallState.UNSUPPORTED_DEVICE_NOT_CAPABLE
                is UnavailableApkTooOldException ->
                    InstallState.SUPPORTED_APK_TOO_OLD
                else -> InstallState.UNSUPPORTED_DEVICE_NOT_CAPABLE
            }
        }
    }
}
