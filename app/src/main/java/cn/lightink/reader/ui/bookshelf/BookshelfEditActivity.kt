package cn.lightink.reader.ui.bookshelf

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.core.view.get
import cn.lightink.reader.R
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.Bookshelf
import cn.lightink.reader.module.*
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_bookshelf_edit.*

class BookshelfEditActivity : LifecycleActivity() {

    private var bookshelf: Bookshelf? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookshelf_edit)
        bookshelf = intent.getParcelableExtra(INTENT_BOOKSHELF)
        //名字
        mBookshelfNameInput.paint.isFakeBoldText = true
        if (bookshelf == null) {
            //新建书架时自动弹起软键盘
            mBookshelfNameInput.requestFocus()
            mBookshelfNameInput.postDelayed({
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(mBookshelfNameInput, InputMethodManager.SHOW_IMPLICIT)
            }, 200L)
        } else {
            //编辑书架
            mBookshelfNameInput.setText(bookshelf?.name)
            mBookshelfNameInput.setSelection(bookshelf?.name.orEmpty().length)
        }
        //首选
        mBookshelfPreferredGroup.check(mBookshelfPreferredGroup[if (bookshelf?.preferred == true) 1 else 0].id)
        //布局
        mBookshelfLayoutGroup.check(mBookshelfLayoutGroup[if (bookshelf?.layout == 1) 1 else 0].id)
        //信息
        mBookshelfInfoGroup.check(mBookshelfInfoGroup[if (bookshelf?.info == 1) 1 else 0].id)
        //排序
        mBookshelfSortGroup.check(mBookshelfSortGroup[if (bookshelf?.sort == 1) 1 else 0].id)
        //保存
        mBookshelfSave.setOnClickListener { onSave() }
    }

    /**
     * 保存
     */
    private fun onSave() {
        val name = mBookshelfNameInput.text.toString().trim()
        if (name.isBlank()) return toast("未设置书架名字")
        if (bookshelf?.name != name && Room.bookshelf().has(name)) return toast("已存在同名书架")
        if (bookshelf == null) bookshelf = Bookshelf(name = name)
        bookshelf!!.name = name
        bookshelf!!.preferred = mBookshelfPreferredGroup.indexOfChild(mBookshelfPreferredGroup.findViewById(mBookshelfPreferredGroup.checkedButtonId)) == 1
        bookshelf!!.layout = mBookshelfLayoutGroup.indexOfChild(mBookshelfLayoutGroup.findViewById(mBookshelfLayoutGroup.checkedButtonId))
        bookshelf!!.info = mBookshelfInfoGroup.indexOfChild(mBookshelfInfoGroup.findViewById(mBookshelfInfoGroup.checkedButtonId))
        bookshelf!!.sort = mBookshelfSortGroup.indexOfChild(mBookshelfSortGroup.findViewById(mBookshelfSortGroup.checkedButtonId))
        //覆盖已存在的首选书架
        Room.bookshelf().insert(bookshelf!!)
        if (Preferences.get(Preferences.Key.BOOKSHELF, 1L) == bookshelf?.id) {
            //修改的是当前正在使用的书架
            Notify.post(Notify.BookshelfChangedEvent())
        }
        toast("已保存", TOAST_TYPE_SUCCESS)
        onBackPressed()
    }
}