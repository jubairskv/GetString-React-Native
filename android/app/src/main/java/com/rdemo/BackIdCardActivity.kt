package com.rdemo

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity

class BackIdCardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        rootLayout.addView(imageView)

        // Retrieve the byte array from the intent
        val byteArray = intent.getByteArrayExtra("imageByteArray")
        byteArray?.let {
            // Convert the byte array to a Bitmap
            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            // Set the Bitmap to the ImageView
            imageView.setImageBitmap(bitmap)
        }

        setContentView(rootLayout)
    }
}
