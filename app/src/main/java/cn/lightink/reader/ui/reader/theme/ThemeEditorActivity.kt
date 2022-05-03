package cn.lightink.reader.ui.reader.theme

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.palette.graphics.Palette
import cn.lightink.reader.MIPMAP_PATH
import cn.lightink.reader.R
import cn.lightink.reader.controller.ThemeController
import cn.lightink.reader.ktx.change
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.ktx.px
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.LifecycleActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ktx.immersionBar
import com.gyf.immersionbar.ktx.navigationBarHeight
import kotlinx.android.synthetic.main.activity_theme_editor.*
import kotlinx.android.synthetic.main.item_theme_action.view.*
import java.io.File

class ThemeEditorActivity : LifecycleActivity() {

    private val inputMethodManager by lazy { applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager  }
    private val controller by lazy { ViewModelProvider(this)[ThemeController::class.java] }
    private val actions = listOf(R.string.theme_background, R.string.theme_foreground, R.string.theme_content, R.string.theme_secondary, R.string.theme_control, R.string.theme_horizontal, R.string.theme_top, R.string.theme_bottom)
    private val adapter = ListAdapter<Int>(R.layout.item_theme_action) { item, title -> onBindView(item, title) }
    private val behavior by lazy { BottomSheetBehavior.from(mThemeMenuLayout) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_theme_editor)
        controller.setupTheme(intent.getLongExtra(INTENT_THEME, -1), UIModule.isNightMode(this))
        mThemeMenuRecycler.layoutManager = RVGridLayoutManager(this, 4)
        mThemeMenuRecycler.adapter = adapter.apply { submitList(actions) }
        mThemeEditorLayout.setPadding(px(controller.theme.horizontal), px(controller.theme.top), px(controller.theme.horizontal), px(controller.theme.bottom))
        mThemeEditorContent.text = getString(R.string.theme_editor_content)
        mThemeEditorContent.textSize = px(17).toFloat()
        mThemeEditorContent.lineSpacing = 1.3F
        mThemeMenuLayout.parentView.setOnClickListener { showOrHideMenu() }
        mThemeEditorPicker.setOnClickListener { pickPicture() }
        mThemeMenuLayout.post { mThemeMenuLayout.setPadding(0, 0, 0, getRealNavigationBarHeight()) }
        mThemeEditorSubmit.setOnClickListener { submit() }
        //主题名
        mThemeEditorNameInput.change { text -> controller.theme.name = text.trim() }
        mThemeEditorNameInput.setText(controller.theme.name)
        mThemeEditorNameInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) v.clearFocus()
            return@setOnEditorActionListener false
        }
        //更新主题配色
        updateViewTheme()
        immersionBar {
            hideBar(BarHide.FLAG_HIDE_BAR)
            statusBarDarkFont(true)
            navigationBarDarkIcon(true)
        }
        behavior.state = STATE_HIDDEN
        showOrHideMenu()
    }

    private fun updateViewTheme() {
        if (controller.theme.mipmap.isBlank()) {
            mThemeEditorLayout.setBackgroundColor(controller.theme.background)
        } else {
            mThemeEditorLayout.background = UIModule.getMipmapByTheme(controller.theme)
        }
        mThemeEditorPicker.setTextColor(controller.theme.content)
        mThemeEditorPicker.compoundDrawableTintList = ColorStateList.valueOf(controller.theme.content)
        mThemeMenuLayout.backgroundTintList = ColorStateList.valueOf(controller.theme.foreground)
        mThemeEditorSubmit.setTextColor(ColorStateList(arrayOf(arrayOf(android.R.attr.state_enabled).toIntArray(), IntArray(0)), arrayOf(controller.theme.control, controller.theme.secondary).toIntArray()))
        mThemeEditorTitle.setTextColor(controller.theme.content)
        mThemeEditorChapter.setTextColor(controller.theme.secondary)
        mThemeEditorChapterTime.setTextColor(controller.theme.secondary)
        mThemeEditorChapterSchedule.setTextColor(controller.theme.secondary)
        mThemeEditorContent.textColor = controller.theme.content
        mThemeEditorContent.invalidate()
        mThemeEditorNameInput.setTextColor(controller.theme.content)
        mThemeEditorNameInput.setHintTextColor(controller.theme.secondary)
        mThemeMenuTopLine.setBackgroundColor(controller.theme.content)
        mThemeMenuBottomLine.setBackgroundColor(controller.theme.content)
        immersionBar { navigationBarColorInt(controller.theme.foreground) }
        adapter.notifyItemRangeChanged(0, actions.size)
    }

    private fun showOrHideMenu() {
        if (behavior.state == STATE_HIDDEN) {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            immersionBar {
                hideBar(BarHide.FLAG_SHOW_BAR)
                transparentNavigationBar()
                navigationBarColorInt(controller.theme.foreground)
            }
        } else {
            currentFocus?.run { inputMethodManager.hideSoftInputFromWindow(windowToken, 0) }
            behavior.state = STATE_HIDDEN
            immersionBar { hideBar(BarHide.FLAG_HIDE_BAR) }
        }
    }

    /**
     * 保存主题
     */
    private fun submit() {
        if (mThemeEditorNameInput.text.isNullOrBlank()) return toast("未填写主题名")
        val result = controller.saveTheme()
        if (result.isNotBlank()) {
            toast(result)
        } else {
            onBackPressed()
        }
    }

    private fun onBindView(item: VH, titleResId: Int) {
        item.view.mThemeActionValue.parentView.backgroundTintList = ColorStateList.valueOf(controller.theme.background)
        item.view.mThemeActionValue.backgroundTintList = ColorStateList.valueOf(controller.theme.getColorByName(titleResId))
        item.view.mThemeActionValue.text = controller.theme.getValueByName(titleResId).let { if (it > 0) it.toString() else EMPTY }
        item.view.mThemeActionValue.setTextColor(controller.theme.content)
        item.view.mThemeActionTitle.setText(titleResId)
        item.view.mThemeActionTitle.setTextColor(controller.theme.content)
        item.view.mThemeActionValue.parentView.setOnClickListener { if (controller.theme.getValueByName(titleResId) == 0) showColorPicker(titleResId) else showDistanceSeek(titleResId) }
    }

    private fun showDistanceSeek(titleResId: Int) {
        showOrHideMenu()
        ThemeDistancePopup(this, controller.theme.getValueByName(titleResId)) { value ->
            controller.theme.setValueByName(titleResId, value)
            mThemeEditorLayout.setPadding(px(controller.theme.horizontal), px(controller.theme.top), px(controller.theme.horizontal), px(controller.theme.bottom))
            adapter.notifyItemRangeChanged(0, actions.size)
        }.apply {
            setOnDismissListener { showOrHideMenu() }
        }.showAtLocation(window.decorView, Gravity.BOTTOM, 0, 0)
    }

    private fun showColorPicker(titleResId: Int) {
        showOrHideMenu()
        ThemeColorPickerPopup(this, controller.theme.getColorByName(titleResId)) { color ->
            controller.theme.setValueByName(titleResId, color)
            updateViewTheme()
        }.apply {
            setOnDismissListener { showOrHideMenu() }
        }.showAtLocation(window.decorView, Gravity.BOTTOM, 0, 0)
    }

    /**
     * 选择图片
     */
    private fun pickPicture() {
        try {
            startActivityForResult(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI), Activity.RESULT_FIRST_USER)
        } catch (e: Exception) {
            //抛出异常
        }
    }

    private fun getRealNavigationBarHeight() = if (Preferences.get(Preferences.Key.HAS_NAVIGATION, false)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.rootWindowInsets.systemWindowInsetBottom
        } else {
            navigationBarHeight
        }
    } else 0

    /**
     * 处理图片
     */
    private fun onPickPicture(uri: Uri) {
        val document = DocumentFile.fromSingleUri(this, uri) ?: return
        val bytes = contentResolver.openInputStream(document.uri).use { it?.readBytes() } ?: return
        val cache = File(MIPMAP_PATH, "mipmap").apply { writeBytes(bytes) }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        Palette.from(bitmap).generate { palette ->
            palette?.run {
                controller.updateThemeByPalette(cache, this)
                updateViewTheme()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Activity.RESULT_FIRST_USER && resultCode == Activity.RESULT_OK && data?.data != null) {
            onPickPicture(data.data!!)
        }
    }

    override fun onBackPressed() {
        if (controller.theme.id == UIModule.getConfiguredTheme(this).id) {
            //修改正在使用的主题
            setResult(READER_RESULT_RESTART)
        }
        super.onBackPressed()
    }
}