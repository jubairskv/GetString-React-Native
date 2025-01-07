package com.rdemo

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
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
            } ?: cameraManager.cameraIdList[0]

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
                        if (currentTime - lastDetectionTime >= detectionInterval && !isDetectingFaces) {
                            isDetectingFaces = true
                            lastDetectionTime = currentTime
                            detectFaces()
                        }
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
                        } else {
                            val face = faces[0]
                            processDetectedFace(face)
                        }
                        drawFacesOnOverlay(bitmap, faces)
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

    private fun processDetectedFace(face: Face) {
        val headEulerAngleY = face.headEulerAngleY
        val leftEyeOpenProb = face.leftEyeOpenProbability ?: -1.0f
        val rightEyeOpenProb = face.rightEyeOpenProbability ?: -1.0f

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
    }

    private fun updateTask(taskName: String) {
        headMovementTasks[taskName] = true
    }

    private fun resetTasks() {
        headMovementTasks = headMovementTasks.mapValues { false }.toMutableMap()
    }

    private fun drawFacesOnOverlay(bitmap: Bitmap, faces: List<Face>) {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f
            textSize = 48f
        }

        for (face in faces) {
            val bounds = face.boundingBox

            // Calculate scaled coordinates
            val scaledLeft = bounds.left.toFloat()
            val scaledTop = bounds.top.toFloat()
            val scaledRight = bounds.right.toFloat()
            val scaledBottom = bounds.bottom.toFloat()

            // Draw face rectangle
            paint.color = Color.GREEN // Green color for face rectangle
            canvas.drawRect(scaledLeft, scaledTop, scaledRight, scaledBottom, paint)

            // Draw task status (initially red, then green when completed)
            var yOffset = scaledTop - 60f
            headMovementTasks.forEach { (task, completed) ->
                paint.color = if (completed) Color.GREEN else Color.RED // Green if completed, red if not
                val status = if (completed) "✓" else "×"
                canvas.drawText("$task: $status", scaledLeft, yOffset, paint)
                yOffset -= 60f
            }
        }

        UiThreadUtil.runOnUiThread {
            overlayImageView.setImageBitmap(mutableBitmap)
        }

        // Take the picture if all tasks are completed
        if (!isPictureTaken && headMovementTasks.all { it.value }) {
            takePicture()
        }
    }

   private fun takePicture() {
      // Post a delayed task to take a picture after 4 seconds
    backgroundHandler?.postDelayed({
        try {
            val bitmap = textureView.bitmap
            bitmap?.let {
                savePicture(bitmap)
                // Now pass the saved file to showInPreview
                val file = File(currentActivity?.filesDir, "capturedPhoto.jpg")
                showInPreview(file) // Pass the file here
                isPictureTaken = true
            }
        } catch (e: Exception) {
            Log.e("Picture", "Error taking picture: ${e.message}")
        }
    }, 1000) // 1 seconds delay (4000 milliseconds)
}

private fun savePicture(bitmap: Bitmap) {
    try {
        val file = File(currentActivity?.filesDir, "capturedPhoto.jpg")
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        // After saving, you can now show the file in preview
        showInPreview(file)
    } catch (e: Exception) {
        Log.e("MyModule", "Error saving photo: ${e.message}")
    }
}

private fun showInPreview(file: File) {
    UiThreadUtil.runOnUiThread {
        val previewImageView = ImageView(currentActivity)
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        previewImageView.setImageBitmap(bitmap)

        // Assuming frameLayout is already initialized
        frameLayout.addView(previewImageView)
    }
}

}
