package cn.lightink.reader.ui.reader

import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import cn.lightink.reader.R
import cn.lightink.reader.controller.ReaderSettingController
import cn.lightink.reader.ktx.notifyItemAllChanged
import cn.lightink.reader.model.Font
import cn.lightink.reader.model.SystemFont
import cn.lightink.reader.module.FontModule
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.RVLinearLayoutManager
import cn.lightink.reader.module.VH
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_reader_font.*
import kotlinx.android.synthetic.main.item_font.view.*

class ReaderFontActivity : LifecycleActivity() {

    private val controller by lazy { ViewModelProvider(this)[ReaderSettingController::class.java] }
    private val systemAdapter by lazy { buildSystemFontAdapter() }
    private val adapter by lazy { buildFontAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader_font)
        mSystemFontRecycler.layoutManager = RVLinearLayoutManager(this)
        mSystemFontRecycler.adapter = systemAdapter.apply { submitList(listOf(SystemFont.System)) }
        mReaderFontFolder.text = getString(R.string.reader_setting_font_fixed, getExternalFilesDir("fonts")?.absolutePath?.removePrefix(Environment.getExternalStorageDirectory().absolutePath))
        mFontRecycler.layoutManager = RVLinearLayoutManager(this)
        mFontRecycler.adapter = adapter
        controller.queryFont(getExternalFilesDir("fonts")).observe(this, Observer { list -> adapter.submitList(list) })
    }

    /**
     * 构建内置字体数据适配器
     */
    private fun buildSystemFontAdapter() = ListAdapter<SystemFont>(R.layout.item_font) { item, font ->
        item.view.mFontDisplay.text = font.display
        item.view.mFontDisplay.typeface = if (font.demo.isBlank()) Typeface.DEFAULT else Typeface.createFromAsset(assets, font.demo)
        item.view.mFontUsing.isVisible = font.display == FontModule.mCurrentFont.display
        item.view.mFontInstall.isVisible = !item.view.mFontUsing.isVisible
        if (FontModule.isInstalled(font)) {
            item.view.mFontInstall.setText(R.string.use)
            item.view.mFontInstall.setOnClickListener { useFont(font) }
        } else {
            item.view.mFontInstall.setText(R.string.download)
            item.view.mFontInstall.setOnClickListener { download(item, font) }
        }
    }

    /**
     * 使用字体
     */
    private fun useFont(font: Any) {
        controller.useFont(font)
        mFontRecycler.notifyItemAllChanged()
        mSystemFontRecycler.notifyItemAllChanged()
    }

    /**
     * 下载内置字体
     */
    private fun download(item: VH, font: SystemFont) {
        controller.downloadFont(font).observe(this, Observer { result ->
            item.view.mFontInstallLoading.isVisible = result
            item.view.mFontInstall.isVisible = !result
            if (!result) systemAdapter.notifyItemChanged(item.adapterPosition)
        })
    }

    /**
     * 构建外置字体数据适配器
     */
    private fun buildFontAdapter() = ListAdapter<Font>(R.layout.item_font) { item, font ->
        item.view.mFontDisplay.text = font.display
        item.view.mFontDisplay.typeface = font.typeface
        item.view.mFontUsing.isVisible = font == FontModule.mCurrentFont
        item.view.mFontInstall.isVisible = font != FontModule.mCurrentFont
        item.view.mFontInstall.setOnClickListener { useFont(font) }
    }
}