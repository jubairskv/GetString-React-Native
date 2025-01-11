package com.rdemo

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.widget.LinearLayout.LayoutParams
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.rdemo.SharedViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider





class NewActivity : AppCompatActivity() {

    private val sharedViewModel: SharedViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the root layout programmatically
        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.gravity = Gravity.CENTER
        rootLayout.setPadding(16, 16, 16, 16)

        // Create ImageView to show frontImage
        val imageView = ImageView(this)
        imageView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        rootLayout.addView(imageView)

        // Retrieve the image path passed from the Intent
        val imagePath = intent.getStringExtra("imagePath")
        if (imagePath != null) {
            val bitmap = BitmapFactory.decodeFile(imagePath)
            imageView.setImageBitmap(bitmap)
        } else {
            Log.e("NewActivity", "No image path received")
        }

        // Set the root layout as the content view
        setContentView(rootLayout)
    }
}

