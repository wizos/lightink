package cn.lightink.reader.ui.booksource

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import cn.lightink.reader.R
import cn.lightink.reader.ktx.openFullscreen
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.module.INTENT_BOOK_SOURCE
import cn.lightink.reader.module.Room
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_book_source_auth.*

class BookSourceAuthActivity : LifecycleActivity() {

    private val bookSource by lazy { Room.bookSource().get(intent.getStringExtra(INTENT_BOOK_SOURCE).orEmpty()) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openFullscreen()
        setContentView(R.layout.activity_book_source_auth)
        mAuthWebView.settings.javaScriptEnabled = true
        mAuthWebView.settings.useWideViewPort = true
        mAuthWebView.settings.loadWithOverviewMode = true
        mAuthWebView.settings.domStorageEnabled = true
        mAuthWebView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        mAuthWebView.webViewClient = buildClient()
        mAuthWebView.webChromeClient = object : WebChromeClient() {}
        mAuthWebView.loadUrl(bookSource?.json?.auth?.login!!)
    }

    private fun buildClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            if (!URLUtil.isNetworkUrl(request?.url?.toString())) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request?.url?.toString())))
                } catch (e: ActivityNotFoundException) {
                    //未安装网站指定的应用
                    toast(R.string.booksource_auth_not_install)
                }
                return true
            }
            return super.shouldOverrideUrlLoading(view, request)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {

        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        mAuthWebView.loadUrl(intent?.dataString!!)
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK, Intent().putExtra(INTENT_BOOK_SOURCE, bookSource?.url))
        finish()
    }
}