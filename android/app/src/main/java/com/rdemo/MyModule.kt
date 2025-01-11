package com.rdemo

import android.app.Activity
import android.content.Context
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



class CameraModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
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
    val sharedViewModel = SharedViewModel()

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    override fun getName(): String = "CameraModule"

    private fun setupUI(activity: Activity, promise: Promise) {
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

        /// Add white border box at the center
        val borderBox = View(activity).apply {
        // Set width to 700 and height to maintain a 4:3 aspect ratio
        layoutParams = FrameLayout.LayoutParams(
            700, // Width of the border box
            (700 * 3) / 4 // Height calculated for a 4:3 aspect ratio
        ).apply {
            gravity = Gravity.CENTER // Center it in the parent layout
        }
        // Create a transparent background with a white border
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT) // Transparent background
            setStroke(4, Color.WHITE)  // White border with 4dp thickness
            cornerRadius = 16f         // Optional: Rounded corners
        }
    }


        frameLayout.addView(textureView)
        frameLayout.addView(overlayImageView)
        frameLayout.addView(borderBox) // Add the white box to the layout
        frameLayout.addView(instructionTextView)
        frameLayout.addView(captureButton)

        activity.setContentView(frameLayout)

        // Inside your setupUI function, add a click listener to the captureButton
        captureButton.setOnClickListener {
            captureImage(promise)
        }
    }




    // Expose the method to React Native to start camera preview
    @ReactMethod
    fun startCameraPreview(promise: Promise) {
        UiThreadUtil.runOnUiThread {
            try {
                val currentActivity = currentActivity ?: throw Exception("No current activity")

                setupUI(currentActivity,promise)

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



private fun captureImage(promise: Promise) {
    if (cameraDevice == null) {
        Toast.makeText(currentActivity, "Camera is not initialized", Toast.LENGTH_SHORT).show()
        Log.e("CameraModule", "Camera is not initialized")
        return
    }

    try {
        // Set up the capture request
        val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        val surface = Surface(textureView.surfaceTexture)

        // Add the surface to the capture request
        captureRequestBuilder.addTarget(surface)

        // Set capture settings (optional, like focusing or orientation)
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

        // Create an ImageReader to capture the image
        val imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1) // Adjust resolution if needed
        Log.d("CameraModule", "ImageReader created with resolution: 1920x1080")

        // Set the ImageReader surface as a target for the capture
        captureRequestBuilder.addTarget(imageReader.surface)

        // Capture the image
        cameraDevice!!.createCaptureSession(
            listOf(surface, imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    // Capture the image when session is configured
                    try {
                        session.capture(captureRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession, 
                                request: CaptureRequest, 
                                result: TotalCaptureResult
                            ) {
                                super.onCaptureCompleted(session, request, result)
                                Log.d("CameraModule", "Capture completed, processing the image")

                                // Handle the captured image
                                val image = imageReader.acquireLatestImage()
                                if (image != null) {
                                    Log.d("CameraModule", "Captured image: $image")

                                    // Extract image byte data
                                    val buffer = image.planes[0].buffer
                                    val byteArray = ByteArray(buffer.remaining())
                                    buffer.get(byteArray)
                                    // Call the function to send the image to the API
                                    sendImageToApi(byteArray,promise,sharedViewModel)

                                    // Log the byte array of the JPEG image (this can be large, so be cautious about logging large data)
                                    Log.d("CameraModule", "Captured image byte array: ${byteArray.joinToString(", ")}")

                                    // Optionally, you could convert to base64 for logging or further use
                                    val base64Image = Base64.encodeToString(byteArray, Base64.DEFAULT)
                                    Log.d("CameraModule", "Captured image in base64: $base64Image")

                                    // Handle the image as needed (e.g., process it or save it)
                                   // saveAndLogCapturedImage(base64Image)
                                    //navController.navigate("emptyScreen")

                                    // Close the image to release resources
                                    image.close()
                                    Log.d("CameraModule", "Image closed")
                                } else {
                                    Log.e("CameraModule", "Failed to acquire image")
                                }
                            }
                        }, backgroundHandler)
                    } catch (e: CameraAccessException) {
                        Log.e("CameraModule", "Failed to capture image: ${e.message}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("CameraModule", "Failed to configure camera session for capture")
                }
            },
            backgroundHandler
        )
    } catch (e: Exception) {
        Log.e("CameraModule", "Error capturing image: ${e.message}")
    }
}


private fun sendImageToApi(byteArray: ByteArray, promise: Promise, sharedViewModel: SharedViewModel) {
    val client = OkHttpClient()
    val mediaType = "image/jpeg".toMediaType()
    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart(
            "file", 
            "image.jpg", 
            byteArray.toRequestBody(mediaType)
        )
        .build()

    val request = Request.Builder()
        .url("https://api-innovitegra.online/crop-aadhar-card/")
        .post(requestBody)
        .build()

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.bytes()
                if (responseBody != null) {
                    Log.d("APIResponse", "Image uploaded successfully, received byte array")

                    val bitmap = BitmapFactory.decodeByteArray(responseBody, 0, responseBody.size)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            sharedViewModel.setFrontImage(bitmap)

                            // Start a new activity here after successful upload
                            navigateToNewActivity()
                        }

                        Log.d("APIResponse", "Bitmap dimensions: ${bitmap.width}x${bitmap.height}")
                    } else {
                        Log.e("APIResponse", "Failed to decode Bitmap from response")
                    }

                    withContext(Dispatchers.Main) {
                        promise.resolve("Image processed and stored successfully")
                    }
                } else {
                    Log.e("APIResponse", "No response body received")
                    withContext(Dispatchers.Main) {
                        promise.reject("UPLOAD_FAILED", "No response body received")
                    }
                }
            } else {
                Log.e("APIResponse", "Failed to upload image: ${response.message}")
                withContext(Dispatchers.Main) {
                    promise.reject("UPLOAD_FAILED", "Failed to upload image: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("APIResponse", "Error uploading image: ${e.message}")
            withContext(Dispatchers.Main) {
                promise.reject("UPLOAD_ERROR", "Error uploading image: ${e.message}")
            }
        }
    }
}




    // Start background thread to handle camera operations
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    // Stop the background thread
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("CameraModule", "Error stopping background thread", e)
        }
    }


    // Stop the camera session and release resources
    private fun stopCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e("CameraModule", "Error stopping camera", e)
        }
    }

    

    // Check if the app has camera permissions
    private fun hasCameraPermission(activity: Activity): Boolean {
        // Implement permission check logic here
        return true
    }

    // Request camera permission if not granted
    private fun requestCameraPermission(activity: Activity, requestCode: Int) {
        // Implement permission request logic here
    }


       
    // Method to navigate to a new Android Activity
private fun navigateToNewActivity() {
    val intent = Intent(currentActivity, NewActivity::class.java)  // Replace NewActivity with your target activity class
    currentActivity?.startActivity(intent)
}

}


class SharedViewModel : ViewModel() {
    private val _frontImage = MutableStateFlow<Bitmap?>(null)
    val frontImage: StateFlow<Bitmap?> get() = _frontImage

    fun setFrontImage(bitmap: Bitmap) {
        _frontImage.value = bitmap
    }
}


class MyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var isDetectingFaces = false
    private lateinit var frameLayout: FrameLayout
    private lateinit var textureView: TextureView
    private lateinit var overlayImageView: ImageView
    private var lastDetectionTime = 0L
    private val detectionInterval = 500L // Process every 500ms for smoother detection

    private var headMovementTasks = mutableMapOf(
        "Blink detected" to false,
        "Head moved right" to false,
        "Head moved left" to false
    )
    private var isPictureTaken = false

     companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    private var frameCounter = 0
    private val frameUpdateFrequency = 10


    private lateinit var previewImageView: ImageView
    private lateinit var previewFrameLayout: FrameLayout

    override fun getName(): String = "MyModule"

    private fun setupUI(activity: Activity) {
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

        previewFrameLayout = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        previewImageView = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        previewFrameLayout.addView(previewImageView)
        frameLayout.addView(textureView)
        frameLayout.addView(overlayImageView)

        activity.setContentView(frameLayout)
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e("MyModule", "Error stopping background thread", e)
        }
    }

   private fun openCamera(surfaceTexture: SurfaceTexture, promise: Promise) {
        try {
            val cameraManager = currentActivity?.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Find front camera
            val cameraId = cameraManager.cameraIdList.find { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_FRONT
            } ?: cameraManager.cameraIdList[1]

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession(surfaceTexture, promise)
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

    private fun createCameraPreviewSession(surfaceTexture: SurfaceTexture, promise: Promise) {
        try {
            val surface = Surface(surfaceTexture)
            val previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            cameraDevice!!.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        try {
                            session.setRepeatingRequest(
                                previewRequestBuilder.build(),
                                null,
                                backgroundHandler
                            )
                            promise.resolve(null)
                        } catch (e: CameraAccessException) {
                            promise.reject("CAMERA_ERROR", "Failed to start camera preview: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        promise.reject("CAMERA_ERROR", "Failed to configure camera session")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            promise.reject("CAMERA_ERROR", "Failed to create camera preview session: ${e.message}")
        }
    }

    private fun stopCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: Exception) {
            Log.e("MyModule", "Error stopping camera", e)
        }
    }

    @ReactMethod
    fun startCameraPreview(promise: Promise) {
        UiThreadUtil.runOnUiThread {
            try {
                val currentActivity = currentActivity ?: throw Exception("No current activity")

                if (!hasCameraPermission(currentActivity)) {
                    requestCameraPermission(currentActivity, CAMERA_PERMISSION_REQUEST_CODE)
                    promise.reject("PERMISSION_ERROR", "Camera permission is not granted")
                    return@runOnUiThread
                }

                setupUI(currentActivity)

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

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
    val currentTime = System.currentTimeMillis()
    if (frameCounter % frameUpdateFrequency == 0 &&
        currentTime - lastDetectionTime >= detectionInterval && 
        !isDetectingFaces) {
        isDetectingFaces = true
        lastDetectionTime = currentTime
        detectFaces()
    }
    frameCounter++
}

                }
            } catch (e: Exception) {
                promise.reject("CAMERA_ERROR", e.message)
            }
        }
    }
    private fun detectFaces() {
    backgroundHandler?.post {
        try {
            val bitmap = textureView.bitmap ?: return@post
            val image = InputImage.fromBitmap(bitmap, 0)

            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build()

            val detector = FaceDetection.getClient(options)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isEmpty()) {
                        if (headMovementTasks.any { it.value }) {
                            resetTasks()
                            Log.d("FaceDetection", "Face lost - progress reset")
                        }
                        // Clear overlay when no faces are detected
                        drawFacesOnOverlay(emptyList())
                    } else {
                        // Filter to get the primary face
                        val primaryFace = faces.maxByOrNull { face ->
                            val size = face.boundingBox.width() * face.boundingBox.height()
                            val centerDistance = calculateCenterProximity(face.boundingBox)
                            // Combine size and proximity; larger size and closer center get higher priority
                            size - centerDistance
                        }

                        if (primaryFace != null) {
                            processDetectedFace(primaryFace)
                            drawFacesOnOverlay(listOf(primaryFace)) // Pass only the primary face
                        } else {
                            drawFacesOnOverlay(emptyList()) // Handle edge case
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FaceDetection", "Face detection failed: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e("FaceDetection", "Error detecting faces: ${e.message}")
        } finally {
            isDetectingFaces = false
        }
    }
}



// Helper function to calculate center proximity
private fun calculateCenterProximity(bounds: Rect): Int {
    val screenWidth = overlayImageView.width
    val screenHeight = overlayImageView.height
    val centerX = screenWidth / 2
    val centerY = screenHeight / 2

    val faceCenterX = bounds.centerX()
    val faceCenterY = bounds.centerY()

    return (faceCenterX - centerX) * (faceCenterX - centerX) +
           (faceCenterY - centerY) * (faceCenterY - centerY)
}

     private fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission(activity: Activity, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, arrayOf(android.Manifest.permission.CAMERA), requestCode)
    }

    private fun processDetectedFace(face: Face) {
    val headEulerAngleY = face.headEulerAngleY
    val leftEyeOpenProb = face.leftEyeOpenProbability ?: -1.0f
    val rightEyeOpenProb = face.rightEyeOpenProbability ?: -1.0f

    // Evaluate head movements and update tasks
    when {
        !headMovementTasks["Blink detected"]!! &&
                leftEyeOpenProb < 0.5 && rightEyeOpenProb < 0.5 -> {
            updateTask("Blink detected")
            Log.d("FaceDetection", "Blink detected")
        }
        headMovementTasks["Blink detected"]!! &&
                !headMovementTasks["Head moved right"]!! &&
                headEulerAngleY > 10 -> {
            updateTask("Head moved right")
            Log.d("FaceDetection", "Head turned right")
        }
        headMovementTasks["Head moved right"]!! &&
                !headMovementTasks["Head moved left"]!! &&
                headEulerAngleY < -10 -> {
            updateTask("Head moved left")
            Log.d("FaceDetection", "Head turned left")
        }
    }

    // Additional checks and actions outside the `when` block
    if (!headMovementTasks["Blink detected"]!!) {
        Toast.makeText(currentActivity, "Please close your eyes for a second", Toast.LENGTH_SHORT).show()
    } else if (!headMovementTasks["Head moved right"]!!) {
        Toast.makeText(currentActivity, "Please turn your head to the right", Toast.LENGTH_SHORT).show()

    } else if (!headMovementTasks["Head moved left"]!!) {
        Toast.makeText(currentActivity, "Please turn your head to the left", Toast.LENGTH_SHORT).show()
    }
}


    private fun updateTask(taskName: String) {
        headMovementTasks[taskName] = true
    }

    private fun resetTasks() {
        headMovementTasks = headMovementTasks.mapValues { false }.toMutableMap()
    }

   // Update the drawFacesOnOverlay function
private fun drawFacesOnOverlay(faces: List<Face>) {
    backgroundHandler?.post {
        try {
            // Clear existing overlays
            val mutableBitmap = Bitmap.createBitmap(
                overlayImageView.width,
                overlayImageView.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(mutableBitmap)
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 8f
                textSize = 48f
            }

            if (faces.isEmpty()) {
                // Clear the overlay when no faces are detected
                UiThreadUtil.runOnUiThread {
                    overlayImageView.setImageBitmap(null)
                }
                return@post
            }

            // Draw detected faces
            for (face in faces) {
                val bounds = face.boundingBox

                paint.color = Color.GREEN
                canvas.drawRect(bounds, paint)

                // Draw task statuses
                var yOffset = bounds.top.toFloat() - 60f
                headMovementTasks.forEach { (task, completed) ->
                    paint.color = if (completed) Color.GREEN else Color.RED
                    canvas.drawText("$task: ${if (completed) "✓" else "×"}", bounds.left.toFloat(), yOffset, paint)
                    yOffset -= 60f
                }
            }

            // Update overlay image view on UI thread
            UiThreadUtil.runOnUiThread {
                overlayImageView.setImageBitmap(mutableBitmap)
            }

            // Trigger picture capture if tasks are completed
            if (!isPictureTaken && headMovementTasks.all { it.value }) {
                takePicture()
            }
        } catch (e: Exception) {
            Log.e("FaceOverlay", "Error drawing face overlay: ${e.message}")
        }
    }
}
private fun takePicture() {
    UiThreadUtil.runOnUiThread {
        // Show "Taking selfie..." text above the countdown
        val takingSelfieTextView = TextView(currentActivity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM // Center horizontally, align to bottom
                bottomMargin = 1200 // Adjust the bottom margin to position it above the countdown
            }
            textSize = 24f
            setTextColor(Color.WHITE)
            text = "Taking selfie"

            // Set background with border radius
            val drawable = GradientDrawable().apply {
                setColor(Color.BLACK) // Black background
                cornerRadius = 30f // Border radius
            }
            background = drawable
            setPadding(20, 10, 20, 10) // Padding for the text inside the rounded box
        }

        // Add the "Taking selfie..." text to the frameLayout
        frameLayout.addView(takingSelfieTextView)

        // Check if a countdown is already in progress
        val existingCountdownView = frameLayout.findViewWithTag<TextView>("countdownTextView")
        if (existingCountdownView != null) {
            // A countdown is already running, skip creating a new one
            return@runOnUiThread
        }

        // Create a TextView for the countdown
        val countdownTextView = TextView(currentActivity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER // Center the countdown text horizontally and vertically
            }
            textSize = 48f
            setTextColor(Color.WHITE)
            tag = "countdownTextView" // Assign a tag to identify it later

            // Set background with border radius for countdown
            val drawable = GradientDrawable().apply {
                setColor(Color.BLACK) // Black background
                cornerRadius = 30f // Border radius
            }
            background = drawable
            setPadding(40, 20, 40, 20) // Padding for the countdown text inside the rounded box
        }

        // Add the countdown TextView to the frameLayout
        frameLayout.addView(countdownTextView)

        // Start a countdown from 3 to 1
        val countdownHandler = Handler()
        var remainingTime = 3 // Countdown duration

        // Update the countdown every second
        val countdownRunnable = object : Runnable {
            override fun run() {
                if (remainingTime > 0) {
                    countdownTextView.text = remainingTime.toString()
                    remainingTime--
                    countdownHandler.postDelayed(this, 1000) // Update every 1 second
                } else {
                    // Countdown complete, remove the "Taking selfie..." text and countdown TextView
                    frameLayout.removeView(takingSelfieTextView)
                    frameLayout.removeView(countdownTextView)

                    // Capture the selfie
                    captureSelfie()
                }
            }
        }

        // Start the countdown
        countdownHandler.post(countdownRunnable)
    }
}




private fun captureSelfie() {
    backgroundHandler?.post {
        try {
            val bitmap = textureView.bitmap
            bitmap?.let {
                savePicture(it)
                isPictureTaken = true
            }
        } catch (e: Exception) {
            Log.e("Picture", "Error taking picture: ${e.message}")
        }
    }
}


private fun savePicture(bitmap: Bitmap) {
    try {
        val file = File(currentActivity?.filesDir, "capturedPhoto.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()

        // Immediately show the photo in preview
        showInPreview(file)
    } catch (e: Exception) {
        Log.e("MyModule", "Error saving photo: ${e.message}")
    }
}

private fun showInPreview(file: File) {
    UiThreadUtil.runOnUiThread {
        val previewImageView = ImageView(currentActivity).apply {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            setImageBitmap(bitmap)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        // Assuming frameLayout is already initialized
        frameLayout.addView(previewImageView)
    }
}



}
