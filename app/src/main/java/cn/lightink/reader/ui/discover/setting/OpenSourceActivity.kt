package cn.lightink.reader.ui.discover.setting

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import cn.lightink.reader.R
import cn.lightink.reader.ktx.toast
import cn.lightink.reader.model.OpenSource
import cn.lightink.reader.module.ListAdapter
import cn.lightink.reader.module.RVLinearLayoutManager
import cn.lightink.reader.ui.base.LifecycleActivity
import cn.lightink.reader.widget.VerticalDividerItemDecoration
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.android.synthetic.main.activity_open_source.*
import kotlinx.android.synthetic.main.item_open_source.view.*

class OpenSourceActivity : LifecycleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_source)
        mOpenSourceRecycler.layoutManager = RVLinearLayoutManager(this)
        mOpenSourceRecycler.addItemDecoration(VerticalDividerItemDecoration(this, R.dimen.margin))
        mOpenSourceRecycler.adapter = buildAdapter().apply {
            submitList(Gson().fromJson(String(assets.open("license").readBytes()), object : TypeToken<List<OpenSource>>() {}.type))
        }
    }

    private fun buildAdapter() = ListAdapter<OpenSource>(R.layout.item_open_source) { item, openSource ->
        item.view.mOpenSourceName.text = openSource.name
        item.view.mOpenSourceLicense.text = openSource.license
        item.view.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(openSource.link)))
            } catch (e: Exception) {
                toast("未安装浏览器")
            }
        }
    }

}