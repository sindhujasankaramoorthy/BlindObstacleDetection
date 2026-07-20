package com.example.blindobstacledetection

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {

        val nv21 = yuv420888ToNv21(image)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = ByteArrayOutputStream()

        yuvImage.compressToJpeg(
            Rect(
                0,
                0,
                image.width,
                image.height
            ),
            90,
            out
        )

        val imageBytes = out.toByteArray()

        return android.graphics.BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size
        )
    }

    fun bitmapToJpeg(bitmap: Bitmap): ByteArray {

        val stream = ByteArrayOutputStream()

        bitmap.compress(
            Bitmap.CompressFormat.JPEG,
            90,
            stream
        )

        return stream.toByteArray()

    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        return nv21
    }

}