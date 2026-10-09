package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WordDatabase
import com.example.data.model.ReviewMark
import com.example.data.model.Word
import com.example.data.model.ExerciseConfig
import com.example.data.model.OngoingSessionState
import com.example.data.model.OverdueSessionItem
import com.example.data.repository.WordRepository
import com.example.util.NotificationScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.Calendar
import java.util.UUID

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PracticedWordEntry(
    val english: String,
    val farsiMeaning: String,
    val status: String // "Remembered" or "Forgotten"
)

@JsonClass(generateAdapter = true)
data class TrailLogEntry(
    val timestamp: Long,
    val sessionType: String, // "Learning" or "Review" or "Recap"
    val totalWords: Int,
    val passedCount: Int,
    val wordsPracticed: List<PracticedWordEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExerciseSettings(
    val dailyGoalCount: Int = 5,
    val sessionHour: Int = 10,
    val sessionMinute: Int = 0,
    val recapEnabled: Boolean = true,
    val recapDelayHours: Int = 12,
    val mainNotificationsEnabled: Boolean = true,
    val recapNotificationsEnabled: Boolean = true
)

@JsonClass(generateAdapter = true)
data class VocabVaultBackup(
    val words: List<Word>,
    val customExercises: List<ExerciseConfig>,
    val exerciseSettings: ExerciseSettings,
    val trailLog: List<TrailLogEntry>,
    val lastRuns: Map<String, Long> = emptyMap()
)

class VocabViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WordRepository
    private val sharedPrefs = application.getSharedPreferences("vocab_vault_prefs", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // Database flow
    val allWords: StateFlow<List<Word>>

    // Live state inputs
    val searchQuery = MutableStateFlow("")
    val filterMastery = MutableStateFlow("All") // "All", "Unlearned", "Troublesome words", "Learning", "Fully learned"
    val sortBy = MutableStateFlow(sharedPrefs.getString("sort_by", "Date Added") ?: "Date Added") // "A-Z", "Date Added", "Forgotten Marks", "Custom Order"
    val multiSelectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val searchResults: StateFlow<List<Word>>

    // Examples Cache
    val examplesCache = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    val loadingExamplesWordId = MutableStateFlow<Long?>(null)

    // Online Auto-Translation UI Status
    val autoTranslationState = MutableStateFlow<AutoTranslateState>(AutoTranslateState.Idle)

    sealed interface AutoTranslateState {
        object Idle : AutoTranslateState
        object Loading : AutoTranslateState
        data class Success(val synonymsMeaning: String, val farsiMeaning: String) : AutoTranslateState
        data class Error(val message: String) : AutoTranslateState
    }

    // Custom exercise setups
    val customExercises = MutableStateFlow<List<ExerciseConfig>>(emptyList())
    val nextScheduledExercise = MutableStateFlow<ExerciseConfig?>(null)
    val isNextExerciseRecap = MutableStateFlow(false)
    val hasOngoingSessionFlow = MutableStateFlow(false)
    val ignoredSessions = MutableStateFlow<Set<String>>(emptySet())

    // Active Exercise State
    val activeSessionConfigId = MutableStateFlow<String?>(null)
    val activeSessionConfigName = MutableStateFlow("")
    val activeSessionWords = MutableStateFlow<List<Word>>(emptyList())
    val currentWordIndex = MutableStateFlow(0)
    val isMeaningRevealed = MutableStateFlow(false)
    val isSessionActive = MutableStateFlow(false)
    val sessionAnswers = MutableStateFlow<Map<Long, String>>(emptyMap()) // WordId to "Remembered" or "Forgotten"
    val activeSessionType = MutableStateFlow("") // "Learn" or "Review"

    // Settings
    val exerciseSettings = MutableStateFlow(ExerciseSettings())
    val fullyLearnedThreshold = MutableStateFlow(sharedPrefs.getInt("fully_learned_threshold", 4))
    val troublesomeThreshold = MutableStateFlow(sharedPrefs.getInt("troublesome_threshold", 1))

    // Historical Logs
    val trailLog = MutableStateFlow<List<TrailLogEntry>>(emptyList())

    // Live Session Countdown (millisecond ticks)
    val nextSessionCountdown = MutableStateFlow<Long?>(null)
    private var countdownJob: Job? = null
    private var isTransitioningToNextWord = false

    init {
        Word.fullyLearnedThreshold = fullyLearnedThreshold.value
        Word.troublesomeThreshold = troublesomeThreshold.value
        val database = WordDatabase.getDatabase(application)
        repository = WordRepository(database.wordDao())

        allWords = repository.allWords.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Combine for Search, Filter, Sort
        searchResults = combine(allWords, searchQuery, filterMastery, sortBy) { list, query, filter, sort ->
            var result = list

            // 1. Search Query
            if (query.isNotBlank()) {
                result = result.filter {
                    it.english.contains(query, ignoreCase = true) ||
                    it.synonymsMeaning.contains(query, ignoreCase = true) ||
                    it.farsiMeaning.contains(query, ignoreCase = true)
                }
            }

            // 2. Filter
            if (filter != "All") {
                result = result.filter { it.masteryLevel == filter }
            }

            // 3. Sort
            result = sortWords(result, sort)

            result
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        loadSettings()
        loadCustomExercises()
        loadTrailLogs()
        loadOngoingSessionState()
        startCountdownTimer()

        // Persist sortBy options
        viewModelScope.launch {
            sortBy.collect { option ->
                sharedPrefs.edit().putString("sort_by", option).apply()
            }
        }
    }

    fun sortWords(list: List<Word>, sortOption: String): List<Word> {
        return when (sortOption) {
            "A-Z" -> list.sortedBy { it.english.lowercase() }
            "Forgotten Marks" -> list.sortedByDescending { it.forgottenCount }
            "Date Practiced" -> list.sortedByDescending { it.reviewHistory.lastOrNull()?.timestamp ?: 0L }
            "Custom Order" -> list.sortedBy { it.customOrder }
            "Random" -> list.shuffled(java.security.SecureRandom())
            else -> list.sortedBy { it.dateAdded }
        }
    }

    fun updateCustomPositions(newOrderedList: List<Word>) {
        viewModelScope.launch {
            val updatedWords = newOrderedList.mapIndexed { index, word ->
                word.copy(customOrder = index.toLong())
            }
            repository.insertWords(updatedWords)
        }
    }

    // --- Data Management Methods ---

    val duplicatePromptState = MutableStateFlow<DuplicatePrompt?>(null)

    fun insertWord(english: String, meaning: String, farsi: String, onResolveFinish: () -> Unit = {}) {
        val word = Word(
            english = english.trim(),
            synonymsMeaning = meaning.trim(),
            farsiMeaning = farsi.trim()
        )
        checkAndInsertWords(listOf(word)) {
            onResolveFinish()
        }
    }

    fun checkAndInsertWords(words: List<Word>, onFinish: (insertedCount: Int) -> Unit = {}) {
        viewModelScope.launch {
            val duplicatesQueue = mutableListOf<Pair<Word, Word>>() // Pair of (newWord, existingWord)
            val uniqueToInsert = mutableListOf<Word>()
            val dbWords = repository.getAllWordsList()

            for (newWord in words) {
                val match = dbWords.find { it.english.trim().lowercase() == newWord.english.trim().lowercase() }
                if (match != null) {
                    duplicatesQueue.add(newWord to match)
                } else {
                    uniqueToInsert.add(newWord)
                }
            }

            if (uniqueToInsert.isNotEmpty()) {
                repository.insertWords(uniqueToInsert)
            }

            var insertedCount = uniqueToInsert.size

            if (duplicatesQueue.isNotEmpty()) {
                processNextDuplicate(duplicatesQueue, insertedCount, onFinish)
            } else {
                onFinish(insertedCount)
            }
        }
    }

    private fun processNextDuplicate(
        queue: List<Pair<Word, Word>>,
        currentInsertedCount: Int,
        onFinish: (insertedCount: Int) -> Unit
    ) {
        if (queue.isEmpty()) {
            duplicatePromptState.value = null
            onFinish(currentInsertedCount)
            return
        }

        val next = queue.first()
        val remaining = queue.drop(1)

        duplicatePromptState.value = DuplicatePrompt(
            newWord = next.first,
            existingWord = next.second,
            onResolve = { replace ->
                viewModelScope.launch {
                    var newCount = currentInsertedCount
                    if (replace) {
                        val updated = next.second.copy(
                            synonymsMeaning = next.first.synonymsMeaning,
                            farsiMeaning = next.first.farsiMeaning,
                            usageExamples = next.first.usageExamples.ifEmpty { next.second.usageExamples }
                        )
                        repository.insertWord(updated)
                        newCount++
                    }
                    processNextDuplicate(remaining, newCount, onFinish)
                }
            }
        )
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            repository.deleteWord(word)
        }
    }

    fun updateWord(word: Word) {
        viewModelScope.launch {
            repository.insertWord(word)
        }
    }

    fun deleteSelectedWords() {
        viewModelScope.launch {
            val ids = multiSelectedIds.value.toList()
            repository.deleteWordsByIds(ids)
            multiSelectedIds.value = emptySet()
        }
    }

    fun toggleSelection(wordId: Long) {
        val current = multiSelectedIds.value.toMutableSet()
        if (current.contains(wordId)) {
            current.remove(wordId)
        } else {
            current.add(wordId)
        }
        multiSelectedIds.value = current
    }

    fun clearSelection() {
        multiSelectedIds.value = emptySet()
    }

    // Direct Mock Seed for User Convenience if empty list
    fun seedSampleWordsIfEmpty() {
        viewModelScope.launch {
            val dbWords = repository.getAllWordsList()
            if (dbWords.isEmpty()) {
                val samples = listOf(
                    Word(english = "Ubiquitous", synonymsMeaning = "Present, appearing, or found everywhere.", farsiMeaning = "همه جا حاضر، شایع"),
                    Word(english = "Capricious", synonymsMeaning = "Given to sudden and unaccountable changes of mood or behavior.", farsiMeaning = "هوس باز، دمدمی مزاج"),
                    Word(english = "Ebullient", synonymsMeaning = "Cheerful and full of energy.", farsiMeaning = "پرانرژی، جوشان"),
                    Word(english = "Reticent", synonymsMeaning = "Not revealing one's thoughts or feelings readily.", farsiMeaning = "کم حرف، تودار"),
                    Word(english = "Obsequious", synonymsMeaning = "Obedient or attentive to an excessive or servile degree.", farsiMeaning = "چاپلوس، بادمجان دور قاب چین")
                )
                repository.insertWords(samples)
            }
        }
    }

    // --- Online Auto-Translation fetcher ---

    fun fetchAutoTranslation(englishWord: String) {
        if (englishWord.isBlank()) return
        autoTranslationState.value = AutoTranslateState.Loading
        viewModelScope.launch {
            val result = repository.fetchAutoTranslation(englishWord)
            if (result != null) {
                autoTranslationState.value = AutoTranslateState.Success(result.first, result.second)
            } else {
                autoTranslationState.value = AutoTranslateState.Error("Could not fetch translation. Please input manually.")
            }
        }
    }

    fun resetAutoTranslationState() {
        autoTranslationState.value = AutoTranslateState.Idle
    }

    // --- On-Demand Examples Flipped Card action ---

    fun showExamples(word: Word, onFailure: ((String) -> Unit)? = null) {
        val currentCache = examplesCache.value
        if (currentCache.containsKey(word.id)) return // Already loaded
        
        if (word.usageExamples.isNotEmpty()) {
            examplesCache.value = currentCache + (word.id to word.usageExamples)
            return
        }
        
        loadingExamplesWordId.value = word.id
        viewModelScope.launch {
            val fetched = repository.fetchContextExamples(word.english)
            loadingExamplesWordId.value = null
            
            if (fetched.isEmpty()) {
                onFailure?.invoke("Failed to fetch usage examples. Please try again.")
            } else {
                examplesCache.value = currentCache + (word.id to fetched)
                val updatedWord = word.copy(usageExamples = fetched)
                repository.insertWord(updatedWord)
            }
        }
    }

    // --- Import Parsing Logic ---

    fun importFromCsvStream(inputStream: InputStream, onSuccess: (count: Int) -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            try {
                val words = repository.parseCsvInputStream(inputStream)
                if (words.isNotEmpty()) {
                    checkAndInsertWords(words) { insertedCount ->
                        onSuccess(insertedCount)
                    }
                } else {
                    onFailure()
                }
            } catch (e: Exception) {
                onFailure()
            }
        }
    }

    fun importCsvText(text: String, onSuccess: (count: Int) -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            try {
                val inputStream = text.byteInputStream()
                val words = repository.parseCsvInputStream(inputStream)
                if (words.isNotEmpty()) {
                    checkAndInsertWords(words) { insertedCount ->
                        onSuccess(insertedCount)
                    }
                } else {
                    onFailure()
                }
            } catch (e: Exception) {
                onFailure()
            }
        }
    }

    // --- Settings Synchronization System ---

    fun saveSettings(settings: ExerciseSettings) {
        exerciseSettings.value = settings
        sharedPrefs.edit().apply {
            putInt("exercise_goal", settings.dailyGoalCount)
            putInt("exercise_hour", settings.sessionHour)
            putInt("exercise_minute", settings.sessionMinute)
            putBoolean("recap_enabled", settings.recapEnabled)
            putInt("recap_delay_hours", settings.recapDelayHours)
            putBoolean("exercise_notifications", settings.mainNotificationsEnabled)
            putBoolean("recap_notifications", settings.recapNotificationsEnabled)
            apply()
        }
        // Reschedule notifications based on changes
        NotificationScheduler.scheduleAlarms(getApplication())
    }

    fun updateMasterySettings(fullyLearned: Int, troublesome: Int) {
        fullyLearnedThreshold.value = fullyLearned
        troublesomeThreshold.value = troublesome
        Word.fullyLearnedThreshold = fullyLearned
        Word.troublesomeThreshold = troublesome
        sharedPrefs.edit().apply {
            putInt("fully_learned_threshold", fullyLearned)
            putInt("troublesome_threshold", troublesome)
            apply()
        }
    }

    private fun loadSettings() {
        val dailyGoal = sharedPrefs.getInt("exercise_goal", 5)
        val hour = sharedPrefs.getInt("exercise_hour", 10)
        val minute = sharedPrefs.getInt("exercise_minute", 0)
        val rEnabled = sharedPrefs.getBoolean("recap_enabled", true)
        val rDelay = sharedPrefs.getInt("recap_delay_hours", 12)
        val mainNotif = sharedPrefs.getBoolean("exercise_notifications", true)
        val recapNotif = sharedPrefs.getBoolean("recap_notifications", true)

        exerciseSettings.value = ExerciseSettings(
            dailyGoalCount = dailyGoal,
            sessionHour = hour,
            sessionMinute = minute,
            recapEnabled = rEnabled,
            recapDelayHours = rDelay,
            mainNotificationsEnabled = mainNotif,
            recapNotificationsEnabled = recapNotif
        )
    }

    // --- Trail Log Manager ---

    private fun loadTrailLogs() {
        try {
            val logsJson = sharedPrefs.getString("trail_logs", "[]") ?: "[]"
            val type = Types.newParameterizedType(List::class.java, TrailLogEntry::class.java)
            val adapter = moshi.adapter<List<TrailLogEntry>>(type)
            trailLog.value = adapter.fromJson(logsJson) ?: emptyList()
        } catch (e: Exception) {
            trailLog.value = emptyList()
        }
    }

    private fun saveTrailLogEntry(entry: TrailLogEntry) {
        val newList = listOf(entry) + trailLog.value
        trailLog.value = newList
        try {
            val type = Types.newParameterizedType(List::class.java, TrailLogEntry::class.java)
            val adapter = moshi.adapter<List<TrailLogEntry>>(type)
            val json = adapter.toJson(newList)
            sharedPrefs.edit().putString("trail_logs", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Custom Exercises CRUD & Persistence ---

    fun saveCustomExercises(list: List<ExerciseConfig>) {
        customExercises.value = list
        try {
            val type = Types.newParameterizedType(List::class.java, ExerciseConfig::class.java)
            val adapter = moshi.adapter<List<ExerciseConfig>>(type)
            val json = adapter.toJson(list)
            sharedPrefs.edit().putString("custom_exercises", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        NotificationScheduler.scheduleAlarms(getApplication())
        calculateCountdown()
    }

    fun loadCustomExercises() {
        try {
            val json = sharedPrefs.getString("custom_exercises", null)
            if (json != null) {
                val type = Types.newParameterizedType(List::class.java, ExerciseConfig::class.java)
                val adapter = moshi.adapter<List<ExerciseConfig>>(type)
                val list = adapter.fromJson(json) ?: emptyList()
                customExercises.value = list
            } else {
                // Seed default configurations
                val defaultList = listOf(
                    ExerciseConfig(
                        name = "Core Learn Session",
                        type = "Learn",
                        mode = "Daily",
                        wordCount = 5,
                        delayedRecapEnabled = true,
                        delayedRecapHours = 12,
                        notificationEnabled = true,
                        scheduleHour = 10,
                        scheduleMinute = 0
                    )
                )
                saveCustomExercises(defaultList)
            }
        } catch (e: Exception) {
            customExercises.value = emptyList()
        }
    }

    fun getConfigCreatedAt(configId: String): Long {
        return sharedPrefs.getLong("config_created_at_$configId", 1L)
    }

    fun addExerciseConfig(config: ExerciseConfig) {
        val current = customExercises.value.toMutableList()
        current.add(config)
        sharedPrefs.edit().putLong("config_created_at_${config.id}", System.currentTimeMillis()).apply()
        saveCustomExercises(current)
    }

    fun updateExerciseConfig(config: ExerciseConfig) {
        val current = customExercises.value.map { if (it.id == config.id) config else it }
        sharedPrefs.edit().putLong("config_created_at_${config.id}", System.currentTimeMillis()).apply()
        sharedPrefs.edit().putLong("last_run_${config.id}", 1L).apply()
        saveCustomExercises(current)
    }

    fun deleteExerciseConfig(id: String) {
        val current = customExercises.value.filter { it.id != id }
        saveCustomExercises(current)
    }

    // --- Ongoing Session State Persister ---

    fun saveOngoingSessionState() {
        if (!isSessionActive.value) {
            sharedPrefs.edit().remove("ongoing_session_state").apply()
            hasOngoingSessionFlow.value = false
            return
        }
        val state = OngoingSessionState(
            exerciseId = activeSessionConfigId.value ?: "",
            exerciseName = activeSessionConfigName.value,
            sessionType = activeSessionType.value,
            wordIds = activeSessionWords.value.map { it.id },
            currentIndex = currentWordIndex.value,
            isRevealed = isMeaningRevealed.value,
            answers = sessionAnswers.value
        )
        try {
            val json = moshi.adapter(OngoingSessionState::class.java).toJson(state)
            sharedPrefs.edit().putString("ongoing_session_state", json).apply()
            hasOngoingSessionFlow.value = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadOngoingSessionState() {
        try {
            val json = sharedPrefs.getString("ongoing_session_state", null)
            if (json != null) {
                val state = moshi.adapter(OngoingSessionState::class.java).fromJson(json)
                if (state != null && state.wordIds.isNotEmpty()) {
                    viewModelScope.launch {
                        val dbWords = repository.getAllWordsList()
                        val words = dbWords.filter { it.id in state.wordIds }
                        val sortedWords = state.wordIds.mapNotNull { id -> words.find { it.id == id } }
                        if (sortedWords.isNotEmpty()) {
                            activeSessionConfigId.value = state.exerciseId
                            activeSessionConfigName.value = state.exerciseName
                            activeSessionType.value = state.sessionType
                            activeSessionWords.value = sortedWords
                            currentWordIndex.value = state.currentIndex
                            isMeaningRevealed.value = state.isRevealed
                            sessionAnswers.value = state.answers
                            hasOngoingSessionFlow.value = true
                        }
                    }
                } else {
                    hasOngoingSessionFlow.value = false
                }
            } else {
                hasOngoingSessionFlow.value = false
            }
        } catch (e: Exception) {
            hasOngoingSessionFlow.value = false
        }
    }

    fun clearOngoingSessionState() {
        sharedPrefs.edit().remove("ongoing_session_state").apply()
        hasOngoingSessionFlow.value = false
    }

    fun resumeOngoingSession() {
        if (hasOngoingSessionFlow.value) {
            isSessionActive.value = true
        }
    }

    // --- Custom Spaced Repetition (SRS) Engine & Sessions ---

    fun startExerciseByConfig(config: ExerciseConfig) {
        viewModelScope.launch {
            val words = allWords.value
            activeSessionConfigId.value = config.id
            activeSessionConfigName.value = config.name
            activeSessionType.value = config.type
            isMeaningRevealed.value = false
            currentWordIndex.value = 0
            sessionAnswers.value = emptyMap()

            val selectedBatch = when (config.type) {
                "Learn" -> {
                    val unlearned = words.filter { it.reviewHistory.isEmpty() }
                    sortWords(unlearned, sortBy.value).take(config.wordCount)
                }
                "Review" -> {
                    val hasHistory = words.filter { it.reviewHistory.isNotEmpty() }
                    val effectiveScopes = config.getEffectiveReviewScopes()
                    val scoped = hasHistory.filter { it.masteryLevel in effectiveScopes }
                    
                    val rng = java.security.SecureRandom()
                    val isRandom = config.reviewAlgorithm.trim().equals("Random", ignoreCase = true) ||
                                   config.reviewAlgorithm.contains("random", ignoreCase = true)
                    val isLeastPracticed = config.reviewAlgorithm.trim().equals("Least practiced first", ignoreCase = true) ||
                                           config.reviewAlgorithm.contains("least", ignoreCase = true)
                    
                    when {
                        isRandom -> scoped.shuffled(rng).take(config.wordCount)
                        isLeastPracticed -> scoped.shuffled(rng).sortedBy { it.totalReviews }.take(config.wordCount)
                        else -> sortWords(scoped, sortBy.value).take(config.wordCount) // "In order" / "In order (Dictionary sort)"
                    }
                }
                else -> emptyList()
            }

            activeSessionWords.value = selectedBatch
            isSessionActive.value = selectedBatch.isNotEmpty()
            saveOngoingSessionState()
        }
    }

    fun startRecapSession(config: ExerciseConfig) {
        viewModelScope.launch {
            activeSessionConfigId.value = config.id
            activeSessionConfigName.value = config.name
            activeSessionType.value = "Recap"
            isMeaningRevealed.value = false
            currentWordIndex.value = 0
            sessionAnswers.value = emptyMap()

            val wordIdsJson = sharedPrefs.getString("last_learned_word_ids_${config.id}", null)
            val selectedBatch = if (wordIdsJson != null) {
                try {
                    val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, java.lang.Long::class.java)
                    val adapter = moshi.adapter<List<Long>>(type)
                    val ids = adapter.fromJson(wordIdsJson) ?: emptyList()
                    val allWordsList = repository.getAllWordsList()
                    ids.mapNotNull { id -> allWordsList.find { it.id == id } }
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val finalBatch = if (selectedBatch.isNotEmpty()) {
                selectedBatch.shuffled(java.security.SecureRandom())
            } else {
                val wordsSorted = allWords.value.filter { it.reviewHistory.isNotEmpty() }
                    .shuffled(java.security.SecureRandom())
                wordsSorted.take(config.wordCount)
            }

            activeSessionWords.value = finalBatch
            isSessionActive.value = finalBatch.isNotEmpty()
            saveOngoingSessionState()
        }
    }

    fun isRecapPendingForConfig(config: ExerciseConfig): Boolean {
        if (config.type != "Learn" || !config.delayedRecapEnabled) return false
        val lastRun = getLastRunTimestamp(config.id)
        if (lastRun <= 1L) return false
        val recapLastRun = sharedPrefs.getLong("recap_last_run_${config.id}", 0L)
        if (recapLastRun >= lastRun) return false
        val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
        return System.currentTimeMillis() >= recapScheduledTime
    }

    fun isRecapUpcomingForConfig(config: ExerciseConfig): Boolean {
        if (config.type != "Learn" || !config.delayedRecapEnabled) return false
        val lastRun = getLastRunTimestamp(config.id)
        if (lastRun <= 1L) return false
        val recapLastRun = sharedPrefs.getLong("recap_last_run_${config.id}", 0L)
        if (recapLastRun >= lastRun) return false
        val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
        return System.currentTimeMillis() < recapScheduledTime
    }

    fun getRecapScheduledTime(config: ExerciseConfig): Long {
        val lastRun = getLastRunTimestamp(config.id)
        return if (lastRun > 1L) lastRun + config.delayedRecapHours * 60 * 60 * 1000L else 0L
    }

    fun isRecapOverdueForConfig(config: ExerciseConfig): Boolean {
        if (config.type != "Learn" || !config.delayedRecapEnabled) return false
        val lastRun = getLastRunTimestamp(config.id)
        if (lastRun <= 1L) return false
        val recapLastRun = getRecapLastRun(config.id)
        if (recapLastRun >= lastRun) return false
        val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
        val isPassed30Mins = System.currentTimeMillis() - recapScheduledTime > 30 * 60 * 1000L
        val isIgnored = isRecapSessionIgnored(config.id, recapScheduledTime)
        return isPassed30Mins && !isIgnored
    }

    fun isRecapActiveNowForConfig(config: ExerciseConfig): Boolean {
        if (config.type != "Learn" || !config.delayedRecapEnabled) return false
        val lastRun = getLastRunTimestamp(config.id)
        if (lastRun <= 1L) return false
        val recapLastRun = getRecapLastRun(config.id)
        if (recapLastRun >= lastRun) return false
        val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
        val diff = System.currentTimeMillis() - recapScheduledTime
        val isIgnored = isRecapSessionIgnored(config.id, recapScheduledTime)
        return diff >= 0L && diff <= 30 * 60 * 1000L && !isIgnored
    }

    fun getRecapLastRun(configId: String): Long {
        return sharedPrefs.getLong("recap_last_run_$configId", 0L)
    }

    fun ignoreRecapSession(configId: String, scheduledTime: Long) {
        val key = "recap_${configId}_$scheduledTime"
        ignoredSessions.value = ignoredSessions.value + key
        sharedPrefs.edit()
            .putLong("ignored_recap_${configId}_${scheduledTime}", scheduledTime)
            .putLong("recap_last_run_$configId", System.currentTimeMillis())
            .apply()
        calculateCountdown()
    }

    fun isRecapSessionIgnored(configId: String, scheduledTime: Long): Boolean {
        val key = "recap_${configId}_$scheduledTime"
        if (ignoredSessions.value.contains(key)) return true
        val persisted = sharedPrefs.getLong("ignored_recap_${configId}_${scheduledTime}", 0L) == scheduledTime
        if (persisted) {
            ignoredSessions.value = ignoredSessions.value + key
            return true
        }
        return false
    }

    fun getOverdueSessions(): List<OverdueSessionItem> {
        val list = mutableListOf<OverdueSessionItem>()
        val configs = customExercises.value
        val now = System.currentTimeMillis()

        for (config in configs) {
            val lastRun = getLastRunTimestamp(config.id)
            val createdAt = getConfigCreatedAt(config.id)
            val lastScheduled = getLastScheduledTime(config)
            val isOverdue = lastRun < lastScheduled && lastScheduled <= now && lastScheduled > createdAt
            val isPassed30Mins = now - lastScheduled > 30 * 60 * 1000L
            val isIgnored = isSessionIgnored(config.id, lastScheduled)
            if (isOverdue && isPassed30Mins && !isIgnored) {
                list.add(
                    OverdueSessionItem(
                        sessionKey = "std_${config.id}_$lastScheduled",
                        config = config,
                        isRecap = false,
                        scheduledTime = lastScheduled,
                        name = config.name,
                        type = config.type
                    )
                )
            }

            if (config.type == "Learn" && config.delayedRecapEnabled) {
                if (lastRun > 1L) {
                    val recapLastRun = getRecapLastRun(config.id)
                    if (recapLastRun < lastRun) {
                        val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
                        val isRecapDue = now >= recapScheduledTime
                        val isRecapPassed30Mins = now - recapScheduledTime > 30 * 60 * 1000L
                        val isRecapIgnored = isRecapSessionIgnored(config.id, recapScheduledTime)
                        if (isRecapDue && isRecapPassed30Mins && !isRecapIgnored) {
                            list.add(
                                OverdueSessionItem(
                                    sessionKey = "recap_${config.id}_$recapScheduledTime",
                                    config = config,
                                    isRecap = true,
                                    scheduledTime = recapScheduledTime,
                                    name = "${config.name} Recap",
                                    type = "Recap"
                                )
                            )
                        }
                    }
                }
            }
        }

        // Return strictly in chronological order (earliest overdue first)
        return list.sortedBy { it.scheduledTime }
    }

    fun startSession(sessionType: String) {
        val config = customExercises.value.find { it.type == sessionType } ?: customExercises.value.firstOrNull()
        if (config != null) {
            startExerciseByConfig(config)
        } else {
            viewModelScope.launch {
                val words = allWords.value
                activeSessionType.value = sessionType
                isMeaningRevealed.value = false
                currentWordIndex.value = 0
                sessionAnswers.value = emptyMap()

                val filtered = words.filter { if (sessionType == "Learning") it.reviewHistory.isEmpty() else it.reviewHistory.isNotEmpty() }
                val selectedBatch = sortWords(filtered, sortBy.value).take(5)
                activeSessionWords.value = selectedBatch
                isSessionActive.value = selectedBatch.isNotEmpty()
                saveOngoingSessionState()
            }
        }
    }

    fun startSessionEarly(sessionType: String) {
        startSession(sessionType)
    }

    fun revealMeaning() {
        isMeaningRevealed.value = true
        saveOngoingSessionState()
    }

    fun toggleMeaningRevealed() {
        isMeaningRevealed.value = !isMeaningRevealed.value
        saveOngoingSessionState()
    }

    fun goToPreviousWord() {
        isTransitioningToNextWord = false
        val idx = currentWordIndex.value
        if (idx > 0) {
            currentWordIndex.value = idx - 1
            isMeaningRevealed.value = false
            saveOngoingSessionState()
        }
    }

    fun goToNextWord() {
        isTransitioningToNextWord = false
        val idx = currentWordIndex.value
        val list = activeSessionWords.value
        if (idx + 1 < list.size) {
            currentWordIndex.value = idx + 1
            isMeaningRevealed.value = false
            saveOngoingSessionState()
        }
    }

    fun setCurrentWordIndex(index: Int) {
        isTransitioningToNextWord = false
        val list = activeSessionWords.value
        if (index in list.indices) {
            currentWordIndex.value = index
            isMeaningRevealed.value = false
            saveOngoingSessionState()
        }
    }

    fun recordAnswerForCurrentWord(status: String) {
        val words = activeSessionWords.value
        val index = currentWordIndex.value
        if (index in words.indices) {
            val word = words[index]
            val updatedAnswers = sessionAnswers.value.toMutableMap()
            updatedAnswers[word.id] = status
            sessionAnswers.value = updatedAnswers
            saveOngoingSessionState()
        }
    }

    fun completeActiveSession() {
        val batch = activeSessionWords.value
        val answers = sessionAnswers.value
        val timestamp = System.currentTimeMillis()
        
        viewModelScope.launch {
            batch.forEach { word ->
                val status = answers[word.id] ?: "Remembered"
                val updatedHistory = word.reviewHistory + ReviewMark(timestamp, status)
                
                val totalMarkings = updatedHistory.size
                val forgottenCount = updatedHistory.count { it.status == "Forgotten" }
                val rememberedCount = updatedHistory.count { it.status == "Remembered" }
                val baseMillis = 12 * 60 * 60 * 1000L

                val nextInterval: Long = if (status == "Forgotten") {
                    5 * 60 * 1000L
                } else {
                    var consecutiveCorrect = 0
                    for (mark in updatedHistory.asReversed()) {
                        if (mark.status == "Remembered") consecutiveCorrect++ else break
                    }
                    val growthFactor = Math.pow(2.0, consecutiveCorrect.toDouble()).coerceAtMost(30.0)
                    val ratioFactor = if (totalMarkings > 0) {
                        rememberedCount.toDouble() / totalMarkings.toDouble()
                    } else {
                        1.0
                    }.coerceIn(0.1, 1.0)
                    (baseMillis * growthFactor * ratioFactor).toLong()
                }

                val scheduledNext = timestamp + nextInterval
                val updatedWord = word.copy(
                    reviewHistory = updatedHistory,
                    nextReviewTimestamp = scheduledNext
                )
                repository.insertWord(updatedWord)
            }

            val passed = batch.count { answers[it.id] == "Remembered" }
            val entry = TrailLogEntry(
                timestamp = timestamp,
                sessionType = activeSessionType.value,
                totalWords = batch.size,
                passedCount = passed,
                wordsPracticed = batch.map { word ->
                    PracticedWordEntry(
                        english = word.english,
                        farsiMeaning = word.farsiMeaning,
                        status = answers[word.id] ?: "Remembered"
                    )
                }
            )
            saveTrailLogEntry(entry)
            
            val configId = activeSessionConfigId.value
            if (configId != null) {
                if (activeSessionType.value == "Recap") {
                    sharedPrefs.edit().putLong("recap_last_run_$configId", timestamp).apply()
                } else {
                    sharedPrefs.edit().putLong("last_run_$configId", timestamp).apply()
                    if (activeSessionType.value == "Learn") {
                        try {
                            val wordIdsJson = moshi.adapter<List<Long>>(com.squareup.moshi.Types.newParameterizedType(List::class.java, java.lang.Long::class.java))
                                .toJson(batch.map { it.id })
                            sharedPrefs.edit().putString("last_learned_word_ids_$configId", wordIdsJson).apply()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            // Recalculate alarms immediately to schedule recaps or move normal schedules forward
            NotificationScheduler.scheduleAlarms(getApplication())

            isSessionActive.value = false
            clearOngoingSessionState()
            calculateCountdown()
        }
    }

    fun submitCardMark(status: String) {
        if (isTransitioningToNextWord) return
        recordAnswerForCurrentWord(status)
        val batch = activeSessionWords.value
        val index = currentWordIndex.value
        if (index + 1 < batch.size) {
            isTransitioningToNextWord = true
            viewModelScope.launch {
                delay(120) // stay for 120ms so user can see selection
                isMeaningRevealed.value = false
                currentWordIndex.value = index + 1
                saveOngoingSessionState()
                isTransitioningToNextWord = false
            }
        } else {
            saveOngoingSessionState()
        }
    }

    fun endSessionEarly() {
        saveOngoingSessionState()
        isSessionActive.value = false
    }

    fun cancelOngoingSession() {
        clearOngoingSessionState()
        isSessionActive.value = false
    }

    // --- Live Widget Session Clock ---

    private fun startCountdownTimer() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                calculateCountdown()
                delay(1000)
            }
        }
    }

    fun getLastRunTimestamp(configId: String): Long {
        if (!sharedPrefs.contains("last_run_$configId")) {
            sharedPrefs.edit().putLong("last_run_$configId", 1L).apply()
            return 1L
        }
        return sharedPrefs.getLong("last_run_$configId", 1L)
    }

    fun getLastScheduledTime(config: ExerciseConfig): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.scheduleHour)
            set(Calendar.MINUTE, config.scheduleMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        when (config.mode) {
            "Daily" -> {
                if (calendar.timeInMillis > System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                }
            }
            "Weekly" -> {
                val targetDays = config.getEffectiveDaysOfWeek()
                val lastTimes = targetDays.map { targetDay ->
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, config.scheduleHour)
                        set(Calendar.MINUTE, config.scheduleMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    while (cal.timeInMillis > System.currentTimeMillis() || cal.get(Calendar.DAY_OF_WEEK) != targetDay) {
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                    }
                    cal.timeInMillis
                }
                return lastTimes.maxOrNull() ?: calendar.timeInMillis
            }
            "Monthly" -> {
                val targetDay = config.dayOfMonth
                val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDay))
                if (calendar.timeInMillis > System.currentTimeMillis()) {
                    calendar.add(Calendar.MONTH, -1)
                    val prevMaxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(prevMaxDay))
                }
            }
        }
        return calendar.timeInMillis
    }

    fun ignoreMissedSession(configId: String, scheduledTime: Long) {
        val key = "${configId}_$scheduledTime"
        ignoredSessions.value = ignoredSessions.value + key
        sharedPrefs.edit().putLong("ignored_${configId}_${scheduledTime}", scheduledTime).apply()
        calculateCountdown()
    }

    fun isSessionIgnored(configId: String, scheduledTime: Long): Boolean {
        val key = "${configId}_$scheduledTime"
        if (ignoredSessions.value.contains(key)) return true
        val persisted = sharedPrefs.getLong("ignored_${configId}_${scheduledTime}", 0L) == scheduledTime
        if (persisted) {
            ignoredSessions.value = ignoredSessions.value + key
            return true
        }
        return false
    }

    fun getNextScheduledTime(config: ExerciseConfig, lastRunTimestamp: Long): Long {
        val now = System.currentTimeMillis()
        val baseTime = Math.max(now, lastRunTimestamp)
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.scheduleHour)
            set(Calendar.MINUTE, config.scheduleMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // Helper to check if a timestamp is on the same calendar period as our calendar instant
        fun isSamePeriodAsScheduled(timestamp: Long): Boolean {
            if (timestamp <= 1L) return false // Placeholder
            val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp }
            return when (config.mode) {
                "Daily" -> {
                    cal1.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
                }
                "Weekly" -> {
                    val targetDays = config.getEffectiveDaysOfWeek()
                    cal1.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                    cal1.get(Calendar.DAY_OF_WEEK) in targetDays
                }
                "Monthly" -> {
                    cal1.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    cal1.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
                }
                else -> false
            }
        }
        
        when (config.mode) {
            "Daily" -> {
                if (isSamePeriodAsScheduled(lastRunTimestamp)) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                while (calendar.timeInMillis <= baseTime) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "Weekly" -> {
                val targetDays = config.getEffectiveDaysOfWeek()
                val nextTimes = targetDays.map { targetDay ->
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = baseTime
                        set(Calendar.HOUR_OF_DAY, config.scheduleHour)
                        set(Calendar.MINUTE, config.scheduleMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val lastCal = if (lastRunTimestamp > 1L) Calendar.getInstance().apply { timeInMillis = lastRunTimestamp } else null
                    fun ranOnSameDay(c: Calendar): Boolean {
                        if (lastCal == null) return false
                        return lastCal.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
                               lastCal.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
                    }
                    while (cal.timeInMillis <= baseTime || cal.get(Calendar.DAY_OF_WEEK) != targetDay || ranOnSameDay(cal)) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    cal.timeInMillis
                }
                return nextTimes.minOrNull() ?: calendar.timeInMillis
            }
            "Monthly" -> {
                val targetDay = config.dayOfMonth
                val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDay))
                if (isSamePeriodAsScheduled(lastRunTimestamp)) {
                    calendar.add(Calendar.MONTH, 1)
                }
                while (calendar.timeInMillis <= baseTime) {
                    calendar.add(Calendar.MONTH, 1)
                    val nextMaxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(nextMaxDay))
                }
            }
        }
        return calendar.timeInMillis
    }

    private fun calculateCountdown() {
        val configs = customExercises.value
        if (configs.isEmpty()) {
            nextSessionCountdown.value = null
            nextScheduledExercise.value = null
            isNextExerciseRecap.value = false
            return
        }

        val now = System.currentTimeMillis()
        var closestTime = Long.MAX_VALUE
        var closestConfig: ExerciseConfig? = null
        var nextIsRecap = false

        for (config in configs) {
            val lastRun = getLastRunTimestamp(config.id)
            
            // Check for recap schedule first, if enabled and pending or upcoming
            if (config.type == "Learn" && config.delayedRecapEnabled) {
                if (lastRun > 1L) {
                    val recapLastRun = sharedPrefs.getLong("recap_last_run_${config.id}", 0L)
                    if (recapLastRun < lastRun) {
                        val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
                        val isRecapIgnored = isRecapSessionIgnored(config.id, recapScheduledTime)
                        val isOverdueRecap = now - recapScheduledTime > 30 * 60 * 1000L
                        // Only consider in countdown if not ignored and not overdue (>30 mins past)
                        if (!isRecapIgnored && !isOverdueRecap) {
                            if (recapScheduledTime < closestTime) {
                                closestTime = recapScheduledTime
                                closestConfig = config
                                nextIsRecap = true
                            }
                        }
                    }
                }
            }

            // Check standard scheduled next run or CURRENT active standard run (within 30-min window)
            val lastScheduled = getLastScheduledTime(config)
            val isActiveNow = lastRun < lastScheduled && 
                    !isSessionIgnored(config.id, lastScheduled) && 
                    now >= lastScheduled && 
                    (now - lastScheduled <= 30 * 60 * 1000L)
            
            val targetTimeForThis = if (isActiveNow) {
                lastScheduled
            } else {
                getNextScheduledTime(config, lastRun)
            }

            if (targetTimeForThis < closestTime) {
                closestTime = targetTimeForThis
                closestConfig = config
                nextIsRecap = false
            }
        }

        nextScheduledExercise.value = closestConfig
        isNextExerciseRecap.value = nextIsRecap
        if (closestConfig != null) {
            val delta = closestTime - System.currentTimeMillis()
            nextSessionCountdown.value = if (delta > 0) delta else 0L
        } else {
            nextSessionCountdown.value = null
        }
    }

    fun generateBackupJson(): String {
        val backupObj = VocabVaultBackup(
            words = allWords.value,
            customExercises = customExercises.value,
            exerciseSettings = exerciseSettings.value,
            trailLog = trailLog.value,
            lastRuns = customExercises.value.associate { it.id to getLastRunTimestamp(it.id) }
        )
        val adapter = moshi.adapter(VocabVaultBackup::class.java)
        return adapter.toJson(backupObj)
    }

    suspend fun restoreBackupFromJson(jsonString: String): Boolean {
        return try {
            val adapter = moshi.adapter(VocabVaultBackup::class.java)
            val backupObj = adapter.fromJson(jsonString) ?: return false
            
            // Restore Word Database: clear database and insert restored words
            repository.clearAll()
            repository.insertWords(backupObj.words)
            
            // Restore Custom Exercises
            saveCustomExercises(backupObj.customExercises)
            
            // Restore Settings
            saveSettings(backupObj.exerciseSettings)
            
            // Restore Trail Logs
            trailLog.value = backupObj.trailLog
            val type = Types.newParameterizedType(List::class.java, TrailLogEntry::class.java)
            val logAdapter = moshi.adapter<List<TrailLogEntry>>(type)
            sharedPrefs.edit().putString("trail_logs", logAdapter.toJson(backupObj.trailLog)).apply()
            
            // Restore Last Runs
            backupObj.lastRuns.forEach { (configId, timestamp) ->
                sharedPrefs.edit().putLong("last_run_$configId", timestamp).apply()
            }
            
            calculateCountdown()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}

data class DuplicatePrompt(
    val newWord: Word,
    val existingWord: Word,
    val onResolve: (replace: Boolean) -> Unit
)
