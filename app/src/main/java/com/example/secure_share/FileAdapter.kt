package com.example.secure_share

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter(context: Context, private val files: List<FileModel>) :
    ArrayAdapter<FileModel>(context, 0, files) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        val file = files[position]

        val fileNameText = file.fileName
        val senderDisplay = file.senderDisplayName.ifBlank { file.senderUid }

        val infoText = "From: $senderDisplay\n${dateFormat.format(Date(file.timestamp))}"

        view.findViewById<TextView>(android.R.id.text1).text = fileNameText
        view.findViewById<TextView>(android.R.id.text2).text = infoText

        return view
    }
}
