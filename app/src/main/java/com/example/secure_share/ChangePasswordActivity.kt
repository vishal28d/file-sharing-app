package com.example.secure_share

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var updateButton: Button
    private lateinit var progressBar: ProgressBar

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        newPasswordInput = findViewById(R.id.etNewPassword)
        confirmPasswordInput = findViewById(R.id.etConfirmPassword)
        updateButton = findViewById(R.id.btnUpdatePassword)
        progressBar = findViewById(R.id.progressBarChange)

        val email = intent.getStringExtra("USER_EMAIL") ?: return
        val role = intent.getStringExtra("USER_ROLE") ?: return
        val uid = intent.getStringExtra("USER_UID") ?: return

        updateButton.setOnClickListener {
            val newPassword = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (newPassword.length < 6) {
                newPasswordInput.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                confirmPasswordInput.error = "Passwords do not match"
                return@setOnClickListener
            }

            progressBar.visibility = android.view.View.VISIBLE

            auth.currentUser?.updatePassword(newPassword)
                ?.addOnSuccessListener {
                    firestore.collection("users").document(uid)
                        .update("mustChangePassword", false)
                        .addOnSuccessListener {
                            progressBar.visibility = android.view.View.GONE
                            Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                            goToDashboard(role)
                        }
                        .addOnFailureListener { e ->
                            progressBar.visibility = android.view.View.GONE
                            Toast.makeText(this, "Password changed but Firestore update failed.", Toast.LENGTH_LONG).show()
                            Log.e("FirestoreUpdate", "Failed to update mustChangePassword", e)
                            auth.signOut()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                }
                ?.addOnFailureListener { e ->
                    progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this, "Failed to update password: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("PasswordChange", "Error updating password", e)
                }
        }
    }

    private fun goToDashboard(role: String) {
        val dashboard = when (role.lowercase()) {
            "employee" -> EmployeeMainActivity::class.java
            "manager" -> ManagerMainActivity::class.java
            "department_head" -> DepartmentHeadMainActivity::class.java
            "admin" -> AdminMainActivity::class.java
            else -> LoginActivity::class.java
        }
        startActivity(Intent(this, dashboard))
        finish()
    }
}
