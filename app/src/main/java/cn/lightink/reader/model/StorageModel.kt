package cn.lightink.reader.model

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.room.*
import cn.lightink.reader.module.EMPTY

/********************************************************************************************************************************
 * 存储类  授权文件夹
 *******************************************************************************************************************************/
@Entity
data class Folder(@PrimaryKey val uri: String) {

    @Ignore
    val name = Uri.parse(uri).path?.replace("tree/primary:", EMPTY).orEmpty()
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folder")
    fun getAll(): LiveData<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(folder: Folder)

    @Delete
    fun delete(folder: Folder)

}

