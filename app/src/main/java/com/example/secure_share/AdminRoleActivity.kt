package com.example.secure_share

import android.app.AlertDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

// Data class to hold user details
data class User(val id: String, val email: String, val roleType: String)

class AdminRoleActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var departmentHeadListView: ListView
    private lateinit var managerListView: ListView
    private lateinit var employeeListView: ListView

    private lateinit var departmentHeadAdapter: ArrayAdapter<String>
    private lateinit var managerAdapter: ArrayAdapter<String>
    private lateinit var employeeAdapter: ArrayAdapter<String>

    private val departmentHeads = mutableListOf<User>()
    private val managers = mutableListOf<User>()
    private val employees = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_role)

        db = FirebaseFirestore.getInstance()

        // Bind ListViews
        departmentHeadListView = findViewById(R.id.listViewDepartmentHeads)
        managerListView = findViewById(R.id.listViewManagers)
        employeeListView = findViewById(R.id.listViewEmployees)

        // Initialize adapters
        departmentHeadAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        managerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        employeeAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())

        departmentHeadListView.adapter = departmentHeadAdapter
        managerListView.adapter = managerAdapter
        employeeListView.adapter = employeeAdapter

        // Fetch and display users
        fetchUsers()

        // Click listeners
        setListClickListener(departmentHeadListView, departmentHeads)
        setListClickListener(managerListView, managers)
        setListClickListener(employeeListView, employees)
    }

    private fun fetchUsers() {
        db.collection("users").get().addOnSuccessListener { result ->
            departmentHeads.clear()
            managers.clear()
            employees.clear()

            for (document in result) {
                val email = document.getString("email") ?: continue
                val roleType = document.getString("roleType") ?: continue
                val userId = document.id
                val user = User(userId, email, roleType)

                when (roleType.lowercase()) {
                    "head" -> departmentHeads.add(user)
                    "manager" -> managers.add(user)
                    "employee" -> employees.add(user)
                }
            }

            refreshAdapters()
        }.addOnFailureListener {
            Toast.makeText(this, "Error loading users", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshAdapters() {
        departmentHeadAdapter.clear()
        departmentHeadAdapter.addAll(departmentHeads.map { it.email })

        managerAdapter.clear()
        managerAdapter.addAll(managers.map { it.email })

        employeeAdapter.clear()
        employeeAdapter.addAll(employees.map { it.email })

        departmentHeadAdapter.notifyDataSetChanged()
        managerAdapter.notifyDataSetChanged()
        employeeAdapter.notifyDataSetChanged()
    }

    private fun setListClickListener(listView: ListView, userList: MutableList<User>) {
        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedUser = userList[position]
            showRoleSelectionDialog(selectedUser)
        }
    }

    private fun showRoleSelectionDialog(user: User) {
        val roles = arrayOf("employee", "manager", "head") // Match current roleType values
        AlertDialog.Builder(this)
            .setTitle("Select New Role for ${user.email}")
            .setItems(roles) { _, which ->
                updateRole(user.id, roles[which])
            }
            .show()
    }

    private fun updateRole(userId: String, newRole: String) {
        db.collection("users").document(userId)
            .update("roleType", newRole)
            .addOnSuccessListener {
                Toast.makeText(this, "Role updated to $newRole", Toast.LENGTH_SHORT).show()
                fetchUsers()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error updating role", Toast.LENGTH_SHORT).show()
            }
    }
}
