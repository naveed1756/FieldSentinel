package com.fieldsentinel

import android.graphics.Bitmap
import android.graphics.Matrix

object FaceAligner {

    // Max tilt angle in degrees — beyond this we reject the frame
    const val MAX_TILT_DEGREES = 15f

    // Returns true if face is aligned enough to proceed
    fun isAligned(result: FaceDetectionResult): Boolean {
        return Math.abs(result.tiltAngle) <= MAX_TILT_DEGREES
    }

    // Crops and aligns face from the original bitmap using detection result
    // Returns a bitmap ready for TFLite inference (not yet resized to model input)
    fun cropAndAlign(bitmap: Bitmap, result: FaceDetectionResult): Bitmap {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Convert relative coords to pixel coords
        val left   = (result.left   * w).toInt().coerceIn(0, bitmap.width)
        val top    = (result.top    * h).toInt().coerceIn(0, bitmap.height)
        val right  = (result.right  * w).toInt().coerceIn(0, bitmap.width)
        val bottom = (result.bottom * h).toInt().coerceIn(0, bitmap.height)

        val cropW = (right - left).coerceAtLeast(1)
        val cropH = (bottom - top).coerceAtLeast(1)

        // Crop to face bounding box
        val cropped = Bitmap.createBitmap(bitmap, left, top, cropW, cropH)

        // Rotate to correct tilt using eye landmarks
        val matrix = Matrix()
        matrix.postRotate(-result.tiltAngle)

        val aligned = Bitmap.createBitmap(
            cropped, 0, 0, cropped.width, cropped.height, matrix, true
        )

        android.util.Log.d("FaceAligner", "Aligned: ${aligned.width}x${aligned.height}, tilt corrected by ${result.tiltAngle}°")
        return aligned
    }
}