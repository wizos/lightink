package cn.lightink.reader.module

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import cn.lightink.reader.R
import cn.lightink.reader.model.Book
import cn.lightink.reader.model.Channel
import cn.lightink.reader.ui.book.BookSummaryActivity
import cn.lightink.reader.ui.discover.AirPlayActivity
import cn.lightink.reader.ui.reader.ListeningActivity

/**
 * @class 通知栏助手
 */
object NotificationHelper {

    const val MEDIA = 0x10
    const val AIR_PLAY = 0x20

    /**
     * 创建通知渠道 Android O
     */
    fun createNotificationChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context.applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //清理旧版通知渠道，迭代至1.2.4+时删除此行
            manager.deleteNotificationChannel("LIGHTINK")
            manager.createNotificationChannels(Channel.values().map { channel ->
                NotificationChannel(channel.id, context.getString(channel.title), channel.importance).apply { description = context.getString(channel.description) }
            })
        }
    }

    @Suppress("DEPRECATION")
    fun normal(context: Context, id: Int, title: String, content: String) {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, Channel.BOOKS.id) else Notification.Builder(context)
        notification.setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setAutoCancel(true)
        NotificationManagerCompat.from(context.applicationContext).notify(id, notification.build())
    }

    @Suppress("DEPRECATION")
    fun progress(context: Context, id: Int, book: Book?, title: String, content: String, progress: Int, max: Int) {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, Channel.BOOKS.id) else Notification.Builder(context)
        notification.setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setOnlyAlertOnce(true)
                .setProgress(max, progress, false)
                .setOngoing(max != progress)
                .setAutoCancel(max == progress)
        //存在图书时支持跳转至缓存管理页面
        if (book != null) {
            notification.setContentIntent(PendingIntent.getActivity(context, id, Intent(context, BookSummaryActivity::class.java).putExtra(INTENT_BOOK, book).putExtra(INTENT_BOOK_CACHE, true), PendingIntent.FLAG_IMMUTABLE))
        }
        NotificationManagerCompat.from(context.applicationContext).notify(id, notification.build())
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context.applicationContext).cancel(id)
    }

    /**
     * 显示TTS朗读通知
     */
    @Suppress("DEPRECATION")
    fun play(context: Context, bookId: String, title: String, content: String, cover: Bitmap?, isPlaying: Boolean): Notification {
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, Channel.MEDIA.id) else Notification.Builder(context)
        notificationBuilder.setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setLargeIcon(cover)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(R.drawable.ic_notification_timer, "定时", buildMediaIntent(context, bookId, 0x01)).build())
                .addAction(Notification.Action.Builder(R.drawable.ic_notification_stop, "停止", buildMediaIntent(context, bookId, 0x02)).build())
        if (isPlaying) {
            notificationBuilder.addAction(Notification.Action.Builder(R.drawable.ic_notification_pause, "暂停", buildMediaIntent(context, bookId, 0x03)).build())
        } else {
            notificationBuilder.addAction(Notification.Action.Builder(R.drawable.ic_notification_play, "播放", buildMediaIntent(context, bookId, 0x03)).build())
        }
        notificationBuilder.setStyle(Notification.MediaStyle().setShowActionsInCompactView(1))
                .setContentIntent(PendingIntent.getActivity(context, MEDIA, Intent(context, ListeningActivity::class.java).putExtra(INTENT_BOOK, bookId), PendingIntent.FLAG_IMMUTABLE))
        val notification = notificationBuilder.build()
        NotificationManagerCompat.from(context.applicationContext).notify(MEDIA, notification)
        return notification
    }

    /**
     * 多媒体Intent
     */
    private fun buildMediaIntent(context: Context, bookId: String, action: Int): PendingIntent? {
        return PendingIntent.getBroadcast(context, MEDIA + action, Intent(context, ListeningBroadcastReceiver::class.java).putExtra(INTENT_BOOK, bookId).putExtra(INTENT_ACTION, action), PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * 清理TTS朗读通知
     */
    fun stop(context: Context) {
        NotificationManagerCompat.from(context.applicationContext).cancel(MEDIA)
    }

    /**
     * 显示AirPlay通知
     */
    fun airPlay(context: Context): Notification {
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, Channel.MEDIA.id) else Notification.Builder(context)
        notificationBuilder.setSmallIcon(R.mipmap.ic_notification)
                .setContentTitle("投屏")
                .setContentText("服务正在运行中...")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(context, AIR_PLAY, Intent(context, AirPlayActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        return notificationBuilder.build()
    }
}