package com.rdemo

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.*
import android.widget.Toast
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.view.*
import android.widget.FrameLayout
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.TextView
import android.view.Gravity
import android.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import android.Manifest
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ActivityEventListener
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.view.Surface
import android.widget.Button
import android.util.Size
import android.media.Image
import android.media.ImageReader
import android.util.Base64
import android.graphics.BitmapFactory
import android.util.Log
import java.io.ByteArrayOutputStream
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.*
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.widget.ProgressBar
import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.activity.viewModels
import android.app.Application
import android.view.TextureView
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class BackIdCardActivity : AppCompatActivity() {

    private var cameraDevice: CameraDevice? = null
    private lateinit var surface: Surface
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private lateinit var frameLayout: FrameLayout
    private lateinit var textureView: TextureView
    private lateinit var overlayImageView: ImageView
    private lateinit var instructionTextView: TextView
    private lateinit var captureButton: Button
    private lateinit var progressBar: ProgressBar
    //private val context: Context = reactContext

    private val CAMERA_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?,activity: Activity, promise: Promise,context: Context) {
        super.onCreate(savedInstanceState)

        // Set up the UI
        setupUI( activity,promise, context)

        // Check for camera permission before starting the camera preview
        if (!hasCameraPermission(this)) {
            requestCameraPermission(this)
        } else {
            startCameraPreview()
        }
    }

    private fun setupUI(activity: Activity, promise: Promise,context: Context) {
        frameLayout = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        textureView = TextureView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        overlayImageView = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        instructionTextView = TextView(activity).apply {
            text = "Take a Picture of Front side of ID Card"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#b9b9b9"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                900,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = 80
                leftMargin = 100
                rightMargin = 50
            }
            setPadding(50, 0, 50, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#b9b9b9"))
                cornerRadius = 30f
            }
        }

        captureButton = Button(activity).apply {
            text = "Capture Front ID"
            setBackgroundColor(Color.parseColor("#FF4081"))
            setTextColor(Color.WHITE)
            textSize = 18f
            layoutParams = FrameLayout.LayoutParams(
                800,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 50
            }
            setPadding(50, 0, 50, 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF4081"))
                cornerRadius = 30f
            }
        }

        // Progress bar for loading
        progressBar = ProgressBar(activity).apply {
            visibility = View.GONE // Initially hidden
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        val borderBox = View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                700,
                (700 * 3) / 4
            ).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(4, Color.WHITE)
                cornerRadius = 16f
            }
        }

        frameLayout.addView(textureView)
        frameLayout.addView(overlayImageView)
        frameLayout.addView(borderBox)
        frameLayout.addView(instructionTextView)
        frameLayout.addView(captureButton)
        frameLayout.addView(progressBar) // Add progress bar to the layout

        activity.setContentView(frameLayout)

        // captureButton.setOnClickListener {
        //     captureImage(promise,context)
        // }
    }

    // Check camera permission
    private fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Request camera permission
    private fun requestCameraPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    // Start camera preview
    fun startCameraPreview(promise: Promise) {
    UiThreadUtil.runOnUiThread {
        try {
            val currentActivity = currentActivity ?: throw Exception("No current activity")

            // Check for camera permission
            if (!hasCameraPermission(currentActivity)) {
                requestCameraPermission(currentActivity)
                return@runOnUiThread
            }

            // Proceed with camera preview setup
            setupUI(currentActivity, promise, context)

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    startBackgroundThread()
                    openCamera(surfaceTexture, promise)
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                    stopCamera()
                    stopBackgroundThread()
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
            }
        } catch (e: Exception) {
            promise.reject("CAMERA_ERROR", e.message)
        }
    }
}



    // Open the camera and start the preview session
    private fun openCamera(surfaceTexture: SurfaceTexture, promise: Promise) {
        try {
            val cameraManager = currentActivity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Find the back camera (use front camera if preferred)
            val cameraId = cameraManager.cameraIdList.find { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_BACK // Use back camera
            } ?: cameraManager.cameraIdList[0] // Default to first camera if back camera not found

            // Get the camera characteristics
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            
            // Get the best resolution for the selected camera
            val bestResolution = getCameraResolution(cameraManager, cameraId)

            // Modify the height to reduce the preview size while maintaining aspect ratio
            val reducedHeight = bestResolution.height / 4  // Reducing the height (adjust as needed)
            val adjustedWidth = bestResolution.width * reducedHeight / bestResolution.height

            // Set the SurfaceTexture to the modified resolution
            surfaceTexture.setDefaultBufferSize(adjustedWidth, reducedHeight)

            // Get the device rotation (screen orientation)
            val rotation = currentActivity?.windowManager?.defaultDisplay?.rotation ?: 0
            val degrees = when (rotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }

            // Get the camera's sensor orientation and lens facing
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

            // Calculate the display orientation based on device rotation and camera orientation
            val displayOrientation = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                // Front-facing camera: Adjust orientation by adding the sensor orientation and device rotation
                (sensorOrientation + degrees) % 360
            } else {
                // Back-facing camera: Adjust orientation by subtracting the device rotation from the sensor orientation
                (sensorOrientation - degrees + 360) % 360
            }

            // Open the camera and start the preview session
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera

                    // Create the camera preview session
                    createCameraPreviewSession(surfaceTexture, displayOrientation, promise)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    promise.reject("CAMERA_ERROR", "Failed to open camera: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            promise.reject("CAMERA_ERROR", "Failed to open camera: ${e.message}")
        }
    }


    // Select the best resolution available for the camera
    private fun getCameraResolution(cameraManager: CameraManager, cameraId: String): Size {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        // Check if the map is not null and get the supported output sizes for the camera
        val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()

        // Select the largest resolution available
        val bestResolution = sizes.maxByOrNull { it.width * it.height }
        return bestResolution ?: Size(1920, 1080)  // Default resolution if no best resolution found
    }



    
    // Create the camera preview session
    private fun createCameraPreviewSession(surfaceTexture: SurfaceTexture, displayOrientation: Int, promise: Promise) {
        try {
            val surface = Surface(surfaceTexture)

            // Create the CaptureRequest for the preview
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            // Set the orientation of the camera output
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, displayOrientation)

            // Create the camera capture session
            cameraDevice!!.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        try {
                            session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                            promise.resolve("Camera preview started successfully")
                        } catch (e: CameraAccessException) {
                            promise.reject("CAMERA_ERROR", "Failed to start preview session: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        promise.reject("CAMERA_ERROR", "Failed to configure camera session")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            promise.reject("CAMERA_ERROR", "Failed to create preview session: ${e.message}")
        }
    }



private fun startBackgroundThread() {
    backgroundThread = Thread(Runnable {
        Looper.prepare()
        backgroundHandler = Handler(Looper.myLooper()!!)
        Looper.loop()
    })
    backgroundThread.start()
}

private fun stopBackgroundThread() {
    backgroundThread.quitSafely()
    try {
        backgroundThread.join()
    } catch (e: InterruptedException) {
        e.printStackTrace()
    }
}

    // Handle the result of the camera permission request
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start the camera preview
                startCameraPreview()
            } else {
                // Permission denied, handle accordingly
            }
        }
    }
}
