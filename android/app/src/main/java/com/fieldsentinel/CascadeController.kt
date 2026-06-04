package com.fieldsentinel

import android.graphics.Bitmap

// Represents the final result of one authentication attempt
data class AuthResult(
    val success: Boolean,
    val abortStage: String?,       // null if completed fully
    val authResult: String,        // SUCCESS | FAIL_SPOOF | FAIL_LIVENESS | FAIL_RECOGNITION | FAIL_NO_FACE
    val faceMatchScore: Float,     // 0.0 if not reached
    val antispoofScore: Float,     // 0.0 if not reached
    val livenessMethod: String,    // "blink" | "skipped_high_conf" | "failed" | "not_reached"
    val driftUpdated: Boolean
)

class CascadeController(
    private val tfLiteRunner: TFLiteRunner,
    private val embeddingStore: EmbeddingStore,
    private val livenessDetector: LivenessDetector
) {

    // Thresholds — must match constants.ts on the JS side
    companion object {
        const val FACE_DETECT_MIN        = 0.60f
        const val ANTISPOOF_REJECT       = 0.40f
        const val ANTISPOOF_SKIP_BLINK   = 0.80f
        const val RECOGNITION_MATCH      = 0.70f
        const val DRIFT_UPDATE_MIN_SCORE = 0.85f
        const val DRIFT_ALPHA            = 0.10f
    }

    // Main entry point — called from FaceAuthModule
    // faceBitmap: already cropped and aligned 112x112 face image
    // antispoofBitmap: same face cropped to 80x80 for antispoof model
    fun authenticate(
        employeeId: String,
        faceBitmap: Bitmap,
        antispoofBitmap: Bitmap,
        faceDetectionConfidence: Float
    ): AuthResult {

        // ── GATE 1: Face Detection confidence check ───────────────────
        if (faceDetectionConfidence < FACE_DETECT_MIN) {
            return AuthResult(
                success        = false,
                abortStage     = "NO_FACE",
                authResult     = "FAIL_NO_FACE",
                faceMatchScore = 0f,
                antispoofScore = 0f,
                livenessMethod = "not_reached",
                driftUpdated   = false
            )
        }

        // ── GATE 2: Passive anti-spoofing ─────────────────────────────
        val antispoofScore = tfLiteRunner.runAntispoof(antispoofBitmap)

        if (antispoofScore <= ANTISPOOF_REJECT) {
            return AuthResult(
                success        = false,
                abortStage     = "ANTISPOOF_FAIL",
                authResult     = "FAIL_SPOOF",
                faceMatchScore = 0f,
                antispoofScore = antispoofScore,
                livenessMethod = "not_reached",
                driftUpdated   = false
            )
        }

        // ── GATE 3: Active liveness (conditional) ─────────────────────
        var livenessMethod = "skipped_high_conf"

        if (antispoofScore < ANTISPOOF_SKIP_BLINK) {
            // Ambiguous anti-spoof score — require blink to confirm
            val blinkDetected = livenessDetector.waitForBlink()

            if (!blinkDetected) {
                return AuthResult(
                    success        = false,
                    abortStage     = "LIVENESS_TIMEOUT",
                    authResult     = "FAIL_LIVENESS",
                    faceMatchScore = 0f,
                    antispoofScore = antispoofScore,
                    livenessMethod = "failed",
                    driftUpdated   = false
                )
            }
            livenessMethod = "blink"
        }

        // ── GATE 4: Face Recognition ───────────────────────────────────
        val storedEmbedding = embeddingStore.loadEmbedding(employeeId)
            ?: return AuthResult(
                success        = false,
                abortStage     = "ENROLLMENT_MISSING",
                authResult     = "FAIL_NO_FACE",
                faceMatchScore = 0f,
                antispoofScore = antispoofScore,
                livenessMethod = livenessMethod,
                driftUpdated   = false
            )

        val liveEmbedding  = tfLiteRunner.runFaceNet(faceBitmap)
        val matchScore     = tfLiteRunner.cosineSimilarity(liveEmbedding, storedEmbedding)

        if (matchScore < RECOGNITION_MATCH) {
            return AuthResult(
                success        = false,
                abortStage     = "RECOGNITION_FAIL",
                authResult     = "FAIL_RECOGNITION",
                faceMatchScore = matchScore,
                antispoofScore = antispoofScore,
                livenessMethod = livenessMethod,
                driftUpdated   = false
            )
        }

        // ── SUCCESS: Check if embedding drift update needed ────────────
        var driftUpdated = false

        if (matchScore >= DRIFT_UPDATE_MIN_SCORE) {
            val updated = updateEmbeddingReference(
                employeeId      = employeeId,
                liveEmbedding   = liveEmbedding,
                storedEmbedding = storedEmbedding
            )
            driftUpdated = updated
        }

        return AuthResult(
            success        = true,
            abortStage     = null,
            authResult     = "SUCCESS",
            faceMatchScore = matchScore,
            antispoofScore = antispoofScore,
            livenessMethod = livenessMethod,
            driftUpdated   = driftUpdated
        )
    }

    // Enrollment — captures 5 embeddings and averages them
    fun enroll(employeeId: String, faceBitmaps: List<Bitmap>): Boolean {
        if (faceBitmaps.isEmpty()) return false

        // Generate one embedding per bitmap
        val embeddings = faceBitmaps.map { tfLiteRunner.runFaceNet(it) }

        // Element-wise average across all embeddings
        val averaged = FloatArray(128) { i ->
            embeddings.sumOf { it[i].toDouble() }.toFloat() / embeddings.size
        }

        // Re-normalize to unit vector
        val magnitude = Math.sqrt(averaged.map { it * it }.sum().toDouble()).toFloat()
        val normalized = FloatArray(128) { i -> averaged[i] / magnitude }

        // Encrypt and store
        embeddingStore.saveEmbedding(employeeId, normalized)
        return true
    }

    // Weighted rolling average — 10% toward new embedding
    private fun updateEmbeddingReference(
        employeeId: String,
        liveEmbedding: FloatArray,
        storedEmbedding: FloatArray
    ): Boolean {
        return try {
            val updated = FloatArray(128) { i ->
                (1 - DRIFT_ALPHA) * storedEmbedding[i] + DRIFT_ALPHA * liveEmbedding[i]
            }

            // Re-normalize to unit vector after drift
            val magnitude = Math.sqrt(updated.map { it * it }.sum().toDouble()).toFloat()
            val normalized = FloatArray(128) { i -> updated[i] / magnitude }

            embeddingStore.saveEmbedding(employeeId, normalized)
            true
        } catch (e: Exception) {
            false
        }
    }
}