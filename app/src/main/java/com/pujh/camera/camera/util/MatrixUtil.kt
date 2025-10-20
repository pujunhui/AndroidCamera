package com.pujh.camera.camera.util

import android.graphics.Matrix
import android.util.Size

/**
 * 矩阵计算相关工具方法
 */

private const val TAG = "MatrixUtil"

enum class ScaleType {
    FIT_XY,  //不保持宽高比，拉伸填充
    CENTER,  //保持原大小，居中显示
    CENTER_CROP,  //保持宽高比，居中，同时满足任意方向填充满，不会留空白
    CENTER_INSIDE,  //保持宽高比，居中，满足任意方向填充满即可，可能会留空白
}

/**
 * Camera1+TextureView进行预览时，计算需要应用到 TextureView 的变换矩阵。
 * 此方法可以一次性对预览画面进行旋转、缩放、镜像等操作。
 *
 * 注意：必须手动设置 camera.setDisplayOrientation(0)，否则 camera 会自动旋转，导致计算错误。
 *
 * 矩阵计算思路：
 *   1. 计算旋转后的 camera 内容尺寸（rotatedSize）
 *   2. 根据 scaleType 计算合适的缩放比例
 *   3. 先旋转再缩放（围绕 display 中心）
 *
 * @param cameraRotate camera 画面需要旋转的角度（0, 90, 180, 270）
 * @param cameraSize camera 预览尺寸（未旋转的原始尺寸）
 * @param displaySize TextureView 的显示尺寸
 * @param scaleType 缩放方式
 * @param mirror 是否水平镜像（默认为false），前置摄像头已经镜像，如果想让其不镜像，这里可以设置为true，相当于二次镜像
 */
fun getPreviewMatrix(
    cameraRotate: Int,
    cameraSize: Size,
    displaySize: Size,
    scaleType: ScaleType = ScaleType.CENTER_CROP,
    mirror: Boolean = false
): Matrix {
    /**
     * TextureView 变换说明：
     * - TextureView 默认将 camera 内容拉伸到整个 View 尺寸
     * - setTransform() 在此基础上应用额外变换
     * - 我们需要：1) 旋转画面  2) 调整缩放避免变形
     *
     * 计算思路：
     * - rotatedSize: camera 内容旋转后的尺寸
     * - viewportSize: TextureView 坐标系中，旋转后的可视区域尺寸
     * - 缩放比例需要同时考虑旋转和 TextureView 默认拉伸的影响
     */

    // 旋转后的 camera 内容尺寸
    val rotatedWidth: Int
    val rotatedHeight: Int
    // TextureView 坐标系中，旋转后的视口尺寸
    val viewportWidth: Int
    val viewportHeight: Int

    if (cameraRotate == 90 || cameraRotate == 270) {
        rotatedWidth = cameraSize.height
        rotatedHeight = cameraSize.width
        viewportWidth = displaySize.height
        viewportHeight = displaySize.width
    } else {
        rotatedWidth = cameraSize.width
        rotatedHeight = cameraSize.height
        viewportWidth = displaySize.width
        viewportHeight = displaySize.height
    }

    // 根据 scaleType 计算最终缩放比例
    val scaleX: Float
    val scaleY: Float
    when (scaleType) {
        ScaleType.FIT_XY -> {
            // 拉伸填充：不保持宽高比
            scaleX = displaySize.width / viewportWidth.toFloat()
            scaleY = displaySize.height / viewportHeight.toFloat()
        }

        ScaleType.CENTER -> {
            // 保持原始大小：按实际尺寸缩放
            // P * (viewportSize / rotatedSize) * scale = P`
            // 当P = P`时， scale = rotatedSize / viewportSize
            scaleX = rotatedWidth / viewportWidth.toFloat()
            scaleY = rotatedHeight / viewportHeight.toFloat()
        }

        ScaleType.CENTER_CROP -> {
            // 保持宽高比，裁剪填充（确保填满，可能裁剪）
            val displayRatio = displaySize.width / displaySize.height.toFloat()
            val contentRatio = rotatedWidth / rotatedHeight.toFloat()

            // 选择能填满 display 的缩放比例（较大的）
            val scale = if (displayRatio > contentRatio) {
                displaySize.width / rotatedWidth.toFloat()
            } else {
                displaySize.height / rotatedHeight.toFloat()
            }

            // 补偿 TextureView 的默认拉伸
            //求得共同缩放比后，再乘各自的反形变缩放比，从而避免变形
            scaleX = scale * rotatedWidth / viewportWidth
            scaleY = scale * rotatedHeight / viewportHeight
        }

        ScaleType.CENTER_INSIDE -> {
            // 保持宽高比，完整显示（确保完整，可能留白）
            val displayRatio = displaySize.width / displaySize.height.toFloat()
            val contentRatio = rotatedWidth / rotatedHeight.toFloat()

            // 选择能完整显示的缩放比例（较小的）
            val scale = if (displayRatio > contentRatio) {
                displaySize.height / rotatedHeight.toFloat()
            } else {
                displaySize.width / rotatedWidth.toFloat()
            }

            // 补偿 TextureView 的默认拉伸
            //求得共同缩放比后，再乘各自的反形变缩放比，避免变形
            scaleX = scale * rotatedWidth / viewportWidth
            scaleY = scale * rotatedHeight / viewportHeight
        }
    }

    // 构建变换矩阵：先旋转，再缩放
    val matrix = Matrix()
    val centerX = displaySize.width / 2f
    val centerY = displaySize.height / 2f

    matrix.postRotate(cameraRotate.toFloat(), centerX, centerY)
    if (mirror) {
        matrix.postScale(-scaleX, scaleY, centerX, centerY)
    } else {
        matrix.postScale(scaleX, scaleY, centerX, centerY)
    }

    return matrix
}

/**
 * 用于将帧数据位置转换为原始预览画面的位置
 */
fun getFrameMatrix(
    cameraSize: Size,
    cameraOrientation: Int,
    isFrontCamera: Boolean,
    displaySize: Size
): Matrix {
    val drawMatrix = Matrix()

    //步骤1、将人脸检测结果的坐标系转化到camera数据帧的坐标系

    //将原点移到frame中心
    val frameCenterX: Float
    val frameCenterY: Float
    if (cameraOrientation == 90 || cameraOrientation == 270) {
        frameCenterX = cameraSize.height / 2f
        frameCenterY = cameraSize.width / 2f
    } else {
        frameCenterX = cameraSize.width / 2f
        frameCenterY = cameraSize.height / 2f
    }
    drawMatrix.postTranslate(-frameCenterX, -frameCenterY)
    //旋转frame
    val rotate = 360 - cameraOrientation
    drawMatrix.postRotate(rotate.toFloat())
    //旋转后frame尺寸将与camera数据帧的尺寸一致
    //再将原点移到新frame的左上角
    val cameraCenterX = cameraSize.width / 2f
    val cameraCenterY = cameraSize.height / 2f
    drawMatrix.postTranslate(cameraCenterX, cameraCenterY)

    //步骤2、将camera数据帧的坐标系，转化成预览帧的坐标系
    if (isFrontCamera) {
        drawMatrix.postScale(-1f, 1f, cameraCenterX, cameraCenterY);
    }

    //步骤3、将预览帧拉伸到整个View
    val scaleX = displaySize.width / cameraSize.width.toFloat()
    val scaleY = displaySize.height / cameraSize.height.toFloat()
    drawMatrix.postScale(scaleX, scaleY)

    //至此，已经将人脸检测结果的坐标系转化到默认预览坐标系
    //后续的步骤，是如何将预览帧正确的显示在view上，如旋转、设置宽高缩放比，是否需要镜像
    return drawMatrix
}