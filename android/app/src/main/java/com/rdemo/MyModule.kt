package com.rdemo

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.widget.Toast
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
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
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream




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
        showToasty("Please close your eyes for a second")
    } else if (!headMovementTasks["Head moved right"]!!) {
        showToasty("Please turn your head to the right")
    } else if (!headMovementTasks["Head moved left"]!!) {
        showToasty("Please turn your head to the left")
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

private fun showToasty(message: String) {
    UiThreadUtil.runOnUiThread {
        Toast.makeText(currentActivity, message, Toast.LENGTH_SHORT).show()
    }
}


}
