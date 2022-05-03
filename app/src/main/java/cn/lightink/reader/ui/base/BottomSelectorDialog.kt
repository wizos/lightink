package cn.lightink.reader.ui.base

import android.content.Context
import android.widget.TextView
import cn.lightink.reader.R
import cn.lightink.reader.ktx.setDrawableStart
import cn.lightink.reader.module.ListAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.android.synthetic.main.dialog_bottom_selector.*

class BottomSelectorDialog<T>(context: Context, val title: String, val items: List<T>, val display: (T) -> String) : BottomSheetDialog(context, R.style.AppTheme_BottomSheet) {

    private var callback: ((T) -> Unit)? = null

    init {
        setContentView(R.layout.dialog_bottom_selector)
        mBottomSelectorTitle.text = title
        mBottomSelectorRecycler.adapter = buildAdapter().apply { submitList(items.toList()) }
        mBottomSelectorCancel.setOnClickListener { dismiss() }
    }

    fun callback(callback: (T) -> Unit): BottomSelectorDialog<T> {
        this.callback = callback
        return this
    }

    private fun buildAdapter() = ListAdapter<T>(R.layout.item_bottom_selector) { item, entity ->
        (item.view as TextView).text = display.invoke(entity)
        item.view.setDrawableStart(getIconByEntity(entity))
        item.view.setOnClickListener {
            callback?.run { invoke(entity) }
            dismiss()
        }
    }

    private fun getIconByEntity(entity: T): Int {
        if (entity !is Int) return 0
        return when (entity) {
            R.string.sso_wechat -> R.drawable.ic_sso_wechat
            R.string.sso_weibo -> R.drawable.ic_sso_weibo
            R.string.sso_qq -> R.drawable.ic_sso_qq
            R.string.delete, R.string.uninstall -> R.drawable.ic_delete
            R.string.edit, R.string.feed_edit -> R.drawable.ic_edit
            R.string.login -> R.drawable.ic_login
            R.string.score -> R.drawable.ic_score
            R.string.purify_list -> R.drawable.ic_purify
            R.string.move -> R.drawable.ic_move
            R.string.unsubscribe_public_account -> R.drawable.ic_unsubscirbe
            R.string.download -> R.drawable.ic_download
            R.string.ticket_use_single -> R.drawable.ic_ticket_single
            R.string.ticket_use_all -> R.drawable.ic_ticket_all
            R.string.extract_reprint -> R.drawable.ic_forward
            R.string.extract_love, R.string.flow_collect -> R.drawable.ic_love
            R.string.extract_loved, R.string.flow_collected -> R.drawable.ic_loved
            R.string.report_error -> R.drawable.ic_option_image
            R.string.search -> R.drawable.ic_option_search
            R.string.feed_pushpin -> R.drawable.ic_pushpin
            R.string.feed_pushpin_cancel -> R.drawable.ic_pushpin_cancel

            R.string.menu_summary -> R.drawable.ic_menu_summary
            R.string.menu_move_bookshelf -> R.drawable.ic_menu_move
            R.string.menu_manage_book -> R.drawable.ic_menu_manage
            R.string.menu_edit_bookshelf -> R.drawable.ic_menu_edit
            R.string.menu_delete_book, R.string.menu_delete_bookshelf -> R.drawable.ic_menu_delete

            else -> 0
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setDimAmount(0.6F)
    }

}