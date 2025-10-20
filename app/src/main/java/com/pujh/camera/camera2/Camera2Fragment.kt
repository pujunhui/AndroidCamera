package com.pujh.camera.camera2

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.pujh.camera.camera2.data.CameraInfo
import com.pujh.camera.camera2.util.ScaleType
import com.pujh.camera.camera2.util.getCamera2Matrix
import com.pujh.camera.camera2.util.getCameraId
import com.pujh.camera.camera2.util.getCameraInfo
import com.pujh.camera.camera2.util.getCameraRotate
import com.pujh.camera.camera2.util.getDisplayRotation
import com.pujh.camera.camera2.util.getOptimalPreviewSize
import com.pujh.camera.camera2.util.getOutputSizes
import com.pujh.camera.databinding.FragmentCamera2Binding

/**
 * camera2 默认全填充Surface，并且旋转了画面，确保displayOrientation=0时，画面角度正确。
 * 但是设备旋转后，displayOrientation可能改变，也可能不变(如锁定方向)。
 * 当displayOrientation改变后，Camera2模块其实是检测不到的，依旧按照原先计算的角度显示，导致画面角度不正确。
 *
 * 解决办法：
 *      假设device旋转90度，则display旋转-90度，只需要TextureView旋转90度，画面就能正常显示
 *
 * Camera1录制视频可以使用 MediaRecorder.setCamera(Camera) 方法，
 * 将MediaRecorder创建，延迟到录制时，而无需重建Camera
 *
 * Camera2录制视频，需要在创建session时，就指定需要输出的surface。
 * 而MediaRecorder.getSurface(),必须要在调用prepare()后，而prepare()调用前需要指定outputFile。
 * 换句话说，想要录制视频，在创建session时，就需要指定录制文件。
 * 但是，我们一般会有在一次预览中，录制多个视频文件的需求，这就要求我们每次录制时，都要重建Camera
 *
 * 如果我们的app最低支持的系统版本>=23，则可以通过MediaCodec.createPersistentInputSurface()方法提前创建Surface。
 * 当我们需要录制视频时，再创建MediaRecorder，并调用MediaRecorder.setInputSurface(Surface)
 *
 * 如果我们的app最低支持的系统版本<23，则需要使用MediaCodec+AudioRecord+MediaMuxer
 */

class Camera2Fragment : Fragment(), Camera2Callback {
    private lateinit var binding: FragmentCamera2Binding

    private lateinit var textureView: TextureView

    // 相机配置
    private var facing = CameraCharacteristics.LENS_FACING_BACK
    private var scaleType: ScaleType = ScaleType.CENTER_CROP

    // Helper类
    private val cameraHelper: Camera2Helper by lazy { Camera2Helper(requireContext(), this) }
    private val imageCaptureHelper: ImageCaptureHelper by lazy { ImageCaptureHelper(requireContext()) }

    // 状态
    private var currentCamera: CameraDevice? = null
    private var currentSession: CameraCaptureSession? = null

    // TextureView相关
    private val texture: SurfaceTexture?
        get() = textureView.takeIf { it.isAvailable }?.surfaceTexture

    private val displaySize: Size?
        get() = textureView.takeIf { it.isAvailable }?.let { Size(it.width, it.height) }

    private val cameraId: String
        get() = requireContext().getCameraId(facing)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCamera2Binding.inflate(inflater, container, false)
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

    override fun onResume() {
        super.onResume()
        if (textureView.isAvailable) {
            reopenCamera()
        }
        binding.textureView.surfaceTextureListener = textureListener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        closeCamera()
        imageCaptureHelper.release()
    }

    // ==================== TextureView监听 ====================

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

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    // ==================== 相机操作 ====================

    private fun reopenCamera() {
        closeCamera()

        val texture = this.texture ?: return
        val displaySize = this.displaySize ?: return

        openCamera(cameraId, texture, displaySize, scaleType)
    }

    private fun openCamera(
        cameraId: String,
        texture: SurfaceTexture,
        displaySize: Size,
        scaleType: ScaleType
    ) {
        // 获取相机特性
        val characteristics = cameraHelper.getCameraCharacteristics(cameraId)

        // 获取camera信息
        val cameraInfo = characteristics.getCameraInfo(cameraId)

        val sizeList = characteristics.getOutputSizes(SurfaceTexture::class.java)

        val displayRotation = requireActivity().getDisplayRotation()

        val isFrontCamera = cameraInfo.facing == CameraInfo.LENS_FACING_FRONT
        val cameraRotate = getCameraRotate(isFrontCamera, cameraInfo.orientation, displayRotation)

        // 根据displaySize和camera支持的分辨率列表，计算最佳尺寸
        val cameraSize = getOptimalPreviewSize(sizeList, cameraRotate, displaySize)

        Log.d(TAG, "Display size: ${displaySize.width}x${displaySize.height}")
        Log.d(TAG, "Camera rotate: $cameraRotate°")
        Log.d(TAG, "Optimal camera size: ${cameraSize.width}x${cameraSize.height}")

        // 计算并应用Matrix变换
        applyMatrixTransform(cameraId, cameraSize, displaySize, scaleType)

        // 创建ImageReader
        val imageReader = imageCaptureHelper.createImageReader(cameraSize) { reader ->
            handleCapturedImage(reader)
        }

        // 准备输出Surface
        val previewSurface = Surface(texture)
        val outputs = listOf(previewSurface, imageReader.surface)

        // 打开相机
        cameraHelper.openCamera(cameraId, cameraSize, texture, outputs)
    }

    private fun closeCamera() {
        cameraHelper.closeCamera()
        currentCamera = null
        currentSession = null
    }

    private fun switchCamera() {
        facing = if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        reopenCamera()
    }

    // ==================== Matrix变换 ====================

    private fun applyMatrixTransform(
        cameraId: String,
        cameraSize: Size,
        displaySize: Size,
        scaleType: ScaleType
    ) {
        val characteristics = cameraHelper.getCameraCharacteristics(cameraId)

        val isFrontCamera =
            characteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_FRONT
        val cameraOrientation = characteristics[CameraCharacteristics.SENSOR_ORIENTATION]!!
        val displayRotation = requireActivity().getDisplayRotation()

        val cameraRotate = getCameraRotate(isFrontCamera, cameraOrientation, displayRotation)
        val matrix = getCamera2Matrix(
            cameraRotate,
            displayRotation,
            cameraSize,
            displaySize,
            scaleType
        )

        textureView.setTransform(matrix)
    }

    // ==================== 拍照 ====================

    private fun takePhoto() {
        val imageReader = imageCaptureHelper.getImageReader() ?: return

        cameraHelper.takePicture(imageReader, object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                Log.d(TAG, "Capture completed")
            }
        })
    }

    private fun handleCapturedImage(reader: ImageReader) {
        val image = reader.acquireLatestImage() ?: return

        try {
            // 这里可以处理图片数据
            // 例如保存到相册
            image.close()

            // 示例：如果需要保存JPEG数据
            // val buffer = image.planes[0].buffer
            // val bytes = ByteArray(buffer.remaining())
            // buffer.get(bytes)
            // imageCaptureHelper.saveImageToGallery(bytes,
            //     onSuccess = {
            //         Toast.makeText(requireContext(), "保存成功", Toast.LENGTH_SHORT).show()
            //     },
            //     onError = { error ->
            //         Toast.makeText(requireContext(), "保存失败: ${error.message}", Toast.LENGTH_SHORT).show()
            //     }
            // )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle image", e)
            image.close()
        }
    }

    // ==================== Camera2Callback实现 ====================

    override fun onCameraReady(camera: CameraDevice, session: CameraCaptureSession) {
        currentCamera = camera
        currentSession = session
        Log.d(TAG, "Camera ready")
    }

    override fun onCameraClosed() {
        Log.d(TAG, "Camera closed")
    }

    override fun onCameraError(error: String) {
        Log.e(TAG, "Camera error: $error")
        Toast.makeText(requireContext(), "相机错误: $error", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "Camera2Fragment"
    }
}
