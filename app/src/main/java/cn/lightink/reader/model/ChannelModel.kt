package cn.lightink.reader.model

import android.app.NotificationManager
import cn.lightink.reader.R

enum class Channel(val id: String, val title: Int, val description: Int, val importance: Int) {
    BOOKS("BOOKS", R.string.channel_books_title, R.string.channel_books_description, NotificationManager.IMPORTANCE_LOW),
    MEDIA("MEDIA", R.string.channel_media_title, R.string.channel_media_description, NotificationManager.IMPORTANCE_LOW)
}