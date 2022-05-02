package cn.lightink.reader.ui.reader

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import cn.lightink.reader.R
import cn.lightink.reader.ktx.openFullscreen
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.SpeechCell
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.BottomSelectorDialog
import cn.lightink.reader.ui.base.LifecycleActivity
import com.gyf.immersionbar.ktx.navigationBarHeight
import kotlinx.android.synthetic.main.activity_listening.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ListeningActivity : LifecycleActivity() {

    private val theme by lazy { UIModule.getConfiguredTheme(this) }
    private val connection by lazy { buildServiceConnection() }
    private var binder: ListeningService.ListeningBinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(INTENT_BOOK) ?: return finish()
        setContentView(R.layout.activity_listening)
        openFullscreen()
        setupTheme()
        //启动Service
        ContextCompat.startForegroundService(this, Intent(this, ListeningService::class.java))
        //绑定Service
        bindService(Intent(this, ListeningService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun buildServiceConnection() = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) = Unit
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binder = service as? ListeningService.ListeningBinder
            binder?.setupWithBookId(intent.getStringExtra(INTENT_BOOK).orEmpty())?.run { setupView(this) }
            binder?.statusLiveData()?.observe(this@ListeningActivity, Observer { onStatus(it) })
            binder?.progressLiveData()?.observe(this@ListeningActivity, Observer { onProgress(it) })
            binder?.timerLiveData()?.observe(this@ListeningActivity, Observer { onTimerChanged(it) })
        }
    }

    /**
     * 设置视图
     */
    private fun setupView(book: Book) {
        mTopbar.parentView.setPadding(0, 0, 0, if (Preferences.get(Preferences.Key.HAS_NAVIGATION, false)) navigationBarHeight else 0)
        mBookCover.updateLayoutParams {
            width = resources.displayMetrics.widthPixels / 3
            height = (width * 1.4F).toInt()
        }
        mBookCover.hint(book.name).load(book.cover)
        mBookName.text = getString(R.string.book_name, book.name)
        mListeningEngine.setOnClickListener { startActivity(Intent("com.android.settings.TTS_SETTINGS").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        mListeningPlay.setOnClickListener { playOrPause() }
        mListeningTimer.setOnClickListener { showTimerDialog() }
    }

    /**
     * 播放
     */
    private fun playOrPause() {
        if (binder?.getEngine() == getString(R.string.tts_engine_none)) return
        binder?.switch()
    }

    private fun onProgress(cell: SpeechCell) {
        mBookChapter.text = cell.chapter.title.replaceFirst(Regex("""\s+"""), "\n")
        mBookParagraph.text = cell.value
        mBookParagraph.invalidate()
    }

    private fun onStatus(status: Int) = when (status) {
        STATUS_PREPARE -> mListeningEngine.text = binder?.getEngine()?.apply { binder?.play() }
        STATUS_ERROR -> mListeningEngine.text = "未设置文字转语音引擎"
        STATUS_PLAY -> mListeningPlay.setText(R.string.pause)
        STATUS_STOP -> mListeningPlay.setText(R.string.play)
        else -> Unit
    }

    /**
     * 定时器变化
     */
    @SuppressLint("SetTextI18n")
    private fun onTimerChanged(minutes: Int) {
        mListeningTimer.text = "${minutes}分钟"
    }

    /**
     * 设置主题颜色
     */
    private fun setupTheme() {
//        window.setBackgroundDrawable(if (theme.mipmap == null) ColorDrawable(theme.background) else theme.getDrawable(resources))
        mTopbar.setTint(theme.content)
        mBookName.typeface = FontModule.mCurrentFont.typeface
        mBookName.setTextColor(theme.content)
        mBookChapter.typeface = FontModule.mCurrentFont.typeface
        mBookChapter.setTextColor(theme.secondary)
        mBookParagraph.typeface = FontModule.mCurrentFont.typeface
        mBookParagraph.setTextColor(theme.content)
        mListeningEngine.backgroundTintList = ColorStateList.valueOf(theme.control)
        mListeningEngine.compoundDrawableTintList = ColorStateList.valueOf(theme.foreground)
        mListeningEngine.typeface = FontModule.mCurrentFont.typeface
        mListeningEngine.setTextColor(theme.foreground)
        mListeningTimer.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        mListeningTimer.typeface = FontModule.mCurrentFont.typeface
        mListeningTimer.setTextColor(theme.content)
        mListeningPlay.backgroundTintList = ColorStateList.valueOf(theme.foreground)
        mListeningPlay.typeface = FontModule.mCurrentFont.typeface
        mListeningPlay.setTextColor(theme.content)
    }

    /**
     * 定时
     */
    private fun showTimerDialog() {
        BottomSelectorDialog(this, getString(R.string.timer), listOf(60, 45, 30, 15)) { "${it}分钟" }.callback { minutes ->
            binder?.setTimer(minutes)
        }.show()
    }

    override fun onResume() {
        super.onResume()
        if (Preferences.get(Preferences.Key.SCREEN_BRIGHT, false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Notify.MediaActionEvent) {
        if (event.action == 2) {
            onBackPressed()
        }
    }
}