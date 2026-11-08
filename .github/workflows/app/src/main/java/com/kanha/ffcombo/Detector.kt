package com.kanha.ffcombo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Detection(val box: RectF, val score: Float)

class Detector(context: Context) {
    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize = 300
    private val numBoxes = 10

    init {
        val model = FileUtil.loadMappedFile(context, "detect.tflite")
        val options = Interpreter.Options().apply {
            setNumThreads(4)
            setUseXNNPACK(true)
        }
        interpreter = Interpreter(model, options)
        labels = FileUtil.loadLabels(context, "labelmap.txt")
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val input = preprocess(bitmap)
        val outputBoxes = Array(1) { Array(numBoxes) { FloatArray(4) } }
        val outputClasses = Array(1) { FloatArray(numBoxes) }
        val outputScores = Array(1) { FloatArray(numBoxes) }
        val outputCount = FloatArray(1)

        interpreter.runForMultipleInputsOutputs(
            arrayOf<Any>(input),
            mapOf(0 to outputBoxes, 1 to outputClasses, 2 to outputScores, 3 to outputCount)
        )

        val results = mutableListOf<Detection>()
        val count = outputCount[0].toInt().coerceIn(0, numBoxes)
        for (i in 0 until count) {
            val score = outputScores[0][i]
            if (score < 0.5f) continue
            val labelIdx = outputClasses[0][i].toInt()
            if (labels.getOrNull(labelIdx) != "person") continue
            val y1 = outputBoxes[0][i][0]
            val x1 = outputBoxes[0][i][1]
            val y2 = outputBoxes[0][i][2]
            val x2 = outputBoxes[0][i][3]
            results.add(Detection(
                RectF(x1 * bitmap.width, y1 * bitmap.height,
                    x2 * bitmap.width, y2 * bitmap.height), score))
        }
        return results
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            buffer.putFloat((r - 0.5f) * 2f)
            buffer.putFloat((g - 0.5f) * 2f)
            buffer.putFloat((b - 0.5f) * 2f)
        }
        buffer.rewind()
        return buffer
    }

    fun close() = interpreter.close()
}