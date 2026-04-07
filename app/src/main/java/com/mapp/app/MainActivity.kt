package com.mapp.app

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.google.android.material.button.MaterialButtonToggleGroup
import com.mapp.app.databinding.ActivityMainBinding
import com.mapp.app.distance.DistanceCalculator
import com.mapp.app.distance.DistanceSource
import com.mapp.app.renderer.ArSceneGlRenderer
import com.mapp.app.session.ArCoreSupport
import com.mapp.app.session.ArSessionManager
import com.mapp.app.ui.ReferenceOverlayView
import com.google.ar.core.Session

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: ArSessionManager
    private lateinit var distanceCalculator: DistanceCalculator
    private lateinit var glRenderer: ArSceneGlRenderer

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onCameraPermissionGranted()
        } else {
            binding.statusText.visibility = android.view.View.VISIBLE
            binding.statusText.text = getString(R.string.camera_permission_rationale)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = ArSessionManager(this)
        bindDisplay(sessionManager)

        distanceCalculator = DistanceCalculator(
            assumedRealWidthMeters = 1.0f,
            assumedVisibleFractionOfImageWidth = 0.25f,
        )

        glRenderer = ArSceneGlRenderer(assets, distanceCalculator).apply {
            distanceToScaleK = 0.35f
            minObjectScale = 0.08f
            maxObjectScale = 1.6f
            objectBaseScale = 0.4f

            listener = ArSceneGlRenderer.ArRenderListener { meters, source, trackingOk ->
                runOnUiThread {
                    updateDistanceUi(meters, source, trackingOk)
                }
            }

            setViewportSessionHook {
                sessionManager.setDisplayGeometry(
                    binding.glSurfaceView.width,
                    binding.glSurfaceView.height,
                )
            }
        }

        binding.glSurfaceView.apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(glRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        // ✅ FIXED LISTENER (THIS WAS YOUR ERROR)
        binding.referenceOverlay.referenceListener =
            object : ReferenceOverlayView.ReferenceListener {
                override fun onReferenceDotMoved(x: Float, y: Float) {
                    glRenderer.hitXView = x
                    glRenderer.hitYView = y
                }
            }

        binding.referenceModeGroup.addOnButtonCheckedListener(
            MaterialButtonToggleGroup.OnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@OnButtonCheckedListener
                val mode = when (checkedId) {
                    R.id.modeDotButton -> ReferenceOverlayView.SelectionMode.DOT
                    R.id.modeLineButton -> ReferenceOverlayView.SelectionMode.LINE
                    else -> ReferenceOverlayView.SelectionMode.NONE
                }
                binding.referenceOverlay.selectionMode = mode
                val interact = mode != ReferenceOverlayView.SelectionMode.NONE
                binding.referenceOverlay.isClickable = interact
                binding.referenceOverlay.isFocusable = interact
            },
        )

        binding.resetAimButton.setOnClickListener {
            binding.referenceOverlay.resetToCenter()
            binding.glSurfaceView.doOnLayout {
                glRenderer.hitXView = binding.referenceOverlay.dotX
                glRenderer.hitYView = binding.referenceOverlay.dotY
            }
        }

        // Offset controls
        binding.offsetLeftButton.setOnClickListener { glRenderer.objectOffsetX -= 0.1f }
        binding.offsetRightButton.setOnClickListener { glRenderer.objectOffsetX += 0.1f }
        binding.offsetUpButton.setOnClickListener { glRenderer.objectOffsetY += 0.1f }
        binding.offsetDownButton.setOnClickListener { glRenderer.objectOffsetY -= 0.1f }

        binding.glSurfaceView.doOnLayout {
            if (glRenderer.hitXView == 0f && glRenderer.hitYView == 0f) {
                glRenderer.hitXView = binding.referenceOverlay.dotX
                glRenderer.hitYView = binding.referenceOverlay.dotY
            }
        }

        when (ArCoreSupport.ensureInstalled(this)) {
            ArCoreSupport.InstallState.SUPPORTED_INSTALLED -> requestCameraIfNeeded()
            ArCoreSupport.InstallState.UNKNOWN_CHECKING -> {
                binding.statusText.visibility = android.view.View.VISIBLE
                binding.statusText.text = "Complete ARCore installation if prompted, then return."
            }
            ArCoreSupport.InstallState.SUPPORTED_NOT_INSTALLED,
            ArCoreSupport.InstallState.SUPPORTED_APK_TOO_OLD -> {
                binding.statusText.visibility = android.view.View.VISIBLE
                binding.statusText.text = "Install or update Google Play Services for AR."
            }
            ArCoreSupport.InstallState.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                binding.statusText.visibility = android.view.View.VISIBLE
                binding.statusText.text = "This device is not supported by ARCore."
            }
        }
    }

    private fun bindDisplay(manager: ArSessionManager) {
        val d: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        manager.setDisplay(d)
    }

    private fun requestCameraIfNeeded() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> onCameraPermissionGranted()
            else -> cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun onCameraPermissionGranted() {
        if (sessionManager.session != null) return
        when (val result = sessionManager.createSessionIfPossible()) {
            is ArSessionManager.SessionCreateResult.Error -> {
                binding.statusText.visibility = android.view.View.VISIBLE
                binding.statusText.text = result.userMessage
                Toast.makeText(this, result.userMessage, Toast.LENGTH_LONG).show()
            }
            is ArSessionManager.SessionCreateResult.Success -> {
                binding.statusText.visibility = android.view.View.GONE
                attachSession(result.session)
            }
        }
    }

    private fun attachSession(session: Session) {
        glRenderer.session = session
        sessionManager.setDisplayGeometry(
            binding.glSurfaceView.width.coerceAtLeast(1),
            binding.glSurfaceView.height.coerceAtLeast(1),
        )
    }

    private fun updateDistanceUi(meters: Float, source: DistanceSource, trackingOk: Boolean) {
        if (!trackingOk || meters.isNaN()) {
            binding.distanceText.text = "Distance: — (move device to track)"
            binding.sourceText.text = "Source: —"
            return
        }
        binding.distanceText.text = "Distance: %.2f m".format(meters)
        binding.sourceText.text = when (source) {
            DistanceSource.PLANE_HIT_CENTER -> "Source: plane hit (center ray)"
            DistanceSource.PLANE_HIT_TAP -> "Source: plane hit (tap ray)"
            DistanceSource.INTRINSICS_ASSUMED_SIZE ->
                "Source: intrinsics + assumed size"
        }
    }

    override fun onResume() {
        super.onResume()
        if (ArCoreSupport.ensureInstalled(this) == ArCoreSupport.InstallState.SUPPORTED_INSTALLED) {
            requestCameraIfNeeded()
        }
        binding.glSurfaceView.onResume()
        sessionManager.resume()
        sessionManager.session?.let { s ->
            glRenderer.session = s
        }
    }

    override fun onPause() {
        sessionManager.pause()
        binding.glSurfaceView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        glRenderer.session = null
        sessionManager.close()
        super.onDestroy()
    }
}