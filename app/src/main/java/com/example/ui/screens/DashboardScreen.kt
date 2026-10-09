package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.launch
import com.example.ui.viewmodel.ExerciseSettings
import com.example.ui.viewmodel.VocabViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: VocabViewModel, onNavigateToExercise: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val words by viewModel.allWords.collectAsState()
    val trialLogs by viewModel.trailLog.collectAsState()
    val countdownMs by viewModel.nextSessionCountdown.collectAsState()
    val settings by viewModel.exerciseSettings.collectAsState()
    val nextScheduled by viewModel.nextScheduledExercise.collectAsState()
    val isNextRecap by viewModel.isNextExerciseRecap.collectAsState()
    val ignoredSessions by viewModel.ignoredSessions.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSkipConfirmationDialog by remember { mutableStateOf(false) }

    // Infinite transition for glowing alert states in cards using robust Animatable
    val glowProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            glowProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            )
            glowProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            )
        }
    }
    val glowOutwardBy = (1.dp + (glowProgress.value * 8).dp)
    val glowAlpha = (0.2f + glowProgress.value * 0.6f)

    val listState = rememberLazyListState()
    var selectedHeatmapDayKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedHeatmapDayKey) {
        if (selectedHeatmapDayKey != null) {
            // Smoothly scroll down to make the day's activity logs fully visible (the heatmap card is at index 3)
            listState.animateScrollToItem(3)
        }
    }
    val sdfDayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val logsByDay = remember(trialLogs) {
        trialLogs.groupBy { log ->
            sdfDayKey.format(Date(log.timestamp))
        }
    }
    val wordPracticeCountByDay = remember(words) {
        val counts = mutableMapOf<String, Int>()
        words.forEach { word ->
            word.reviewHistory.forEach { mark ->
                val dayKey = sdfDayKey.format(Date(mark.timestamp))
                counts[dayKey] = (counts[dayKey] ?: 0) + 1
            }
        }
        counts
    }

    val heatmapWeeks = remember(trialLogs) {
        val list = mutableListOf<List<Pair<Calendar, List<com.example.ui.viewmodel.TrailLogEntry>>>>()
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_YEAR, -(dayOfWeek - 1))
        cal.add(Calendar.DAY_OF_YEAR, -13 * 7)

        for (w in 0 until 14) {
            val weekDays = mutableListOf<Pair<Calendar, List<com.example.ui.viewmodel.TrailLogEntry>>>()
            for (d in 0 until 7) {
                val dayCal = cal.clone() as Calendar
                val key = sdfDayKey.format(dayCal.time)
                val dayLogs = logsByDay[key] ?: emptyList()
                weekDays.add(dayCal to dayLogs)
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            list.add(weekDays)
        }
        list
    }

    // Backup Document Creator Launcher
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val jsonString = viewModel.generateBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Backup Document Opener Launcher
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader(Charsets.UTF_8).readText()
                    scope.launch {
                        val success = viewModel.restoreBackupFromJson(jsonString)
                        if (success) {
                            Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to restore backup: Invalid file format", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to restore backup: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Analytics Calculations
    val totalWords = words.size
    val unlearnedCount = words.count { it.masteryLevel == "Unlearned" }
    val troublesomeCount = words.count { it.masteryLevel == "Troublesome words" }
    val learningCount = words.count { it.masteryLevel == "Learning" }
    val fullyLearnedCount = words.count { it.masteryLevel == "Fully learned" }

    // Retention: Remembered total vs Forgotten total
    val totalMarksCount = words.sumOf { it.totalReviews }
    val totalRememberedCount = words.sumOf { it.rememberedCount }
    val retentionRate = if (totalMarksCount == 0) 100 else (totalRememberedCount * 100) / totalMarksCount

    // Format Countdown as beautiful relative "In xh ym zs" realtime format
    val countdownText = remember(countdownMs, nextScheduled) {
        if (countdownMs == null) {
            "No Upcoming Exercises"
        } else if (countdownMs!! <= 0L) {
            if (nextScheduled != null) "Session Active Now!" else "No Upcoming Exercises"
        } else {
            val totalSecs = countdownMs!! / 1000
            val secs = totalSecs % 60
            val mins = (totalSecs / 60) % 60
            val hrs = (totalSecs / 3600) % 24
            val days = totalSecs / (3600 * 24)
            if (days > 0) {
                String.format("In %dd %dh %dm %ds", days, hrs, mins, secs)
            } else {
                String.format("In %dh %dm %ds", hrs, mins, secs)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = com.example.R.drawable.app_icon_128),
                                contentDescription = "App Icon",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("VocabVault", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("dashboard_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
             // 1. Dynamic Session Card (Precedence: Ongoing, Missed, Countdown)
            item {
                val hasOngoing by viewModel.hasOngoingSessionFlow.collectAsState()
                val activeWords by viewModel.activeSessionWords.collectAsState()
                val sessionAnswers by viewModel.sessionAnswers.collectAsState()
                val customExercises by viewModel.customExercises.collectAsState()
                
                // Overdue sessions computation (gathers standard and recap overdue sessions in chronological order)
                val overdueSessions = remember(customExercises, words, ignoredSessions) {
                    viewModel.getOverdueSessions()
                }
                var selectedOverdueIndex by remember { mutableStateOf(0) }
                if (selectedOverdueIndex >= overdueSessions.size && overdueSessions.isNotEmpty()) {
                    selectedOverdueIndex = 0
                }
                
                if (hasOngoing && activeWords.isNotEmpty()) {
                    // Ongoing left in middle (Priority 1) - GLOWING ALWAYS
                    val solvedCount = sessionAnswers.size
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 6.dp + (glowProgress.value * 12).dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color(0xFFEAB308),
                                spotColor = Color(0xFFEAB308),
                                clip = false
                            )
                            .testTag("session_countdown_widget"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF08A)), // Warning Yellow
                        border = BorderStroke(2.dp + glowOutwardBy, Color(0xFFEAB308).copy(alpha = glowAlpha)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFF854D0E),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Ongoing Exercise in Progress!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF854D0E)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "You left an exercise session unfinished.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF854D0E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Progress: $solvedCount / ${activeWords.size} words solved (Ongoing)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF854D0E)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { solvedCount.toFloat() / activeWords.size.coerceAtMost(1) },
                                color = Color(0xFFCA8A04),
                                trackColor = Color(0xFFFEF9C3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    viewModel.resumeOngoingSession()
                                    onNavigateToExercise()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("continue_session_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF854D0E), contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Continue", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (overdueSessions.isNotEmpty()) {
                    val currentOverdue = overdueSessions.getOrNull(selectedOverdueIndex) ?: overdueSessions.first()
                    // Missed session (Priority 2) - GLOWING ALWAYS with Relative time & Exact time
                    val iconVector = when {
                        currentOverdue.isRecap -> Icons.Default.History
                        currentOverdue.type == "Learn" -> Icons.Default.AutoStories
                        else -> Icons.Default.RateReview
                    }
                    
                    val relativeMissedString = remember(currentOverdue) {
                        val diffMs = System.currentTimeMillis() - currentOverdue.scheduledTime
                        if (diffMs <= 0L) {
                            "Missed just now"
                        } else {
                            val diffMins = (diffMs / 60000) % 60
                            val diffHrs = (diffMs / (60000 * 60))
                            if (diffHrs > 0) {
                                "Missed $diffHrs hrs $diffMins mins ago"
                            } else {
                                "Missed $diffMins mins ago"
                            }
                        }
                    }
                    val exactMissedTimeText = remember(currentOverdue) {
                        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                        "Scheduled at ${sdf.format(Date(currentOverdue.scheduledTime))}"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 6.dp + (glowProgress.value * 12).dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color(0xFFF97316),
                                spotColor = Color(0xFFF97316),
                                clip = false
                            )
                            .testTag("missed_session_widget"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEDD5)), // Warning Orange Light
                        border = BorderStroke(2.dp + glowOutwardBy, Color(0xFFF97316).copy(alpha = glowAlpha)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = "Missed",
                                        tint = Color(0xFF7C2D12),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Missed Schedule Warning!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C2D12)
                                    )
                                }
                                if (overdueSessions.size > 1) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { if (selectedOverdueIndex > 0) selectedOverdueIndex-- },
                                            enabled = selectedOverdueIndex > 0,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Previous overdue session",
                                                tint = if (selectedOverdueIndex > 0) Color(0xFF7C2D12) else Color(0xFF7C2D12).copy(alpha = 0.3f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Text(
                                            text = "${selectedOverdueIndex + 1}/${overdueSessions.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF7C2D12)
                                        )
                                        IconButton(
                                            onClick = { if (selectedOverdueIndex < overdueSessions.size - 1) selectedOverdueIndex++ },
                                            enabled = selectedOverdueIndex < overdueSessions.size - 1,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "Next overdue session",
                                                tint = if (selectedOverdueIndex < overdueSessions.size - 1) Color(0xFF7C2D12) else Color(0xFF7C2D12).copy(alpha = 0.3f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (overdueSessions.size > 1) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    overdueSessions.forEachIndexed { idx, session ->
                                        val isSel = idx == selectedOverdueIndex
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { selectedOverdueIndex = idx },
                                            label = { Text("${idx + 1}. ${session.name}") },
                                            leadingIcon = {
                                                val chipIcon = if (session.isRecap) Icons.Default.History else if (session.type == "Learn") Icons.Default.AutoStories else Icons.Default.RateReview
                                                Icon(chipIcon, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Missed: ${currentOverdue.name} (${currentOverdue.type})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C2D12)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Elapsed: $relativeMissedString",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF7C2D12)
                            )
                            Text(
                                text = exactMissedTimeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7C2D12).copy(alpha = 0.75f)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (currentOverdue.isRecap) {
                                        viewModel.startRecapSession(currentOverdue.config)
                                    } else {
                                        viewModel.startExerciseByConfig(currentOverdue.config)
                                    }
                                    onNavigateToExercise()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_missed_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C2D12), contentColor = Color.White)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (currentOverdue.isRecap) "Start Recap Now" else "Start Now", fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(
                                    onClick = {
                                        showSkipConfirmationDialog = true
                                    },
                                    modifier = Modifier.testTag("skip_missed_session_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip missed session",
                                        tint = Color(0xFF7C2D12).copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        if (currentOverdue.isRecap) "Skip this missed recap" else "Skip this missed session",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7C2D12).copy(alpha = 0.7f),
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    )
                                }
                            }

                            if (showSkipConfirmationDialog) {
                                AlertDialog(
                                    onDismissRequest = { showSkipConfirmationDialog = false },
                                    title = { Text("Confirm Skip") },
                                    text = { Text(if (currentOverdue.isRecap) "Are you sure you want to skip this missed recap session?" else "Are you sure you want to skip this missed session?") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                if (currentOverdue.isRecap) {
                                                    viewModel.ignoreRecapSession(currentOverdue.config.id, currentOverdue.scheduledTime)
                                                } else {
                                                    viewModel.ignoreMissedSession(currentOverdue.config.id, currentOverdue.scheduledTime)
                                                }
                                                Toast.makeText(context, "Missed session ignored", Toast.LENGTH_SHORT).show()
                                                showSkipConfirmationDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Skip")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showSkipConfirmationDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Countdown card (Priority 3) - GLOWS IF <30 MINS, shows relative time and exact time
                    val shouldGlowNextSchedule = countdownMs != null && countdownMs!! <= 30 * 60 * 1000L

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (shouldGlowNextSchedule) {
                                    Modifier.shadow(
                                        elevation = 6.dp + (glowProgress.value * 12).dp,
                                        shape = RoundedCornerShape(12.dp),
                                        ambientColor = MaterialTheme.colorScheme.primary,
                                        spotColor = MaterialTheme.colorScheme.primary,
                                        clip = false
                                    )
                                } else Modifier
                            )
                            .testTag("session_countdown_widget"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = if (shouldGlowNextSchedule) {
                            BorderStroke(
                                2.dp + glowOutwardBy,
                                MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)
                            )
                        } else null,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val nextIcon = if (isNextRecap) {
                                    Icons.Default.History
                                } else if (nextScheduled?.type == "Learn") {
                                    Icons.Default.AutoStories
                                } else {
                                    Icons.Default.RateReview
                                }
                                Icon(
                                    imageVector = nextIcon,
                                    contentDescription = "Session",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (nextScheduled != null) {
                                        if (isNextRecap) {
                                            "${nextScheduled?.name} (Recap)"
                                        } else {
                                            "${nextScheduled?.name} (${nextScheduled?.type})"
                                        }
                                    } else {
                                        "No Upcoming Schedule"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                countdownText,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            if (nextScheduled != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isNextRecap) "Scheduled Recap Session" else "Scheduled at ${String.format("%02d:%02d", nextScheduled?.scheduleHour, nextScheduled?.scheduleMinute)} (${nextScheduled?.mode})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Create an Exercise!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                             Button(
                                onClick = {
                                    val nextEx = nextScheduled ?: customExercises.firstOrNull()
                                    if (nextEx != null) {
                                        if (isNextRecap) {
                                            viewModel.startRecapSession(nextEx)
                                        } else {
                                            viewModel.startExerciseByConfig(nextEx)
                                        }
                                    } else {
                                        viewModel.startSession("Learning")
                                    }
                                    onNavigateToExercise()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_early_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                val isActiveNow = countdownMs != null && countdownMs!! <= 0L
                                val labelText = if (isNextRecap) {
                                    if (isActiveNow) "Start Recap" else "Start Recap early"
                                } else {
                                    if (isActiveNow) "Start" else "Start early"
                                }
                                Icon(if (isNextRecap) Icons.Default.History else Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(labelText, fontWeight = FontWeight.Bold)
                             }
                        }
                    }
                }
            }

            // 2. Stats Grid / Scorecards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_total_words"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Total Words", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("$totalWords", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("stat_retention_rate"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Retention Rate", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("$retentionRate%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = if (retentionRate >= 80) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        }
                    }
                }
            }

            // 3. Mastery Levels Progress Visualizer
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Word Mastery Statuses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Visual Progress Bar Breakdown
                        val totalActive = unlearnedCount + troublesomeCount + learningCount + fullyLearnedCount
                        if (totalActive > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                if (fullyLearnedCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(fullyLearnedCount.toFloat() / totalActive)
                                            .fillMaxHeight()
                                            .background(Color(0xFF4CAF50))
                                    )
                                }
                                if (learningCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(learningCount.toFloat() / totalActive)
                                            .fillMaxHeight()
                                            .background(Color(0xFF2196F3))
                                    )
                                }
                                if (troublesomeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(troublesomeCount.toFloat() / totalActive)
                                            .fillMaxHeight()
                                            .background(Color(0xFFF44336))
                                    )
                                }
                                if (unlearnedCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .weight(unlearnedCount.toFloat() / totalActive)
                                            .fillMaxHeight()
                                            .background(Color(0xFF9E9E9E))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Text indicators with colored bullets
                        MasteryRowIndicator("Fully learned", fullyLearnedCount, Color(0xFF4CAF50))
                        MasteryRowIndicator("Learning", learningCount, Color(0xFF2196F3))
                        MasteryRowIndicator("Troublesome words", troublesomeCount, Color(0xFFF44336))
                        MasteryRowIndicator("Unlearned", unlearnedCount, Color(0xFF9E9E9E))
                    }
                }
            }

            // 4. GitHub Activity-Style Heatmap Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("github_heatmap_card"),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Practice Consistency Heatmap",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Render horizontal Row of columns (weeks)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Subtly and minimally display day of the week on left-side
                            val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                dayLabels.forEach { label ->
                                    Box(
                                        modifier = Modifier.size(width = 16.dp, height = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }

                            heatmapWeeks.forEach { weekList ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    weekList.forEach { (dayCal, dayLogs) ->
                                        val dayKey = sdfDayKey.format(dayCal.time)
                                        val practiceCount = wordPracticeCountByDay[dayKey] ?: 0
                                        val color = when {
                                            practiceCount == 0 -> if (MaterialTheme.colorScheme.primaryContainer == MaterialTheme.colorScheme.surfaceVariant) Color(0xFF334155) else Color(0xFFE2E8F0)
                                            practiceCount < 5 -> Color(0xFFBBF7D0) // Light green
                                            practiceCount < 15 -> Color(0xFF4ADE80) // Medium green
                                            practiceCount < 30 -> Color(0xFF22C55E) // Bright green
                                            else -> Color(0xFF15803D) // Deep green
                                        }
                                        val isSelected = selectedHeatmapDayKey == dayKey

                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(color)
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 0.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = RoundedCornerShape(3.dp)
                                                )
                                                .clickable {
                                                    selectedHeatmapDayKey = if (isSelected) null else dayKey
                                                }
                                        )
                                    }
                                }
                            }
                        }

                        // Info Legend
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Less", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val borderCol = if (MaterialTheme.colorScheme.primaryContainer == MaterialTheme.colorScheme.surfaceVariant) Color(0xFF334155) else Color(0xFFE2E8F0)
                                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(borderCol))
                                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFBBF7D0)))
                                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF4ADE80)))
                                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF22C55E)))
                                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF15803D)))
                            }
                            Text("More", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }

                        // Selected Heatmap log breakdown details
                        selectedHeatmapDayKey?.let { dayKey ->
                            val currentDayLogs = logsByDay[dayKey] ?: emptyList()
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))

                            val parsedDate = try { sdfDayKey.parse(dayKey) } catch (e: Exception) { null }
                            val displayDateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                            val titleDateStr = if (parsedDate != null) displayDateFormat.format(parsedDate) else dayKey

                            Text(
                                text = "Activity for $titleDateStr",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (currentDayLogs.isEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No study sessions recorded on this day.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    currentDayLogs.forEach { log ->
                                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestamp))
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${log.sessionType} Exercise",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "Practiced: ${log.totalWords} words",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = timeStr,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = "Accuracy: ${log.passedCount}/${log.totalWords}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (log.passedCount == log.totalWords) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Core Dialogs ---

    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false },
            onBackup = {
                createDocumentLauncher.launch("vocab_vault_backup.json")
            },
            onRestore = {
                openDocumentLauncher.launch(arrayOf("application/json"))
            }
        )
    }
}

@Composable
fun MasteryRowIndicator(title: String, count: Int, bulletColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(bulletColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium)
        }
        Text("$count words", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SettingsDialog(
    viewModel: VocabViewModel,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    val fullyLearned by viewModel.fullyLearnedThreshold.collectAsState()
    val troublesome by viewModel.troublesomeThreshold.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("settings_modal"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Word Mastery Settings Section
                Text(
                    text = "Word Mastery Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Fully Learned Item
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Stars,
                                        contentDescription = "Fully learned icon",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Fully learned",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Consecutive remembered marks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Required count: ", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            IconButton(
                                onClick = { if (fullyLearned > 1) viewModel.updateMasterySettings(fullyLearned - 1, troublesome) },
                                modifier = Modifier.size(32.dp).testTag("mastery_fully_learned_minus")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = fullyLearned.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.widthIn(min = 24.dp).testTag("mastery_fully_learned_value"),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = { if (fullyLearned < 20) viewModel.updateMasterySettings(fullyLearned + 1, troublesome) },
                                modifier = Modifier.size(32.dp).testTag("mastery_fully_learned_plus")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Troublesome Words Item
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE53935).copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Troublesome words icon",
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Troublesome words",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Consecutive forgotten marks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Required count: ", 
                                style = MaterialTheme.typography.bodySmall, 
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            IconButton(
                                onClick = { if (troublesome > 1) viewModel.updateMasterySettings(fullyLearned, troublesome - 1) },
                                modifier = Modifier.size(32.dp).testTag("mastery_troublesome_minus")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = troublesome.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.widthIn(min = 24.dp).testTag("mastery_troublesome_value"),
                                textAlign = TextAlign.Center
                            )
                            IconButton(
                                onClick = { if (troublesome < 10) viewModel.updateMasterySettings(fullyLearned, troublesome + 1) },
                                modifier = Modifier.size(32.dp).testTag("mastery_troublesome_plus")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Export your complete vocabulary list and study history to a JSON file, or restore data from a previous backup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onBackup,
                        modifier = Modifier.weight(1f).testTag("backup_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onRestore,
                        modifier = Modifier.weight(1f).testTag("restore_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Version:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("v1.1.0", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Copyright © 2026 Alireza",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
