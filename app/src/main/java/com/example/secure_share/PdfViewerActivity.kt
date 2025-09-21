package com.example.secure_share

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import java.io.IOException

class PdfViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewPager = ViewPager2(this)
        setContentView(viewPager)

        val uriString = intent.getStringExtra("pdf_uri") ?: return
        val uri = Uri.parse(uriString)

        try {
            fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
            pdfRenderer = PdfRenderer(fileDescriptor!!)

            val adapter = PdfPageAdapter(pdfRenderer!!)
            viewPager.adapter = adapter
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
        fileDescriptor?.close()
    }
}
