package com.rdemo

import android.os.Build
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

class MyModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "MyModule"

    @ReactMethod
    fun getDummyString(promise: Promise) {
        // Get the Android version
        val androidVersion = Build.VERSION.RELEASE // This gives you the version name, e.g., "12", "11", etc.
        
        // Return the Android version
        promise.resolve("Android Version: $androidVersion")
    }
}
