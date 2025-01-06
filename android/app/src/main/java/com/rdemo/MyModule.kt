package com.rdemo

import android.app.Activity
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.UiThreadUtil

class MyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null

    override fun getName(): String = "MyModule"

    @ReactMethod
    fun startCameraPreview(promise: Promise) {
        UiThreadUtil.runOnUiThread {
            try {
                val currentActivity: Activity? = currentActivity

                if (currentActivity != null) {
                    val textureView = TextureView(currentActivity)
                    currentActivity.setContentView(textureView)  // Set the texture view as the content view

                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            try {
                                val surface = Surface(surfaceTexture)

                                // Get the CameraManager system service
                                val cameraManager = currentActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                                val cameraId = cameraManager.cameraIdList[1]  // Assuming we use the first camera

                                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                                    override fun onOpened(camera: CameraDevice) {
                                        cameraDevice = camera
                                        val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                        previewRequestBuilder.addTarget(surface)

                                        camera.createCaptureSession(
                                            listOf(surface),
                                            object : CameraCaptureSession.StateCallback() {
                                                override fun onConfigured(session: CameraCaptureSession) {
                                                    cameraCaptureSession = session
                                                    try {
                                                        session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
                                                        promise.resolve("Camera preview started successfully!")
                                                    } catch (e: Exception) {
                                                        promise.reject("CameraError", "Failed to start camera preview: ${e.message}")
                                                    }
                                                }

                                                override fun onConfigureFailed(session: CameraCaptureSession) {
                                                    promise.reject("CameraError", "Camera configuration failed.")
                                                }
                                            },
                                            null
                                        )
                                    }

                                    override fun onDisconnected(camera: CameraDevice) {
                                        cameraDevice?.close()
                                    }

                                    override fun onError(camera: CameraDevice, error: Int) {
                                        promise.reject("CameraError", "Error opening camera: $error")
                                    }
                                }, null)
                            } catch (e: Exception) {
                                promise.reject("CameraError", "Failed to start camera: ${e.message}")
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            // Handle surface size changes here
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            // Clean up resources when the surface is destroyed
                            cameraDevice?.close()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                            // You can handle frame updates here if needed
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
}