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
import java.security.PublicKey
import java.util.*

class FileUploadActivity : AppCompatActivity() {
    private lateinit var selectFileButton: Button
    private lateinit var uploadFileButton: Button
    private lateinit var managerSpinner: Spinner
    private lateinit var selectedFileText: TextView

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firebaseStorage: FirebaseStorage
    private lateinit var firestore: FirebaseFirestore

    private var selectedFileUri: Uri? = null
    private val managerDisplayList = mutableListOf<String>()
    private val managerUidMap = mutableMapOf<String, String>()
    private lateinit var progressDialog: ProgressDialog

    private val PICK_FILE_REQUEST = 1
    private val MAX_FILE_SIZE_MB = 10
    private val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024
    private var selectedManagerUid: String = ""
    private var employeeDepartment: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_upload)

        initViews()
        initFirebase()

        val userEmail = firebaseAuth.currentUser?.email
        employeeDepartment = getDepartmentFromEmail(userEmail)

        if (employeeDepartment == null) {
            showToast("Unrecognized email prefix. Must start with FE, IE, or HE.")
            finish()
            return
        }

        loadManagers(employeeDepartment!!)
    }

    private fun initViews() {
        selectFileButton = findViewById(R.id.btn_select_file)
        uploadFileButton = findViewById(R.id.btn_upload)
        managerSpinner = findViewById(R.id.spinner_managers)
        selectedFileText = findViewById(R.id.selectedFileText)

        selectFileButton.setOnClickListener { openFilePicker() }
        uploadFileButton.setOnClickListener { validateAndUpload() }
    }

    private fun initFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()
        firebaseStorage = FirebaseStorage.getInstance()
        firestore = FirebaseFirestore.getInstance()
    }

    private fun getDepartmentFromEmail(email: String?): String? {
        if (email.isNullOrBlank()) return null
        return when (email.substringBefore("@").take(2).uppercase()) {
            "FE" -> "Finance"
            "IE" -> "IT"
            "HE" -> "HR"
            else -> null
        }
    }

    private fun loadManagers(department: String) {
        showProgressDialog("Loading department managers...")

        firestore.collection("users")
            .whereEqualTo("roleType", "manager")
            .whereEqualTo("department", department)
            .get()
            .addOnSuccessListener { docs ->
                managerDisplayList.clear()
                managerUidMap.clear()
                docs.forEach { doc ->
                    val email = doc.getString("email") ?: return@forEach
                    val uid = doc.id
                    managerDisplayList.add(email)
                    managerUidMap[email] = uid
                }
                setupManagerSpinner()
                dismissProgressDialog()
            }
            .addOnFailureListener {
                dismissProgressDialog()
                showToast("Failed to load managers")
            }
    }

    private fun setupManagerSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, managerDisplayList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        managerSpinner.adapter = adapter

        managerSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedEmail = managerDisplayList[position]
                selectedManagerUid = managerUidMap[selectedEmail] ?: ""
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
        } else if (selectedManagerUid.isEmpty()) {
            showToast("Please select a recipient")
        } else {
            prepareMetadataAndUpload()
        }
    }

    private fun prepareMetadataAndUpload() {
        val senderUid = firebaseAuth.currentUser?.uid ?: return
        val senderEmail = firebaseAuth.currentUser?.email ?: ""

        firestore.collection("users").document(senderUid).get()
            .addOnSuccessListener { senderDoc ->
                val senderDepartment = senderDoc.getString("department") ?: ""
                val senderRole = senderDoc.getString("roleType") ?: ""

                firestore.collection("users").document(selectedManagerUid).get()
                    .addOnSuccessListener { recipientDoc ->
                        val recipientEmail = recipientDoc.getString("email") ?: ""
                        val recipientKeyStr = recipientDoc.getString("publicKey") ?: ""
                        val recipientKey = RSAHelper.decodePublicKey(recipientKeyStr)

                        val fingerprint = RSAHelper.getPublicKeyFingerprint(selectedManagerUid)

                        if (recipientKey != null) {
                            startSecureUploadWithMetadata(
                                selectedFileUri!!,
                                recipientKey,
                                fingerprint,
                                senderEmail,
                                senderDepartment,
                                senderRole,
                                recipientEmail
                            )
                        } else {
                            showToast("Invalid recipient key")
                        }
                    }
            }
    }

    private fun startSecureUploadWithMetadata(
        fileUri: Uri,
        recipientPublicKey: PublicKey,
        recipientFingerprint: String?,
        senderEmail: String,
        senderDepartment: String,
        senderRole: String,
        recipientEmail: String
    ) {
        val senderUid = firebaseAuth.currentUser?.uid ?: return

        try {
            val fileKey = EncryptionHelper.generateAESKey()
            val encryptedContent = contentResolver.openInputStream(fileUri)?.use {
                EncryptionHelper.encryptStream(it, fileKey)
            } ?: throw IOException("File read error")

            val encryptedKey = RSAHelper.encryptAESKey(fileKey, recipientPublicKey)
            val fileName = getFileNameFromUri(fileUri) ?: "file_${System.currentTimeMillis()}"
            val mimeType = contentResolver.getType(fileUri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "bin")) ?: "*/*"

            val metadata = StorageMetadata.Builder()
                .setContentType(mimeType)
                .setCustomMetadata("senderUid", senderUid)
                .setCustomMetadata("recipientUid", selectedManagerUid)
                .setCustomMetadata("originalName", fileName)
                .build()

            val storagePath = "uploads/${UUID.randomUUID()}.enc"
            val storageRef = firebaseStorage.reference.child(storagePath)

            storageRef.putBytes(encryptedContent, metadata)
                .addOnSuccessListener {
                    it.storage.downloadUrl.addOnSuccessListener { url ->
                        saveFileShareRecord(
                            senderUid, selectedManagerUid, url.toString(), storagePath, fileName,
                            encryptedKey, mimeType, recipientFingerprint,
                            senderEmail, senderDepartment, senderRole, recipientEmail
                        )
                    }
                }
                .addOnFailureListener {
                    showToast("Upload failed: ${it.message}")
                }

        } catch (e: Exception) {
            showToast("Encryption failed: ${e.message}")
            Log.e("Upload", "Encryption failed", e)
        }
    }

    private fun saveFileShareRecord(
        senderUid: String,
        recipientUid: String,
        fileUrl: String,
        storagePath: String,
        fileName: String,
        encryptedKey: String,
        mimeType: String,
        recipientFingerprint: String?,
        senderEmail: String,
        senderDepartment: String,
        senderRole: String,
        recipientEmail: String
    ) {
        val fileData = hashMapOf(
            "senderUid" to senderUid,
            "senderEmail" to senderEmail,
            "senderDepartment" to senderDepartment,
            "senderRole" to senderRole,
            "recipientUid" to recipientUid,
            "recipientEmail" to recipientEmail,
            "fileUrl" to fileUrl,
            "fileName" to fileName,
            "encryptedKey" to encryptedKey,
            "mimeType" to mimeType,
            "storagePath" to storagePath,
            "timestamp" to System.currentTimeMillis(),
            "recipientKeyFingerprint" to recipientFingerprint
        )

        firestore.collection("file_shares")
            .add(fileData)
            .addOnSuccessListener {
                dismissProgressDialog()
                showToast("File shared securely!")
                finish()
            }
            .addOnFailureListener {
                dismissProgressDialog()
                showToast("Failed to record file share")
            }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        }
    }

    private fun showProgressDialog(message: String) {
        progressDialog = ProgressDialog(this).apply {
            setMessage(message)
            setCancelable(false)
            show()
        }
    }

    private fun dismissProgressDialog() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissProgressDialog()
    }
}
