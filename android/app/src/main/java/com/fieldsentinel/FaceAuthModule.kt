package com.fieldsentinel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.facebook.react.bridge.*

class FaceAuthModule(reactContext: ReactApplicationContext)
    : ReactContextBaseJavaModule(reactContext) {

    companion object {
        var activeCameraView: CameraPreviewView? = null
    }

    private val tfLiteRunner      = TFLiteRunner(reactContext)
    private val embeddingStore    = EmbeddingStore(reactContext)
    private val livenessDetector  = LivenessDetector()
    private val cascadeController = CascadeController(tfLiteRunner, embeddingStore, livenessDetector)

    init {
    tfLiteRunner.loadModels()
    }

    override fun getName(): String = "FaceAuthModule"

    // Called from JS: FaceAuthModule.enroll(employeeId, base64Frames)
    // base64Frames: array of 5 base64-encoded JPEG strings
    @ReactMethod
    fun enroll(employeeId: String, base64Frames: ReadableArray, promise: Promise) {
        try {
            val bitmaps = mutableListOf<Bitmap>()

            for (i in 0 until base64Frames.size()) {
                val base64 = base64Frames.getString(i) ?: continue
                val bytes  = Base64.decode(base64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) bitmaps.add(bitmap)
            }

            if (bitmaps.size < 3) {
                promise.reject("ENROLL_ERROR", "Not enough valid frames captured")
                return
            }

            val success = cascadeController.enroll(employeeId, bitmaps)

            if (success) {
                promise.resolve("ENROLLED")
            } else {
                promise.reject("ENROLL_ERROR", "Enrollment failed")
            }

        } catch (e: Exception) {
            promise.reject("ENROLL_ERROR", e.message)
        }
    }

    // Called from JS: FaceAuthModule.authenticate(employeeId, base64Frame, detectionConfidence)
    // base64Frame: single base64-encoded JPEG of the detected face region
    @ReactMethod
    fun authenticate(
        employeeId: String,
        base64Frame: String,
        detectionConfidence: Double,
        promise: Promise
    ) {
        try {
            val bytes            = Base64.decode(base64Frame, Base64.DEFAULT)
            val faceBitmap       = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw Exception("Could not decode face image")

            // For antispoof we use same bitmap — model handles resize internally in TFLiteRunner
            val antispoofBitmap  = faceBitmap

            val result = cascadeController.authenticate(
                employeeId              = employeeId,
                faceBitmap              = faceBitmap,
                antispoofBitmap         = antispoofBitmap,
                faceDetectionConfidence = detectionConfidence.toFloat()
            )

            // Build result map to send back to JS
            val map = Arguments.createMap().apply {
                putBoolean("success",        result.success)
                putString("authResult",      result.authResult)
                putString("abortStage",      result.abortStage ?: "")
                putDouble("faceMatchScore",  result.faceMatchScore.toDouble())
                putDouble("antispoofScore",  result.antispoofScore.toDouble())
                putString("livenessMethod",  result.livenessMethod)
                putBoolean("driftUpdated",   result.driftUpdated)
            }

            promise.resolve(map)

        } catch (e: Exception) {
            promise.reject("AUTH_ERROR", e.message)
        }
    }

    // Called from JS to check if an employee is enrolled
    @ReactMethod
    fun isEnrolled(employeeId: String, promise: Promise) {
        promise.resolve(embeddingStore.hasEnrollment(employeeId))
    }

    // Feed a single EAR value from the JS blink detection loop
    @ReactMethod
    fun feedEAR(ear: Double, promise: Promise) {
        val state = livenessDetector.feedEAR(ear.toFloat())
        promise.resolve(state.name)  // "WAITING" | "CONFIRMED" | "TIMED_OUT"
    }

    // Start a new blink challenge session
    @ReactMethod
    fun startBlinkChallenge(promise: Promise) {
        livenessDetector.startChallenge()
        promise.resolve("STARTED")
    }

    // Called from JS to capture a frame from the camera preview as base64 JPEG
    @ReactMethod
    fun captureFrame(promise: Promise) {
        val camera = activeCameraView
        if (camera == null) {
            promise.reject("NO_CAMERA", "Camera not initialized")
            return
        }
        camera.captureFrame { base64 ->
            if (base64.isEmpty()) {
                promise.reject("CAPTURE_FAILED", "Could not capture frame")
            } else {
                promise.resolve(base64)
            }
        }
    }
}