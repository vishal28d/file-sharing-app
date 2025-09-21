package com.example.secure_share

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PdfPageAdapter(private val renderer: PdfRenderer) :
    RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val imageView = ImageView(parent.context)
        imageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        imageView.adjustViewBounds = true
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        return PageViewHolder(imageView)
    }

    override fun getItemCount(): Int = renderer.pageCount

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val page = renderer.openPage(position)
        val bitmap = Bitmap.createBitmap(
            page.width, page.height, Bitmap.Config.ARGB_8888
        )
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        holder.imageView.setImageBitmap(bitmap)
        page.close()
    }

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}
