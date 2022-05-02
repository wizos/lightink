package cn.lightink.reader.ui.booksource

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.webkit.URLUtil
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.BookSourceController
import cn.lightink.reader.ktx.change
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.module.TOAST_TYPE_SUCCESS
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_booksource_verify.*

class BookSourceVerifyActivity : LifecycleActivity() {

    private val clipboard by lazy { applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    private val controller by lazy { ViewModelProvider(this).get(BookSourceController::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_booksource_verify)
        mBookSourceVerifyTextField.change { text ->
            mBookSourceVerifyTextFieldLayout.error = null
            mBookSourceVerifyButton.isEnabled = URLUtil.isNetworkUrl(text)
        }
        mBookSourceVerifyButton.setOnClickListener { verify(mBookSourceVerifyTextField.text.toString()) }
    }

    private fun verify(url: String) {
        mBookSourceVerifyTextFieldLayout.isEnabled = false
        mBookSourceVerifyTextFieldLayout.error = null
        mBookSourceVerifyLoading.isVisible = true
        mBookSourceVerifyButton.isVisible = false
        controller.verifyRepository(url).observe(this, Observer { message ->
            mBookSourceVerifyTextFieldLayout.isEnabled = true
            mBookSourceVerifyLoading.isVisible = false
            mBookSourceVerifyButton.isVisible = true
            if (message.isNotBlank()) {
                mBookSourceVerifyTextFieldLayout.error = message
            } else {
                mBookSourceVerifyTextField.text?.clear()
                mBookSourceVerifyTextField.requestFocus()
                toast(R.string.booksource_verify_success, TOAST_TYPE_SUCCESS)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if(clipboard.hasPrimaryClip() && URLUtil.isNetworkUrl(clipboard.primaryClip?.getItemAt(0)?.text?.toString())) {
            mBookSourceVerifyTextField.setText(clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            mBookSourceVerifyTextField.setSelection(mBookSourceVerifyTextField.text?.length ?: 0)
        }
    }
}