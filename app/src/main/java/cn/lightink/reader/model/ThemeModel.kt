package cn.lightink.reader.model

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.room.*
import cn.lightink.reader.module.EMPTY
import cn.lightink.reader.R


/********************************************************************************************************************************
 * 存储类
 ********************************************************************************************************************************
 * 主题
 * @property name           名字
 * @property dark           深色模式
 * @property background     背景色
 * @property foreground     前景色
 * @property control        着重色
 * @property content        主要内容
 * @property secondary      次要内容
 * @property horizontal     横向边距
 * @property top            顶部边距
 * @property bottom         底部边距
 * @property author         设计师
 * @property mipmap         背景图地址
 * @property owner          个人作品
 * @property time           使用时长
 */
@Entity(primaryKeys = ["id"])
data class Theme(var id: Long, var name: String, var dark: Boolean, var background: Int, var foreground: Int, var control: Int, var content: Int, var secondary: Int, var horizontal: Int, var top: Int, var bottom: Int, var author: String, var mipmap: String = EMPTY, var owner: Boolean = false, var time: Int = 0) {

    fun getColorByName(@StringRes nameResId: Int) = when (nameResId) {
        R.string.theme_background -> background
        R.string.theme_foreground -> foreground
        R.string.theme_control -> control
        R.string.theme_content -> content
        R.string.theme_secondary -> secondary
        else -> background
    }

    fun getValueByName(@StringRes nameResId: Int) = when (nameResId) {
        R.string.theme_horizontal -> horizontal
        R.string.theme_top -> top
        R.string.theme_bottom -> bottom
        else -> 0
    }

    fun setValueByName(@StringRes nameResId: Int, value: Int) = when (nameResId) {
        R.string.theme_background -> background = value
        R.string.theme_foreground -> foreground = value
        R.string.theme_control -> control = value
        R.string.theme_content -> content = value
        R.string.theme_secondary -> secondary = value
        R.string.theme_horizontal -> horizontal = value
        R.string.theme_top -> top = value
        R.string.theme_bottom -> bottom = value
        else -> Unit
    }

    companion object {
        fun custom(isNight: Boolean) = if (isNight) customNight() else customLight()
        private fun customLight() = Theme(System.currentTimeMillis(), EMPTY, false, 0xFFF1F3F4.toInt(), 0xFFFFFFFF.toInt(), 0xFF3473E7.toInt(), 0xFF2D2D33.toInt(), 0xFFABABAD.toInt(), 24, 24, 24, EMPTY, owner = true)
        private fun customNight() = Theme(System.currentTimeMillis(), EMPTY, true, 0xFF000000.toInt(), 0xFF111418.toInt(), 0xFF3876C9.toInt(), 0xFF878787.toInt(), 0xFF272727.toInt(), 24, 24, 24, EMPTY, owner = true)
    }
}

/**
 * 默认主题
 */
val THEME_LIGHT = Theme(1580000000000, "亮色主题", false, 0xFFF1F3F4.toInt(), 0xFFFFFFFF.toInt(), 0xFF3473E7.toInt(), 0xFF2D2D33.toInt(), 0xFFABABAD.toInt(), 24, 24, 24, "轻墨")
val THEME_NIGHT = Theme(1580000000001, "暗色主题", true, 0xFF000000.toInt(), 0xFF111418.toInt(), 0xFF3876C9.toInt(), 0xFF878787.toInt(), 0xFF272727.toInt(), 24, 24, 24, "轻墨")
val THEME_SHEEP = Theme(1580000000002, "少女和羊", false, 0xFFFFFEF0.toInt(), 0xFFFFFEF0.toInt(), 0xFF13372E.toInt(), 0xFF43675E.toInt(), 0xFFB4C2BF.toInt(), 24, 24, 24, "轻墨")
val THEME_STORY = Theme(1580000000003, "和风物语", false, 0xFFF4E9DE.toInt(), 0xFFFFF3E8.toInt(), 0xFFE77E3F.toInt(), 0xFF321309.toInt(), 0xFFB9B1A8.toInt(), 24, 24, 24, "轻墨")

@Dao
interface ThemeDao {

    @Query("SELECT * FROM theme WHERE dark = :dark ORDER BY time DESC")
    fun getAll(dark: Int): LiveData<List<Theme>>

    @Query("SELECT * FROM theme WHERE id = :id LIMIT 1")
    fun get(id: Long): Theme?

    @Query("SELECT COUNT(id) FROM theme LIMIT 1")
    fun isNotEmpty(): Boolean

    @Query("SELECT COUNT(id) FROM theme WHERE name =:name LIMIT 1")
    fun isExistName(name: String): Boolean

    @Query("SELECT * FROM theme WHERE owner = 1 ORDER BY time DESC")
    fun getOwnerAll(): LiveData<List<Theme>>

//    @Query("SELECT * FROM theme WHERE owner = 1 AND time > 43200")
//    fun getUploadAll(): List<Theme>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg themes: Theme)

    @Update
    fun update(theme: Theme)

    @Delete
    fun remove(theme: Theme)
}
