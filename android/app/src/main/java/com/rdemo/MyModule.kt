package com.rdemo

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil

class MyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var faceDetector: android.media.FaceDetector? = null
    private var isDetectingFaces = false // To prevent redundant face detection calls

    override fun getName(): String = "MyModule"

    @ReactMethod
    fun startCameraPreview(promise: Promise) {
        UiThreadUtil.runOnUiThread {
            try {
                val currentActivity: Activity? = currentActivity

                if (currentActivity != null) {
                    val frameLayout = FrameLayout(currentActivity)
                    val textureView = TextureView(currentActivity)
                    frameLayout.addView(textureView)

                    currentActivity.setContentView(frameLayout)  // Set the frame layout as the content view

                    // Set layout parameters
                    val displayMetrics = currentActivity.resources.displayMetrics
                    val layoutParams = FrameLayout.LayoutParams(displayMetrics.widthPixels, displayMetrics.heightPixels)
                    layoutParams.gravity = Gravity.CENTER
                    textureView.layoutParams = layoutParams

                    // Set up TextureView listener
                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            startCamera(surfaceTexture, textureView, promise)
                        }

                        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            stopCamera()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                            // Check if face detection is already in progress to avoid redundant processing
                            if (!isDetectingFaces) {
                                isDetectingFaces = true
                                detectFaces(textureView)
                            }
                        }
                    }
                } else {
                    promise.reject("ActivityError", "No activity found.")
                }
            } catch (e: Exception) {
                promise.reject("Error", e.message)
            }
        }
    }

    private fun startCamera(surfaceTexture: SurfaceTexture, textureView: TextureView, promise: Promise) {
        val handlerThread = HandlerThread("CameraBackgroundThread")
        handlerThread.start()
        backgroundHandler = Handler(handlerThread.looper)

        try {
            val currentActivity = currentActivity ?: throw Exception("Activity not found")
            val cameraManager = currentActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[1] // Use the back camera

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreviewSession(surfaceTexture, promise)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice?.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    promise.reject("CameraError", "Failed to open camera: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            promise.reject("CameraError", "Error starting camera: ${e.message}")
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
                    try {
                        session.setRepeatingRequest(previewRequestBuilder.build(), null, backgroundHandler)
                        promise.resolve("Camera preview started successfully!")
                    } catch (e: Exception) {
                        promise.reject("CameraError", "Error during camera preview: ${e.message}")
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    promise.reject("CameraError", "Failed to configure camera session")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            promise.reject("CameraError", "Failed to create preview session: ${e.message}")
        }
    }

    private fun stopCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
        backgroundHandler?.looper?.thread?.interrupt()
        backgroundHandler = null
    }

    private fun detectFaces(textureView: TextureView) {
        // Run face detection in a background thread
        backgroundHandler?.post {
            try {
                val bitmap = textureView.bitmap ?: return@post

                // Convert bitmap to mutable format
                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)
                val paint = Paint()
                paint.color = Color.GREEN
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f

                val maxFaces = 5
                val faceDetector = android.media.FaceDetector(mutableBitmap.width, mutableBitmap.height, maxFaces)
                val faces = arrayOfNulls<android.media.FaceDetector.Face>(maxFaces)
                val faceCount = faceDetector.findFaces(mutableBitmap, faces)

                Log.d("FaceDetection", "Detected $faceCount faces")

                for (i in 0 until faceCount) {
                    val face = faces[i]
                    if (face != null) {
                        val midPoint = PointF()
                        face.getMidPoint(midPoint)
                        val eyesDistance = face.eyesDistance()

                        // Draw green rectangle around the detected face
                        canvas.drawRect(
                            midPoint.x - eyesDistance,
                            midPoint.y - eyesDistance,
                            midPoint.x + eyesDistance,
                            midPoint.y + eyesDistance,
                            paint
                        )

                        Log.d(
                            "FaceDetection",
                            "Face $i: Position=(${midPoint.x}, ${midPoint.y}), Confidence=${"%.2f".format(face.confidence())}"
                        )
                    }
                }

                // Set the modified bitmap to the ImageView or handle UI update
                // For example, create an ImageView and set its bitmap to mutableBitmap
            } finally {
                // Ensure we reset face detection flag after completion
                isDetectingFaces = false
            }
        }
    }
}

