package com.mapp.app.session

import android.content.Context
import android.view.Display
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException

/**
 * Owns the ARCore [Session], configures features for mid-range devices, and applies
 * [Session.setDisplayGeometry] each frame when the display size is known.
 *
 * **Depth API:** explicitly disabled so devices without depth (e.g. many Galaxy A-series) behave the same.
 */
class ArSessionManager(private val context: Context) {

    var session: Session? = null
        private set

    private var display: Display? = null

    fun createSessionIfPossible(): SessionCreateResult {
        session?.let { return SessionCreateResult.Success(it) }
        return try {
            val s = Session(context)
            val config = s.config.apply {
                // Primary plane hit-testing does not need depth; keep off for A52-class hardware.
                depthMode = Config.DepthMode.DISABLED
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                focusMode = Config.FocusMode.AUTO
                lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            }
            s.configure(config)
            session = s
            SessionCreateResult.Success(s)
        } catch (e: UnavailableArcoreNotInstalledException) {
            SessionCreateResult.Error("Install Google Play Services for AR from the Play Store.")
        } catch (e: UnavailableApkTooOldException) {
            SessionCreateResult.Error("Please update Google Play Services for AR.")
        } catch (e: UnavailableSdkTooOldException) {
            SessionCreateResult.Error("This app was built with an outdated ARCore SDK.")
        } catch (e: UnavailableDeviceNotCompatibleException) {
            SessionCreateResult.Error("This device does not support ARCore.")
        } catch (e: Exception) {
            SessionCreateResult.Error(e.message ?: "Failed to create ARCore session.")
        }
    }

    fun setDisplay(display: Display) {
        this.display = display
    }

    /**
     * Call from the GL thread before [Session.update].
     */
    fun setDisplayGeometry(viewWidthPx: Int, viewHeightPx: Int) {
        val d = display ?: return
        val s = session ?: return
        val rotation = d.rotation
        s.setDisplayGeometry(rotation, viewWidthPx, viewHeightPx)
    }

    fun resume() {
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            // Activity should show a message; session stays null-safe for renderer.
        }
    }

    fun pause() {
        session?.pause()
    }

    fun close() {
        session?.close()
        session = null
    }

    fun updateFrame(): Frame? {
        val s = session ?: return null
        return try {
            s.update()
        } catch (t: Throwable) {
            null
        }
    }

    fun trackingFailureReason(frame: Frame): TrackingFailureReason {
        return frame.camera.trackingFailureReason
    }

    sealed class SessionCreateResult {
        data class Success(val session: Session) : SessionCreateResult()
        data class Error(val userMessage: String) : SessionCreateResult()
    }
}
