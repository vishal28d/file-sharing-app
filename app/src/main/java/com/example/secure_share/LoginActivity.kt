package com.example.secure_share

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val emailEditText = findViewById<EditText>(R.id.editTextTextEmailAddress)
        val passwordEditText = findViewById<EditText>(R.id.editTextTextPassword)
        val loginButton = findViewById<Button>(R.id.button)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val uid = auth.currentUser?.uid
                            val userEmail = auth.currentUser?.email
                            if (uid != null && userEmail != null) {
                                fetchUserRole(uid, userEmail)
                            } else {
                                Toast.makeText(this, "Error: Unable to get user info.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchUserRole(uid: String, email: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role") ?: "unknown"          // Full title (e.g., "Finance Head")
                    val roleType = document.getString("roleType") ?: "unknown"  // Short code (e.g., "head")

                    val mustChangePassword = document.getBoolean("mustChangePassword") ?: false

                    if (mustChangePassword) {
                        val intent = Intent(this, PasswordPolicyActivity::class.java)
                        intent.putExtra("USER_EMAIL", email)
                        intent.putExtra("USER_ROLE", role)
                        intent.putExtra("USER_UID", uid)
                        startActivity(intent)
                        finish()
                        return@addOnSuccessListener
                    }

                    Log.d("LoginDebug", "Fetched UID: $uid")
                    Log.d("LoginDebug", "User Email: $email")
                    Log.d("LoginDebug", "User Role: $role")
                    Log.d("LoginDebug", "User RoleType: $roleType")
                    Log.d("LoginDebug", "User Document: ${document.data}")

                    if (roleType == "unknown") {
                        Toast.makeText(this, "⚠️ User roleType missing. Contact admin.", Toast.LENGTH_LONG).show()
                        auth.signOut()
                        return@addOnSuccessListener
                    }

                    try {
                        RSAHelper.generateKeyPair(uid)
                        EncryptionHelper.generateAndStoreKey(this)
                        uploadPublicKeyToFirestore(uid, email)
                    } catch (e: Exception) {
                        Log.e("KeyGeneration", "Failed to generate keys", e)
                        Toast.makeText(this, "Security setup failed. Try again.", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    // ✅ Log login activity with roleType
                    saveLoginActivity(uid, email, role, roleType)

                    // ✅ Navigate based on roleType
                    when (roleType.lowercase()) {
                        "employee" -> {
                            val intent = Intent(this, EmployeeMainActivity::class.java)
                            intent.putExtra("USER_EMAIL", email)
                            intent.putExtra("USER_ROLE", role)
                            startActivity(intent)
                        }
                        "manager" -> {
                            val intent = Intent(this, ManagerMainActivity::class.java)
                            intent.putExtra("USER_EMAIL", email)
                            intent.putExtra("USER_ROLE", role)
                            startActivity(intent)
                        }
                        "head" -> {
                            val intent = Intent(this, DepartmentHeadMainActivity::class.java)
                            intent.putExtra("USER_EMAIL", email)
                            intent.putExtra("USER_ROLE", role)
                            startActivity(intent)
                        }
                        "admin" -> {
                            val intent = Intent(this, AdminMainActivity::class.java)
                            intent.putExtra("USER_EMAIL", email)
                            intent.putExtra("USER_ROLE", role)
                            startActivity(intent)
                        }
                        else -> {
                            Toast.makeText(this, "Unauthorized Role: $roleType", Toast.LENGTH_LONG).show()
                            auth.signOut()
                        }
                    }


                    finish()
                } else {
                    Log.e("FirestoreDebug", "❌ No Firestore document found for UID: $uid")
                    Toast.makeText(this, "User profile not found. Contact admin.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreDebug", "Error retrieving role: ${e.message}", e)
                Toast.makeText(this, "Error retrieving user data: ${e.message}", Toast.LENGTH_LONG).show()
                auth.signOut()
            }
    }

    private fun uploadPublicKeyToFirestore(uid: String, email: String) {
        val publicKeyBase64 = RSAHelper.getPublicKeyBase64(uid)
        if (publicKeyBase64 != null) {
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "publicKey" to publicKeyBase64,
                        "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                .addOnFailureListener { e ->
                    Log.e("Firestore", "❌ Failed to upload public key", e)
                }
        } else {
            Log.e("RSA", "❌ Public key not available for: $email")
        }
    }

    private fun saveLoginActivity(uid: String, email: String, role: String, roleType: String) {
        val loginData = hashMapOf(
            "uid" to uid,
            "email" to email,
            "role" to role,
            "roleType" to roleType,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("loginActivity").add(loginData)
            .addOnFailureListener { e ->
                Toast.makeText(this, "⚠️ Failed to store login record: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
