package com.pujh.camera.camera2

import android.content.ContentResolver
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.contentValuesOf
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "ImageCaptureHelper"
private const val FILE_NAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

/**
 * 拍照辅助类，处理图片捕获和保存
 */
class ImageCaptureHelper(
    private val context: Context
) {
    private var imageReader: ImageReader? = null

    /**
     * 创建ImageReader
     */
    fun createImageReader(
        cameraSize: android.util.Size,
        onImageAvailable: (ImageReader) -> Unit
    ): ImageReader {
        val reader = ImageReader.newInstance(
            cameraSize.width,
            cameraSize.height,
            ImageFormat.JPEG,
            2
        )
        reader.setOnImageAvailableListener(onImageAvailable, null)
        imageReader = reader
        return reader
    }

    /**
     * 获取最大输出尺寸
     */
    fun getMaxOutputSize(characteristics: CameraCharacteristics): android.util.Size {
        val map = characteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]!!
        return map.getOutputSizes(ImageFormat.JPEG).maxBy { it.width * it.height }
    }

    /**
     * 保存图片到相册
     */
    fun saveImageToGallery(data: ByteArray, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        try {
            val imageUri = createImageUri(context.contentResolver) ?: run {
                onError(Exception("Failed to create image URI"))
                return
            }

            context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                outputStream.write(data)
                outputStream.flush()
                onSuccess()
            } ?: run {
                onError(Exception("Failed to open output stream"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            onError(e)
        }
    }

    /**
     * 创建图片URI
     */
    private fun createImageUri(contentResolver: ContentResolver): Uri? {
        val image = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val name = SimpleDateFormat(FILE_NAME_FORMAT, Locale.CHINA)
            .format(System.currentTimeMillis())

        val contentValues = contentValuesOf(
            MediaStore.MediaColumns.DISPLAY_NAME to name,
            MediaStore.MediaColumns.MIME_TYPE to "image/jpeg",
        )

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }

        return contentResolver.insert(image, contentValues)
    }

    /**
     * 释放资源
     */
    fun release() {
        imageReader?.close()
        imageReader = null
    }

    fun getImageReader(): ImageReader? = imageReader
}

