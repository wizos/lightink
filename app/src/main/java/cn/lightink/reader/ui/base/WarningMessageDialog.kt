package cn.lightink.reader.ui.base

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import cn.lightink.reader.R
import kotlinx.android.synthetic.main.dialog_warning_message.*

class WarningMessageDialog(context: Context, message: String) : Dialog(context) {

    init {
        setContentView(R.layout.dialog_warning_message)
        mWarningMessageContent.text = message
        mWarningMessageCancel.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(-1, -2)
        window?.setDimAmount(0.6F)
    }

}