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
 * Camera1+TextureView进行预览时，计算需要应用到 TextureView 的变换矩阵（方法2）。
 * 此方法可以一次性对预览画面进行旋转、缩放、镜像等操作。
 *
 * 注意：必须手动设置 camera.setDisplayOrientation(0)，否则 camera 会自动旋转，导致计算错误。
 *
 * 矩阵计算思路：
 *   使用矩阵求逆的方式：
 *   - textureMatrix: TextureView 默认将未旋转的 Frame 拉伸铺满的矩阵
 *   - displayMatrix: Frame 正常显示所需的最终矩阵
 *   - previewMatrix: 我们需要求解的矩阵
 *   - 关系式: displayMatrix * I= previewMatrix * textureMatrix * I
 *   - 因此: previewMatrix = displayMatrix * textureMatrix^(-1)
 *
 * @param cameraRotate camera 画面需要旋转的角度（0, 90, 180, 270）
 * @param cameraSize camera 预览尺寸（未旋转的原始尺寸）
 * @param displaySize TextureView 的显示尺寸
 * @param scaleType 缩放方式
 * @param mirror 是否水平镜像（默认为false），前置摄像头已经镜像，如果想让其不镜像，这里可以设置为true，相当于二次镜像
 */
fun getPreviewMatrix2(
    cameraRotate: Int,
    cameraSize: Size,
    displaySize: Size,
    scaleType: ScaleType = ScaleType.CENTER_CROP,
    mirror: Boolean = false
): Matrix {
    // 步骤1：计算 textureMatrix（默认拉伸矩阵）
    // TextureView 默认将 cameraSize 拉伸到 displaySize
    val textureMatrix = Matrix()
    val scaleX = displaySize.width / cameraSize.width.toFloat()
    val scaleY = displaySize.height / cameraSize.height.toFloat()
    textureMatrix.setScale(scaleX, scaleY)

    // 步骤2：计算 displayMatrix（正确显示所需的最终矩阵）
    val displayMatrix = getFrameToDisplayMatrix(
        cameraRotate,
        cameraSize,
        displaySize,
        scaleType,
        mirror
    )

    // 步骤3：计算 previewMatrix = displayMatrix * textureMatrix^(-1)
    val invertedTextureMatrix = Matrix()
    if (!textureMatrix.invert(invertedTextureMatrix)) {
        // 如果矩阵不可逆，返回单位矩阵
        return Matrix()
    }

    val previewMatrix = Matrix()
    previewMatrix.setConcat(displayMatrix, invertedTextureMatrix)

    return previewMatrix
}

/**
 * 用于将 Frame 数据坐标转换为 TextureView 显示坐标。
 *
 * @param cameraRotate camera 画面需要旋转的角度（0, 90, 180, 270）
 * @param cameraSize camera 预览尺寸（Frame 的原始尺寸）
 * @param displaySize TextureView 的显示尺寸
 * @param scaleType 缩放方式（需要与 TextureView 的 previewMatrix 使用相同的 scaleType）
 * @param mirror 是否镜像（需要与 TextureView 的 previewMatrix 使用相同的 mirror 参数）
 */
fun getFrameMatrix(
    cameraRotate: Int,
    cameraSize: Size,
    displaySize: Size,
    scaleType: ScaleType = ScaleType.CENTER_CROP,
    mirror: Boolean = false
): Matrix {
    return getFrameToDisplayMatrix(cameraRotate, cameraSize, displaySize, scaleType, mirror)
}

/**
 * 计算从 Frame 原始坐标系到最终显示坐标系的变换矩阵
 *
 * @param cameraRotate camera 画面需要旋转的角度（0, 90, 180, 270）
 * @param cameraSize camera 预览尺寸（Frame 的原始尺寸）
 * @param displaySize 显示尺寸
 * @param scaleType 缩放方式
 * @param mirror 是否镜像
 * @return 从 Frame 坐标系到显示坐标系的变换矩阵
 */
private fun getFrameToDisplayMatrix(
    cameraRotate: Int,
    cameraSize: Size,
    displaySize: Size,
    scaleType: ScaleType,
    mirror: Boolean
): Matrix {
    val matrix = Matrix()
    val centerX = displaySize.width / 2f
    val centerY = displaySize.height / 2f

    // 1. 计算旋转后的 camera 内容尺寸
    val rotatedWidth: Int
    val rotatedHeight: Int
    if (cameraRotate == 90 || cameraRotate == 270) {
        rotatedWidth = cameraSize.height
        rotatedHeight = cameraSize.width
    } else {
        rotatedWidth = cameraSize.width
        rotatedHeight = cameraSize.height
    }

    // 2. 根据 scaleType 计算最终缩放比例
    val finalScaleX: Float
    val finalScaleY: Float
    when (scaleType) {
        ScaleType.FIT_XY -> {
            // 拉伸填充：不保持宽高比
            finalScaleX = displaySize.width / rotatedWidth.toFloat()
            finalScaleY = displaySize.height / rotatedHeight.toFloat()
        }

        ScaleType.CENTER -> {
            // 保持原始大小
            finalScaleX = 1f
            finalScaleY = 1f
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

            finalScaleX = scale
            finalScaleY = scale
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

            finalScaleX = scale
            finalScaleY = scale
        }
    }

    // 3. 构建 resultMatrix：从原始 camera 坐标系变换到最终显示
    // 步骤1：将原点移到 Frame 中心
    matrix.postTranslate(-cameraSize.width / 2f, -cameraSize.height / 2f)
    // 步骤2：旋转到正确的方向
    matrix.postRotate(cameraRotate.toFloat())
    // 步骤3：镜像（前置摄像头通常需要）
    if (mirror) {
        matrix.postScale(-1f, 1f)
    }
    // 步骤4：缩放到正确的大小
    matrix.postScale(finalScaleX, finalScaleY)
    // 步骤5：移动到 Display 中心
    matrix.postTranslate(centerX, centerY)

    return matrix
}
