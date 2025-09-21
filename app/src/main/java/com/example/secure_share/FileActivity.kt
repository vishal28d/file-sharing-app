package com.example.secure_share

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FileActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var fileAdapter: FileAdapter2
    private val fileList = mutableListOf<FileModel>()
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file)

        listView = findViewById(R.id.listView)
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        fileAdapter = FileAdapter2(this, fileList)
        listView.adapter = fileAdapter

        loadSentFiles()
    }

    private fun loadSentFiles() {
        val currentUid = firebaseAuth.currentUser?.uid
        if (currentUid == null) {
            showToast("Not authenticated")
            return
        }

        progressDialog = ProgressDialog(this).apply {
            setMessage("Loading files...")
            setCancelable(false)
            show()
        }

        firestore.collection("file_shares")
            .whereEqualTo("senderUid", currentUid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                fileList.clear()

                for (doc in snapshot) {
                    val file = FileModel(
                        id = doc.id,
                        senderUid = doc.getString("senderUid") ?: "",
                        recipientUid = doc.getString("recipientUid") ?: "",
                        fileName = doc.getString("fileName") ?: "Unnamed",
                        fileUrl = doc.getString("fileUrl") ?: "",
                        encryptedKey = doc.getString("encryptedKey") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0,
                        mimeType = doc.getString("mimeType") ?: "*/*"
                    )

                    // Fetch both emails for display
                    fetchUserEmail(file.senderUid) { senderEmail ->
                        file.senderDisplayName = senderEmail
                        fileAdapter.notifyDataSetChanged()
                    }

                    fetchUserEmail(file.recipientUid) { recipientEmail ->
                        file.recipientDisplayName = recipientEmail
                        fileAdapter.notifyDataSetChanged()
                    }

                    fileList.add(file)
                }

                fileAdapter.notifyDataSetChanged()
                progressDialog.dismiss()
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Log.e("FileActivity", "Failed to load files: ${e.message}", e)
                showToast("Error loading files: ${e.message}")
            }
    }

    private fun fetchUserEmail(uid: String, callback: (String) -> Unit) {
        firestore.collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val email = doc.getString("email") ?: uid
                callback(email)
            }
            .addOnFailureListener {
                callback(uid)
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
