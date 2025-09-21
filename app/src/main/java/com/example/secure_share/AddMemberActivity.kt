package com.example.secure_share

import android.content.*
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.functions.FirebaseFunctions

class AddMemberActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var btnAddMember: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvDetectedRole: TextView

    private val functions = FirebaseFunctions.getInstance("us-central1")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_member)

        etEmail = findViewById(R.id.etEmail)
        btnAddMember = findViewById(R.id.btnAddMember)
        progressBar = findViewById(R.id.progressBar)
        tvDetectedRole = findViewById(R.id.tvDetectedRole)

        btnAddMember.setOnClickListener {
            val email = etEmail.text.toString().trim()

            if (email.isEmpty()) {
                etEmail.error = "Email is required"
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Invalid email format"
                return@setOnClickListener
            }

            val roleInfo = extractRoleFromEmailPrefix(email)
            if (roleInfo == null) {
                handleError("Unrecognized email prefix. Use formats like FM, IH, HE, etc.")
                return@setOnClickListener
            }

            val (department, roleType, code) = roleInfo
            val fullRoleName = "$department ${roleType.replaceFirstChar { it.uppercase() }}"

            tvDetectedRole.text = "Detected: $fullRoleName"

            val password = "123456"
            createUserWithFunction(email, password, roleType, fullRoleName, department, code)
        }
    }

    private fun extractRoleFromEmailPrefix(email: String): Triple<String, String, String>? {
        val prefix = email.substringBefore("@").take(2).uppercase()

        return when (prefix) {
            "FH" -> Triple("Finance", "head", prefix)
            "FM" -> Triple("Finance", "manager", prefix)
            "FE" -> Triple("Finance", "employee", prefix)

            "IH" -> Triple("IT", "head", prefix)
            "IM" -> Triple("IT", "manager", prefix)
            "IE" -> Triple("IT", "employee", prefix)

            "HH" -> Triple("HR", "head", prefix)
            "HM" -> Triple("HR", "manager", prefix)
            "HE" -> Triple("HR", "employee", prefix)

            else -> null
        }
    }

    private fun createUserWithFunction(
        email: String,
        password: String,
        roleType: String,
        fullRoleName: String,
        department: String,
        code: String
    ) {
        progressBar.visibility = View.VISIBLE
        btnAddMember.isEnabled = false

        val keyGenerated = RSAHelper.generateKeyPair(email)
        if (!keyGenerated) {
            progressBar.visibility = View.GONE
            btnAddMember.isEnabled = true
            handleError("Failed to generate RSA key pair for $email")
            return
        }

        val publicKeyBase64 = RSAHelper.getPublicKeyBase64(email)
        if (publicKeyBase64 == null) {
            progressBar.visibility = View.GONE
            btnAddMember.isEnabled = true
            handleError("Failed to retrieve public key for $email")
            return
        }

        val data = hashMapOf(
            "email" to email,
            "password" to password,
            "role" to fullRoleName,           // e.g., "Finance Head"
            "roleType" to roleType,           // e.g., "head" (for logic checks)
            "department" to department,
            "code" to code,
            "publicKey" to publicKeyBase64,
            "mustChangePassword" to true
        )

        functions
            .getHttpsCallable("createNewUser")
            .call(data)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                btnAddMember.isEnabled = true

                copyToClipboard(password)
                Toast.makeText(this, "$email created!\nPassword: $password", Toast.LENGTH_LONG).show()

                val intent = Intent(this, AdminDashboardActivity::class.java)
                intent.putExtra("USER_EMAIL", email)
                intent.putExtra("USER_ROLE", fullRoleName)
                startActivity(intent)

                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnAddMember.isEnabled = true
                handleError("❌ Failed to create user: ${e.message}")
            }
    }

    private fun handleError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun copyToClipboard(password: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("TempPassword", password)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
//xvgfgrf