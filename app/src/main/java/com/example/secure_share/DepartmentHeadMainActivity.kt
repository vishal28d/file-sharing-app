package com.example.secure_share

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class DepartmentHeadMainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Set Department Head layout

        auth = FirebaseAuth.getInstance()

        // ✅ Get user info from intent
        val email = intent.getStringExtra("USER_EMAIL") ?: "Unknown"
        val role = intent.getStringExtra("USER_ROLE") ?: "Unknown"

        // ✅ Set user info on screen
        val tvUserInfo = findViewById<TextView>(R.id.tvUserInfo)
        tvUserInfo.text = "Logged in as: $email\nYour Role: $role"

        // Find buttons by their IDs
        val sendFileButton = findViewById<Button>(R.id.btnSendFile)
        val receiveFileButton = findViewById<Button>(R.id.btnReceiveFile)
        val fileActivityButton = findViewById<Button>(R.id.btnFileActivity)

        val logoutButton = findViewById<Button>(R.id.btnLogout)

        // Set OnClickListener for each button
        sendFileButton.setOnClickListener {
            val intent = Intent(this, FileUploadActivityDeptHead::class.java) // Use the correct activity name
            startActivity(intent)
        }

        receiveFileButton.setOnClickListener {
            val intent = Intent(this, FileDownloadActivity::class.java)
            startActivity(intent)
        }


        fileActivityButton.setOnClickListener {
            val intent = Intent(this, FileActivity::class.java)
            startActivity(intent)
        }




        // Set OnClickListener for logout button
        logoutButton.setOnClickListener {
            // Sign out from Firebase
            auth.signOut()

            // Redirect to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Close the DepartmentHeadMainActivity
        }
    }
}
