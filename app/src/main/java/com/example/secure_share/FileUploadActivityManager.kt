package com.example.secure_share

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.IOException
import java.util.*

class FileUploadActivityManager : AppCompatActivity() {

    private lateinit var spinnerRoles: Spinner
    private lateinit var spinnerRecipients: Spinner
    private lateinit var btnSelectFile: Button
    private lateinit var btnUpload: Button
    private lateinit var selectedFileText: TextView

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseStorage: FirebaseStorage
    private lateinit var progressDialog: ProgressDialog

    private var selectedFileUri: Uri? = null
    private var selectedRecipientUid: String = ""
    private var selectedRole: String = ""
    private var managerDepartment: String? = null
    private var senderEmail: String = ""
    private var recipientEmail: String = ""
    private val senderRole: String = "manager"

    private val PICK_FILE_REQUEST = 100
    private val roles = listOf("employee", "head")
    private val recipientList = mutableListOf<String>()
    private val recipientUidMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_upload_manager)

        initViews()
        initFirebase()

        senderEmail = firebaseAuth.currentUser?.email ?: ""
        managerDepartment = getDepartmentFromEmail(senderEmail)

        if (managerDepartment == null) {
            showToast("Unrecognized manager email format")
            finish()
            return
        }

        setupRoleSpinner()
    }

    private fun initViews() {
        spinnerRoles = findViewById(R.id.spinner_roles)
        spinnerRecipients = findViewById(R.id.spinner_recipients)
        btnSelectFile = findViewById(R.id.btn_select_file)
        btnUpload = findViewById(R.id.btn_upload)
        selectedFileText = findViewById(R.id.selectedFileText)

        btnSelectFile.setOnClickListener { openFilePicker() }
        btnUpload.setOnClickListener { validateAndUpload() }
    }

    private fun initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        firebaseStorage = FirebaseStorage.getInstance()
    }

    private fun getDepartmentFromEmail(email: String?): String? {
        if (email.isNullOrBlank()) return null
        return when (email.substringBefore("@").take(2).uppercase()) {
            "FM" -> "Finance"
            "IM" -> "IT"
            "HM" -> "HR"
            else -> null
        }
    }

    private fun setupRoleSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRoles.adapter = adapter

        spinnerRoles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                selectedRole = roles[position]
                loadRecipientsForRole(selectedRole)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadRecipientsForRole(roleType: String) {
        if (managerDepartment == null) {
            showToast("Department not identified")
            return
        }

        showProgressDialog("Loading $roleType users...")

        firestore.collection("users")
            .whereEqualTo("roleType", roleType)
            .whereEqualTo("department", managerDepartment)
            .get()
            .addOnSuccessListener { snapshot ->
                recipientList.clear()
                recipientUidMap.clear()
                for (doc in snapshot) {
                    val email = doc.getString("email")
                    val uid = doc.id
                    if (email != null) {
                        recipientList.add(email)
                        recipientUidMap[email] = uid
                    }
                }
                setupRecipientSpinner()
                dismissProgressDialog()
            }
            .addOnFailureListener {
                dismissProgressDialog()
                showToast("Failed to load recipients")
            }
    }

    private fun setupRecipientSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, recipientList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRecipients.adapter = adapter

        spinnerRecipients.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedEmail = recipientList[position]
                selectedRecipientUid = recipientUidMap[selectedEmail] ?: ""
                recipientEmail = selectedEmail
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun openFilePicker() {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }.also { startActivityForResult(it, PICK_FILE_REQUEST) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            selectedFileUri = data?.data
            selectedFileText.text = selectedFileUri?.lastPathSegment ?: "File selected"
        }
    }

    private fun validateAndUpload() {
        if (selectedFileUri == null) {
            showToast("Please select a file")
        } else if (selectedRecipientUid.isEmpty()) {
            showToast("Please select a recipient")
        } else {
            verifyRecipientKeyAndUpload()
        }
    }

    private fun verifyRecipientKeyAndUpload() {
        showProgressDialog("Verifying recipient...")

        firestore.collection("users").document(selectedRecipientUid)
            .get()
            .addOnSuccessListener { doc ->
                val publicKeyStr = doc.getString("publicKey")
                val publicKey = RSAHelper.decodePublicKey(publicKeyStr ?: "")

                if (publicKey != null) {
                    startSecureUpload(publicKey)
                } else {
                    dismissProgressDialog()
                    showToast("Invalid public key")
                }
            }
            .addOnFailureListener {
                dismissProgressDialog()
                showToast("Failed to verify recipient")
            }
    }

    private fun startSecureUpload(publicKey: java.security.PublicKey) {
        val senderUid = firebaseAuth.currentUser?.uid ?: return
        val fileUri = selectedFileUri ?: return

        try {
            val fileKey = EncryptionHelper.generateAESKey()
            val encryptedData = contentResolver.openInputStream(fileUri)?.use {
                EncryptionHelper.encryptStream(it, fileKey)
            } ?: throw IOException("Unable to read file")

            val encryptedKey = RSAHelper.encryptAESKey(fileKey, publicKey)
            val fileName = getFileNameFromUri(fileUri)
            val mimeType = contentResolver.getType(fileUri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "bin")) ?: "*/*"

            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCustomMetadata("mimeType", mimeType)
                .setCustomMetadata("senderUid", senderUid)
                .setCustomMetadata("recipientUid", selectedRecipientUid)
                .setCustomMetadata("originalName", fileName)
                .build()

            val storagePath = "uploads/${UUID.randomUUID()}.enc"
            val storageRef = firebaseStorage.reference.child(storagePath)

            storageRef.putBytes(encryptedData, metadata)
                .addOnSuccessListener {
                    it.storage.downloadUrl.addOnSuccessListener { url ->
                        saveFileShareRecord(senderUid, selectedRecipientUid, url.toString(), storagePath, fileName, encryptedKey, mimeType)
                    }
                }
                .addOnFailureListener {
                    dismissProgressDialog()
                    showToast("Upload failed: ${it.message}")
                }

        } catch (e: Exception) {
            dismissProgressDialog()
            showToast("Upload error: ${e.message}")
            Log.e("Upload", "Error", e)
        }
    }

    private fun saveFileShareRecord(
        senderUid: String,
        recipientUid: String,
        fileUrl: String,
        storagePath: String,
        fileName: String,
        encryptedKey: String,
        mimeType: String
    ) {
        val fileRecord = hashMapOf(
            "senderUid" to senderUid,
            "senderEmail" to senderEmail,
            "senderDepartment" to managerDepartment,
            "senderRole" to senderRole,
            "recipientUid" to recipientUid,
            "recipientEmail" to recipientEmail,
            "fileUrl" to fileUrl,
            "storagePath" to storagePath,
            "fileName" to fileName,
            "encryptedKey" to encryptedKey,
            "mimeType" to mimeType,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("file_shares")
            .add(fileRecord)
            .addOnSuccessListener {
                dismissProgressDialog()
                showToast("File shared successfully!")
                finish()
            }
            .addOnFailureListener {
                dismissProgressDialog()
                showToast("Failed to save file record")
            }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(index)
        } ?: "file_${System.currentTimeMillis()}"
    }

    private fun showProgressDialog(message: String) {
        progressDialog = ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    private fun dismissProgressDialog() {
        if (::progressDialog.isInitialized) progressDialog.dismiss()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissProgressDialog()
    }
}
