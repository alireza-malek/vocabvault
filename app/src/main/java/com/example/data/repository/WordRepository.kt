package com.example.data.repository

import com.example.data.local.WordDao
import com.example.data.model.Word
import com.example.data.api.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class WordRepository(private val wordDao: WordDao) {
    val allWords: Flow<List<Word>> = wordDao.getAllWordsFlow()

    suspend fun getAllWordsList(): List<Word> = wordDao.getAllWords()

    suspend fun getWordById(id: Long): Word? = wordDao.getWordById(id)

    suspend fun insertWord(word: Word): Long = wordDao.insertWord(word)

    suspend fun insertWords(words: List<Word>) = wordDao.insertWords(words)

    suspend fun deleteWord(word: Word) = wordDao.deleteWord(word)

    suspend fun deleteWordsByIds(ids: List<Long>) = wordDao.deleteWordsByIds(ids)

    suspend fun clearAll() = wordDao.clearAll()

    // Automatic translator using MyMemory & Dictionary API
    suspend fun fetchAutoTranslation(englishWord: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        var farsiTranslation = ""
        var englishMeaning = ""
        var synonyms = ""

        // Try Google Translate public API for Farsi translation first
        try {
            val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=fa&dt=t&q=" + java.net.URLEncoder.encode(englishWord, "UTF-8")
            val request = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val rawHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            rawHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        // Safe regex parsing for the nested array [[["translated_text","original_text",...
                        val matchResult = Regex("""^\[+["']([^"']+)["']""").find(body)
                        if (matchResult != null) {
                            val candidate = matchResult.groupValues[1]
                            if (candidate.isNotEmpty() && candidate != englishWord) {
                                farsiTranslation = candidate
                            }
                        } else {
                            // Secondary fallback parsing inside quotes
                            val firstQuote = body.indexOf("\"")
                            if (firstQuote != -1) {
                                val secondQuote = body.indexOf("\"", firstQuote + 1)
                                if (secondQuote != -1) {
                                    val candidate = body.substring(firstQuote + 1, secondQuote)
                                    if (candidate.isNotEmpty() && candidate != englishWord && candidate.any { it.code in 0x0600..0x06FF }) {
                                        farsiTranslation = candidate
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to MyMemory if Google Translate failed or returned empty
        if (farsiTranslation.isBlank()) {
            try {
                val response = RetrofitClient.myMemoryApi.getTranslation(englishWord)
                val candidate = response.responseData?.translatedText ?: ""
                if (candidate.isNotEmpty() && candidate != englishWord) {
                    farsiTranslation = android.text.Html.fromHtml(candidate, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Try Dictionary API for English definition and synonyms
        try {
            val dictResponse = RetrofitClient.dictionaryApi.getDefinitions(englishWord)
            englishMeaning = dictResponse.firstOrNull()?.meanings?.firstOrNull()?.definitions?.firstOrNull()?.definition ?: ""
            synonyms = dictResponse.firstOrNull()?.meanings?.firstOrNull()?.definitions?.firstOrNull()?.synonyms?.take(3)?.joinToString(", ") ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If both failed to get any substantive string, return null
        if (farsiTranslation.isBlank() && englishMeaning.isBlank() && synonyms.isBlank()) {
            return@withContext null
        }

        val combinedEnglishMeaning = if (synonyms.isNotEmpty()) {
            if (englishMeaning.isNotEmpty()) "$englishMeaning (Synonyms: $synonyms)" else "Synonyms: $synonyms"
        } else {
            englishMeaning
        }

        Pair(combinedEnglishMeaning, farsiTranslation)
    }

    // Dynamic Context Sentence fetcher from dictionary API
    suspend fun fetchContextExamples(englishWord: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.freeDictionaryApi.getDefinitions(englishWord)
            val examples = mutableListOf<String>()
            response.entries?.forEach { entry ->
                entry.senses?.forEach { sense ->
                    sense.examples?.forEach { example ->
                        if (example.isNotBlank()) {
                            examples.add(example.trim())
                        }
                    }
                }
            }
            if (examples.isEmpty()) {
                response.entries?.forEach { entry ->
                    entry.senses?.forEach { sense ->
                        sense.quotes?.forEach { quote ->
                            val txt = quote.text
                            if (!txt.isNullOrBlank()) {
                                examples.add(txt.trim())
                            }
                        }
                    }
                }
            }
            if (examples.isNotEmpty()) {
                examples.distinct().take(3)
            } else {
                // Secondary fallback to old api
                try {
                    val oldResponse = RetrofitClient.dictionaryApi.getDefinitions(englishWord)
                    val oldExamples = mutableListOf<String>()
                    oldResponse.forEach { entry ->
                        entry.meanings?.forEach { meaning ->
                            meaning.definitions?.forEach { def ->
                                if (!def.example.isNullOrBlank()) {
                                    oldExamples.add(def.example)
                                }
                            }
                        }
                    }
                    if (oldExamples.isEmpty()) {
                        oldResponse.firstOrNull()?.meanings?.firstOrNull()?.definitions?.mapNotNull { it.definition }?.take(2) ?: emptyList()
                    } else {
                        oldExamples.distinct().take(3)
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // If primary fails, try fallback
            try {
                val oldResponse = RetrofitClient.dictionaryApi.getDefinitions(englishWord)
                val oldExamples = mutableListOf<String>()
                oldResponse.forEach { entry ->
                    entry.meanings?.forEach { meaning ->
                        meaning.definitions?.forEach { def ->
                            if (!def.example.isNullOrBlank()) {
                                oldExamples.add(def.example)
                            }
                        }
                    }
                }
                if (oldExamples.isEmpty()) {
                    oldResponse.firstOrNull()?.meanings?.firstOrNull()?.definitions?.mapNotNull { it.definition }?.take(2) ?: emptyList()
                } else {
                    oldExamples.distinct().take(3)
                }
            } catch (fallbackEx: Exception) {
                emptyList()
            }
        }
    }

    // Parse CSV / TXT input stream
    fun parseCsvInputStream(inputStream: InputStream): List<Word> {
        val words = mutableListOf<Word>()
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.startsWith("\uFEFF")) {
                    line = line.substring(1)
                }
                val parts = splitCsvLine(line)
                if (parts.isNotEmpty()) {
                    val english = parts[0].trim()
                    val englishMeaning = if (parts.size > 1) parts[1].trim() else ""
                    val farsiMeaning = if (parts.size > 2) parts[2].trim() else ""
                    
                    if (english.isNotEmpty()) {
                        words.add(
                            Word(
                                english = english,
                                synonymsMeaning = englishMeaning,
                                farsiMeaning = farsiMeaning
                            )
                        )
                    }
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return words
    }

    private fun splitCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString())
        return tokens
    }
}
