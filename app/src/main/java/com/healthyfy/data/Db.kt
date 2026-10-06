package com.healthyfy.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val meal: String,          // Breakfast / Lunch / Dinner / Snack
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val dayEpoch: Long,        // LocalDate.toEpochDay()
    val source: String,        // gemini / openfoodfacts / offline / manual
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entries WHERE dayEpoch = :day ORDER BY id DESC")
    fun forDay(day: Long): Flow<List<FoodEntry>>

    @Query("SELECT dayEpoch, SUM(calories) AS calories FROM food_entries WHERE dayEpoch >= :from GROUP BY dayEpoch ORDER BY dayEpoch")
    fun dailyTotals(from: Long): Flow<List<DayTotal>>

    @Insert suspend fun insert(e: FoodEntry)
    @Delete suspend fun delete(e: FoodEntry)
}

data class DayTotal(val dayEpoch: Long, val calories: Int)

@Database(entities = [FoodEntry::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun food(): FoodDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "healthyfy.db")
                .build().also { inst = it }
        }
    }
}
