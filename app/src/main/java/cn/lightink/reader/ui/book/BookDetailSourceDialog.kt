package cn.lightink.reader.ui.book

import android.content.Context
import cn.lightink.reader.R
import cn.lightink.reader.ktx.setDrawableStart
import cn.lightink.reader.model.SearchResult
import cn.lightink.reader.module.ListAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.dialog_book_detail_source.*
import kotlinx.android.synthetic.main.item_book_detail_source.view.*

class BookDetailSourceDialog(context: Context, list: List<SearchResult>, val callback: (Int) -> Unit) : BottomSheetDialog(context, R.style.AppTheme_BottomSheet) {

    init {
        setContentView(R.layout.dialog_book_detail_source)
        mRecycler.adapter = buildAdapter().apply { submitList(list) }
        mCancel.setOnClickListener { dismiss() }
    }

    private fun buildAdapter() = ListAdapter<SearchResult>(R.layout.item_book_detail_source) { item, result ->
        item.view.mBookDetailSourceName.text = result.source.name
        item.view.mBookDetailSourceUrl.text = when {
            result.metadata.detail.contains("@post->") -> result.metadata.detail.substring(0, result.metadata.detail.indexOf("@post->"))
            result.metadata.detail.contains("@header->") -> result.metadata.detail.substring(0, result.metadata.detail.indexOf("@header->"))
            else -> result.metadata.detail
        }
        item.view.mBookDetailSourceName.setDrawableStart(when {
            result.speed < 750 -> R.drawable.ic_response_speed_3
            result.speed < 1500 -> R.drawable.ic_response_speed_2
            else -> R.drawable.ic_response_speed_1
        })
        item.view.setOnClickListener { callback.invoke(item.adapterPosition).run { dismiss() } }
    }

    override fun onStart() {
        super.onStart()
        window?.setDimAmount(0.6F)
    }

}