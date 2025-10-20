package com.pujh.camera.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.util.Log
import android.util.Size
import com.pujh.camera.camera.data.CameraInfo
import com.pujh.camera.camera.data.FrameInfo
import com.pujh.camera.camera.util.getCameraInfo
import com.pujh.camera.camera.util.getCameraRotate
import com.pujh.camera.camera.util.getDisplayRotation
import com.pujh.camera.camera.util.getOptimalPreviewSize

private const val TAG = "CameraHelper"

class CameraHelper(
    private val context: Context,
    private val callback: CameraCallback,
) {
    private var camera: Camera? = null
    private val cameraLock = Any()

    /**
     * 打开Camera
     * @param cameraId 摄像头ID
     * @param surface 预览 SurfaceTexture
     * @param displaySize 显示控件尺寸
     */
    fun openCamera(
        cameraId: Int,
        surface: SurfaceTexture,
        displaySize: Size,
    ) = synchronized(cameraLock) {
        if (camera != null) {
            Log.e(TAG, "camera has opened!")
            return@synchronized
        }

        //获取cameraInfo
        val cameraInfo = getCameraInfo(cameraId)
        Log.d(TAG, "start open camera: $cameraInfo")

        //打开camera
        val camera = Camera.open(cameraId)

        //设置预览格式
        val parameters = camera.parameters
        val previewFormat = getPreviewFormat(cameraInfo, parameters)
        parameters.previewFormat = previewFormat

        //设置预览尺寸
        val displayRotation = context.getDisplayRotation()
        val previewSize = getPreviewSize(cameraInfo, parameters, displaySize, displayRotation)
        parameters.setPreviewSize(previewSize.width, previewSize.height)

        //设置摄像头自动对焦
        if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }
        if (parameters.isZoomSupported()) {
            parameters.zoom = 0
        }
        camera.parameters = parameters

        //构建FrameInfo
        val frameInfo = FrameInfo(
            width = previewSize.width,
            height = previewSize.height,
            format = previewFormat,
            rotate = cameraInfo.orientation //这里需要注意，这里是camera数据帧的角度，即camera sensor角度，而不是display rotate
        )
        Log.d(TAG, "frame info: $frameInfo")

        val bufferSize = previewSize.width * previewSize.height *
                ImageFormat.getBitsPerPixel(previewFormat) / 8
        repeat(3) {
            camera.addCallbackBuffer(ByteArray(bufferSize))
        }
        camera.setPreviewCallbackWithBuffer { data, camera ->
            callback.onCameraFrame(camera, frameInfo, data)
            synchronized(cameraLock) {
                camera?.addCallbackBuffer(data)
            }
        }

        //一定要设置方向，不然camera可能会自动帮你设置一个非0值
        camera.setDisplayOrientation(0)
        camera.setPreviewTexture(surface)
        camera.startPreview()
        callback.onCameraReady(camera, cameraInfo, frameInfo)

        this.camera = camera
    }

    /**
     * 关闭Camera
     */
    fun closeCamera() = synchronized(cameraLock) {
        camera?.let { callback.onCameraClose(it) }
        camera?.setPreviewCallbackWithBuffer(null)
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    /**
     * 拍照
     */
    fun takePicture(
        shutter: Camera.ShutterCallback?,
        raw: Camera.PictureCallback?,
        jpeg: Camera.PictureCallback?,
    ) {
        camera?.takePicture(shutter, raw, jpeg)
    }
}

/**
 * 获取预览格式，目前只支持NV21
 */
private fun getPreviewFormat(cameraInfo: CameraInfo, parameters: Camera.Parameters): Int {
    if (parameters.supportedPreviewFormats.contains(ImageFormat.NV21)) {
        return ImageFormat.NV21
    }
    // 打印支持的格式
    parameters.supportedPreviewFormats.forEach { format ->
        Log.d(TAG, "preview format = $format")
    }
    throw RuntimeException("camera_${cameraInfo.cameraId}, not support preview format NV21.")
}

/**
 * 计算预览尺寸
 */
private fun getPreviewSize(
    cameraInfo: CameraInfo,
    parameters: Camera.Parameters,
    displaySize: Size,
    displayRotation: Int,
): Size {
    val sizeList = parameters.supportedPreviewSizes
    sizeList.forEach { size ->
        Log.d(TAG, "preview size = ${size.width}x${size.height}")
    }

    // 计算正确的旋转角度：camera画面需要旋转多少度才能跟当前屏幕方向匹配
    val isFrontCamera = cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT
    val cameraRotate = getCameraRotate(isFrontCamera, cameraInfo.orientation, displayRotation)

    Log.d(
        TAG,
        "cameraOrientation=${cameraInfo.orientation}, displayRotation=$displayRotation, cameraRotate=$cameraRotate"
    )

    val previewSize = getOptimalPreviewSize(sizeList, cameraRotate, displaySize)
    Log.d(TAG, "display size = ${displaySize.width}x${displaySize.height}")
    Log.d(TAG, "result size = ${previewSize.width}x${previewSize.height}")
    return previewSize
}