package com.healthyfy.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.healthyfy.ai.Nutrition
import com.healthyfy.data.UserSettings
import java.time.LocalDate

private val meals = listOf("Breakfast", "Lunch", "Dinner", "Snack")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, onAdd: () -> Unit, onSettings: () -> Unit) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val eaten = entries.sumOf { it.calories }
    val goal = settings.calorieGoal

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ vm.shiftDay(-1) }) { Icon(Icons.Default.ChevronLeft, "Previous day") }
                        Text(if (day == LocalDate.now()) "Today" else day.toString())
                        IconButton({ vm.shiftDay(1) }, enabled = day.isBefore(LocalDate.now())) { Icon(Icons.Default.ChevronRight, "Next day") }
                    }
                },
                actions = { IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add food") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CalorieRing(eaten, goal)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Macro("Protein", entries.sumOf { it.proteinG })
                        Macro("Carbs", entries.sumOf { it.carbsG })
                        Macro("Fat", entries.sumOf { it.fatG })
                    }
                }
            }
            meals.forEach { meal ->
                val list = entries.filter { it.meal == meal }
                if (list.isNotEmpty()) {
                    item { Text("$meal · ${list.sumOf { it.calories }} kcal", style = MaterialTheme.typography.titleSmall) }
                    items(list, key = { it.id }) { e ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name)
                                    Text("P ${e.proteinG.toInt()}g · C ${e.carbsG.toInt()}g · F ${e.fatG.toInt()}g",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Text("${e.calories} kcal")
                                IconButton({ vm.delete(e) }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun CalorieRing(eaten: Int, goal: Int) {
    val progress = (eaten.toFloat() / goal).coerceIn(0f, 1f)
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = Stroke(18.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = stroke, size = Size(size.width, size.height))
            drawArc(color, -90f, 360f * progress, false, style = stroke, size = Size(size.width, size.height))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$eaten", style = MaterialTheme.typography.headlineLarge)
            Text(if (eaten <= goal) "${goal - eaten} left" else "${eaten - goal} over", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Macro(label: String, grams: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${grams.toInt()} g", style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(vm: MainViewModel, onDone: () -> Unit) {
    val state by vm.add.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var meal by remember { mutableStateOf(meals[1]) }
    val ctx = LocalContext.current

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { photo = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { ctx.contentResolver.openInputStream(it)?.use { s -> photo = BitmapFactory.decodeStream(s) } }
    }
    DisposableEffect(Unit) { onDispose { vm.clearAdd() } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Add food") }, navigationIcon = {
            IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                meals.forEach { m -> FilterChip(meal == m, { meal = m }, { Text(m) }) }
            }
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(),
                label = { Text("What did you eat? e.g. 2 roti and 1 cup dal") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ camera.launch(null) }) { Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(4.dp)); Text("Camera") }
                OutlinedButton({ gallery.launch("image/*") }) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(4.dp)); Text("Gallery") }
                if (photo != null) Text("Photo attached ✓", Modifier.align(Alignment.CenterVertically))
            }
            Button({ vm.estimate(text, photo) }, Modifier.fillMaxWidth(), enabled = !state.loading && (text.isNotBlank() || photo != null)) {
                Text(if (state.loading) "Estimating…" else "Estimate calories")
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            state.results.forEach { n -> ResultRow(n) { vm.save(it, meal); onDone() } }

            HorizontalDivider()
            ManualEntry { vm.save(it, meal); onDone() }
        }
    }
}

@Composable
private fun ResultRow(n: Nutrition, onAdd: (Nutrition) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(n.name)
                Text("${n.calories} kcal · P ${n.proteinG.toInt()} C ${n.carbsG.toInt()} F ${n.fatG.toInt()} · ${n.source}",
                    style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton({ onAdd(n) }) { Text("Add") }
        }
    }
}

@Composable
private fun ManualEntry(onAdd: (Nutrition) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    Text("Or enter manually", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Food name") })
    OutlinedTextField(kcal, { kcal = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Calories") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Button({ onAdd(Nutrition(name, kcal.toInt(), 0.0, 0.0, 0.0, "manual")) }, enabled = name.isNotBlank() && kcal.isNotBlank()) { Text("Save") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var goal by remember(s) { mutableStateOf(s.calorieGoal.toString()) }
    var key by remember(s) { mutableStateOf(s.geminiKey) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(goal, { goal = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Daily calorie goal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("Gemini API key (free)") },
                supportingText = { Text("Get one at aistudio.google.com/apikey. Enables text + photo calorie estimates.") })
            Button({ vm.saveSettings(UserSettings(goal.toIntOrNull() ?: 2000, key)); onBack() }) { Text("Save") }
        }
    }
}
