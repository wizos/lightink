package cn.lightink.reader.ui.base

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.fragment.app.FragmentActivity
import cn.lightink.reader.R
import kotlinx.android.synthetic.main.dialog_simple.*

class SimpleDialog(val activity: FragmentActivity, val content: String, val callback: (Boolean) -> Unit) : Dialog(activity) {

    init {
        setContentView(R.layout.dialog_simple)
        mSimpleContent.text = content
        mSimpleSubmit.setOnClickListener { callback.invoke(true).run { dismiss() } }
        mSimpleCancel.setOnClickListener { callback.invoke(false).run { dismiss() } }
        setOnCancelListener { callback.invoke(false) }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setLayout(-1, -2)
        window?.setDimAmount(0.6F)
    }
}