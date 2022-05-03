package cn.lightink.reader.ui.book

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.fragment.app.FragmentActivity
import cn.lightink.reader.R
import kotlinx.android.synthetic.main.dialog_book_delete.*

class BookDeleteDialog(val activity: FragmentActivity, val callback: (Boolean) -> Unit) : Dialog(activity) {

    init {
        setContentView(R.layout.dialog_book_delete)
        mBookDeleteSubmit.setOnClickListener {
            callback.invoke(mBookDeleteCheck.isChecked)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(-1, -2)
        window?.setDimAmount(0.6F)
    }
}