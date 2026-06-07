package com.fieldsentinel

data class FaceDetectionResult(
    val detected:    Boolean,
    val confidence:  Float,
    val left:        Float = 0f,   // relative 0-1
    val top:         Float = 0f,
    val right:       Float = 1f,
    val bottom:      Float = 1f,
    val rightEyeX:   Float = 0f,
    val rightEyeY:   Float = 0f,
    val leftEyeX:    Float = 0f,
    val leftEyeY:    Float = 0f,
    val tiltAngle:   Float = 0f    // degrees, 0 = eyes perfectly horizontal
)