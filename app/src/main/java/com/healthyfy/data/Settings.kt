package com.healthyfy.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

data class UserSettings(
    val calorieGoal: Int = 2000,
    val geminiKey: String = "",
)

class SettingsRepo(private val ctx: Context) {
    private val goal = intPreferencesKey("goal")
    private val key = stringPreferencesKey("gemini_key")

    val flow: Flow<UserSettings> = ctx.store.data.map {
        UserSettings(it[goal] ?: 2000, it[key] ?: "")
    }

    suspend fun save(s: UserSettings) {
        ctx.store.edit { it[goal] = s.calorieGoal; it[key] = s.geminiKey.trim() }
    }
}

/** Mifflin-St Jeor BMR x activity factor, adjusted for goal (-500 lose, +300 gain). */
object GoalCalc {
    fun dailyCalories(male: Boolean, age: Int, heightCm: Double, weightKg: Double, activity: Double, delta: Int): Int {
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + if (male) 5 else -161
        return (bmr * activity + delta).toInt().coerceAtLeast(1200)
    }
}
