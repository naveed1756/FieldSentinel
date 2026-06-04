package com.fieldsentinel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
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
    private var pendingCaptureCallback: ((String) -> Unit)? = null

    init {
        surfaceTextureListener = this
    }

    // Called when the TextureView surface is ready
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startBackgroundThread()
        openCamera(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopCamera()
        return true
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun openCamera(surface: SurfaceTexture, width: Int, height: Int) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Get front camera ID
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: manager.cameraIdList[0]  // fallback to first camera

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                startPreview(surface)
            }
            override fun onDisconnected(camera: CameraDevice) { camera.close() }
            override fun onError(camera: CameraDevice, error: Int) { camera.close() }
        }, backgroundHandler)
    }

    private fun startPreview(surfaceTexture: SurfaceTexture) {
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
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            backgroundHandler
        )
    }

    // Called from FaceAuthModule when authentication is triggered
    // Captures the current frame as base64 JPEG string
    fun captureFrame(callback: (String) -> Unit) {
        pendingCaptureCallback = callback

        // Capture current TextureView bitmap
        backgroundHandler?.post {
            try {
                val bitmap = getBitmap()  // TextureView built-in method
                if (bitmap == null) {
                    callback("")
                    return@post
                }

                // Flip horizontally (front camera mirror correction)
                val matrix = Matrix().apply { preScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f) }
                val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                // Compress to JPEG and encode to base64
                val stream = ByteArrayOutputStream()
                flipped.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

                callback(base64)
            } catch (e: Exception) {
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