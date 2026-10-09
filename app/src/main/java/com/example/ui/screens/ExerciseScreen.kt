package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ExerciseConfig
import com.example.ui.viewmodel.VocabViewModel
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private data class ScopeOption(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color
)

private fun formatRelativeTime(targetTime: Long): String {
    val diffMs = targetTime - System.currentTimeMillis()
    if (diffMs <= 0) return "Due now"
    
    val totalSecs = diffMs / 1000
    val totalMins = totalSecs / 60
    val mins = totalMins % 60
    val hrs = (totalMins / 60) % 24
    val days = totalMins / (60 * 24)
    
    return when {
        days > 0 -> "in $days d"
        hrs > 0 -> "in ${hrs}h ${mins}m"
        else -> "in ${mins}m"
    }
}

private fun getExerciseRelativeTime(timestamp: Long, fallbackNow: String): String {
    val diff = System.currentTimeMillis() - timestamp
    if (diff < 0) return fallbackNow
    
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "$days d ago"
        hours > 0 -> "$hours h ago"
        minutes > 0 -> "$minutes m ago"
        else -> fallbackNow
    }
}

private fun formatRelativeDuration(diffMs: Long): String {
    if (diffMs <= 0) return "just now"
    val totalSecs = diffMs / 1000
    val totalMins = totalSecs / 60
    val mins = totalMins % 60
    val hrs = (totalMins / 60) % 24
    val days = totalMins / (60 * 24)
    
    return when {
        days > 0 -> "$days d"
        hrs > 0 -> "${hrs}h ${mins}m"
        else -> "${mins}m"
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ExerciseScreen(viewModel: VocabViewModel) {
    val context = LocalContext.current

    val allWords by viewModel.allWords.collectAsState()
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val activeSessionType by viewModel.activeSessionType.collectAsState()
    val activeSessionWords by viewModel.activeSessionWords.collectAsState()
    val currentWordIndex by viewModel.currentWordIndex.collectAsState()
    val isMeaningRevealed by viewModel.isMeaningRevealed.collectAsState()
    val sessionAnswers by viewModel.sessionAnswers.collectAsState()
    val customExercises by viewModel.customExercises.collectAsState()
    val ignoredSessions by viewModel.ignoredSessions.collectAsState()
    val trailLogs by viewModel.trailLog.collectAsState()
    val hasOngoing by viewModel.hasOngoingSessionFlow.collectAsState()
    val activeSessionConfigId by viewModel.activeSessionConfigId.collectAsState()

    val examplesCache by viewModel.examplesCache.collectAsState()
    val loadingExamplesWordId by viewModel.loadingExamplesWordId.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Learn Configurations, 1: Review Configurations
    var showConfigDialog by remember { mutableStateOf(false) }
    var configToEdit by remember { mutableStateOf<ExerciseConfig?>(null) }
    var configToDelete by remember { mutableStateOf<ExerciseConfig?>(null) }

    // On-demand examples toggle
    var showExamplesChecked by remember { mutableStateOf(true) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { 
                    if (isSessionActive) {
                        val sessionName by viewModel.activeSessionConfigName.collectAsState()
                        val sessionType by viewModel.activeSessionType.collectAsState()
                        val exerciseIcon = if (sessionType == "Learn") Icons.Default.School else Icons.Default.RateReview
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = exerciseIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = sessionName, 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        Text(
                            "Exercises", 
                            fontWeight = FontWeight.Bold
                        ) 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Removed and moved inline for better UX
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isSessionActive) {
                // CONFIGURATION SELECTION & LOGS
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Learn") },
                        icon = { Icon(Icons.Default.School, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Review") },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                    )
                }

                val filteredConfigs = remember(customExercises, selectedTab, ignoredSessions) {
                    val tabConfigs = customExercises.filter { 
                        if (selectedTab == 0) it.type == "Learn" else it.type == "Review" 
                    }
                    tabConfigs.sortedWith(
                        compareBy<ExerciseConfig> { config ->
                            val lastRun = viewModel.getLastRunTimestamp(config.id)
                            val createdAt = viewModel.getConfigCreatedAt(config.id)
                            val lastScheduled = viewModel.getLastScheduledTime(config)
                            val isOverdue = lastRun < lastScheduled && lastScheduled <= System.currentTimeMillis() && lastScheduled > createdAt
                            val isPassed30Mins = System.currentTimeMillis() - lastScheduled > 30 * 60 * 1000L
                            val isIgnored = viewModel.isSessionIgnored(config.id, lastScheduled)
                            val isMissed = isOverdue && isPassed30Mins && !isIgnored
                            
                            val isRecapMissed = if (config.type == "Learn") viewModel.isRecapOverdueForConfig(config) else false

                            val isActive = lastRun < lastScheduled && !isIgnored && System.currentTimeMillis() >= lastScheduled && (System.currentTimeMillis() - lastScheduled <= 30 * 60 * 1000L)
                            val isRecapActive = if (config.type == "Learn") viewModel.isRecapActiveNowForConfig(config) else false

                            when {
                                isMissed || isRecapMissed -> 0
                                isActive || isRecapActive -> 1
                                else -> 2
                            }
                        }.thenBy { config ->
                            val lastScheduled = viewModel.getLastScheduledTime(config)
                            val lastRun = viewModel.getLastRunTimestamp(config.id)
                            val isOverdue = lastScheduled <= System.currentTimeMillis()
                            if (isOverdue) lastScheduled else viewModel.getNextScheduledTime(config, lastRun)
                        }
                    )
                }

                val filteredLogs = remember(trailLogs, selectedTab) {
                    trailLogs.filter { log ->
                        if (selectedTab == 0) {
                            log.sessionType == "Learn" || log.sessionType == "Learning" || log.sessionType == "Recap"
                        } else {
                            log.sessionType == "Review"
                        }
                    }
                }

                val expandedLogs = remember { mutableStateMapOf<Long, Boolean>() }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (hasOngoing) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ongoing_session_banner"),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Ongoing Session Paused",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            "You have an ongoing study session. Resume or discard it.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { viewModel.cancelOngoingSession() }
                                        ) {
                                            Text("Discard", color = Color(0xFFC62828))
                                        }
                                        Button(
                                            onClick = { viewModel.resumeOngoingSession() },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Resume", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 1: Configurations List
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (selectedTab == 0) "Learn Exercises" else "Review Exercises",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isSessionActive) {
                                IconButton(
                                    onClick = {
                                        configToEdit = null
                                        showConfigDialog = true
                                    },
                                    modifier = Modifier.testTag("add_config_button")
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Exercise",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    if (filteredConfigs.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No Exercises yet.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    Text("Tap the '+' button next to the header to create one.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    } else {
                        items(filteredConfigs) { config ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("exercise_config_card_${config.name.lowercase().replace(" ", "_")}"),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    val lastRun = viewModel.getLastRunTimestamp(config.id)
                                    val createdAt = viewModel.getConfigCreatedAt(config.id)
                                    val lastScheduled = viewModel.getLastScheduledTime(config)
                                    val isOverdue = lastRun < lastScheduled && lastScheduled <= System.currentTimeMillis() && lastScheduled > createdAt
                                    val isPassed30Mins = System.currentTimeMillis() - lastScheduled > 30 * 60 * 1000L
                                    val isIgnored = viewModel.isSessionIgnored(config.id, lastScheduled)
                                    val isMissed = isOverdue && isPassed30Mins && !isIgnored
                                    val isActiveNow = lastRun < lastScheduled && !isIgnored && System.currentTimeMillis() >= lastScheduled && (System.currentTimeMillis() - lastScheduled <= 30 * 60 * 1000L)
                                    
                                    val nextScheduledTime = if (isActiveNow) lastScheduled else viewModel.getNextScheduledTime(config, lastRun)
                                    val calToday = Calendar.getInstance()
                                    val calTomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                                    val calTarget = Calendar.getInstance().apply { timeInMillis = nextScheduledTime }
                                    val isToday = calToday.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) && 
                                                  calToday.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)
                                    val isTomorrow = calTomorrow.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) && 
                                                     calTomorrow.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)
                                    val dayPrefix = when {
                                        isToday -> "Today"
                                        isTomorrow -> "Tomorrow"
                                        else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(nextScheduledTime))
                                    }
                                    val exactTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextScheduledTime))
                                    val relativeTimeStr = formatRelativeTime(nextScheduledTime)
                                    val nextScheduleDisplayStr = "$dayPrefix at $exactTimeFormatted ($relativeTimeStr)"

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(config.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                            val daysAbbrMap = remember {
                                                mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
                                            }
                                            val modeSummary = if (config.mode == "Weekly") {
                                                val daysNames = config.getEffectiveDaysOfWeek().mapNotNull { daysAbbrMap[it] }.joinToString(", ")
                                                "Weekly ($daysNames)"
                                            } else {
                                                config.mode
                                            }
                                            Text("Mode: $modeSummary • Count: ${config.wordCount} words", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                            if (config.type == "Review") {
                                                val scopes = config.getEffectiveReviewScopes()
                                                val scopeDisplay = if (scopes.size >= 3) {
                                                    "All (Learnings, Fully learned, Troublesomes)"
                                                } else {
                                                    scopes.joinToString(", ") { s ->
                                                        when (s) {
                                                            "Learning" -> "Learnings"
                                                            "Troublesome words" -> "Troublesomes"
                                                            else -> s
                                                        }
                                                    }
                                                }
                                                Text("Scope: $scopeDisplay • Algorithm: ${config.reviewAlgorithm}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            }
                                            
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (isMissed) {
                                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    Text("Missed session: ${formatRelativeDuration(System.currentTimeMillis() - lastScheduled)} ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                                } else if (isActiveNow) {
                                                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                                    Text("Session active now!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                                } else {
                                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                                    Text("Next: $nextScheduleDisplayStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                                }
                                            }
                                            
                                            // Dynamic Recap session status (if enabled and applicable)
                                            if (config.type == "Learn" && config.delayedRecapEnabled) {
                                                val hasRun = lastRun > 1L
                                                if (hasRun) {
                                                    val recapLastRun = viewModel.getRecapLastRun(config.id)
                                                    if (recapLastRun < lastRun) {
                                                        val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
                                                        val isRecapDue = System.currentTimeMillis() >= recapScheduledTime
                                                        val isRecapOverdue = viewModel.isRecapOverdueForConfig(config)
                                                        val isRecapActiveNow = viewModel.isRecapActiveNowForConfig(config)
                                                        val recapRelative = if (isRecapDue) "Due now" else formatRelativeTime(recapScheduledTime)
                                                        val recapTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(recapScheduledTime))
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            modifier = Modifier.padding(top = 2.dp)
                                                        ) {
                                                            if (isRecapOverdue) {
                                                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                                Text("Missed recap session: ${formatRelativeDuration(System.currentTimeMillis() - recapScheduledTime)} ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                                            } else if (isRecapActiveNow) {
                                                                Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                                                Text("Recap session active now!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                                            } else {
                                                                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                                                Text("Recap session: $recapTimeStr ($recapRelative)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    configToEdit = config
                                                    showConfigDialog = true
                                                },
                                                modifier = Modifier.testTag("edit_config_button")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Exercise", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(
                                                onClick = {
                                                    configToDelete = config
                                                },
                                                modifier = Modifier.testTag("delete_config_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Exercise",
                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    val isOngoingForThisConfig = hasOngoing && activeSessionConfigId == config.id
                                    val isRecapPending = config.type == "Learn" && viewModel.isRecapPendingForConfig(config)
                                    val isRecapUpcoming = config.type == "Learn" && viewModel.isRecapUpcomingForConfig(config)
                                    val isRecapAvailable = isRecapPending || isRecapUpcoming

                                    if (isOngoingForThisConfig) {
                                        Button(
                                            onClick = {
                                                viewModel.resumeOngoingSession()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("continue_exercise_button_${config.name.lowercase().replace(" ", "_")}"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Continue", fontWeight = FontWeight.Bold)
                                        }
                                    } else if (isRecapAvailable) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val unlearned = allWords.count { it.reviewHistory.isEmpty() }
                                                    if (unlearned == 0) {
                                                        Toast.makeText(context, "All words are already learned! Add some new words first.", Toast.LENGTH_LONG).show()
                                                        return@Button
                                                    }
                                                    viewModel.startExerciseByConfig(config)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("start_exercise_button_${config.name.lowercase().replace(" ", "_")}"),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                val buildLabel = if (isMissed) "Overdue Learn" else if (isActiveNow) "Learn Now" else "Learn Early"
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(buildLabel, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.startRecapSession(config)
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("recap_exercise_button_${config.name.lowercase().replace(" ", "_")}"),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (viewModel.isRecapOverdueForConfig(config)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                                )
                                            ) {
                                                val isRecapOverdue = viewModel.isRecapOverdueForConfig(config)
                                                val recapLabel = if (isRecapOverdue) "Overdue Recap" else if (isRecapUpcoming) "Start Recap Early" else "Start Recap"
                                                Icon(if (isRecapOverdue) Icons.Default.Warning else Icons.Default.History, contentDescription = null)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(recapLabel, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            }
                                        }

                                        val isRecapOverdue = viewModel.isRecapOverdueForConfig(config)
                                        if (isMissed || isRecapOverdue) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                if (isMissed) {
                                                    TextButton(
                                                        onClick = {
                                                            viewModel.ignoreMissedSession(config.id, lastScheduled)
                                                            Toast.makeText(context, "Missed learn session ignored", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Text("Skip missed learn", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                                if (isRecapOverdue) {
                                                    TextButton(
                                                        onClick = {
                                                            val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
                                                            viewModel.ignoreRecapSession(config.id, recapScheduledTime)
                                                            Toast.makeText(context, "Missed recap ignored", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Text("Skip missed recap", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (config.type == "Learn") {
                                                    val unlearned = allWords.count { it.reviewHistory.isEmpty() }
                                                    if (unlearned == 0) {
                                                        Toast.makeText(context, "All words are already learned! Add some new words first.", Toast.LENGTH_LONG).show()
                                                        return@Button
                                                    }
                                                } else {
                                                    val reviewed = allWords.count { it.reviewHistory.isNotEmpty() }
                                                    if (reviewed == 0) {
                                                        Toast.makeText(context, "No words learned yet! Learn some words before reviewing.", Toast.LENGTH_LONG).show()
                                                        return@Button
                                                    }
                                                }
                                                viewModel.startExerciseByConfig(config)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("start_exercise_button_${config.name.lowercase().replace(" ", "_")}"),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = if (isMissed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                                        ) {
                                            val defaultLabel = if (isMissed) {
                                                "Start Overdue Session"
                                            } else if (isActiveNow) {
                                                "Start"
                                            } else {
                                                "Start Early"
                                            }
                                            Icon(if (isMissed) Icons.Default.Warning else Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(defaultLabel, fontWeight = FontWeight.Bold)
                                        }
                                        if (isMissed) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        viewModel.ignoreMissedSession(config.id, lastScheduled)
                                                        Toast.makeText(context, "Missed session ignored", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Text("Skip missed session", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 2: Filtered Inline Practice logs
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (selectedTab == 0) "Learning Exercise Logs" else "Review Exercise Logs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (filteredLogs.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = LightGrayCircleColor, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("No practice logs recorded yet in this tab.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    } else {
                        items(filteredLogs) { log ->
                            val isExpanded = expandedLogs[log.timestamp] == true
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedLogs[log.timestamp] = !isExpanded },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                val dateString = remember(log.timestamp) {
                                    SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(log.timestamp))
                                }
                                val rate = if (log.totalWords > 0) (log.passedCount * 100) / log.totalWords else 0

                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(dateString, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                val (badgeText, badgeColor) = when (log.sessionType) {
                                                    "Recap" -> "Recap" to MaterialTheme.colorScheme.tertiary
                                                    "Review" -> "Review" to MaterialTheme.colorScheme.secondary
                                                    else -> "Learning" to MaterialTheme.colorScheme.primary
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = badgeColor.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = badgeText,
                                                        color = badgeColor,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Score: ${log.passedCount} / ${log.totalWords} words",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                text = "Accuracy: $rate%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (rate >= 80) Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
                                        }
                                        CircularProgressIndicator(
                                            progress = { rate.toFloat() / 100f },
                                            color = if (rate >= 80) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(36.dp),
                                            strokeWidth = 4.dp
                                        )
                                    }

                                    if (isExpanded && log.wordsPracticed.isNotEmpty()) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Exercised Words Details:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            log.wordsPracticed.forEach { pWord ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1.5f)) {
                                                        Text(
                                                            text = pWord.english,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                        )
                                                        Text(
                                                            text = pWord.farsiMeaning,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    
                                                    val badgeColor = if (pWord.status == "Remembered") Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                                                    val badgeTextColor = if (pWord.status == "Remembered") Color(0xFF2E7D32) else Color(0xFFC62828)
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(badgeColor)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = pWord.status,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = badgeTextColor,
                                                            fontWeight = FontWeight.Bold
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
            } else {
                // ACTIVE PRACTICE SESSION VIEW
                val totalInSession = activeSessionWords.size
                if (currentWordIndex < totalInSession) {
                    val word = activeSessionWords[currentWordIndex]

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header panel with clean title and close/exit button
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Practice Session",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            IconButton(onClick = { viewModel.endSessionEarly() }) {
                                Icon(Icons.Default.Cancel, contentDescription = "Exit practice", tint = Color.Gray.copy(alpha = 0.8f))
                            }
                        }

                        // Centered Container for indicators & flashcard
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.Top,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Circles with arrow buttons at left and right end
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.goToPreviousWord() },
                                    enabled = currentWordIndex > 0,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronLeft,
                                        contentDescription = "Previous word",
                                        tint = if (currentWordIndex > 0) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                                    )
                                }

                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    activeSessionWords.forEachIndexed { index, wordItem ->
                                        val answer = sessionAnswers[wordItem.id]
                                        val isCurrent = index == currentWordIndex
                                        val badgeColor = when {
                                            isCurrent -> MaterialTheme.colorScheme.primary
                                            answer == "Remembered" -> Color(0xFF4CAF50)
                                            answer == "Forgotten" -> Color(0xFFF44336)
                                            else -> Color.Gray
                                        }
                                        val iconVector = when (answer) {
                                            "Remembered" -> Icons.Default.Check
                                            "Forgotten" -> Icons.Default.Close
                                            else -> Icons.Default.Circle
                                        }
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(badgeColor.copy(alpha = 0.15f))
                                                .border(
                                                    width = if (isCurrent) 2.dp else 1.dp,
                                                    color = badgeColor,
                                                    shape = CircleShape
                                                )
                                                .clickable { viewModel.setCurrentWordIndex(index) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = iconVector,
                                                contentDescription = null,
                                                tint = badgeColor,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.goToNextWord() },
                                    enabled = currentWordIndex + 1 < totalInSession,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Next word",
                                        tint = if (currentWordIndex + 1 < totalInSession) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Word index text
                            Text(
                                text = "COMPLETED ${sessionAnswers.size} OF $totalInSession",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Smooth animation transition on word change
                            AnimatedContent(
                                targetState = currentWordIndex,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) + slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(220)) { it / 2 })
                                            .togetherWith(fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) + slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(180)) { -it / 2 })
                                    } else {
                                        (fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) + slideInHorizontally(animationSpec = androidx.compose.animation.core.tween(220)) { -it / 2 })
                                            .togetherWith(fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) + slideOutHorizontally(animationSpec = androidx.compose.animation.core.tween(180)) { it / 2 })
                                    }
                                },
                                label = "WordNavigationTransition"
                            ) { targetIndex ->
                                val targetWord = activeSessionWords.getOrNull(targetIndex) ?: word
                                val coroutineScope = rememberCoroutineScope()
                                val dragOffsetX = remember(targetIndex) { androidx.compose.animation.core.Animatable(0f) }
                                val density = LocalDensity.current
                                val distanceScalePx = remember(density) { with(density) { 120.dp.toPx() } }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(385.dp)
                                        .graphicsLayer {
                                            translationX = dragOffsetX.value
                                            // Gentle rotation/tilt as visual cue (max 6 degrees)
                                            rotationZ = (dragOffsetX.value / distanceScalePx * 6f).coerceIn(-6f, 6f)
                                            // Subtle opacity fading as we swipe off
                                            alpha = (1f - (kotlin.math.abs(dragOffsetX.value) / 1200f)).coerceIn(0.5f, 1f)
                                        }
                                        .pointerInput(targetIndex) {
                                            detectHorizontalDragGestures(
                                                onDragStart = {},
                                                onDragEnd = {
                                                     val thresholdPx = with(density) { 130.dp.toPx() }
                                                     val currentOffset = dragOffsetX.value
                                                     coroutineScope.launch {
                                                         if (currentOffset < -thresholdPx && currentWordIndex + 1 < totalInSession) {
                                                             dragOffsetX.animateTo(
                                                                 targetValue = -1200f,
                                                                 animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
                                                             )
                                                             viewModel.goToNextWord()
                                                         } else if (currentOffset > thresholdPx && currentWordIndex > 0) {
                                                             dragOffsetX.animateTo(
                                                                 targetValue = 1200f,
                                                                 animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)
                                                             )
                                                             viewModel.goToPreviousWord()
                                                         } else {
                                                             dragOffsetX.animateTo(
                                                                 targetValue = 0f,
                                                                 animationSpec = androidx.compose.animation.core.spring(
                                                                     dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                                                     stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                                                 )
                                                             )
                                                         }
                                                     }
                                                },
                                                onDragCancel = {
                                                     coroutineScope.launch {
                                                         dragOffsetX.animateTo(0f)
                                                     }
                                                },
                                                onHorizontalDrag = { change, dragAmount ->
                                                     change.consume()
                                                     coroutineScope.launch {
                                                         dragOffsetX.snapTo(dragOffsetX.value + dragAmount)
                                                     }
                                                }
                                            )
                                        }
                                        .clickable { viewModel.toggleMeaningRevealed() }
                                        .testTag("active_flashcard_view"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isMeaningRevealed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(20.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.Top,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Fixed position English word at the top
                                        Text(
                                            text = targetWord.english,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isMeaningRevealed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )

                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = if (isMeaningRevealed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                        )

                                        // Smooth transition on target reveal state
                                        AnimatedContent(
                                            targetState = isMeaningRevealed,
                                            transitionSpec = {
                                                (fadeIn(animationSpec = androidx.compose.animation.core.tween(120)) + scaleIn(initialScale = 0.97f, animationSpec = androidx.compose.animation.core.tween(120)))
                                                    .togetherWith(fadeOut(animationSpec = androidx.compose.animation.core.tween(100)) + scaleOut(targetScale = 0.97f, animationSpec = androidx.compose.animation.core.tween(140)))
                                            },
                                            label = "CardRevealTransition"
                                        ) { revealed ->
                                            if (!revealed) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Spacer(modifier = Modifier.height(50.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.HelpOutline, 
                                                        contentDescription = null, 
                                                        modifier = Modifier.size(50.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                                                    )
                                                    Spacer(modifier = Modifier.height(16.dp))
                                                    Text(
                                                        "Tap to Reveal",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            } else {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    Text(
                                                        targetWord.farsiMeaning,
                                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )

                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    Text(
                                                        targetWord.synonymsMeaning,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )

                                                    val list = if (targetWord.usageExamples.isNotEmpty()) targetWord.usageExamples else (examplesCache[targetWord.id] ?: emptyList())
                                                    val isLoadingExamples = loadingExamplesWordId == targetWord.id

                                                    if (list.isEmpty()) {
                                                        Spacer(modifier = Modifier.height(20.dp))
                                                        Button(
                                                            onClick = {
                                                                viewModel.showExamples(targetWord) { errMsg ->
                                                                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                                                }
                                                            },
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(38.dp)
                                                                .testTag("show_examples_button_practice"),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.outlineVariant, 
                                                                 contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                            ),
                                                            shape = RoundedCornerShape(8.dp),
                                                            enabled = !isLoadingExamples
                                                        ) {
                                                            if (isLoadingExamples) {
                                                                CircularProgressIndicator(modifier = Modifier.size(14.dp))
                                                            } else {
                                                                Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            }
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Show Usage Examples", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    } else {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        HorizontalDivider()
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            "Usage Examples:",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        list.forEachIndexed { i, ex ->
                                                            Text(
                                                                "${i + 1}. $ex",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontStyle = FontStyle.Italic,
                                                                textAlign = TextAlign.Center,
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(20.dp))
                                                    HorizontalDivider(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                                     )
                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    val relativeAdded = remember(targetWord.dateAdded) {
                                                        getExerciseRelativeTime(targetWord.dateAdded, "Added just now")
                                                    }
                                                    val relativeExercised = remember(targetWord.reviewHistory) {
                                                        val lastMark = targetWord.reviewHistory.lastOrNull()
                                                        if (lastMark != null) {
                                                            getExerciseRelativeTime(lastMark.timestamp, "Just practiced")
                                                        } else {
                                                            "Never"
                                                        }
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            if (targetWord.reviewHistory.isEmpty()) {
                                                                Icon(Icons.Default.Cancel, contentDescription = "Unlearned", tint = Color.Gray, modifier = Modifier.size(13.dp))
                                                                Spacer(modifier = Modifier.width(2.dp))
                                                                Text("No logs", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = Color.Gray)
                                                            } else {
                                                                targetWord.reviewHistory.takeLast(8).forEach { mark ->
                                                                    Icon(
                                                                        imageVector = if (mark.status == "Remembered") Icons.Default.Check else Icons.Default.Close,
                                                                        contentDescription = mark.status,
                                                                        tint = if (mark.status == "Remembered") Color(0xFF4CAF50) else Color(0xFFF44336),
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        
                                                        Spacer(modifier = Modifier.width(10.dp))

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.CalendarToday, contentDescription = "Added date", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(relativeAdded, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = Color.Gray)
                                                        }

                                                        Spacer(modifier = Modifier.width(10.dp))

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.HourglassEmpty, contentDescription = "Exercised date", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(relativeExercised, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = Color.Gray)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom answer block
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val currentAnswer = sessionAnswers[word.id]
                            val isForgottenSelected = currentAnswer == "Forgotten"
                            val isRememberedSelected = currentAnswer == "Remembered"
                            val isAnySelected = currentAnswer != null

                            val forgottenBg = when {
                                isForgottenSelected -> Color(0xFFF44336) // Solid active red
                                isAnySelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) // Muted inactive
                                else -> Color(0xFFF44336).copy(alpha = 0.12f) // Light red default
                            }
                            val forgottenContent = when {
                                isForgottenSelected -> Color.White
                                isAnySelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else -> Color(0xFFD32F2F)
                            }
                            val forgottenBorder = if (isForgottenSelected) null else BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.2f))

                            val rememberedBg = when {
                                isRememberedSelected -> Color(0xFF4CAF50) // Solid active green
                                isAnySelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) // Muted inactive
                                else -> Color(0xFF4CAF50).copy(alpha = 0.12f) // Light green default
                            }
                            val rememberedContent = when {
                                isRememberedSelected -> Color.White
                                isAnySelected -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else -> Color(0xFF2E7D32)
                            }
                            val rememberedBorder = if (isRememberedSelected) null else BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.2f))

                            // Remembered & Forgotten Rating Buttons (Always shown!)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(55.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        viewModel.submitCardMark("Forgotten") 
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("forgotten_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = forgottenBg,
                                        contentColor = forgottenContent
                                    ),
                                    border = forgottenBorder,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = forgottenContent)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Forgotten", fontWeight = FontWeight.Bold, color = forgottenContent)
                                }

                                Button(
                                    onClick = { 
                                        viewModel.submitCardMark("Remembered") 
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag("remembered_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = rememberedBg,
                                        contentColor = rememberedContent
                                    ),
                                    border = rememberedBorder,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = rememberedContent)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Remembered", fontWeight = FontWeight.Bold, color = rememberedContent)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { 
                                    viewModel.toggleMeaningRevealed() 
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(55.dp)
                                    .testTag("tap_to_reveal_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMeaningRevealed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (isMeaningRevealed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMeaningRevealed) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isMeaningRevealed) "Tap to Hide Meaning" else "Tap to Reveal Meaning",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Overall final submit validation button at the end (Only shown when ALL answered!)
                            val hasAllAnswers = remember(sessionAnswers, totalInSession) {
                                sessionAnswers.size == totalInSession
                            }

                            if (hasAllAnswers) {
                                Button(
                                    onClick = {
                                        viewModel.completeActiveSession()
                                        Toast.makeText(context, "Exercise submitted successfully!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("complete_exercise_button_submit"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                    )
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Complete Exercise", fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                } else {
                    // Empty active session fallback
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No words in this batch setup.")
                            Button(onClick = { viewModel.endSessionEarly() }) {
                                Text("Go Back")
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE OR EDIT CONFIGURATION DIALOG
    if (showConfigDialog) {
        var name by remember { mutableStateOf(configToEdit?.name ?: "") }
        var wordCount by remember { mutableStateOf(configToEdit?.wordCount?.toString() ?: "5") }
        var mode by remember { mutableStateOf(configToEdit?.mode ?: "Daily") }
        
        var delayedRecapEnabled by remember { mutableStateOf(configToEdit?.delayedRecapEnabled ?: false) }
        var delayedRecapHours by remember { mutableStateOf(configToEdit?.delayedRecapHours?.toString() ?: "12") }
        var notificationEnabled by remember { mutableStateOf(configToEdit?.notificationEnabled ?: true) }
        
        var scheduleHour by remember { mutableStateOf(configToEdit?.scheduleHour?.toString() ?: "10") }
        var scheduleMinute by remember { mutableStateOf(configToEdit?.scheduleMinute?.toString() ?: "0") }

        // Weekly and Monthly dynamic fields
        var selectedDaysOfWeek by remember {
            mutableStateOf(configToEdit?.getEffectiveDaysOfWeek()?.toSet() ?: setOf(java.util.Calendar.MONDAY))
        }
        var dayOfMonth by remember { mutableStateOf(configToEdit?.dayOfMonth ?: 1) }

        // Review-specific multi-select scope options
        var selectedScopes by remember {
            mutableStateOf(
                configToEdit?.getEffectiveReviewScopes()?.toSet()
                    ?: setOf("Learning", "Fully learned", "Troublesome words")
            )
        }
        var reviewAlgorithm by remember { mutableStateOf(configToEdit?.reviewAlgorithm ?: "Least practiced first") }

        var modeExpanded by remember { mutableStateOf(false) }
        var algorithmExpanded by remember { mutableStateOf(false) }

        val showTimePicker = {
            android.app.TimePickerDialog(
                context,
                { _, pickedHour, pickedMinute ->
                    scheduleHour = pickedHour.toString()
                    scheduleMinute = pickedMinute.toString()
                },
                scheduleHour.toIntOrNull() ?: 10,
                scheduleMinute.toIntOrNull() ?: 0,
                true // 24-hour mode
            ).show()
        }

        val daysList = remember {
            listOf(
                java.util.Calendar.MONDAY to "Mon",
                java.util.Calendar.TUESDAY to "Tue",
                java.util.Calendar.WEDNESDAY to "Wed",
                java.util.Calendar.THURSDAY to "Thu",
                java.util.Calendar.FRIDAY to "Fri",
                java.util.Calendar.SATURDAY to "Sat",
                java.util.Calendar.SUNDAY to "Sun"
            )
        }

        Dialog(onDismissRequest = { showConfigDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (configToEdit == null) "Create Exercise" else "Modify Exercise",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Exercise Name") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Word Count Config
                    OutlinedTextField(
                        value = wordCount,
                        onValueChange = { wordCount = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Word List Limit (Count)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Mode selections dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = mode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Interval Recurrence Mode") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { modeExpanded = !modeExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = modeExpanded,
                            onDismissRequest = { modeExpanded = false }
                        ) {
                            listOf("Daily", "Weekly", "Monthly").forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        mode = opt
                                        modeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // WEEKLY Multi-select Day list selector
                    if (mode == "Weekly") {
                        val selectedDaysStr = daysList.filter { selectedDaysOfWeek.contains(it.first) }.joinToString(", ") { it.second }
                        Text(
                            text = "Select Recurrence Days of Week: ($selectedDaysStr)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            daysList.forEach { (calDay, desc) ->
                                val isSel = selectedDaysOfWeek.contains(calDay)
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            selectedDaysOfWeek = if (isSel) {
                                                if (selectedDaysOfWeek.size > 1) selectedDaysOfWeek - calDay else selectedDaysOfWeek
                                            } else {
                                                selectedDaysOfWeek + calDay
                                            }
                                        }
                                        .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = desc,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // MONTHLY Day list slider selector
                    if (mode == "Monthly") {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Select Recurrence Day of Month: $dayOfMonth",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = dayOfMonth.toFloat(),
                                onValueChange = { dayOfMonth = it.toInt().coerceIn(1, 28) },
                                valueRange = 1f..28f,
                                steps = 26,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Schedule Time (Native TimePickerDialog)
                    OutlinedButton(
                        onClick = showTimePicker,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Schedule Time: ${String.format("%02d:%02d", scheduleHour.toIntOrNull() ?: 10, scheduleMinute.toIntOrNull() ?: 0)} (Tap to Change)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Alerts switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Reminder Notifications", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = notificationEnabled,
                            onCheckedChange = { notificationEnabled = it }
                        )
                    }

                    // Learn vs Review Tab Conditional Configurations
                    if (selectedTab == 0) {
                        // LEARN configurations: Delayed Recap option
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Enable Additional Recap Exercise", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = delayedRecapEnabled,
                                onCheckedChange = { delayedRecapEnabled = it }
                            )
                        }

                        if (delayedRecapEnabled) {
                            OutlinedTextField(
                                value = delayedRecapHours,
                                onValueChange = { delayedRecapHours = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Delayed Recap Hours Offset") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // REVIEW configurations: Multi-select Scope & Algorithm options
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Review Scope:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val isDark = isSystemInDarkTheme()
                            val scopeChoices = remember(isDark) {
                                listOf(
                                    ScopeOption(
                                        key = "Troublesome words",
                                        label = "Troublesomes",
                                        icon = Icons.Default.Warning,
                                        color = if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
                                    ),
                                    ScopeOption(
                                        key = "Learning",
                                        label = "Learnings",
                                        icon = Icons.Default.AutoStories,
                                        color = if (isDark) Color(0xFF64B5F6) else Color(0xFF1976D2)
                                    ),
                                    ScopeOption(
                                        key = "Fully learned",
                                        label = "Fully learned",
                                        icon = Icons.Default.Stars,
                                        color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    )
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                scopeChoices.forEach { scope ->
                                    val isSelected = selectedScopes.contains(scope.key)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedScopes = if (isSelected) {
                                                if (selectedScopes.size > 1) selectedScopes - scope.key else selectedScopes
                                            } else {
                                                selectedScopes + scope.key
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = scope.label,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = scope.icon,
                                                contentDescription = null,
                                                tint = if (isSelected) scope.color else scope.color.copy(alpha = 0.55f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = scope.color.copy(alpha = 0.14f),
                                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                            selectedLeadingIconColor = scope.color,
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            selectedBorderColor = scope.color.copy(alpha = 0.55f),
                                            borderWidth = if (isSelected) 1.5.dp else 1.dp
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Review Algorithm Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = reviewAlgorithm,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Review Algorithm") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { algorithmExpanded = !algorithmExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenu(
                                expanded = algorithmExpanded,
                                onDismissRequest = { algorithmExpanded = false }
                            ) {
                                listOf("Random", "Least practiced first", "In order").forEach { opt ->
                                    DropdownMenuItem(
                                        text = { Text(opt) },
                                        onClick = {
                                            reviewAlgorithm = opt
                                            algorithmExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showConfigDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = Color.Gray)
                        }

                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    Toast.makeText(context, "Please input a configuration name", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val h = scheduleHour.toIntOrNull() ?: 10
                                val m = scheduleMinute.toIntOrNull() ?: 0
                                val cnt = wordCount.toIntOrNull() ?: 5
                                val rHours = delayedRecapHours.toIntOrNull() ?: 12

                                val newConfig = ExerciseConfig(
                                    id = configToEdit?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    type = if (selectedTab == 0) "Learn" else "Review",
                                    mode = mode,
                                    wordCount = cnt,
                                    delayedRecapEnabled = delayedRecapEnabled,
                                    delayedRecapHours = rHours,
                                    notificationEnabled = notificationEnabled,
                                    scheduleHour = h,
                                    scheduleMinute = m,
                                    dayOfWeek = selectedDaysOfWeek.firstOrNull() ?: java.util.Calendar.MONDAY,
                                    daysOfWeek = selectedDaysOfWeek.sorted(),
                                    dayOfMonth = dayOfMonth,
                                    reviewScope = if (selectedScopes.size >= 3) "All previous words" else selectedScopes.joinToString(", "),
                                    reviewScopes = selectedScopes.toList(),
                                    sortByLastFlagged = false,
                                    reviewAlgorithm = reviewAlgorithm
                                )

                                if (configToEdit == null) {
                                    viewModel.addExerciseConfig(newConfig)
                                    Toast.makeText(context, "Exercise created", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.updateExerciseConfig(newConfig)
                                    Toast.makeText(context, "Exercise updated", Toast.LENGTH_SHORT).show()
                                }
                                showConfigDialog = false
                            },
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (configToDelete != null) {
        val config = configToDelete!!
        AlertDialog(
            onDismissRequest = { configToDelete = null },
            title = { Text("Delete Exercise") },
            text = { Text("Are you sure you want to delete the exercise configuration '${config.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExerciseConfig(config.id)
                        Toast.makeText(context, "Exercise configuration deleted", Toast.LENGTH_SHORT).show()
                        configToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { configToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private val LightGrayCircleColor = Color(0xFFE0E0E0)
