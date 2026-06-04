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
        setUseNNAPI(true)
    }

    fun loadModels() {
        try {
            faceNetInterpreter = Interpreter(loadModel("mobilefacenet.tflite"), options)
            antispoofInterpreter = Interpreter(loadModel("antispoof.tflite"), options)
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

    // Runs MobileFaceNet and returns a 128-float embedding
    fun runFaceNet(bitmap: Bitmap): FloatArray {
        val input = preprocessBitmap(bitmap, 112, 112)
        val output = Array(1) { FloatArray(128) }
        faceNetInterpreter!!.run(input, output)
        return normalizeEmbedding(output[0])
    }

    // Runs anti-spoof model and returns a score 0.0–1.0
    // Score > 0.5 means real face, < 0.5 means spoof
    fun runAntispoof(bitmap: Bitmap): Float {
        val input = preprocessBitmap(bitmap, 80, 80) // antispoof model uses 80x80
        val output = Array(1) { FloatArray(2) } // 2 classes: [spoof, real]
        antispoofInterpreter!!.run(input, output)
        return output[0][1] // return the "real" class confidence
    }

    // Convert bitmap to normalized float ByteBuffer [-1, 1]
    private fun preprocessBitmap(bitmap: Bitmap, width: Int, height: Int): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val byteBuffer = ByteBuffer.allocateDirect(4 * width * height * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Normalize to [-1, 1]
            byteBuffer.putFloat((r - 128f) / 128f)
            byteBuffer.putFloat((g - 128f) / 128f)
            byteBuffer.putFloat((b - 128f) / 128f)
        }
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