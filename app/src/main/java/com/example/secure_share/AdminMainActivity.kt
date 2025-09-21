package com.example.secure_share

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class AdminMainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_main) // Set Admin layout

        auth = FirebaseAuth.getInstance()

        // Find buttons by their unique IDs
        val sendFileButton = findViewById<Button>(R.id.btnAdminSendFile)
        val receiveFileButton = findViewById<Button>(R.id.btnAdminReceiveFile)
        val fileActivityButton = findViewById<Button>(R.id.btnAdminFileActivity)
        val roleActivityButton = findViewById<Button>(R.id.btnAdminRoleActivity) // Corrected variable name

        val viewLoginHistoryButton = findViewById<Button>(R.id.btnAdminViewLoginHistory)
        val logoutButton = findViewById<Button>(R.id.btnAdminLogout)
        val addMemberButton = findViewById<Button>(R.id.btnAdminAddMember)

        // Set OnClickListener for each button
        sendFileButton.setOnClickListener {

                val intent = Intent(this, FileUploadActivityAdmin::class.java)
                startActivity(intent)
                // Handle send file action here
            }

            receiveFileButton.setOnClickListener {
                val intent = Intent(this, FileDownloadActivity::class.java)
                startActivity(intent)
            }

        fileActivityButton.setOnClickListener {
            val intent =
                Intent(this, AdminFileActivity::class.java) // Navigate to AdminRoleActivity
            startActivity(intent)
        }

            roleActivityButton.setOnClickListener {
                val intent =
                    Intent(this, AdminRoleActivity::class.java) // Navigate to AdminRoleActivity
                startActivity(intent)
            }



            viewLoginHistoryButton.setOnClickListener {
                val intent = Intent(this, AdminLoginActivity::class.java)
                startActivity(intent)
            }


            addMemberButton.setOnClickListener {
                val intent = Intent(this, AddMemberActivity::class.java)
                startActivity(intent)
            }


            // Set OnClickListener for logout button
            logoutButton.setOnClickListener {
                // Sign out from Firebase
                auth.signOut()

                // Redirect to LoginActivity
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish() // Close the AdminMainActivity
            }
        }
    }





