package com.example.secure_share

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PasswordPolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_policy)

        val btnAgree = findViewById<Button>(R.id.btnAgreePolicy)
        val policyText = findViewById<TextView>(R.id.tvPolicy)

        val email = intent.getStringExtra("USER_EMAIL") ?: return
        val role = intent.getStringExtra("USER_ROLE") ?: return
        val uid = intent.getStringExtra("USER_UID") ?: return

        policyText.text = """
            📜 App Policy:
            
            For your security, you must change the default password.
            Please choose a strong password to continue using the app.
        """.trimIndent()

        btnAgree.setOnClickListener {
            val intent = Intent(this, ChangePasswordActivity::class.java)
            intent.putExtra("USER_EMAIL", email)
            intent.putExtra("USER_ROLE", role)
            intent.putExtra("USER_UID", uid)
            startActivity(intent)
            finish()
        }
    }
}
