package cn.lightink.reader.module

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import cn.lightink.reader.model.SearchBook

object SearchObserver {

    private val liveData = MutableLiveData<List<SearchBook>>()

    fun observer(owner: LifecycleOwner, callback: (List<SearchBook>) -> Unit) {
        liveData.observe(owner, Observer { callback.invoke(it) })
    }

    fun postValue(value: List<SearchBook>) {
        liveData.postValue(value)
    }

    fun findValue(publishId: String?) = liveData.value?.firstOrNull { it.objectId() == publishId }

}