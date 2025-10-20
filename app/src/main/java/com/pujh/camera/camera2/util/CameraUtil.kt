package com.pujh.camera.camera2.util

import android.graphics.Matrix
import android.util.Log
import android.util.Size

private const val TAG = "CameraUtil"

enum class ScaleType {
    FIT_XY,  //不保持宽高比，拉伸填充
    CENTER,  //保持原大小，居中显示
    CENTER_CROP,  //保持宽高比，居中，同时满足任意方向填充满，不会留空白
    CENTER_INSIDE,  //保持宽高比，居中，满足任意方向填充满即可，可能会留空白
}

/**
 * Camera2+TextureView进行预览时，默认采用全铺满的方式进行显示，并且确保了在设备自然方向，预览画面角度正确。
 * 但是当设备旋转后，displayOrientation可能改变，也可能不变(如锁定方向)。
 * 当displayOrientation改变后，Camera2模块其实是检测不到的，依旧按照原先计算的角度显示，导致画面角度不正确。
 *
 * 矩阵计算思路:
 *   1、同时旋转camera帧和surface
 *   2、计算surface的宽高缩放比，让旋转后的camera帧满足显示要求
 *   3、通过display裁剪surface区域
 *
 * 其中涉及的变量：
 * contentSize，camera frame旋转后的尺寸
 * surfaceSize，display旋转后的尺寸，最终可绘制的区域是surface和display交集
 *
 * @param cameraRotate 表示camera画面旋转多少度后，才能正确在当前屏幕上显示
 * @param displayRotation 屏幕旋转角度
 * @param cameraSize camera预览尺寸（camera数据流大小）
 * @param displaySize 显示控件（TextureView）大小
 * @param scaleType 缩放方式
 * @param mirror 对显示画面进行额外水平镜像，一般默认false即可。如果是前置摄像头，并且不希望镜像显示，则可设置为true
 */
fun getCamera2Matrix(
    cameraRotate: Int,
    displayRotation: Int,
    cameraSize: Size,
    displaySize: Size,
    scaleType: ScaleType = ScaleType.CENTER_CROP,
    mirror: Boolean = false
): Matrix {
    val matrix = Matrix()
    val remainRotate = (360 - displayRotation) % 360

    val contentWidth: Int
    val contentHeight: Int
    val surfaceWidth: Int
    val surfaceHeight: Int

    if (cameraRotate == 90 || cameraRotate == 270) {
        contentWidth = cameraSize.height
        contentHeight = cameraSize.width
    } else {
        contentWidth = cameraSize.width
        contentHeight = cameraSize.height
    }

    if (remainRotate == 90 || remainRotate == 270) {
        surfaceWidth = displaySize.height
        surfaceHeight = displaySize.width
    } else {
        surfaceWidth = displaySize.width
        surfaceHeight = displaySize.height
    }
    val scaleX: Float
    val scaleY: Float
    when (scaleType) {
        ScaleType.FIT_XY -> {
            scaleX = displaySize.width / surfaceWidth.toFloat()
            scaleY = displaySize.height / surfaceHeight.toFloat()
        }

        ScaleType.CENTER -> {
            // P * (surfaceSize / contentSize) * scale = P`
            // 当P = P`时， scale = contentSize / surfaceSize
            scaleX = contentWidth / surfaceWidth.toFloat()
            scaleY = contentHeight / surfaceHeight.toFloat()
        }

        ScaleType.CENTER_CROP -> {
            val displayRatio = displaySize.width / displaySize.height.toFloat()
            val contentRatio = contentWidth / contentHeight.toFloat()
            val scale = if (displayRatio > contentRatio) {
                displaySize.width / contentWidth.toFloat()
            } else {
                displaySize.height / contentHeight.toFloat()
            }
            //求得共同缩放比后，再乘各自的反形变缩放比，从而避免变形
            scaleX = scale * contentWidth / surfaceWidth.toFloat()
            scaleY = scale * contentHeight / surfaceHeight.toFloat()
        }

        ScaleType.CENTER_INSIDE -> {
            val displayRatio = displaySize.width / displaySize.height.toFloat()
            val contentRatio = contentWidth / contentHeight.toFloat()
            val scale = if (displayRatio > contentRatio) {
                displaySize.height / contentHeight.toFloat()
            } else {
                displaySize.width / contentWidth.toFloat()
            }
            //求得共同缩放比后，再乘各自的反形变缩放比，避免变形
            scaleX = scale * contentWidth / surfaceWidth.toFloat()
            scaleY = scale * contentHeight / surfaceHeight.toFloat()
        }
    }

    Log.d(TAG, "scaleX=$scaleX, scaleY=$scaleY, rotate=$remainRotate")

    val centerX = displaySize.width / 2f
    val centerY = displaySize.height / 2f
    matrix.postRotate(remainRotate.toFloat(), centerX, centerY)
    if (mirror) {
        matrix.postScale(-scaleX, scaleY, centerX, centerY)
    } else {
        matrix.postScale(scaleX, scaleY, centerX, centerY)
    }
    return matrix
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
