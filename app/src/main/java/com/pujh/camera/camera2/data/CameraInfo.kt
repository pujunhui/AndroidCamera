package com.pujh.camera.camera2.data

import android.hardware.camera2.CameraMetadata

data class CameraInfo(
    val cameraId: String,
    val facing: Int,
    val orientation: Int,
) {
    companion object {
        const val LENS_FACING_FRONT = CameraMetadata.LENS_FACING_FRONT
        const val LENS_FACING_BACK = CameraMetadata.LENS_FACING_BACK
        const val LENS_FACING_EXTERNAL = CameraMetadata.LENS_FACING_EXTERNAL
    }
}
