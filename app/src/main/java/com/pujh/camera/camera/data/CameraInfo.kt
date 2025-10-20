package com.pujh.camera.camera.data

import android.hardware.Camera

data class CameraInfo(
    val cameraId: Int,
    val facing: Int,
    val orientation: Int,
) {
    companion object {
        const val CAMERA_FACING_BACK = Camera.CameraInfo.CAMERA_FACING_BACK
        const val CAMERA_FACING_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT
    }
}
