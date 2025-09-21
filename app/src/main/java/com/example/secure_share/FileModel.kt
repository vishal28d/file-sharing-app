package com.example.secure_share

data class FileModel(
    val id: String = "",
    val senderUid: String = "",
    val recipientUid: String = "",
    val fileName: String = "",
    val fileUrl: String = "",
    val encryptedKey: String = "",
    val timestamp: Long = 0,
    val mimeType: String = "*/*",
    var senderDisplayName: String = "" ,// for UI display only
    var recipientDisplayName: String = ""

)
