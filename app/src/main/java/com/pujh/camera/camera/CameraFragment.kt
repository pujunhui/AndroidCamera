package com.pujh.camera.camera

import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.contentValuesOf
import androidx.fragment.app.Fragment
import com.pujh.camera.camera.data.CameraInfo
import com.pujh.camera.camera.data.FrameInfo
import com.pujh.camera.camera.util.ScaleType
import com.pujh.camera.camera.util.getCameraId
import com.pujh.camera.camera.util.getCameraRotate
import com.pujh.camera.camera.util.getDisplayRotation
import com.pujh.camera.camera.util.getPreviewMatrix
import com.pujh.camera.databinding.FragmentCameraBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * camera1 默认全填充Surface
 *
 * 如果没有调用camera.setDisplayOrientation，camera会根据sensor方向以及是否是前置摄像头，计算出一个默认值。
 * 计算规则如下：
 *      sensorOrientation=0, displayOrientation=0,
 *      sensorOrientation=90, 如果是前置摄像头，displayOrientation=180，否则displayOrientation=0
 *      sensorOrientation=180, displayOrientation=180,
 *      sensorOrientation=270, 如果不是前置摄像头，displayOrientation=180，否则displayOrientation=0
 *
 * http://aospxref.com/android-12.0.0_r3/xref/frameworks/base/core/jni/android_hardware_Camera.cpp#627
 *
 * 这个计算规则十分难以理解，所以一般不会使用默认的displayOrientation。
 * 并且预览显示，不仅要考虑画面方向，还有考虑宽高缩放比、前置摄像头是否水平镜像等情况。
 * 所以我们可以直接设置displayOrientation=0，并将所有的旋转、缩放、镜像等操作放到一个Matrix中，并设置给TextureView。
 */
class CameraFragment : Fragment(), CameraCallback {
    private lateinit var binding: FragmentCameraBinding

    private lateinit var textureView: TextureView

    private var facing = CameraInfo.CAMERA_FACING_BACK
    private val cameraId: Int
        get() = getCameraId(facing)

    private var scaleType: ScaleType = ScaleType.CENTER_INSIDE

    private val surface: SurfaceTexture?
        get() = textureView.takeIf { it.isAvailable }?.surfaceTexture

    private val displaySize: Size?
        get() = textureView.takeIf { it.isAvailable }?.let { Size(it.width, it.height) }

    private val cameraHelper: CameraHelper by lazy {
        CameraHelper(requireContext(), this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = binding.textureView
        binding.imageCaptureBtn.setOnClickListener {
            takePhoto()
        }
        binding.switchCameraBtn.setOnClickListener {
            switchCamera()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        closeCamera()
    }

    override fun onResume() {
        super.onResume()
        if (textureView.isAvailable) {
            reopenCamera()
        }
        binding.textureView.surfaceTextureListener = textureListener
    }

    private val textureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            reopenCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            reopenCamera()
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            closeCamera()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        }
    }

    private fun reopenCamera() {
        closeCamera()
        openCamera(cameraId)
    }

    private fun openCamera(cameraId: Int) {
        val surface = this.surface ?: return
        val displaySize = this.displaySize ?: return
        cameraHelper.openCamera(cameraId, surface, displaySize)
    }

    private fun closeCamera() {
        cameraHelper.closeCamera()
    }

    private fun switchCamera() {
        facing = if (facing == CameraInfo.CAMERA_FACING_BACK) {
            CameraInfo.CAMERA_FACING_FRONT
        } else {
            CameraInfo.CAMERA_FACING_BACK
        }
        reopenCamera()
    }

    private fun takePhoto() {
        cameraHelper.takePicture(null, null) { data, camera ->
            val image: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            val contentResolver = requireContext().contentResolver
            val imageUri = contentResolver.insert(image, contentValues) ?: return@takePicture
            try {
                contentResolver.openOutputStream(imageUri)?.use {
                    it.write(data)
                    it.flush()
                } ?: return@takePicture
                Toast.makeText(requireContext(), "Take picture success!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                contentResolver.delete(imageUri, null, null)
            }
            camera.startPreview()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = newConfig.orientation
        Log.d(TAG, "orientation = $orientation")
    }

    override fun onCameraReady(
        camera: Camera,
        cameraInfo: CameraInfo,
        frameInfo: FrameInfo
    ) {
        // 计算正确的旋转角度：camera画面需要旋转多少度才能跟当前屏幕方向匹配
        val isFrontCamera = cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT
        val displayRotation = requireContext().getDisplayRotation()
        val cameraRotate = getCameraRotate(
            isFrontCamera,
            cameraInfo.orientation,
            displayRotation
        )

        val previewMatrix = getPreviewMatrix(
            cameraRotate = cameraRotate,
            cameraSize = Size(frameInfo.width, frameInfo.height),
            displaySize = displaySize!!,
            scaleType = scaleType,
        )
        textureView.setTransform(previewMatrix)
    }

    override fun onCameraFrame(
        camera: Camera,
        frameInfo: FrameInfo,
        data: ByteArray
    ) {
    }

    override fun onCameraClose(camera: Camera) {

    }

    companion object {
        private const val TAG = "Camera1Fragment"
        private const val FILE_NAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}

