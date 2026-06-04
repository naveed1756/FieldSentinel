package com.fieldsentinel

// LivenessDetector handles the blink detection challenge.
// The actual EAR computation happens from landmark data
// passed in from the JS side (MediaPipe runs in JS layer).
// This class manages the state machine — waiting, confirmed, timed out.

class LivenessDetector {

    companion object {
        const val BLINK_TIMEOUT_MS       = 10000L  // 10 seconds
        const val EAR_BLINK_THRESHOLD    = 0.20f   // below this = eye closed
        const val EAR_CONSECUTIVE_FRAMES = 2       // frames needed to confirm
    }

    private var closedFrameCount = 0
    private var blinkConfirmed   = false
    private var waitStartTime    = 0L
    private var isWaiting        = false

    // Call this to start a new blink challenge session
    fun startChallenge() {
        closedFrameCount = 0
        blinkConfirmed   = false
        waitStartTime    = System.currentTimeMillis()
        isWaiting        = true
    }

    // Feed each frame's average EAR value as camera frames come in
    // Returns true when blink is confirmed, false if timed out
    fun feedEAR(ear: Float): BlinkState {
        if (!isWaiting) return BlinkState.NOT_STARTED

        val elapsed = System.currentTimeMillis() - waitStartTime
        if (elapsed > BLINK_TIMEOUT_MS) {
            isWaiting = false
            return BlinkState.TIMED_OUT
        }

        if (ear < EAR_BLINK_THRESHOLD) {
            closedFrameCount++
        } else {
            // Eye opened after being closed — check if it was long enough
            if (closedFrameCount >= EAR_CONSECUTIVE_FRAMES) {
                blinkConfirmed = true
                isWaiting      = false
                return BlinkState.CONFIRMED
            }
            closedFrameCount = 0  // reset — was noise, not a real blink
        }

        return BlinkState.WAITING
    }

    // Blocking version used by CascadeController
    // In practice the JS side drives the frame loop and calls feedEAR
    // This is a placeholder for direct Kotlin testing
    fun waitForBlink(): Boolean {
        startChallenge()
        val deadline = System.currentTimeMillis() + BLINK_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (blinkConfirmed) return true
            Thread.sleep(16)  // ~60fps polling
        }
        return false
    }

    fun reset() {
        closedFrameCount = 0
        blinkConfirmed   = false
        isWaiting        = false
    }
}

enum class BlinkState {
    NOT_STARTED,
    WAITING,
    CONFIRMED,
    TIMED_OUT
}