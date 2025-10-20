package com.pujh.camera.camera2.util

import android.content.Context
import android.content.Context.CAMERA_SERVICE
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import kotlin.math.abs

typealias AppCameraInfo = com.pujh.camera.camera2.data.CameraInfo

private const val TAG = "CameraUtil"

fun Context.getCameraId(facing: Int, default: String = "0"): String {
    val manager = getSystemService(CAMERA_SERVICE) as CameraManager
    return manager.cameraIdList.firstOrNull { cameraId ->
        val characteristics = manager.getCameraCharacteristics(cameraId)
        characteristics[CameraCharacteristics.LENS_FACING] == facing
    } ?: default
}

/**
 * 通过cameraId获取摄像头信息
 */
fun CameraCharacteristics.getCameraInfo(cameraId: String): AppCameraInfo {
    val facing = this[CameraCharacteristics.LENS_FACING]!!
    // CameraInfo的orientation，既是camera sensor的角度，也是camera frame的角度
    // camera frame顺时针旋转该角度后，将得到正确的图像
    val orientation = this[CameraCharacteristics.SENSOR_ORIENTATION]!!
    return AppCameraInfo(cameraId, facing, orientation)
}


fun <T> CameraCharacteristics.getOutputSizes(klass: Class<T>): List<Size> {
    // 获取StreamConfigurationMap
    val map = this[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
        ?: throw IllegalStateException("Cannot get SCALER_STREAM_CONFIGURATION_MAP")

    // 获取相机支持的输出尺寸（用于预览）
    return map.getOutputSizes(klass).asList()
}

/**
 * @param isFrontCamera 是否是前置摄像头，如果是则需要水平镜像画面
 * @param cameraOrientation camera sensor角度，即camera帧需要旋转多少角度，才跟设备的自然方向匹配
 * @param displayRotation 显示方向，一般为activity的方向
 * @return 返回camera旋转角度。camera画面旋转该角度后，能够与当前显示方向匹配
 */
fun getCameraRotate(
    isFrontCamera: Boolean,
    cameraOrientation: Int,
    displayRotation: Int
): Int {
    var result: Int
    if (isFrontCamera) {
        result = (cameraOrientation + displayRotation) % 360 //270+0
        result = (360 - result) % 360  // compensate the mirror
    } else { // back-facing
        result = (cameraOrientation - displayRotation + 360) % 360
    }
    return result
}

/**
 * 获取Camera的最佳预览尺寸
 *
 * @param sizeList 摄像头支持的分辨率列表
 * @param cameraRotate 表示camera画面旋转多少度后，才能跟当前屏幕方向匹配
 * @param displaySize 显示控件的尺寸
 * @return 返回摄像头最佳预览尺寸
 *
 * 算法策略：
 * 1. 优先选择宽高比匹配的尺寸
 * 2. 在宽高比匹配的尺寸中，优先选择分辨率接近且不低于目标的
 * 3. 如果没有宽高比匹配的，选择总体最接近的尺寸
 * 4. 保证总是从支持的尺寸列表中选择
 */
fun getOptimalPreviewSize(
    sizeList: List<Size>,
    cameraRotate: Int,
    displaySize: Size,
): Size {
    if (sizeList.isEmpty()) {
        throw IllegalArgumentException("No available output sizes")
    }

    // 根据旋转角度调整目标尺寸
    val targetWidth: Int
    val targetHeight: Int
    if (cameraRotate == 90 || cameraRotate == 270) {
        targetWidth = displaySize.height
        targetHeight = displaySize.width
    } else {
        targetWidth = displaySize.width
        targetHeight = displaySize.height
    }

    val targetRatio = targetWidth / targetHeight.toFloat()
    val targetArea = targetWidth * targetHeight

    // 第一轮：查找宽高比匹配的尺寸（容差 15%）
    val aspectTolerance = 0.15f
    val aspectMatchSizes = sizeList.filter { size ->
        val ratio = size.width / size.height.toFloat()
        abs(ratio - targetRatio) <= aspectTolerance
    }

    if (aspectMatchSizes.isNotEmpty()) {
        // 在宽高比匹配的尺寸中，优先选择分辨率接近目标且不小于目标的
        // 如果没有不小于目标的，则选择最接近的
        val largerOrEqualSizes = aspectMatchSizes.filter { size ->
            size.width >= targetWidth && size.height >= targetHeight
        }

        return if (largerOrEqualSizes.isNotEmpty()) {
            // 选择最接近目标的（面积差最小）
            largerOrEqualSizes.minBy { size ->
                abs(size.width * size.height - targetArea)
            }
        } else {
            // 没有大于等于目标的，选择最接近的（面积差最小）
            aspectMatchSizes.minBy { size ->
                abs(size.width * size.height - targetArea)
            }
        }
    }

    // 第二轮：没有宽高比匹配的，使用综合相似度评分
    // 评分 = 宽高比差异权重 + 分辨率差异权重
    val bestSize = sizeList.minByOrNull { size ->
        val ratio = size.width / size.height.toFloat()
        val ratioDiff = abs(ratio - targetRatio) / targetRatio // 归一化的比例差异

        val area = size.width * size.height
        val areaDiff = abs(area - targetArea) / targetArea.toFloat() // 归一化的面积差异

        // 综合评分：宽高比权重更高（0.7），分辨率权重较低（0.3）
        ratioDiff * 0.7f + areaDiff * 0.3f
    }

    return bestSize ?: throw IllegalStateException("Failed to select optimal size")
}