package com.healthyfy.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthyfy.ai.*
import com.healthyfy.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AddState(
    val loading: Boolean = false,
    val results: List<Nutrition> = emptyList(),
    val error: String? = null,
    val note: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).food()
    private val settingsRepo = SettingsRepo(app)
    private val offline = OfflineDb(app)

    val day = MutableStateFlow(LocalDate.now())
    val settings = settingsRepo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val entries = day.flatMapLatest { dao.forDay(it.toEpochDay()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _add = MutableStateFlow(AddState())
    val add: StateFlow<AddState> = _add

    fun shiftDay(d: Long) { day.value = day.value.plusDays(d) }

    /** Try Gemini first (text/photo); fall back to offline table, then Open Food Facts. */
    fun estimate(text: String, photo: Bitmap?) = viewModelScope.launch {
        _add.value = AddState(loading = true)
        val key = settings.value.geminiKey
        var err: String? = null
        var res: List<Nutrition> = emptyList()
        var note: String? = null
        if (key.isNotBlank()) {
            runCatching { GeminiEstimator(key).estimate(text.ifBlank { null }, photo) }
                .onSuccess { res = it }
                .onFailure { err = it.message }
        } else if (photo != null) {
            err = "Photo analysis needs a Gemini API key (free) in Settings."
        }
        if (res.isEmpty() && text.isNotBlank()) {
            res = offline.estimate(text)
            if (res.isNotEmpty()) note = "Estimated from the offline food table."
        }
        if (res.isEmpty() && text.isNotBlank()) {
            runCatching { OpenFoodFacts.search(text) }
                .onSuccess { res = it; note = "Packaged-food matches from Open Food Facts (per 100 g)." }
                .onFailure { err = err ?: it.message }
        }
        if (res.isEmpty() && err == null) err = "No match found. Enter values manually."
        _add.value = AddState(false, res, if (res.isEmpty()) err else null, note ?: err?.takeIf { res.isNotEmpty() })
    }

    fun clearAdd() { _add.value = AddState() }

    fun save(n: Nutrition, meal: String) = viewModelScope.launch {
        dao.insert(FoodEntry(name = n.name, meal = meal, calories = n.calories, proteinG = n.proteinG,
            carbsG = n.carbsG, fatG = n.fatG, dayEpoch = day.value.toEpochDay(), source = n.source))
    }

    fun delete(e: FoodEntry) = viewModelScope.launch { dao.delete(e) }
    fun saveSettings(s: UserSettings) = viewModelScope.launch { settingsRepo.save(s) }
}
