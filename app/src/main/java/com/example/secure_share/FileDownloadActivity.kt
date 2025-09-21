package com.example.secure_share

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class FileDownloadActivity : AppCompatActivity() {

    private lateinit var filesListView: ListView
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var firebaseStorage: FirebaseStorage
    private lateinit var fileAdapter: FileAdapter
    private val fileList = mutableListOf<FileModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_download)

        initViews()
        setupFirebase()
        loadSharedFiles()
    }

    private fun setupFirebase() {
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        firebaseStorage = FirebaseStorage.getInstance()
    }

    private fun loadSharedFiles() {
        val userUid = firebaseAuth.currentUser?.uid ?: return

        firestore.collection("file_shares")
            .whereEqualTo("recipientUid", userUid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    showError("Failed to load files")
                    return@addSnapshotListener
                }

                fileList.clear()
                documents?.forEach { doc ->
                    val file = FileModel(
                        id = doc.id,
                        senderUid = doc.getString("senderUid") ?: "Unknown",
                        recipientUid = doc.getString("recipientUid") ?: "Unknown",
                        fileName = doc.getString("fileName") ?: "Unnamed File",
                        fileUrl = doc.getString("fileUrl") ?: "",
                        encryptedKey = doc.getString("encryptedKey") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0,
                        mimeType = doc.getString("mimeType") ?: "*/*"
                    )

                    fetchUserEmail(file.senderUid) { email ->
                        file.senderDisplayName = email
                        fileAdapter.notifyDataSetChanged()
                    }

                    fileList.add(file)
                }
                fileAdapter.notifyDataSetChanged()
            }
    }

    private fun fetchUserEmail(uid: String, callback: (String) -> Unit) {
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val email = doc.getString("email") ?: uid
                callback(email)
            }
            .addOnFailureListener {
                callback(uid)
            }
    }

    private fun initViews() {
        filesListView = findViewById(R.id.filesListView)
        fileAdapter = FileAdapter(this, fileList)
        filesListView.adapter = fileAdapter
        filesListView.setOnItemClickListener { _, _, position, _ ->
            showDownloadOptions(fileList[position])
        }
    }

    private fun showDownloadOptions(fileModel: FileModel) {
        val displaySender = fileModel.senderDisplayName.ifBlank { fileModel.senderUid }

        AlertDialog.Builder(this)
            .setTitle(fileModel.fileName)
            .setMessage("From: $displaySender\n" +
                    "Date: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(fileModel.timestamp))}")
            .setPositiveButton("View") { _, _ ->
                startFileDownloadWithOption(fileModel, openAfterDownload = true)
            }
            .setNeutralButton("Download") { _, _ ->
                startFileDownloadWithOption(fileModel, openAfterDownload = false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startFileDownloadWithOption(fileModel: FileModel, openAfterDownload: Boolean) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Preparing file...")
            setCancelable(false)
            show()
        }

        val tempFile = File(cacheDir, "temp_${System.currentTimeMillis()}.enc")

        firebaseStorage.getReferenceFromUrl(fileModel.fileUrl)
            .getFile(tempFile)
            .addOnSuccessListener {
                progressDialog.setMessage("Decrypting file...")
                processDownloadedFile(tempFile, fileModel, progressDialog, openAfterDownload)
            }
            .addOnFailureListener {
                progressDialog.dismiss()
                showError("Download failed")
                tempFile.delete()
            }
    }

    private fun processDownloadedFile(
        encryptedFile: File,
        fileModel: FileModel,
        progressDialog: ProgressDialog,
        openAfterDownload: Boolean
    ) {
        try {
            val userUid = firebaseAuth.currentUser?.uid ?: throw SecurityException("Not authenticated")
            val privateKey = RSAHelper.getPrivateKey(userUid)
                ?: return handleMissingKey(userUid).also { progressDialog.dismiss() }

            val cleanKey = fileModel.encryptedKey.replace("\n", "").trim()

            // Debug and validate AES key
            Log.d("DEBUG", "EncryptedKey length: ${cleanKey.length}")
            Log.d("DEBUG", "EncryptedKey (first 50 chars): ${cleanKey.take(50)}")

            val encryptedBytes = Base64.decode(cleanKey, Base64.NO_WRAP)
            Log.d("DEBUG", "Encrypted AES key byte size: ${encryptedBytes.size}")

            if (encryptedBytes.size != 256) {
                showError("Invalid or corrupted AES key. Contact sender.")
                return
            }

            val aesKey = RSAHelper.decryptAESKey(cleanKey, privateKey)
            val encryptedData = encryptedFile.readBytes()

            if (encryptedData.size <= EncryptionHelper.IV_LENGTH) {
                showError("Corrupted file")
                return
            }

            val decryptedData = EncryptionHelper.decryptData(encryptedData, aesKey)
            val mimeType = fileModel.mimeType.ifBlank { "*/*" }
            val uri = saveFileToDownloadsAndroid11Plus(fileModel.fileName, mimeType, decryptedData)

            if (openAfterDownload && uri != null) {
                if (mimeType == "application/pdf") {
                    val viewerIntent = Intent(this, PdfViewerActivity::class.java).apply {
                        putExtra("pdf_uri", uri.toString())
                    }
                    startActivity(viewerIntent)
                } else {
                    openUri(uri, mimeType)
                }
            } else {
                Toast.makeText(this, "File downloaded to Downloads folder", Toast.LENGTH_LONG).show()
            }

        } catch (e: Exception) {
            showError("File could not be processed: ${e.message}")
            Log.e("Download", "Error during decryption", e)
        } finally {
            encryptedFile.delete()
            progressDialog.dismiss()
        }
    }

    private fun saveFileToDownloadsAndroid11Plus(fileName: String, mimeType: String, data: ByteArray): Uri? {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { output: OutputStream ->
                    output.write(data)
                    output.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                return uri
            } catch (e: IOException) {
                Log.e("FileSave", "Failed to save file", e)
            }
        }
        return null
    }

    private fun openUri(uri: Uri, mimeType: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val resolved = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolved.isNotEmpty()) {
                startActivity(Intent.createChooser(intent, "Open with"))
            } else {
                showInstallAppDialog(mimeType)
            }
        } catch (e: Exception) {
            showError("Failed to open file: ${e.message}")
        }
    }

    private fun getPreferredAppPackage(mimeType: String): String? {
        return when (mimeType) {
            "application/pdf" -> "com.adobe.reader"
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "com.microsoft.office.word"
            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "com.microsoft.office.powerpoint"
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "com.microsoft.office.excel"
            else -> null
        }
    }

    private fun showInstallAppDialog(mimeType: String) {
        val appPackage = getPreferredAppPackage(mimeType) ?: return showError("No suggested app found for this file type")

        AlertDialog.Builder(this)
            .setTitle("No App Found")
            .setMessage("You need an app to open this file. Download from Play Store?")
            .setPositiveButton("Download") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackage")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackage")))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleMissingKey(userUid: String) {
        AlertDialog.Builder(this)
            .setTitle("Security Alert")
            .setMessage("Your decryption key is missing. Possible reasons:\n\n• App was reinstalled\n• Device security changed\n\nWe can attempt to recover your access.")
            .setPositiveButton("Recover") { _, _ -> attemptKeyRecovery(userUid) }
            .setNegativeButton("Support") { _, _ -> contactSupport(userUid) }
            .show()
    }

    private fun attemptKeyRecovery(userUid: String) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Recovering access...")
            setCancelable(false)
            show()
        }

        val success = RSAHelper.regenerateKeys(userUid)
        progressDialog.dismiss()

        if (success) {
            showToast("Recovery successful! Try again.")
        } else {
            AlertDialog.Builder(this)
                .setTitle("Recovery Failed")
                .setMessage("Couldn't recover automatically. Please contact support.")
                .setPositiveButton("Contact Support") { _, _ -> contactSupport(userUid) }
                .show()
        }
    }

    private fun contactSupport(userUid: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:support@company.com")
            putExtra(Intent.EXTRA_SUBJECT, "File Access Issue: $userUid")
            putExtra(Intent.EXTRA_TEXT, "I need help accessing my encrypted files.")
        }
        startActivity(intent)
    }

    private fun showError(message: String) {
        showToast(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}