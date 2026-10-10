package dz.scandz.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val pageCount: Int,
    val pdfPath: String
)

@Dao
interface DocDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocItem>>

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun search(q: String): Flow<List<DocItem>>

    @Insert
    suspend fun insert(doc: DocItem): Long

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [DocItem::class], version = 1, exportSchema = false)
abstract class AppDb : androidx.room.RoomDatabase() {
    abstract fun docs(): DocDao
}
