package com.example.taller2_lapiceselite

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        manageButtons()
    }

    private fun manageButtons() {
        val btnContacts = findViewById<ImageButton>(R.id.contactsIDbt)
        btnContacts.setOnClickListener {
            startActivity(Intent(this, Contacts::class.java))
        }

        val btnCamera = findViewById<ImageButton>(R.id.cameraIDbt)
        btnCamera.setOnClickListener {
            startActivity(Intent(this, Camera::class.java))
        }

        val btnMap = findViewById<ImageButton>(R.id.osmapIDbt)
        btnMap.setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }
    }
}