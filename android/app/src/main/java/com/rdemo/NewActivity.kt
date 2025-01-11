package com.rdemo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.Gravity
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import android.widget.Toast
import android.widget.LinearLayout.LayoutParams

class NewActivity : AppCompatActivity() {

    private val sharedViewModel: SharedViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }

        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
            }
        }

        rootLayout.addView(imageView)

        val byteArray = intent.getByteArrayExtra("imageByteArray")
        byteArray?.let {
            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            val rotatedBitmap = rotateImage(bitmap, 90f)
            imageView.setImageBitmap(rotatedBitmap)
        } ?: run {
            Log.e("NewActivity", "No image byte array received")
        }

        val processBackIdButton = Button(this).apply {
            text = "Process to Back ID Card"
            setBackgroundColor(0xFFFF4081.toInt())
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                imageView.visibility = if (imageView.visibility == ImageView.VISIBLE) {
                    ImageView.GONE
                } else {
                    ImageView.VISIBLE
                }
                processBackIdCard()
            }
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(0xFFFF4081.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(
                800,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                topMargin = 80
            }
        }

        rootLayout.addView(processBackIdButton)

        setContentView(rootLayout)
    }

    private fun rotateImage(source: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun processBackIdCard() {
        Log.d("NewActivity", "Processing the back ID card...")
        Toast.makeText(applicationContext, "Back ID Card processed!", Toast.LENGTH_SHORT).show()

    }
}

