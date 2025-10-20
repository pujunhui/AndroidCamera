package com.pujh.camera.camera2

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.SessionConfiguration.SESSION_REGULAR
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

private const val TAG = "Camera2Helper"

/**
 * Camera2辅助类，封装Camera2的复杂操作
 */
class Camera2Helper(
    private val context: Context,
    private val callback: Camera2Callback
) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null

    private val cameraManager: CameraManager
        get() = context.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager

    /**
     * 打开相机并创建预览会话
     */
    @SuppressLint("MissingPermission")
    fun openCamera(
        cameraId: String,
        cameraSize: Size,
        texture: SurfaceTexture,
        outputs: List<Surface>
    ) {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createPreviewSession(camera, cameraSize, texture, outputs)
            }

            override fun onClosed(camera: CameraDevice) {
                Log.d(TAG, "Camera closed!")
                callback.onCameraClosed()
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.d(TAG, "Camera disconnected!")
                closeCamera()
                callback.onCameraError("Camera disconnected")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error: $error")
                closeCamera()
                callback.onCameraError("Camera error: $error")
            }
        }, null)
    }

    /**
     * 创建预览会话
     */
    private fun createPreviewSession(
        camera: CameraDevice,
        cameraSize: Size,
        texture: SurfaceTexture,
        outputs: List<Surface>
    ) {
        texture.setDefaultBufferSize(cameraSize.width, cameraSize.height)
        
        val surface = Surface(texture)
        previewSurface = surface

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val outputConfigurations = outputs.map { OutputConfiguration(it) }
            val config = SessionConfiguration(
                SESSION_REGULAR,
                outputConfigurations,
                ContextCompat.getMainExecutor(context),
                createSessionCallback(camera, surface)
            )
            camera.createCaptureSession(config)
        } else {
            camera.createCaptureSession(
                outputs,
                createSessionCallback(camera, surface),
                null
            )
        }
    }

    /**
     * 创建会话回调
     */
    private fun createSessionCallback(
        camera: CameraDevice,
        surface: Surface
    ): CameraCaptureSession.StateCallback {
        return object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                startPreview(camera, surface, session)
                callback.onCameraReady(camera, session)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Failed to configure camera session")
                callback.onCameraError("Failed to configure session")
            }
        }
    }

    /**
     * 开始预览
     */
    private fun startPreview(
        camera: CameraDevice,
        surface: Surface,
        session: CameraCaptureSession
    ) {
        val previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
        }.build()
        session.setRepeatingRequest(previewRequest, null, null)
    }

    /**
     * 拍照
     */
    fun takePicture(imageReader: ImageReader, captureCallback: CameraCaptureSession.CaptureCallback) {
        val camera = cameraDevice ?: return
        val session = captureSession ?: return

        val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(imageReader.surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.JPEG_ORIENTATION, 90)
        }.build()

        session.stopRepeating()
        session.abortCaptures()
        session.capture(captureRequest, captureCallback, null)
    }

    /**
     * 获取相机特性
     */
    fun getCameraCharacteristics(cameraId: String): CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(cameraId)
    }

    /**
     * 关闭相机
     */
    fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        previewSurface?.release()
        previewSurface = null
    }
}

/**
 * Camera2回调接口
 */
interface Camera2Callback {
    fun onCameraReady(camera: CameraDevice, session: CameraCaptureSession)
    fun onCameraClosed()
    fun onCameraError(error: String)
}

