package com.fieldsentinel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.view.Surface
import android.view.TextureView
import java.io.ByteArrayOutputStream

class CameraPreviewView(context: Context) : TextureView(context),
    TextureView.SurfaceTextureListener {

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var lastSurface: SurfaceTexture? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        lastSurface = surface
        lastWidth = width
        lastHeight = height
        startBackgroundThread()

        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            openCamera(surface, width, height)
        } else {
            android.util.Log.w("CameraPreviewView", "Camera permission not granted yet")
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopCamera()
        return true
    }

    // Called after permission is granted to start camera without waiting for surface event
    fun openCameraWithPermission() {
        val surface = lastSurface ?: return
        openCamera(surface, lastWidth, lastHeight)
    }

    private fun startBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").also { it.start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun openCamera(surface: SurfaceTexture, width: Int, height: Int) {
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: manager.cameraIdList[0]

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview(surface)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) {
                    android.util.Log.e("CameraPreviewView", "Camera error: $error")
                    camera.close()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            android.util.Log.e("CameraPreviewView", "openCamera failed: ${e.message}")
        }
    }

    private fun startPreview(surfaceTexture: SurfaceTexture) {
        try {
            val surface = Surface(surfaceTexture)
            val previewRequest = cameraDevice!!
                .createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply { addTarget(surface) }
                .build()

            cameraDevice!!.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(previewRequest, null, backgroundHandler)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        android.util.Log.e("CameraPreviewView", "Capture session config failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            android.util.Log.e("CameraPreviewView", "startPreview failed: ${e.message}")
        }
    }

    fun captureFrame(callback: (String) -> Unit) {
        backgroundHandler?.post {
            try {
                val bitmap = getBitmap()
                if (bitmap == null) {
                    callback("")
                    return@post
                }

                val matrix = Matrix().apply {
                    preScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                }
                val flipped = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )

                val stream = ByteArrayOutputStream()
                flipped.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                callback(base64)

            } catch (e: Exception) {
                android.util.Log.e("CameraPreviewView", "captureFrame failed: ${e.message}")
                callback("")
            }
        }
    }

    fun stopCamera() {
        captureSession?.close()
        cameraDevice?.close()
        stopBackgroundThread()
    }
}