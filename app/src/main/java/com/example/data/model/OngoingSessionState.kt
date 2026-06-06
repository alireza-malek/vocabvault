package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OngoingSessionState(
    val exerciseId: String,
    val exerciseName: String,
    val sessionType: String, // "Learn" or "Review"
    val wordIds: List<Long>,
    val currentIndex: Int,
    val isRevealed: Boolean,
    val answers: Map<Long, String> // Column ID -> Answer ("Remembered" or "Forgotten")
)
