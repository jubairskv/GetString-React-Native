package com.rdemo

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import android.widget.LinearLayout.LayoutParams

class NewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create the root layout programmatically
        val rootLayout = LinearLayout(this)
        rootLayout.orientation = LinearLayout.VERTICAL
        rootLayout.gravity = Gravity.CENTER
        rootLayout.setPadding(16, 16, 16, 16)

        // Create TextView dynamically
        val textView = TextView(this)
        textView.text = "Welcome to New Activity!"
        textView.textSize = 20f // Set the text size
        textView.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        rootLayout.addView(textView)

        // Create Button dynamically
        val button = Button(this)
        button.text = "Click Me"
        button.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        
        // Set button click listener to show Toast
        button.setOnClickListener {
            Toast.makeText(this, "Button clicked!", Toast.LENGTH_SHORT).show()
        }
        
        rootLayout.addView(button)

        // Set the root layout as the content view
        setContentView(rootLayout)
    }
}
