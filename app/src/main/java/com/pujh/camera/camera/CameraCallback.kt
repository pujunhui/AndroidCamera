package com.pujh.camera.camera

import android.hardware.Camera
import com.pujh.camera.camera.data.CameraInfo
import com.pujh.camera.camera.data.FrameInfo

interface CameraCallback {

    fun onCameraReady(
        camera: Camera,
        cameraInfo: CameraInfo,
        frameInfo: FrameInfo
    ) {
    }

    fun onCameraFrame(camera: Camera, frameInfo: FrameInfo, data: ByteArray)


    fun onCameraClose(camera: Camera) {
    }
}
