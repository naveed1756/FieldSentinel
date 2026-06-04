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

    private val options = Interpreter.Options().apply {
        setNumThreads(4)
        setUseNNAPI(false)
    }

    fun loadModels() {
        try {
            faceNetInterpreter = Interpreter(loadModel("mobilefacenet.tflite"), options)
            antispoofInterpreter = Interpreter(loadModel("antispoof.tflite"), options)
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

    // index 0 = spoof, index 1 and 2 = real variants
    return maxOf(output[0][1], output[0][2])
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
    }
}