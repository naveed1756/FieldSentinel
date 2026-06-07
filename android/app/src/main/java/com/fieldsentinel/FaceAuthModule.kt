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
        if (embeddingStore.hasEnrollment(employeeId)) {
            android.util.Log.w("FaceAuthModule", "Re-enrolling $employeeId — previous embedding will be overwritten")
        }
        val alignedBitmaps = mutableListOf<Bitmap>()

        for (i in 0 until base64Frames.size()) {
            val base64 = base64Frames.getString(i) ?: continue
            val bytes  = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue

            // Run face detection on this frame
            val detection = tfLiteRunner.runFaceDetection(bitmap)

            if (!detection.detected) {
                android.util.Log.w("FaceAuthModule", "Enrollment frame $i: no face detected (conf=${detection.confidence})")
                continue  // skip this frame, try next
            }

            if (!FaceAligner.isAligned(detection)) {
                android.util.Log.w("FaceAuthModule", "Enrollment frame $i: face tilted ${detection.tiltAngle}°, skipping")
                continue  // skip tilted frame
            }

            // Crop and align
            val aligned = FaceAligner.cropAndAlign(bitmap, detection)
            alignedBitmaps.add(aligned)
        }

        if (alignedBitmaps.size < 3) {
            promise.reject("ENROLL_ERROR",
                "Could not detect a properly aligned face in enough frames. " +
                "Please face the camera directly and ensure good lighting.")
            return
        }

        val success = cascadeController.enroll(employeeId, alignedBitmaps)
        if (success) promise.resolve("ENROLLED")
        else promise.reject("ENROLL_ERROR", "Enrollment processing failed")

    } catch (e: Exception) {
        promise.reject("ENROLL_ERROR", e.message)
    }
}

@ReactMethod
fun authenticate(
    employeeId: String,
    base64Frame: String,
    detectionConfidence: Double,  // kept for API compatibility, now overridden by real detection
    promise: Promise
) {
    try {
        val bytes  = Base64.decode(base64Frame, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw Exception("Could not decode image")

        // Run face detection
        val detection = tfLiteRunner.runFaceDetection(bitmap)

        if (!detection.detected) {
            val map = Arguments.createMap().apply {
                putBoolean("success",       false)
                putString("authResult",     "FAIL_NO_FACE")
                putString("abortStage",     "NO_FACE")
                putString("livenessMethod", "not_reached")
                putDouble("faceMatchScore", 0.0)
                putDouble("antispoofScore", 0.0)
                putBoolean("driftUpdated",  false)
                putString("errorMessage",   "No face detected. Please face the camera directly.")
            }
            promise.resolve(map)
            return
        }

        if (!FaceAligner.isAligned(detection)) {
            val map = Arguments.createMap().apply {
                putBoolean("success",       false)
                putString("authResult",     "FAIL_NO_FACE")
                putString("abortStage",     "FACE_TILTED")
                putString("livenessMethod", "not_reached")
                putDouble("faceMatchScore", 0.0)
                putDouble("antispoofScore", 0.0)
                putBoolean("driftUpdated",  false)
                putString("errorMessage",   "Face is tilted. Please look straight at the camera.")
            }
            promise.resolve(map)
            return
        }

        // Crop and align face — used for recognition only
        val alignedFace = FaceAligner.cropAndAlign(bitmap, detection)

// Anti-spoof gets the ORIGINAL full bitmap for better texture analysis
// MiniFASNet needs context around the face to detect paper/screen texture
        val result = cascadeController.authenticate(
            employeeId              = employeeId,
            faceBitmap              = alignedFace,  // aligned crop for recognition
            antispoofBitmap         = bitmap,       // full frame for anti-spoof
            faceDetectionConfidence = detection.confidence
        )

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