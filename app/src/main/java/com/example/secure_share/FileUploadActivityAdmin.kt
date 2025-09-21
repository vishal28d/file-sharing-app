package com.example.secure_share

import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.IOException
import java.util.*

class FileUploadActivityAdmin : AppCompatActivity() {

    private lateinit var spinnerDepartments: Spinner
    private lateinit var spinnerRoles: Spinner
    private lateinit var spinnerRecipients: Spinner
    private lateinit var btnSelectFile: Button
    private lateinit var btnUpload: Button
    private lateinit var selectedFileText: TextView
    private lateinit var checkboxBroadcast: CheckBox

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseStorage: FirebaseStorage
    private lateinit var progressDialog: ProgressDialog

    private var selectedFileUri: Uri? = null
    private var selectedRecipientUid: String = ""
    private var selectedDepartment: String = ""
    private var selectedRole: String = ""
    private var isBroadcast = false

    private val PICK_FILE_REQUEST = 200
    private val departments = listOf("Finance", "IT", "HR")
    private val roles = listOf("employee", "manager", "head")
    private val recipients = mutableListOf<String>()
    private val recipientUidMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_upload_admin)

        initViews()
        initFirebase()
        setupDepartmentSpinner()
    }

    private fun initViews() {
        spinnerDepartments = findViewById(R.id.spinner_departments)
        spinnerRoles = findViewById(R.id.spinner_roles)
        spinnerRecipients = findViewById(R.id.spinner_recipients)
        btnSelectFile = findViewById(R.id.btn_select_file)
        btnUpload = findViewById(R.id.btn_upload)
        selectedFileText = findViewById(R.id.selectedFileText)
        checkboxBroadcast = findViewById(R.id.checkbox_broadcast)

        checkboxBroadcast.setOnCheckedChangeListener { _, isChecked ->
            isBroadcast = isChecked
            spinnerDepartments.isEnabled = !isChecked
            spinnerRoles.isEnabled = !isChecked
            spinnerRecipients.isEnabled = !isChecked
        }

        btnSelectFile.setOnClickListener { openFilePicker() }
        btnUpload.setOnClickListener { validateAndUpload() }
    }

    private fun initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        firebaseStorage = FirebaseStorage.getInstance()
    }

    private fun setupDepartmentSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, departments)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDepartments.adapter = adapter

        spinnerDepartments.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedDepartment = departments[position]
                setupRoleSpinner()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRoleSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRoles.adapter = adapter

        spinnerRoles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedRole = roles[position]
                loadRecipients(selectedDepartment, selectedRole)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadRecipients(department: String, roleType: String) {
        showProgressDialog("Loading users...")

        firestore.collection("users")
            .whereEqualTo("department", department)
            .whereEqualTo("roleType", roleType)
            .get()
            .addOnSuccessListener { snapshot ->
                recipients.clear()
                recipientUidMap.clear()

                for (doc in snapshot) {
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
        } else if (!isBroadcast && selectedRecipientUid.isEmpty()) {
            showToast("Please select a recipient")
        } else {
            if (isBroadcast) uploadToAllUsers() else uploadFileSecurely()
        }
    }

    private fun uploadFileSecurely() {
        val senderUid = firebaseAuth.currentUser?.uid ?: return
        val senderEmail = firebaseAuth.currentUser?.email ?: ""
        val fileUri = selectedFileUri ?: return

        showProgressDialog("Uploading file securely...")

        firestore.collection("users").document(selectedRecipientUid).get()
            .addOnSuccessListener { doc ->
                val publicKeyStr = doc.getString("publicKey")
                val publicKey = RSAHelper.decodePublicKey(publicKeyStr ?: "")

                if (publicKey == null) {
                    dismissProgressDialog()
                    showToast("Invalid public key")
                    return@addOnSuccessListener
                }

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
                        .setCustomMetadata("senderUid", senderUid)
                        .setCustomMetadata("recipientUid", selectedRecipientUid)
                        .setCustomMetadata("originalName", fileName)
                        .build()

                    val storagePath = "uploads/${UUID.randomUUID()}.enc"
                    val storageRef = firebaseStorage.reference.child(storagePath)

                    storageRef.putBytes(encryptedData, metadata)
                        .addOnSuccessListener {
                            it.storage.downloadUrl.addOnSuccessListener { url ->
                                saveFileShareRecord(senderUid, selectedRecipientUid, url.toString(), fileName, encryptedKey, mimeType, storagePath, senderEmail)
                            }
                        }
                        .addOnFailureListener {
                            dismissProgressDialog()
                            showToast("Upload failed: ${it.message}")
                        }

                } catch (e: Exception) {
                    dismissProgressDialog()
                    showToast("Encryption failed: ${e.message}")
                    Log.e("Upload", "Encryption error", e)
                }
            }
            .addOnFailureListener {
                dismissProgressDialog()
                showToast("Recipient info load failed")
            }
    }

    private fun uploadToAllUsers() {
        val senderUid = firebaseAuth.currentUser?.uid ?: return
        val senderEmail = firebaseAuth.currentUser?.email ?: ""
        val fileUri = selectedFileUri ?: return

        showProgressDialog("Broadcasting file...")

        val fileKey = EncryptionHelper.generateAESKey()
        val encryptedData = contentResolver.openInputStream(fileUri)?.use {
            EncryptionHelper.encryptStream(it, fileKey)
        } ?: run {
            showToast("Failed to read file")
            dismissProgressDialog()
            return
        }

        val fileName = getFileNameFromUri(fileUri)
        val mimeType = contentResolver.getType(fileUri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "bin")) ?: "*/*"
        val storagePath = "uploads/broadcast_${UUID.randomUUID()}.enc"
        val metadata = StorageMetadata.Builder()
            .setContentType(mimeType)
            .setCustomMetadata("senderUid", senderUid)
            .setCustomMetadata("originalName", fileName)
            .build()

        val storageRef = firebaseStorage.reference.child(storagePath)

        storageRef.putBytes(encryptedData, metadata)
            .addOnSuccessListener {
                it.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                    firestore.collection("users").get()
                        .addOnSuccessListener { allUsers ->
                            for (user in allUsers) {
                                val recipientUid = user.id
                                val recipientEmail = user.getString("email") ?: continue
                                val publicKeyStr = user.getString("publicKey") ?: continue
                                val publicKey = RSAHelper.decodePublicKey(publicKeyStr) ?: continue
                                val encryptedKey = RSAHelper.encryptAESKey(fileKey, publicKey)

                                val shareData = hashMapOf(
                                    "senderUid" to senderUid,
                                    "recipientUid" to recipientUid,
                                    "fileUrl" to downloadUrl.toString(),
                                    "fileName" to fileName,
                                    "encryptedKey" to encryptedKey,
                                    "mimeType" to mimeType,
                                    "storagePath" to storagePath,
                                    "timestamp" to System.currentTimeMillis(),
                                    "senderEmail" to senderEmail,
                                    "recipientEmail" to recipientEmail
                                )

                                firestore.collection("file_shares").add(shareData)
                            }
                            dismissProgressDialog()
                            showToast("File broadcasted to all users!")
                            finish()
                        }
                        .addOnFailureListener {
                            dismissProgressDialog()
                            showToast("Failed to fetch users for broadcast")
                        }
                }
            }
            .addOnFailureListener {
                dismissProgressDialog()
                showToast("Failed to upload file for broadcast")
            }
    }

    private fun saveFileShareRecord(
        senderUid: String,
        recipientUid: String,
        fileUrl: String,
        fileName: String,
        encryptedKey: String,
        mimeType: String,
        storagePath: String,
        senderEmail: String
    ) {
        val fileData = hashMapOf(
            "senderUid" to senderUid,
            "recipientUid" to recipientUid,
            "fileUrl" to fileUrl,
            "fileName" to fileName,
            "encryptedKey" to encryptedKey,
            "mimeType" to mimeType,
            "storagePath" to storagePath,
            "timestamp" to System.currentTimeMillis(),
            "senderEmail" to senderEmail
        )

        firestore.collection("file_shares")
            .add(fileData)
            .addOnSuccessListener {
                dismissProgressDialog()
                showToast("File shared successfully")
                finish()
            }
            .addOnFailureListener {
                dismissProgressDialog()
                showToast("Failed to save file info")
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
}
