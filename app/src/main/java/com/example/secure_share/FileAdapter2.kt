package com.example.secure_share

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class FileAdapter2(context: Context, private val files: List<FileModel>) :
    ArrayAdapter<FileModel>(context, 0, files) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)

        val file = files[position]

        val fileNameTextView = view.findViewById<TextView>(android.R.id.text1)
        val infoTextView = view.findViewById<TextView>(android.R.id.text2)

        fileNameTextView.text = file.fileName
        val fromText = file.senderDisplayName.ifBlank { file.senderUid }
        val toText = file.recipientDisplayName.ifBlank { file.recipientUid }

        infoTextView.text = "From: $fromText\nTo: $toText\n${dateFormat.format(Date(file.timestamp))}"


        return view
    }
}

