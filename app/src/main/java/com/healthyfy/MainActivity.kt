package com.healthyfy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.Build
import com.healthyfy.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HealthyfyTheme { App() } }
    }
}

@Composable
fun HealthyfyTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(ctx)
    else lightColorScheme(primary = Color(0xFF2E7D32))
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun App() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    NavHost(nav, startDestination = "home") {
        composable("home") { HomeScreen(vm, onAdd = { nav.navigate("add") }, onSettings = { nav.navigate("settings") }) }
        composable("add") { AddFoodScreen(vm, onDone = { nav.popBackStack() }) }
        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
    }
}
