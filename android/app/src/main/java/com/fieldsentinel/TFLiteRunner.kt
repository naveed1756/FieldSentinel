package com.fieldsentinel

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteRunner(private val context: Context) {

    private var faceNetInterpreter: Interpreter? = null
    private var antispoofInterpreter: Interpreter? = null
    private var blazeFaceInterpreter: Interpreter? = null

    private val options = Interpreter.Options().apply {
        setNumThreads(4)
        setUseNNAPI(false) //produced error on some devices; can benchmark with and without NNAPI to see if it helps
    }

    fun loadModels() {
        try {
            faceNetInterpreter = Interpreter(loadModel("mobilefacenet.tflite"), options)
            antispoofInterpreter = Interpreter(loadModel("antispoof.tflite"), options)
            blazeFaceInterpreter    = Interpreter(loadModel("blaze_face_short_range.tflite"), options)
            logModelInfo()
        } catch (e: Exception) {
            // Models not yet present — will load when files are added
            android.util.Log.w("TFLiteRunner", "Models not loaded: ${e.message}")
        }
    }

    fun areModelsLoaded(): Boolean {
        return faceNetInterpreter != null && antispoofInterpreter != null
    }

    // Load a .tflite file from the assets folder
    private fun loadModel(modelName: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    private fun logModelInfo() {
    val faceNetInput   = faceNetInterpreter?.getInputTensor(0)
    val faceNetOutput  = faceNetInterpreter?.getOutputTensor(0)
    val antispoofInput  = antispoofInterpreter?.getInputTensor(0)
    val antispoofOutput = antispoofInterpreter?.getOutputTensor(0)

    android.util.Log.d("TFLiteRunner", "FaceNet input  type: ${faceNetInput?.dataType()}")
    android.util.Log.d("TFLiteRunner", "FaceNet output type: ${faceNetOutput?.dataType()}")
    android.util.Log.d("TFLiteRunner", "FaceNet output shape: ${faceNetOutput?.shape()?.toList()}")
    android.util.Log.d("TFLiteRunner", "Antispoof input  type: ${antispoofInput?.dataType()}")
    android.util.Log.d("TFLiteRunner", "Antispoof output type: ${antispoofOutput?.dataType()}")
    android.util.Log.d("TFLiteRunner", "Antispoof output shape: ${antispoofOutput?.shape()?.toList()}")
}

fun runFaceNet(bitmap: Bitmap): FloatArray {
    // Input: INT8
    val input = preprocessInt8(bitmap, 112, 112)

    // Output: INT8 [1, 128]
    val outputInt8 = Array(1) { ByteArray(128) }
    faceNetInterpreter!!.run(input, outputInt8)

    // Dequantize INT8 output to float using tensor scale and zero point
    val outputTensor = faceNetInterpreter!!.getOutputTensor(0)
    val scale     = outputTensor.quantizationParams().scale
    val zeroPoint = outputTensor.quantizationParams().zeroPoint

    val embedding = FloatArray(128) { i ->
        (outputInt8[0][i].toInt() - zeroPoint) * scale
    }

    return normalizeEmbedding(embedding)
}

fun runAntispoof(bitmap: Bitmap): Float {
    // Input: FLOAT32
    val input  = preprocessFloat(bitmap, 80, 80)

    // Output: FLOAT32 [1, 3]
    val output = Array(1) { FloatArray(3) }
    antispoofInterpreter!!.run(input, output)

    android.util.Log.d("AntiSpoof", "Raw scores — spoof:${output[0][0]} real1:${output[0][1]} real2:${output[0][2]}")

    // index 0 = spoof, index 1 and 2 = real variants
    return maxOf(output[0][1], output[0][2])
}

// Runs BlazeFace detection on a bitmap
// Returns FaceDetectionResult with bbox, keypoints, and confidence
fun runFaceDetection(bitmap: Bitmap): FaceDetectionResult {
    val interpreter = blazeFaceInterpreter
        ?: return FaceDetectionResult(detected = false, confidence = 0f)

    // BlazeFace short-range input: [1, 128, 128, 3] FLOAT32 normalized to [0, 1]
    val scaled = Bitmap.createScaledBitmap(bitmap, 128, 128, true)
    val inputBuffer = ByteBuffer.allocateDirect(4 * 128 * 128 * 3)
    inputBuffer.order(ByteOrder.nativeOrder())
    inputBuffer.rewind()

    val pixels = IntArray(128 * 128)
    scaled.getPixels(pixels, 0, 128, 0, 0, 128, 128)
    for (pixel in pixels) {
        inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
        inputBuffer.putFloat(((pixel shr 8)  and 0xFF) / 255f)
        inputBuffer.putFloat((pixel           and 0xFF) / 255f)
    }
    inputBuffer.rewind()

    // BlazeFace short-range outputs:
    // output[0]: [1, 896, 16] — raw regressors (bounding boxes + keypoints)
    // output[1]: [1, 896, 1]  — classification scores (confidence)
    val regressors = Array(1) { Array(896) { FloatArray(16) } }
    val scores     = Array(1) { Array(896) { FloatArray(1) } }

    val outputs = mapOf<Int, Any>(0 to regressors, 1 to scores)
    interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

    // Find the anchor with the highest score
    var bestScore = -Float.MAX_VALUE
    var bestIdx   = -1
    for (i in 0 until 896) {
        val s = scores[0][i][0]
        if (s > bestScore) { bestScore = s; bestIdx = i }
    }

    // Apply sigmoid to get probability
    val confidence = 1f / (1f + Math.exp(-bestScore.toDouble())).toFloat()

    if (confidence < 0.60f || bestIdx == -1) {
        return FaceDetectionResult(detected = false, confidence = confidence)
    }

    // Extract bounding box from best anchor (values are relative 0-1)
    val reg = regressors[0][bestIdx]
    // reg[0]=yCenter, reg[1]=xCenter, reg[2]=height, reg[3]=width
    // reg[4,5]=rightEye, reg[6,7]=leftEye (keypoint pairs as x,y)
    val xCenter = reg[1]
    val yCenter = reg[0]
    val width   = reg[3]
    val height  = reg[2]

    val left   = (xCenter - width  / 2f).coerceIn(0f, 1f)
    val top    = (yCenter - height / 2f).coerceIn(0f, 1f)
    val right  = (xCenter + width  / 2f).coerceIn(0f, 1f)
    val bottom = (yCenter + height / 2f).coerceIn(0f, 1f)

    // Keypoints: rightEye at [4,5], leftEye at [6,7]
    val rightEyeX = reg[4]; val rightEyeY = reg[5]
    val leftEyeX  = reg[6]; val leftEyeY  = reg[7]

    // Compute tilt angle in degrees
    val dx    = (leftEyeX - rightEyeX).toDouble()
    val dy    = (leftEyeY - rightEyeY).toDouble()
    val angle = Math.toDegrees(Math.atan2(dy, dx)).toFloat()

    android.util.Log.d("FaceAligner", "Tilt angle: $angle degrees, isAligned: ${Math.abs(angle) <= 15f}")
    android.util.Log.d("TFLiteRunner", "BlazeFace: conf=${"%.3f".format(confidence)} bbox=[$left,$top,$right,$bottom] angle=${"%.1f".format(angle)}°")

    return FaceDetectionResult(
        detected    = true,
        confidence  = confidence,
        left        = left,
        top         = top,
        right       = right,
        bottom      = bottom,
        rightEyeX   = rightEyeX,
        rightEyeY   = rightEyeY,
        leftEyeX    = leftEyeX,
        leftEyeY    = leftEyeY,
        tiltAngle   = angle
    )
}

    // Convert bitmap to normalized float ByteBuffer [-1, 1]
    // For FLOAT32 models — writes normalized floats, 4 bytes per channel
// FLOAT32 preprocessing — for antispoof model
private fun preprocessFloat(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val byteBuffer = ByteBuffer.allocateDirect(4 * width * height * 3)
    byteBuffer.order(ByteOrder.nativeOrder())
    byteBuffer.rewind()

    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)

    for (pixel in pixels) {
        val r = ((pixel shr 16) and 0xFF)
        val g = ((pixel shr 8)  and 0xFF)
        val b = (pixel          and 0xFF)
        byteBuffer.putFloat((r - 128f) / 128f)
        byteBuffer.putFloat((g - 128f) / 128f)
        byteBuffer.putFloat((b - 128f) / 128f)
    }
    byteBuffer.rewind()
    return byteBuffer
}

// INT8 preprocessing — for facenet model
private fun preprocessInt8(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val byteBuffer = ByteBuffer.allocateDirect(width * height * 3)
    byteBuffer.order(ByteOrder.nativeOrder())
    byteBuffer.rewind()

    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)

    for (pixel in pixels) {
        byteBuffer.put(((pixel shr 16) and 0xFF).toByte())
        byteBuffer.put(((pixel shr 8)  and 0xFF).toByte())
        byteBuffer.put((pixel          and 0xFF).toByte())
    }
    byteBuffer.rewind()
    return byteBuffer
}

    // Normalize embedding to unit vector (required for cosine similarity)
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        val magnitude = Math.sqrt(embedding.map { it * it }.sum().toDouble()).toFloat()
        return FloatArray(embedding.size) { i -> embedding[i] / magnitude }
    }

    // Cosine similarity between two unit vectors
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        return a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
    }

    fun close() {
        faceNetInterpreter?.close()
        antispoofInterpreter?.close()
        blazeFaceInterpreter?.close()
    }
}