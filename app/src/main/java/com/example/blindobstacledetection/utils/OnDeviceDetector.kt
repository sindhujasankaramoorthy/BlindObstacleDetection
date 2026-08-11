package com.example.blindobstacledetection.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.blindobstacledetection.models.DetectedObject
import com.example.blindobstacledetection.models.DetectionResponse
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class OnDeviceDetector(private val context: Context) {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var isInitialized = false

    companion object {
        private const val MODEL_NAME = "yolov8n.onnx"
        private const val INPUT_SIZE = 640

        val COCO_CLASSES = arrayOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
            "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
            "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
            "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
            "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
            "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"
        )
    }

    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized) return true
        return try {
            env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_NAME).readBytes()
            session = env?.createSession(modelBytes, OrtSession.SessionOptions())
            isInitialized = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun detect(jpegBytes: ByteArray, confThreshold: Float): DetectionResponse {
        val startTime = System.currentTimeMillis()
        if (!isInitialized && !initialize()) {
            return DetectionResponse(0, emptyList(), error = "Failed to initialize ONNX engine")
        }

        try {
            val originalBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: return DetectionResponse(0, emptyList(), error = "Invalid frame format")

            val origWidth = originalBitmap.width
            val origHeight = originalBitmap.height

            val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, INPUT_SIZE, INPUT_SIZE, true)

            // Convert Bitmap to NCHW FloatBuffer [1, 3, 640, 640] normalized 0..1
            val floatBuffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE)
            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            // Red channel
            for (p in pixels) {
                floatBuffer.put(((p shr 16) and 0xFF) / 255.0f)
            }
            // Green channel
            for (p in pixels) {
                floatBuffer.put(((p shr 8) and 0xFF) / 255.0f)
            }
            // Blue channel
            for (p in pixels) {
                floatBuffer.put((p and 0xFF) / 255.0f)
            }
            floatBuffer.rewind()

            val inputName = session?.inputNames?.iterator()?.next() ?: "images"
            val inputTensor = OnnxTensor.createTensor(env, floatBuffer, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))

            val results = session?.run(mapOf(inputName to inputTensor))
            val outputValue = results?.get(0)?.value

            val candidates = mutableListOf<RawDetection>()

            if (outputValue is Array<*>) {
                // shape [1][84][8400]
                val outputArray = (outputValue as Array<Array<FloatArray>>)[0] // [84][8400]
                val numBoxes = outputArray[0].size // 8400

                for (i in 0 until numBoxes) {
                    var maxScore = 0f
                    var maxClassId = -1

                    for (c in 0 until 80) {
                        val score = outputArray[4 + c][i]
                        if (score > maxScore) {
                            maxScore = score
                            maxClassId = c
                        }
                    }

                    if (maxScore >= confThreshold && maxClassId >= 0 && maxClassId < COCO_CLASSES.size) {
                        val cx = outputArray[0][i]
                        val cy = outputArray[1][i]
                        val w = outputArray[2][i]
                        val h = outputArray[3][i]

                        val x1 = max(0f, cx - w / 2f)
                        val y1 = max(0f, cy - h / 2f)
                        val x2 = min(INPUT_SIZE.toFloat(), cx + w / 2f)
                        val y2 = min(INPUT_SIZE.toFloat(), cy + h / 2f)

                        candidates.add(
                            RawDetection(
                                className = COCO_CLASSES[maxClassId],
                                confidence = maxScore,
                                x1 = x1 / INPUT_SIZE,
                                y1 = y1 / INPUT_SIZE,
                                x2 = x2 / INPUT_SIZE,
                                y2 = y2 / INPUT_SIZE
                            )
                        )
                    }
                }
            }

            inputTensor.close()
            results?.close()

            // Non-Maximum Suppression (NMS)
            val nmsDetections = applyNMS(candidates, iouThreshold = 0.45f)

            // Map to DetectedObject list
            val detectedObjects = nmsDetections.map { det ->
                val normBbox = listOf(det.x1, det.y1, det.x2, det.y2)
                val absBbox = listOf(det.x1 * origWidth, det.y1 * origHeight, det.x2 * origWidth, det.y2 * origHeight)

                val centerX = (det.x1 + det.x2) / 2f
                val boxHeight = (det.y2 - det.y1)

                val direction = when {
                    centerX < 0.35f -> "Left"
                    centerX > 0.65f -> "Right"
                    else -> "Center"
                }

                val fineDirection = when {
                    centerX < 0.20f -> "Far Left"
                    centerX < 0.40f -> "Slight Left"
                    centerX <= 0.60f -> "Center"
                    centerX <= 0.80f -> "Slight Right"
                    else -> "Far Right"
                }

                val distance = when {
                    boxHeight > 0.60f -> "Very Close"
                    boxHeight > 0.35f -> "Close"
                    boxHeight > 0.15f -> "Medium"
                    else -> "Far"
                }

                val priority = if (DetectedObject.HIGH_PRIORITY_CLASSES.contains(det.className.lowercase())) "HIGH" else "NORMAL"

                DetectedObject(
                    name = det.className.capitalizeWords(),
                    confidence = det.confidence,
                    direction = direction,
                    fineDirection = fineDirection,
                    distance = distance,
                    priority = priority,
                    bbox = absBbox,
                    normBbox = normBbox
                )
            }

            val elapsedMs = (System.currentTimeMillis() - startTime).toFloat()
            return DetectionResponse(
                count = detectedObjects.size,
                objects = detectedObjects,
                imageWidth = origWidth,
                imageHeight = origHeight,
                processingTimeMs = elapsedMs
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return DetectionResponse(0, emptyList(), error = "On-device detection failed: ${e.message}")
        }
    }

    private fun applyNMS(candidates: List<RawDetection>, iouThreshold: Float): List<RawDetection> {
        val sorted = candidates.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<RawDetection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (best.className == candidate.className && calculateIoU(best, candidate) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun calculateIoU(a: RawDetection, b: RawDetection): Float {
        val interX1 = max(a.x1, b.x1)
        val interY1 = max(a.y1, b.y1)
        val interX2 = min(a.x2, b.x2)
        val interY2 = min(a.y2, b.y2)

        val interArea = max(0f, interX2 - interX1) * max(0f, interY2 - interY1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    private fun String.capitalizeWords(): String {
        return this.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private data class RawDetection(
        val className: String,
        val confidence: Float,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    )
}
