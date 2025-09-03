package com.natinf.searchpro.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Entity(tableName = "infractions")
data class Infraction(
    @PrimaryKey val natinf: Int,
    val qualification: String,
    val nature: String,
    val articlesDef: String,
    val articlesPeine: String,
    val normalized: String
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val natinf: Int
)

@Dao
interface InfractionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<Infraction>)

    @Query("SELECT COUNT(*) FROM infractions")
    suspend fun count(): Int

    @Query("SELECT * FROM infractions WHERE natinf = :n")
    suspend fun getById(n: Int): Infraction?

    @Query("""
        SELECT * FROM infractions
        WHERE (:nature IS NULL OR nature = :nature)
        AND (
            :q = '' 
            OR natinf = CAST(:qNum AS INT)
            OR normalized LIKE '%' || :q || '%'
            OR normalized LIKE '%' || :qAlt || '%'
        )
        LIMIT 500
    """)
    suspend fun search(q: String, qAlt: String, qNum: String?, nature: String?): List<Infraction>

    // Favorites
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFav(f: Favorite)

    @Delete
    suspend fun removeFav(f: Favorite)

    @Query("SELECT * FROM favorites")
    suspend fun allFavs(): List<Favorite>

    @Query("SELECT 1 FROM favorites WHERE natinf = :n LIMIT 1")
    suspend fun isFav(n: Int): Int?
}

@Database(entities = [Infraction::class, Favorite::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): InfractionDao
    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDb::class.java, "natinf.db").build().also { instance = it }
            }
    }
}

object AppCtx {
    lateinit var appContext: Context
}
