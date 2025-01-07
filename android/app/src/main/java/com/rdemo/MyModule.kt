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
    private val detectionInterval = 1000L // Process every 1000ms (1 second)

    override fun getName(): String = "MyModule"

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
                            detectFaces() // Detect faces only once per second
                        }
                    }
                }
            } catch (e: Exception) {
                promise.reject("Error", e.message)
            }
        }
    }

    private fun setupUI(activity: Activity) {
        frameLayout = FrameLayout(activity)
        textureView = TextureView(activity)
        overlayImageView = ImageView(activity)

        frameLayout.addView(textureView)
        frameLayout.addView(overlayImageView)

        activity.setContentView(frameLayout)
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        textureView.layoutParams = layoutParams
        overlayImageView.layoutParams = layoutParams
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackgroundThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
    }

    private fun openCamera(surfaceTexture: SurfaceTexture, promise: Promise) {
        val cameraManager = currentActivity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[1] // Use front-facing camera if available

        try {
            Log.d("CameraModule", "Opening camera: $cameraId")
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d("CameraModule", "Camera opened successfully.")
                    cameraDevice = camera
                    createCameraPreviewSession(surfaceTexture, promise)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    Log.d("CameraModule", "Camera disconnected.")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    promise.reject("CameraError", "Failed to open camera: $error")
                    Log.e("CameraModule", "Error opening camera: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            promise.reject("CameraError", "Error opening camera: ${e.message}")
            Log.e("CameraModule", "Error opening camera: ${e.message}")
        }
    }

    private fun createCameraPreviewSession(surfaceTexture: SurfaceTexture, promise: Promise) {
        try {
            val surface = Surface(surfaceTexture)
            val previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(surface)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                    Log.d("CameraModule", "Camera session configured successfully.")
                    promise.resolve("Camera preview started successfully!")
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    promise.reject("CameraError", "Failed to configure camera session")
                    Log.e("CameraModule", "Failed to configure camera session.")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            promise.reject("CameraError", "Failed to create preview session: ${e.message}")
            Log.e("CameraModule", "Error creating camera session: ${e.message}")
        }
    }

    private fun stopCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
        Log.d("CameraModule", "Camera stopped.")
    }

    private fun detectFaces() {
        backgroundHandler?.post {
            try {
                val bitmap = textureView.bitmap ?: return@post
                val downscaledBitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, false)

                // Create InputImage from the downscaled bitmap for better performance
                val image = InputImage.fromBitmap(downscaledBitmap, 0)

                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // Use fast mode
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()

                val detector = FaceDetection.getClient(options)

                Log.d("FaceDetection", "Starting face detection.")
                detector.process(image)
                    .addOnSuccessListener { faces ->
                        Log.d("FaceDetection", "Faces detected: ${faces.size}")
                        drawFacesOnBitmap(bitmap, faces)
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

  private fun drawFacesOnBitmap(bitmap: Bitmap, faces: List<Face>) {
    // Create a mutable bitmap so that we can draw on it
    val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    // Paint object for drawing the bounding box
    val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    // Scale the bounding boxes to the original size if we scaled the bitmap before detection
    val scaleX = textureView.width.toFloat() / bitmap.width
    val scaleY = textureView.height.toFloat() / bitmap.height

    for (face in faces) {
        val bounds = face.boundingBox
        // Scale the bounding box to fit the texture view size
        val scaledBounds = Rect(
            (bounds.left * scaleX).toInt(),
            (bounds.top * scaleY).toInt(),
            (bounds.right * scaleX).toInt(),
            (bounds.bottom * scaleY).toInt()
        )

        // Draw the scaled bounding box on the canvas
        canvas.drawRect(scaledBounds, paint)
    }

    // Set the updated bitmap (with bounding boxes) to the overlay image view
    UiThreadUtil.runOnUiThread {
        overlayImageView.setImageBitmap(mutableBitmap)
    }

    Log.d("FaceDetection", "Face bounding boxes drawn.")
}


}
