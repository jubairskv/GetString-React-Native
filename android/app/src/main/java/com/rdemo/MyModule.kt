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

class MyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var isDetectingFaces = false
    private lateinit var frameLayout: FrameLayout
    private lateinit var textureView: TextureView
    private lateinit var overlayImageView: ImageView

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
                        if (!isDetectingFaces) {
                            isDetectingFaces = true
                            detectFaces()
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
        val cameraId = cameraManager.cameraIdList[1]

        try {
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
                    promise.reject("CameraError", "Failed to open camera: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            promise.reject("CameraError", "Error opening camera: ${e.message}")
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
                    promise.resolve("Camera preview started successfully!")
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
    }

    private fun detectFaces() {
        backgroundHandler?.post {
            try {
                val bitmap = textureView.bitmap ?: return@post
                val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBitmap)
                val paint = Paint().apply {
                    color = Color.GREEN
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                }

                val maxFaces = 5
                val faceDetector = android.media.FaceDetector(mutableBitmap.width, mutableBitmap.height, maxFaces)
                val faces = arrayOfNulls<android.media.FaceDetector.Face>(maxFaces)
                val faceCount = faceDetector.findFaces(mutableBitmap, faces)

                for (i in 0 until faceCount) {
                    val face = faces[i]
                    face?.let {
                        val midPoint = PointF()
                        it.getMidPoint(midPoint)
                        val eyesDistance = it.eyesDistance()
                        canvas.drawRect(
                            midPoint.x - eyesDistance,
                            midPoint.y - eyesDistance,
                            midPoint.x + eyesDistance,
                            midPoint.y + eyesDistance,
                            paint
                        )
                    }
                }

                UiThreadUtil.runOnUiThread {
                    overlayImageView.setImageBitmap(mutableBitmap)
                }
            } catch (e: Exception) {
                Log.e("FaceDetection", "Error detecting faces: ${e.message}")
            } finally {
                isDetectingFaces = false
            }
        }
    }
}
