package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DictionaryScreen
import com.example.ui.screens.ExerciseScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VocabViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VocabViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Reminders enabled! Alarms will notify you precisely.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Practice reminder alarms disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        viewModel.seedSampleWordsIfEmpty()

        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(1) } // 0: Dictionary, 1: Dashboard, 2: Exercise

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        val isSessionActive by viewModel.isSessionActive.collectAsState()
                        if (!isSessionActive) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentScreen == 0,
                                    onClick = { currentScreen = 0 },
                                    icon = {
                                        Icon(
                                            if (currentScreen == 0) Icons.Filled.Book else Icons.Outlined.Book,
                                            contentDescription = "Dictionary"
                                        )
                                    },
                                    label = { Text("Dictionary") },
                                    modifier = Modifier.testTag("nav_tab_dictionary")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == 1,
                                    onClick = { currentScreen = 1 },
                                    icon = {
                                        Icon(
                                            if (currentScreen == 1) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = "Dashboard"
                                        )
                                    },
                                    label = { Text("Dashboard") },
                                    modifier = Modifier.testTag("nav_tab_dashboard")
                                )
                                NavigationBarItem(
                                    selected = currentScreen == 2,
                                    onClick = { currentScreen = 2 },
                                    icon = {
                                        Icon(
                                            if (currentScreen == 2) Icons.Filled.School else Icons.Outlined.School,
                                            contentDescription = "Exercise"
                                        )
                                    },
                                    label = { Text("Exercise") },
                                    modifier = Modifier.testTag("nav_tab_exercise")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    ) {
                        when (currentScreen) {
                            0 -> DictionaryScreen(viewModel = viewModel)
                            1 -> DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToExercise = { currentScreen = 2 }
                            )
                            2 -> ExerciseScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
