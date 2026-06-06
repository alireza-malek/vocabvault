package com.example.data.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MyMemoryResponseDto(
    val responseData: MyMemoryDataDto?
)

@JsonClass(generateAdapter = true)
data class MyMemoryDataDto(
    val translatedText: String?
)

@JsonClass(generateAdapter = true)
data class DictionaryEntry(
    val word: String?,
    val meanings: List<DictionaryMeaning>?
)

@JsonClass(generateAdapter = true)
data class DictionaryMeaning(
    val partOfSpeech: String?,
    val definitions: List<DictionaryDefinition>?
)

@JsonClass(generateAdapter = true)
data class DictionaryDefinition(
    val definition: String?,
    val example: String?,
    val synonyms: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class FreeDictResponse(
    val word: String?,
    val entries: List<FreeDictEntry>?
)

@JsonClass(generateAdapter = true)
data class FreeDictEntry(
    val partOfSpeech: String?,
    val senses: List<FreeDictSense>?
)

@JsonClass(generateAdapter = true)
data class FreeDictSense(
    val definition: String?,
    val examples: List<String>?,
    val quotes: List<FreeDictQuote>?
)

@JsonClass(generateAdapter = true)
data class FreeDictQuote(
    val text: String?,
    val reference: String?
)
