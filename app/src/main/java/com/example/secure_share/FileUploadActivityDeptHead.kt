package com.example.secure_share

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.MimeTypeMap
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.IOException
import java.util.*

class FileUploadActivityDeptHead : AppCompatActivity() {

    private lateinit var spinnerRoles: Spinner
    private lateinit var spinnerDepartments: Spinner
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
    private var selectedDepartment: String = ""
    private var headDepartment: String? = null

    private val PICK_FILE_REQUEST = 100
    private val roles = listOf("manager", "head", "admin")
    private val departments = listOf("Finance", "IT", "HR")
    private val recipients = mutableListOf<String>()
    private val recipientUidMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_upload_dept_head)

        initViews()
        initFirebase()

        val email = firebaseAuth.currentUser?.email
        headDepartment = getDepartmentFromEmail(email)

        if (headDepartment == null) {
            showToast("Invalid department head email")
            finish()
            return
        }

        setupRoleSpinner()
        setupDepartmentSpinner()
    }

    private fun initViews() {
        spinnerRoles = findViewById(R.id.spinner_roles)
        spinnerDepartments = findViewById(R.id.spinner_departments)
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
        return when (email?.substringBefore("@")?.take(2)?.uppercase()) {
            "FH" -> "Finance"
            "IH" -> "IT"
            "HH" -> "HR"
            else -> null
        }
    }

    private fun setupRoleSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRoles.adapter = adapter

        spinnerRoles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRole = roles[position]
                if (selectedRole == "head") {
                    spinnerDepartments.visibility = View.VISIBLE
                } else {
                    spinnerDepartments.visibility = View.GONE
                    selectedDepartment = if (selectedRole == "admin") "" else headDepartment!!
                    loadRecipients(selectedRole, selectedDepartment)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDepartmentSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, departments)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartments.adapter = adapter

        spinnerDepartments.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedDepartment = departments[position]
                if (selectedRole == "head") {
                    loadRecipients(selectedRole, selectedDepartment)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadRecipients(roleType: String, department: String) {
        showProgressDialog("Loading $roleType users...")

        var query = firestore.collection("users").whereEqualTo("roleType", roleType)
        if (roleType != "admin") {
            query = query.whereEqualTo("department", department)
        }

        query.get()
            .addOnSuccessListener { docs ->
                recipients.clear()
                recipientUidMap.clear()
                for (doc in docs) {
                    val email = doc.getString("email") ?: continue
                    recipients.add(email)
                    recipientUidMap[email] = doc.id
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
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, recipients)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRecipients.adapter = adapter

        spinnerRecipients.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedRecipientUid = recipientUidMap[recipients[position]] ?: ""
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
                    startSecureUpload(publicKey, doc.getString("email") ?: "")
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

    private fun startSecureUpload(publicKey: java.security.PublicKey, recipientEmail: String) {
        val senderUid = firebaseAuth.currentUser?.uid ?: return
        val fileUri = selectedFileUri ?: return
        val senderEmail = firebaseAuth.currentUser?.email ?: ""
        val senderDepartment = headDepartment ?: ""
        val senderRole = "head"

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

            updateProgress("Uploading file...")
            storageRef.putBytes(encryptedData, metadata)
                .addOnSuccessListener {
                    it.storage.downloadUrl.addOnSuccessListener { url ->
                        val fileData = hashMapOf(
                            "senderUid" to senderUid,
                            "senderEmail" to senderEmail,
                            "senderDepartment" to senderDepartment,
                            "senderRole" to senderRole,
                            "recipientUid" to selectedRecipientUid,
                            "recipientEmail" to recipientEmail,
                            "fileUrl" to url.toString(),
                            "fileName" to fileName,
                            "encryptedKey" to encryptedKey,
                            "mimeType" to mimeType,
                            "storagePath" to storagePath,
                            "timestamp" to System.currentTimeMillis()
                        )

                        firestore.collection("file_shares")
                            .add(fileData)
                            .addOnSuccessListener {
                                dismissProgressDialog()
                                showToast("File shared successfully!")
                                finish()
                            }
                            .addOnFailureListener {
                                dismissProgressDialog()
                                showToast("Failed to save file info")
                            }
                    }
                }
                .addOnFailureListener {
                    dismissProgressDialog()
                    showToast("Upload failed: ${it.message}")
                }

        } catch (e: Exception) {
            dismissProgressDialog()
            showToast("Encryption failed: ${e.message}")
            Log.e("Upload", "Encryption failed", e)
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

    private fun updateProgress(message: String) {
        progressDialog.setMessage(message)
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
