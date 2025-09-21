package com.example.secure_share

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tvUserInfo: TextView
    private lateinit var btnLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        tvUserInfo = findViewById(R.id.tvUserInfo)
        btnLogout = findViewById(R.id.btnLogout)

        val user = auth.currentUser

        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val email = user.email ?: "Unknown Email"

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role") ?: "Unknown Role"
                    val roleType = document.getString("roleType") ?: "unknown"

                    tvUserInfo.text = "Welcome, $email\nRole: $role"

                    Handler(Looper.getMainLooper()).postDelayed({
                        when (roleType.lowercase()) {
                            "employee" -> startActivity(Intent(this, EmployeeMainActivity::class.java))
                            "manager" -> startActivity(Intent(this, ManagerMainActivity::class.java))
                            "head" -> startActivity(Intent(this, DepartmentHeadMainActivity::class.java))
                            "admin" -> startActivity(Intent(this, AdminMainActivity::class.java))
                            else -> {
                                Log.e("MainActivity", "Unknown roleType: $roleType")
                                tvUserInfo.text = "Unrecognized role type: $roleType"
                            }
                        }
                        finish()
                    }, 2000)
                } else {
                    Log.e("MainActivity", "❌ User document not found!")
                    tvUserInfo.text = "User profile not found."
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error fetching user document", e)
                tvUserInfo.text = "Error loading user info."
            }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
