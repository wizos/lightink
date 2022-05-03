package cn.lightink.reader.ui.booksource

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import cn.lightink.reader.R
import cn.lightink.reader.model.BookSource
import kotlinx.android.synthetic.main.dialog_booksource_score.*

class BookSourceScoreDialog(context: Context, val bookSource: BookSource) : Dialog(context) {

    private var callback: ((Float) -> Unit)? = null

    init {
        setContentView(R.layout.dialog_booksource_score)
        mBookSourceScoreRating.setOnRatingBarChangeListener { _, rating, _ -> mBookSourceScoreSubmit.isEnabled = rating > 0F }
        mBookSourceScoreSubmit.setOnClickListener {
            callback?.invoke(mBookSourceScoreRating.rating)
            dismiss()
        }
    }

    fun callback(callback: (Float) -> Unit): BookSourceScoreDialog {
        this.callback = callback
        return this
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(-1, -2)
        window?.setDimAmount(0.6F)
    }

}