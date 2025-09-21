package com.example.secure_share

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class AdminFileActivity : AppCompatActivity() {

    private lateinit var spinnerDepartment: Spinner
    private lateinit var spinnerRole: Spinner
    private lateinit var listView: ListView
    private lateinit var progressDialog: ProgressDialog

    private val departments = listOf("Finance", "IT", "HR")
    private val roles = listOf("All", "employee", "manager", "head", "admin")

    private val fileList = mutableListOf<FileModel>()
    private lateinit var fileAdapter: FileAdapter2
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_file)

        spinnerDepartment = findViewById(R.id.spinner_department)
        spinnerRole = findViewById(R.id.spinner_role)
        listView = findViewById(R.id.fileListView)

        fileAdapter = FileAdapter2(this, fileList)
        listView.adapter = fileAdapter

        firestore = FirebaseFirestore.getInstance()
        setupSpinners()
    }

    private fun setupSpinners() {
        spinnerDepartment.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, departments)
        spinnerRole.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)

        val filterAction = {
            val dept = spinnerDepartment.selectedItem.toString()
            val role = spinnerRole.selectedItem.toString()
            fetchFilteredFileTransfers(dept, role)
        }

        spinnerDepartment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) = filterAction()
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        spinnerRole.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) = filterAction()
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun fetchFilteredFileTransfers(department: String, role: String) {
        fileList.clear()
        showProgress("Fetching records...")

        firestore.collection("users").get().addOnSuccessListener { userSnapshot ->
            val uidMap = mutableMapOf<String, String>()
            userSnapshot.forEach { doc ->
                uidMap[doc.id] = doc.getString("email") ?: doc.id
            }

            var query = firestore.collection("file_shares")
                .whereEqualTo("senderDepartment", department)

            if (role != "All") {
                query = query.whereEqualTo("senderRole", role)
            }

            query.limit(100).get().addOnSuccessListener { fileDocs ->
                fileList.clear()
                for (doc in fileDocs) {
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
                    file.senderDisplayName = uidMap[file.senderUid] ?: file.senderUid
                    file.recipientDisplayName = uidMap[file.recipientUid] ?: file.recipientUid
                    fileList.add(file)
                }
                fileAdapter.notifyDataSetChanged()
                dismissProgress() // ✅ Only here
            }.addOnFailureListener {
                dismissProgress()
                showToast("Failed to load file data: ${it.message}")
            }

        }.addOnFailureListener {
            dismissProgress()
            showToast("Failed to load user data: ${it.message}")
        }

    }

    private fun showProgress(msg: String) {
        progressDialog = ProgressDialog(this).apply {
            setMessage(msg)
            setCancelable(false)
            show()
        }
    }

    private fun dismissProgress() {
        if (::progressDialog.isInitialized && progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
