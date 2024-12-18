package com.pujh.camera.camera

import android.R.attr
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.contentValuesOf
import androidx.fragment.app.Fragment
import com.pujh.camera.databinding.FragmentCameraBinding
import com.pujh.camera.util.ScaleType
import com.pujh.camera.util.getCameraMatrix
import com.pujh.camera.util.getCameraRotate
import com.pujh.camera.util.getDisplayRotation
import com.pujh.camera.util.save
import com.pujh.camera.util.yuvToBitmap
import java.io.File
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
class CameraFragment : Fragment() {
    private lateinit var binding: FragmentCameraBinding

    private var camera: Camera? = null
    private lateinit var textureView: TextureView

    private var facing = CameraInfo.CAMERA_FACING_BACK
    private val cameraId: Int
        get() = getCameraId(facing)
    private var cameraSize: Size = Size(1920, 1080)

    //    private var cameraSize: Size = Size(2592, 1944)
    private var scaleType: ScaleType = ScaleType.CENTER_CROP

    private val texture: SurfaceTexture?
        get() = textureView.takeIf { it.isAvailable }?.surfaceTexture

    private val displaySize: Size?
        get() = textureView.takeIf { it.isAvailable }?.let { Size(it.width, it.height) }


    private lateinit var mediaRecorder: MediaRecorder
    private var isRecording = false

    private lateinit var orientationEventListener: OrientationEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orientationEventListener = object : OrientationEventListener(requireContext()) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val cameraInfo = CameraInfo()
                Camera.getCameraInfo(cameraId, cameraInfo)
                val orientation = (orientation + 45) / 90 * 90
                val rotation = if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    (cameraInfo.orientation - attr.orientation + 360) % 360
                } else {  // back-facing camera
                    (cameraInfo.orientation + attr.orientation) % 360
                }
//                mParameters.setRotation(rotation)
//                Log.d(TAG, "phone orientation=$orientation")
            }
        }

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            MediaRecorder()
        }
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
        orientationEventListener.enable()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        orientationEventListener.disable()
        stopRecord()
        mediaRecorder.release()
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
        val texture = this.texture ?: return
        val displaySize = this.displaySize ?: return
        openCamera(cameraId, cameraSize, texture, displaySize, scaleType)
    }

    private fun openCamera(
        cameraId: Int,
        cameraSize: Size,
        texture: SurfaceTexture,
        displaySize: Size,
        scaleType: ScaleType
    ) {
        val camera = Camera.open(cameraId)

        val parameters = camera.parameters
        parameters.supportedPreviewFormats.forEach { format ->
            Log.d(TAG, "preview format = $format")
        }
        val dataFormat = ImageFormat.NV21
        parameters.previewFormat = dataFormat
        val previewSizes = parameters.supportedPreviewSizes
        previewSizes.forEach { size ->
            Log.d(TAG, "preview size = ${size.width}x${size.height}")
        }
//        val cameraSize = getOptimalPreviewSize(previewSizes, cameraRotate, displaySize)
        parameters.setPreviewSize(cameraSize.width, cameraSize.height)
        if (parameters.supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
        }
        if (parameters.isZoomSupported()) {
            parameters.zoom = 0
        }
        camera.parameters = parameters

        val bufferSize = cameraSize.width * cameraSize.height *
                ImageFormat.getBitsPerPixel(dataFormat) / 8
        repeat(2) {
            camera.addCallbackBuffer(ByteArray(bufferSize))
        }

        var i = 0
        camera.setPreviewCallbackWithBuffer { data, camera ->
            if (i == 10) {
                val file = File("/sdcard/Camera/")
                if (!file.exists()) {
                    file.mkdirs()
                }
                val bitmap = yuvToBitmap(
                    data,
                    dataFormat,
                    cameraSize.width,
                    cameraSize.height
                )
                bitmap.save("/sdcard/Camera/${System.currentTimeMillis()}-${cameraSize.width}x${cameraSize.height}.jpg")
            }
            i++
            camera.addCallbackBuffer(data)
        }

        //一定要设置方向，不然camera可能会帮你设置一个非0值
        camera.setDisplayOrientation(0)
        camera.setPreviewTexture(texture)
        camera.startPreview()
        this.camera = camera

        val cameraInfo = CameraInfo()
        Camera.getCameraInfo(cameraId, cameraInfo)
        val isFrontCamera = cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT
        val cameraOrientation = cameraInfo.orientation
        val displayRotation = requireContext().getDisplayRotation()
        val cameraRotate = getCameraRotate(isFrontCamera, cameraOrientation, displayRotation)
        val previewMatrix = getCameraMatrix(cameraRotate, cameraSize, displaySize, scaleType)
        textureView.setTransform(previewMatrix)
    }

    private fun closeCamera() {
        camera?.setPreviewCallback(null)
        camera?.stopPreview()
        camera?.release()
        camera = null
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
        camera?.takePicture(null, null) { data, camera ->
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

    private fun startRecord() {
        if (isRecording) {
            return
        }
        try {
            val width = 1920
            val height = 1080
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)  // 设置视频来源
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)  // 设置视频编码格式
            mediaRecorder.setVideoFrameRate(25)  //设置视频帧率
            mediaRecorder.setCaptureRate(30.0) // 设置视频捕获率
            mediaRecorder.setVideoSize(width, height)  //设置视频帧大小
            val bitRate = width * height * 8
            mediaRecorder.setVideoEncodingBitRate(bitRate)  //设置比特率

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC) // 设置音频来源从麦克风采集
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC) // 设置音频编码格式
            mediaRecorder.setAudioEncodingBitRate(96000)  // 设置音频编码比特率（一般音乐和语音录制）
            mediaRecorder.setAudioSamplingRate(44100)  // 设置音频采样率（CD音质）

            // 设置视频文件地址，需要读写权限
            mediaRecorder.setOutputFile("")
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)  //设置文件格式
            mediaRecorder.setOrientationHint(90)

            camera?.unlock()
            mediaRecorder.setCamera(camera)

            // prepare 执行之后 才能使用
            mediaRecorder.prepare()
            mediaRecorder.start()
            isRecording = true
        } catch (e: Exception) {
            isRecording = false
        }
    }

    private fun stopRecord() {
        if (!isRecording) {
            return
        }
        isRecording = false
        mediaRecorder.stop()
        mediaRecorder.reset()
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation = newConfig.orientation
        Log.d(TAG, "orientation = $orientation")
    }

    companion object {
        private const val TAG = "Camera1Fragment"
        private const val FILE_NAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}

private fun getCameraId(facing: Int, default: Int = 0): Int {
    return (0 until Camera.getNumberOfCameras()).firstOrNull { cameraId ->
        val cameraInfo = CameraInfo()
        Camera.getCameraInfo(cameraId, cameraInfo)
        cameraInfo.facing == facing
    } ?: default
}
