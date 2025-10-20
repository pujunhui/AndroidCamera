package com.pujh.camera.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Contract for requesting MANAGE_ALL_FILES_ACCESS_PERMISSION on Android 30+ (API 30+)
 * This will navigate to the system settings page
 *
 * Usage: For Android 30 and above, use this contract to request full file access permission
 */
@RequiresApi(Build.VERSION_CODES.R)
class ManageAllFilesAccessPermission : ActivityResultContract<Void?, Boolean>() {

    override fun createIntent(context: Context, input: Void?): Intent {
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return Environment.isExternalStorageManager()
    }
}

class WriteExternalStoragePermission : ActivityResultContract<Void?, Boolean>() {
    private val permission = ActivityResultContracts.RequestPermission()

    override fun createIntent(context: Context, input: Void?): Intent {
        return permission.createIntent(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return permission.parseResult(resultCode, intent)
    }

    override fun getSynchronousResult(context: Context, input: Void?): SynchronousResult<Boolean>? {
        return permission.getSynchronousResult(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

class CameraPermission : ActivityResultContract<Void?, Boolean>() {
    private val permission = ActivityResultContracts.RequestPermission()

    override fun createIntent(context: Context, input: Void?): Intent {
        return permission.createIntent(context, Manifest.permission.CAMERA)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return permission.parseResult(resultCode, intent)
    }

    override fun getSynchronousResult(context: Context, input: Void?): SynchronousResult<Boolean>? {
        return permission.getSynchronousResult(context, Manifest.permission.CAMERA)
    }
}

fun Activity.checkCameraPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}

fun Activity.checkExternalStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}