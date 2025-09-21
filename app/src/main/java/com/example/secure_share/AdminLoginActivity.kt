package com.example.secure_share

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class AdminLoginActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var loginTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        db = FirebaseFirestore.getInstance()
        loginTextView = findViewById(R.id.loginHistoryTextView)

        fetchLoginHistory()
    }

    private fun fetchLoginHistory() {
        db.collection("loginActivity")
            .orderBy("timestamp", Query.Direction.DESCENDING) // Fetch latest logs first
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    loginTextView.text = "No login history available."
                    return@addOnSuccessListener
                }

                val loginHistory = StringBuilder()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                for (document in result) {
                    val email = document.getString("email") ?: "Unknown"
                    val role = document.getString("role") ?: "Unknown Role"

                    // Convert Firestore timestamp to readable format
                    val timestamp = document.getTimestamp("timestamp")?.toDate()?.let { date ->
                        dateFormat.format(date)
                    } ?: "No Time"

                    loginHistory.append("📌 [$role] $timestamp\n  - $email\n\n")
                }

                loginTextView.text = loginHistory.toString()
            }
            .addOnFailureListener { e ->
                loginTextView.text = "Failed to retrieve login history."
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
