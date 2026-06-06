package com.example.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Word
import com.example.data.model.ReviewMark
import com.example.ui.viewmodel.VocabViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import java.io.InputStream
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

fun Modifier.scrollbar(
    state: LazyListState,
    color: Color
): Modifier = this.drawWithContent {
    drawContent()
    val layoutInfo = state.layoutInfo
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    if (visibleItemsInfo.isNotEmpty() && layoutInfo.totalItemsCount > 0) {
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = visibleItemsInfo.size
        
        if (visibleItems < totalItems) {
            val firstVisibleIndex = state.firstVisibleItemIndex
            
            val viewportHeight = size.height
            val barHeight = viewportHeight * (visibleItems.toFloat() / totalItems.toFloat())
            val barY = viewportHeight * (firstVisibleIndex.toFloat() / totalItems.toFloat())
            
            drawRect(
                color = color,
                topLeft = Offset(size.width - 6f, barY),
                size = Size(4f, barHeight)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DictionaryScreen(viewModel: VocabViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val searchResults by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterMastery by viewModel.filterMastery.collectAsState()
    val sortBy by viewModel.sortBy.collectAsState()
    val multiSelectedIds by viewModel.multiSelectedIds.collectAsState()
    val autoTranslateState by viewModel.autoTranslationState.collectAsState()
    val examplesCache by viewModel.examplesCache.collectAsState()
    val loadingExamplesWordId by viewModel.loadingExamplesWordId.collectAsState()

    var draggingWordId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var localActiveList by remember { mutableStateOf<List<Word>>(emptyList()) }

    LaunchedEffect(searchResults) {
        if (draggingWordId == null) {
            localActiveList = searchResults
        }
    }

    var showAddForm by remember { mutableStateOf(false) }
    var showCsvImportPanel by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<Word?>(null) }
    var isReorderEnabled by remember { mutableStateOf(false) }

    // Manual Fields
    var englishWordInput by remember { mutableStateOf("") }
    var definitionInput by remember { mutableStateOf("") }
    var farsiInput by remember { mutableStateOf("") }

    // Paste CSV Text
    var pastedCsvText by remember { mutableStateOf("") }

    // File selection launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    viewModel.importFromCsvStream(
                        inputStream = inputStream,
                        onSuccess = { count ->
                            Toast.makeText(context, "Successfully imported $count words!", Toast.LENGTH_LONG).show()
                            showCsvImportPanel = false
                        },
                        onFailure = {
                            Toast.makeText(context, "Failed to parse CSV. Ensure 3 columns.", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Process Translate response callbacks
    LaunchedEffect(autoTranslateState) {
        if (autoTranslateState is VocabViewModel.AutoTranslateState.Success) {
            val success = autoTranslateState as VocabViewModel.AutoTranslateState.Success
            definitionInput = success.synonymsMeaning
            farsiInput = success.farsiMeaning
            viewModel.resetAutoTranslationState()
            Toast.makeText(context, "Translation fields autofilled!", Toast.LENGTH_SHORT).show()
        } else if (autoTranslateState is VocabViewModel.AutoTranslateState.Error) {
            val error = autoTranslateState as VocabViewModel.AutoTranslateState.Error
            Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            viewModel.resetAutoTranslationState()
        }
    }

    val duplicatePrompt by viewModel.duplicatePromptState.collectAsState()
    duplicatePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = { /* No-op to enforce choice */ },
            title = { Text("Duplicate Word Found", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("The word \"${prompt.newWord.english}\" is already in your vault.", style = MaterialTheme.typography.bodyMedium)
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Current Definition:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            Text("• ${prompt.existingWord.synonymsMeaning}", style = MaterialTheme.typography.bodyMedium)
                            Text("• Farsi: ${prompt.existingWord.farsiMeaning}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("New Definition:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.bodySmall)
                            Text("• ${prompt.newWord.synonymsMeaning}", style = MaterialTheme.typography.bodyMedium)
                            Text("• Farsi: ${prompt.newWord.farsiMeaning}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text("Do you want to replace/overwrite the existing definition, or skip/ignore this word?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = { prompt.onResolve(true) }
                ) {
                    Text("Replace")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { prompt.onResolve(false) }
                ) {
                    Text("Skip/Ignore")
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    if (multiSelectedIds.isNotEmpty()) {
                        Text("${multiSelectedIds.size} Selected", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Vocabulary Dictionary", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (multiSelectedIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Selection")
                        }
                    }
                },
                actions = {
                    if (multiSelectedIds.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.deleteSelectedWords()
                                Toast.makeText(context, "Words deleted.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("delete_selected_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { showCsvImportPanel = !showCsvImportPanel }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Import CSV")
                        }
                        IconButton(onClick = { showAddForm = !showAddForm }) {
                            Icon(if (showAddForm) Icons.Default.Close else Icons.Default.Add, contentDescription = "Add Word")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (multiSelectedIds.isNotEmpty()) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = if (multiSelectedIds.isNotEmpty()) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Add Word Manual Form
            AnimatedVisibility(
                visible = showAddForm,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("manual_add_form"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Add Word Manually", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = englishWordInput,
                                onValueChange = { englishWordInput = it },
                                label = { Text("English Word") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("input_english_word"),
                                singleLine = true
                            )
                            Button(
                                onClick = { viewModel.fetchAutoTranslation(englishWordInput) },
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("auto_fetch_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                enabled = autoTranslateState !is VocabViewModel.AutoTranslateState.Loading && englishWordInput.isNotBlank()
                            ) {
                                if (autoTranslateState is VocabViewModel.AutoTranslateState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.Language, contentDescription = "Auto Translate")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = farsiInput,
                            onValueChange = { farsiInput = it },
                            label = { Text("Farsi Translation") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_farsi_meaning"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = definitionInput,
                            onValueChange = { definitionInput = it },
                            label = { Text("English Definition / Synonyms") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_english_meaning"),
                            minLines = 2
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                englishWordInput = ""
                                definitionInput = ""
                                farsiInput = ""
                                showAddForm = false
                            }) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (englishWordInput.isNotBlank()) {
                                        viewModel.insertWord(englishWordInput, definitionInput, farsiInput)
                                        englishWordInput = ""
                                        definitionInput = ""
                                        farsiInput = ""
                                        showAddForm = false
                                        Toast.makeText(context, "Word saved!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "English word is required", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("submit_manual_button")
                            ) { Text("Save Word") }
                        }
                    }
                }
            }

            // CSV Batch Importer Sheet Panel
            AnimatedVisibility(
                visible = showCsvImportPanel,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Batch Import CSV / Text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Rows must be: English Word, English Meaning, Farsi Meaning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Path 1: Load file
                        Button(
                            onClick = { filePickerLauncher.launch("text/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("choose_csv_file_button")
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Choose Local .csv / .txt File")
                        }

                        // Divider
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text("OR", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall)
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }

                        // Path 2: Paste Clipboard CSV
                        OutlinedTextField(
                            value = pastedCsvText,
                            onValueChange = { pastedCsvText = it },
                            label = { Text("Paste CSV/TXT Text here") },
                            placeholder = { Text("Apple,A sweet red fruit,سیب\nBanana,Yellow sweet fruit,موز") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("paste_csv_field"),
                            minLines = 3
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                pastedCsvText = ""
                                showCsvImportPanel = false
                            }) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (pastedCsvText.isNotBlank()) {
                                        viewModel.importCsvText(
                                            text = pastedCsvText,
                                            onSuccess = { count ->
                                                Toast.makeText(context, "Successfully pasted & imported $count words!", Toast.LENGTH_LONG).show()
                                                pastedCsvText = ""
                                                showCsvImportPanel = false
                                            },
                                            onFailure = {
                                                Toast.makeText(context, "Could not parse text CSV. Check delimiters.", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("submit_paste_csv_button"),
                                enabled = pastedCsvText.isNotBlank()
                            ) { Text("Parse & Import") }
                        }
                    }
                }
            }

            // Realtime Search & Sort Filters Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 2.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.searchQuery.value = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("search_bar_input"),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.searchQuery.value = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Filters Toolbar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sort Menu Dropdown
                        Box {
                            var sortMenuExpanded by remember { mutableStateOf(false) }
                            AssistChip(
                                onClick = { sortMenuExpanded = true },
                                label = { Text("Sort: $sortBy") },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                val sortOptions = listOf("Date Added", "A-Z", "Forgotten Marks", "Date Practiced", "Custom Order")
                                sortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            viewModel.sortBy.value = option
                                            sortMenuExpanded = false
                                            Toast.makeText(
                                                context,
                                                "Sorted by $option. Sessions will follow this order.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            }
                        }

                        // Filter Menu Dropdown
                        Box {
                            var filterMenuExpanded by remember { mutableStateOf(false) }
                            AssistChip(
                                onClick = { filterMenuExpanded = true },
                                label = { Text("Filter: $filterMastery") },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            DropdownMenu(
                                expanded = filterMenuExpanded,
                                onDismissRequest = { filterMenuExpanded = false }
                            ) {
                                val filterOptions = listOf("All", "Unlearned", "Troublesome words", "Learning", "Fully learned")
                                filterOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            viewModel.filterMastery.value = option
                                            filterMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (sortBy == "Custom Order") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = null,
                                    tint = if (isReorderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Enable Reordering",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isReorderEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = isReorderEnabled,
                                onCheckedChange = { isReorderEnabled = it },
                                modifier = Modifier.scale(0.6f).testTag("reorder_toggle_switch")
                            )
                        }
                    }
                }
            }

            // Word List
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No matching vocabulary words.", color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.seedSampleWordsIfEmpty() },
                            modifier = Modifier.testTag("seed_database_button")
                        ) {
                            Text("Seed Sample Words Table")
                        }
                    }
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .scrollbar(listState, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(localActiveList, key = { _, word -> word.id }) { index, word ->
                        val isSelected = multiSelectedIds.contains(word.id)
                        val isCurrentlyDraggingThis = draggingWordId == word.id
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val offsetModifier = if (isCurrentlyDraggingThis) {
                            val dragOffsetDp = with(density) { dragOffset.toDp() }
                            Modifier.offset(y = dragOffsetDp)
                        } else {
                            Modifier
                        }

                        WordCardItem(
                            word = word,
                            isSelected = isSelected,
                            modifier = Modifier
                                .animateItemPlacement()
                                .then(offsetModifier),
                            isCustomSort = sortBy == "Custom Order" && isReorderEnabled,
                            isDragging = isCurrentlyDraggingThis,
                            onDragStarted = {
                                draggingWordId = word.id
                                dragOffset = 0f
                            },
                            onDragged = { dy ->
                                dragOffset += dy
                                val threshold = 180f
                                val currentId = draggingWordId
                                if (currentId != null) {
                                    val currentIndex = localActiveList.indexOfFirst { it.id == currentId }
                                    if (currentIndex != -1) {
                                        if (dragOffset > threshold && currentIndex < localActiveList.lastIndex) {
                                            val newList = localActiveList.toMutableList()
                                            val temp = newList[currentIndex]
                                            newList[currentIndex] = newList[currentIndex + 1]
                                            newList[currentIndex + 1] = temp
                                            localActiveList = newList
                                            dragOffset -= threshold
                                        } else if (dragOffset < -threshold && currentIndex > 0) {
                                            val newList = localActiveList.toMutableList()
                                            val temp = newList[currentIndex]
                                            newList[currentIndex] = newList[currentIndex - 1]
                                            newList[currentIndex - 1] = temp
                                            localActiveList = newList
                                            dragOffset += threshold
                                        }
                                    }
                                }
                            },
                            onDragStopped = {
                                draggingWordId = null
                                dragOffset = 0f
                                viewModel.updateCustomPositions(localActiveList)
                            },
                            onLongClick = { viewModel.toggleSelection(word.id) },
                            onClick = {
                                if (multiSelectedIds.isNotEmpty()) {
                                    viewModel.toggleSelection(word.id)
                                }
                            },
                            showExamplesClick = {
                                viewModel.showExamples(word) { errMsg ->
                                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                                }
                            },
                            examples = if (word.usageExamples.isNotEmpty()) word.usageExamples else (examplesCache[word.id] ?: emptyList()),
                            isLoadingExamples = loadingExamplesWordId == word.id,
                            onEditClick = { editingWord = word }
                        )
                    }
                }
            }
        }
    }

    editingWord?.let { currentWord ->
        var editEnglish by remember(currentWord) { mutableStateOf(currentWord.english) }
        var editFarsi by remember(currentWord) { mutableStateOf(currentWord.farsiMeaning) }
        var editSynonyms by remember(currentWord) { mutableStateOf(currentWord.synonymsMeaning) }
        
        val editExamples = remember(currentWord) { mutableStateListOf(*currentWord.usageExamples.toTypedArray()) }
        val editHistory = remember(currentWord) { mutableStateListOf(*currentWord.reviewHistory.toTypedArray()) }
        
        AlertDialog(
            onDismissRequest = { editingWord = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Edit Word Entry", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editEnglish,
                        onValueChange = { editEnglish = it },
                        label = { Text("English Word") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_english_word"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editFarsi,
                        onValueChange = { editFarsi = it },
                        label = { Text("Farsi Translation") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_farsi_meaning"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editSynonyms,
                        onValueChange = { editSynonyms = it },
                        label = { Text("English Definition / Synonyms") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_english_meaning"),
                        minLines = 2
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Usage Examples",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        IconButton(
                            onClick = { editExamples.add("") },
                            modifier = Modifier.testTag("add_example_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Add Example",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (editExamples.isEmpty()) {
                        Text(
                            "No usage examples added.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else {
                        editExamples.forEachIndexed { index, example ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = example,
                                    onValueChange = { editExamples[index] = it },
                                    label = { Text("Example #${index + 1}") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("edit_example_input_$index"),
                                    maxLines = 2
                                )
                                IconButton(
                                    onClick = { 
                                        if (index in editExamples.indices) {
                                            editExamples.removeAt(index)
                                        } 
                                    },
                                    modifier = Modifier.testTag("delete_example_button_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Example",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Review Marks History",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    editHistory.add(
                                        ReviewMark(
                                            timestamp = System.currentTimeMillis(),
                                            status = "Remembered"
                                        )
                                    )
                                },
                                label = { Text("+ Remembered", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_remembered_mark_button")
                            )
                            AssistChip(
                                onClick = {
                                    editHistory.add(
                                        ReviewMark(
                                            timestamp = System.currentTimeMillis(),
                                            status = "Forgotten"
                                        )
                                    )
                                },
                                label = { Text("+ Forgotten", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = Color(0xFFC62828),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_forgotten_mark_button")
                            )
                        }
                    }

                    if (editHistory.isEmpty()) {
                        Text(
                            "No review history logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    } else {
                        editHistory.forEachIndexed { index, mark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (mark.status == "Remembered") {
                                            Color(0xFFE8F5E9).copy(alpha = 0.5f)
                                        } else {
                                            Color(0xFFFFEBEE).copy(alpha = 0.5f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (mark.status == "Remembered") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = mark.status,
                                        tint = if (mark.status == "Remembered") Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = mark.status,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (mark.status == "Remembered") Color(0xFF1B5E20) else Color(0xFFB71C1C)
                                        )
                                        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(mark.timestamp))
                                        Text(
                                            text = formattedDate,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (index in editHistory.indices) {
                                            editHistory.removeAt(index)
                                        }
                                    },
                                    modifier = Modifier.size(32.dp).testTag("delete_mark_button_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Mark",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editEnglish.isNotBlank()) {
                            val cleanExamples = editExamples.map { it.trim() }.filter { it.isNotEmpty() }
                            val updated = currentWord.copy(
                                english = editEnglish.trim(),
                                farsiMeaning = editFarsi.trim(),
                                synonymsMeaning = editSynonyms.trim(),
                                usageExamples = cleanExamples,
                                reviewHistory = editHistory.toList()
                            )
                            viewModel.updateWord(updated)
                            editingWord = null
                            Toast.makeText(context, "Word updated!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "English word is required", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("save_edit_word_button")
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editingWord = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun getRelativeTimeString(timestamp: Long, fallbackNow: String): String {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordCardItem(
    word: Word,
    isSelected: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    showExamplesClick: () -> Unit,
    examples: List<String>,
    isLoadingExamples: Boolean,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCustomSort: Boolean = false,
    isDragging: Boolean = false,
    onDragStarted: () -> Unit = {},
    onDragged: (Float) -> Unit = {},
    onDragStopped: () -> Unit = {}
) {
    var isFlippedRevealed by remember { mutableStateOf(false) }

    val currentOnDragStarted by rememberUpdatedState(onDragStarted)
    val currentOnDragged by rememberUpdatedState(onDragged)
    val currentOnDragStopped by rememberUpdatedState(onDragStopped)

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1.0f,
        label = "dragScale"
    )

    val relativeAdded = remember(word.dateAdded) {
        getRelativeTimeString(word.dateAdded, "Added just now")
    }
    val relativeExercised = remember(word.reviewHistory) {
        val lastMark = word.reviewHistory.lastOrNull()
        if (lastMark != null) {
            getRelativeTimeString(lastMark.timestamp, "Just practiced")
        } else {
            "Never"
        }
    }

    val isDark = isSystemInDarkTheme()
    // Subtle background color based on mastery
    val cardBg = when (word.masteryLevel) {
        "Fully learned" -> {
            if (isDark) {
                if (isSelected) Color(0xFF1B5E20) else Color(0xFF132F15)
            } else {
                if (isSelected) Color(0xFFC8E6C9) else Color(0xFFE8F5E9)
            }
        }
        "Troublesome words" -> {
            if (isDark) {
                if (isSelected) Color(0xFF7F0000) else Color(0xFF381B1B)
            } else {
                if (isSelected) Color(0xFFFFCDD2) else Color(0xFFFFEBEE)
            }
        }
        "Learning" -> {
            if (isDark) {
                if (isSelected) Color(0xFF0D47A1) else Color(0xFF14243A)
            } else {
                if (isSelected) Color(0xFFBBDEFB) else Color(0xFFE3F2FD)
            }
        }
        else -> {
            if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
        }
    }

    val contentColor = when (word.masteryLevel) {
        "Fully learned" -> if (isDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
        "Troublesome words" -> if (isDark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
        "Learning" -> if (isDark) Color(0xFF90CAF9) else Color(0xFF0D47A1)
        else -> if (isSelected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .combinedClickable(
                onClick = {
                    if (isSelected) {
                        onClick()
                    } else {
                        // Toggling reveals for self-testing! English shown, meaning hidden.
                        isFlippedRevealed = !isFlippedRevealed
                    }
                },
                onLongClick = onLongClick
            )
            .testTag("word_item_card_${word.english.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = cardBg, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 12.dp else if (isSelected) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Header: Word Name and Performance Indicators (Colorized status icon instead of badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        word.english,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor
                    )
                }

                // Small colorized status icon instead of badge
                val statusIcon = when (word.masteryLevel) {
                    "Fully learned" -> Icons.Default.Stars to (if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32))
                    "Troublesome words" -> Icons.Default.Warning to (if (isDark) Color(0xFFE57373) else Color(0xFFC62828))
                    "Learning" -> Icons.Default.AutoStories to (if (isDark) Color(0xFF64B5F6) else Color(0xFF1565C0))
                    else -> Icons.Default.School to (if (isDark) Color.LightGray else Color(0xFF616161))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCustomSort) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag Handle",
                            tint = contentColor,
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { currentOnDragStarted() },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            currentOnDragged(dragAmount.y)
                                        },
                                        onDragEnd = { currentOnDragStopped() },
                                        onDragCancel = { currentOnDragStopped() }
                                    )
                                }
                                .padding(4.dp)
                                .size(24.dp)
                                .testTag("drag_handle_${word.english.lowercase()}")
                        )
                        Spacer(modifier = Modifier.width(1.dp).height(24.dp).background(contentColor.copy(alpha = 0.15f)))
                    }

                    if (isFlippedRevealed) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(24.dp).testTag("edit_word_btn_${word.english.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Word",
                                tint = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Icon(
                        imageVector = statusIcon.first,
                        contentDescription = word.masteryLevel,
                        tint = statusIcon.second,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Score History Info Indicators: Progress Icons (e.g. ✕✕✓✕✓)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (word.reviewHistory.isEmpty()) {
                        Icon(Icons.Default.Cancel, contentDescription = "Unlearned", tint = Color.Gray, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("No logs", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        // Limit to last 8 reviews as a pretty row of check / close indicator icons
                        word.reviewHistory.takeLast(8).forEach { mark ->
                            Icon(
                                imageVector = if (mark.status == "Remembered") Icons.Default.Check else Icons.Default.Close,
                                contentDescription = mark.status,
                                tint = if (mark.status == "Remembered") Color(0xFF4CAF50) else Color(0xFFF44336),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))

                // Date Added & Practiced with thin spacing and minimal icons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Added date", tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(relativeAdded, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = Color.Gray)
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HourglassEmpty, contentDescription = "Exercised date", tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(relativeExercised, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal), color = Color.Gray)
                }
            }

            // Word Meanings - Hidden by default. Reveals on Click!
            AnimatedVisibility(
                visible = isFlippedRevealed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text("Farsi Translation:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary)
                    Text(
                        if (word.farsiMeaning.isBlank()) "No Farsi translation added." else word.farsiMeaning,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        color = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("English Meaning / Synonyms:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (isDark) Color(0xFF90CAF9) else MaterialTheme.colorScheme.primary)
                    Text(
                        if (word.synonymsMeaning.isBlank()) "No English meaning added." else word.synonymsMeaning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Color(0xFFE0E0E0) else Color.DarkGray
                    )
                }
            }

            // Examples API loader accordion panel
            AnimatedVisibility(
                visible = isFlippedRevealed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    if (examples.isEmpty()) {
                        Button(
                            onClick = showExamplesClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("show_examples_button_${word.english.lowercase()}"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            enabled = !isLoadingExamples
                        ) {
                            if (isLoadingExamples) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp).padding(end = 4.dp))
                            } else {
                                Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Show Usage Examples", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Usage Examples:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF689F38))
                                Spacer(modifier = Modifier.height(6.dp))
                                examples.forEachIndexed { i, sentence ->
                                    Text(
                                        "${i+1}. \"$sentence\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        modifier = Modifier.padding(vertical = 3.dp)
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
