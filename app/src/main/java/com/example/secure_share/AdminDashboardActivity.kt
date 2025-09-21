package com.example.secure_share

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AdminDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        val tvSuccessMessage = findViewById<TextView>(R.id.tvSuccessMessage)
        val btnGoBack = findViewById<Button>(R.id.btnGoBack)

        // Get the added member's email and role from intent extras
        val email = intent.getStringExtra("USER_EMAIL") ?: "Unknown Email"
        val role = intent.getStringExtra("USER_ROLE") ?: "unkonwn Role"

        tvSuccessMessage.text = "Successfully added:\nEmail: $email\nRole: $role"

        btnGoBack.setOnClickListener {
            val intent = Intent(this, AdminMainActivity::class.java)
            startActivity(intent)
            finish() // Close the current activity
        }
    }
}
