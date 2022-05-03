package cn.lightink.reader.module

import cn.lightink.reader.model.Book
import cn.lightink.reader.ui.book.BookSummaryInfoFragment
import cn.lightink.reader.ui.bookshelf.BookshelfFragment
import cn.lightink.reader.ui.main.MainActivity
import cn.lightink.reader.ui.reader.ListeningActivity
import cn.lightink.reader.ui.reader.ReaderActivity
import org.greenrobot.eventbus.EventBus

/**
 * 事件通知
 */
object Notify {

    fun register(subscriber: Any) {
        if (hasNotifyEvent(subscriber) && !EventBus.getDefault().isRegistered(subscriber)) {
            EventBus.getDefault().register(subscriber)
        }
    }

    fun unregister(subscriber: Any) {
        if (hasNotifyEvent(subscriber) && EventBus.getDefault().isRegistered(subscriber)) {
            EventBus.getDefault().unregister(subscriber)
        }
    }

    private fun hasNotifyEvent(subscriber: Any?): Boolean {
        return when (subscriber) {
            is MainActivity, is ReaderActivity, is ListeningActivity,
            is BookshelfFragment, is BookSummaryInfoFragment -> true
            is ListeningService -> true
            else -> false
        }
    }

    /**
     * 全局事件推送
     */
    fun post(vararg events: Event) {
        events.forEach { EventBus.getDefault().post(it) }
    }

    //阅读点击事件
    class ReaderViewClickedEvent : Event

    //阅读长按点击事件
    data class ReaderViewLongClickedEvent(val isLongClicked: Boolean) : Event

    //阅读净化事件
    data class ReaderViewPurifyEvent(val content: String) : Event

    //阅读书签事件
    class ReaderViewBookmarkEvent : Event

    //图书封面切换
    data class BookCoverChangedEvent(val book: Book) : Event

    //书架修改事件
    class BookshelfChangedEvent : Event

    //字体设置变化
    class FontChangedEvent : Event

    //多媒体播放事件
    data class MediaActionEvent(val action: Int?) : Event

    //重启
    class RestartEvent : Event

    interface Event

}