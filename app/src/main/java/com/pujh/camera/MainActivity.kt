package com.pujh.camera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pujh.camera.camera.CameraFragment
import com.pujh.camera.camera2.Camera2Fragment
import com.pujh.camera.camerax.CameraXFragment
import com.pujh.camera.databinding.ActivityMainBinding
import com.pujh.camera.util.CameraPermission
import com.pujh.camera.util.checkCameraPermission

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var cameraType = -1

    // Camera permission launcher
    private val requestCameraPermissionLauncher = registerForActivityResult(
        CameraPermission()
    ) { granted ->
        if (granted) {
            setCameraType(cameraType)
        } else {
            handlePermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.cameraType.setOnCheckedChangeListener { _, checkedId ->
            val cameraType = when (checkedId) {
                R.id.camera1_btn -> CAMERA_TYPE_CAMERA1
                R.id.camera2_btn -> CAMERA_TYPE_CAMERA2
                R.id.camerax_btn -> CAMERA_TYPE_CAMERAX
                else -> throw IllegalStateException("error camera type!")
            }
            setCameraType(cameraType)
        }

        binding.cameraType.check(R.id.camera1_btn)
    }

    /**
     * Handle permission denied - show toast and navigate to app settings
     */
    private fun handlePermissionDenied() {
        Toast.makeText(
            this,
            "Permissions not granted by the user!",
            Toast.LENGTH_SHORT
        ).show()
        navigateToAppSettings()
    }

    /**
     * Navigate to app settings page
     */
    private fun navigateToAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun setCameraType(cameraType: Int) {
        this.cameraType = cameraType
        if (!checkCameraPermission()) {
            requestCameraPermissionLauncher.launch(null)
            return
        }

        val fragment = when (cameraType) {
            CAMERA_TYPE_CAMERA1 -> CameraFragment()
            CAMERA_TYPE_CAMERA2 -> Camera2Fragment()
            CAMERA_TYPE_CAMERAX -> CameraXFragment()
            else -> throw IllegalStateException("error camera type!")
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    companion object {
        private const val TAG = "CameraDemo"

        private const val CAMERA_TYPE_CAMERA1 = 1
        private const val CAMERA_TYPE_CAMERA2 = 2
        private const val CAMERA_TYPE_CAMERAX = 3
    }
}