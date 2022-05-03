package cn.lightink.reader.ui.discover.setting

import android.content.Context
import android.os.Bundle
import cn.lightink.reader.R
import cn.lightink.reader.ktx.parentView
import cn.lightink.reader.ktx.total
import cn.lightink.reader.ui.base.LifecycleActivity
import kotlinx.android.synthetic.main.activity_memory.*

class MemoryActivity : LifecycleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_memory)
        //数据库
        mMemoryDatabaseData.text = format(getDatabasePath("canary").parentFile?.total())
        //动态链接库
        mMemoryLibsData.text = format(getDir("libs", Context.MODE_PRIVATE).total())
        //图书数据
        mMemoryBookData.text = format(getDir("book", Context.MODE_PRIVATE).total())
        //图片缓存
        mMemoryCacheData.text = format(cacheDir.total())
        mMemoryCacheData.parentView.setOnClickListener {
            cacheDir.listFiles()?.forEach { file -> file.deleteRecursively() }
            mMemoryCacheData.text = format(cacheDir.total())
        }
        //系统字体
        mMemorySystemFontData.text = format(getDir("font", Context.MODE_PRIVATE).total())
        mMemorySystemFontData.parentView.setOnClickListener {
            getDir("font", Context.MODE_PRIVATE).listFiles()?.forEach { file -> file.deleteRecursively() }
            mMemorySystemFontData.text = format(getDir("font", Context.MODE_PRIVATE).total())
        }
        //字体
        mMemoryFontData.text = format(getExternalFilesDir("fonts").total())
        //图书
        mMemoryFileData.text = format(getExternalFilesDir("books").total())
    }

    /**
     * 格式化文件体积
     */
    private fun format(total: Long?) = when {
        total == null -> "0B"
        //Byte
        total < 1000 -> "${total}B"
        //KB
        total < 1000 * 1024 -> "${String.format("%.1f", total / 1000F)}KB"
        //MB
        else -> "${String.format("%.1f", total / 1024000F)}MB"
    }

}