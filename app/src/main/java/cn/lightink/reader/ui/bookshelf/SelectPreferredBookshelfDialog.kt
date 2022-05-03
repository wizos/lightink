package cn.lightink.reader.ui.bookshelf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.BookController
import cn.lightink.reader.model.Bookshelf
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.RVLinearLayoutManager
import cn.lightink.reader.module.Room
import kotlinx.android.synthetic.main.dialog_select_preferred_bookshelf.*

class SelectPreferredBookshelfDialog : DialogFragment() {

    private val controller by lazy { ViewModelProvider(activity!!)[BookController::class.java] }
    private val adapter by lazy { buildAdapter() }
    private var callback: ((Bookshelf) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_select_preferred_bookshelf, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val current = arguments?.getLong(INTENT_CURRENT, -1L) ?: -1L
        mBookshelfPreferred.isVisible = current < 0 && Room.bookshelf().hasPreferred() < 1
        mBookshelfRecycler.layoutManager = RVLinearLayoutManager(activity)
        mBookshelfRecycler.adapter = adapter
        controller.queryBookshelves().observe(viewLifecycleOwner, Observer { list -> adapter.submitList(list.filter { it.id != current }) })
    }

    fun show(manager: FragmentManager) {
        show(manager, null)
    }

    fun callback(callback: (Bookshelf) -> Unit): SelectPreferredBookshelfDialog {
        this.callback = callback
        return this
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setDimAmount(0.2F)
        dialog?.window?.setLayout(resources.displayMetrics.widthPixels - resources.getDimensionPixelSize(R.dimen.dimen2v) * 2, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun checked(bookshelf: Bookshelf) {
        if (mBookshelfPreferred.isChecked) {
            bookshelf.preferred = true
            Room.bookshelf().update(bookshelf)
        }
        callback?.invoke(bookshelf).run { dismissAllowingStateLoss() }
    }

    private fun buildAdapter() = ListAdapter<Bookshelf>(R.layout.item_popup) { item, bookshelf ->
        (item.view as TextView).text = bookshelf.name
        item.view.setOnClickListener { checked(bookshelf) }
    }

    companion object {
        private const val INTENT_CURRENT = "current"
        fun newInstance(current: Long = -1L) = SelectPreferredBookshelfDialog().apply {
            arguments = Bundle().apply { putLong(INTENT_CURRENT, current) }
        }
    }
}