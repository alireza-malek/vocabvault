package com.example.data.model

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class ExerciseConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // "Learn" or "Review"
    val mode: String, // "Daily", "Weekly", "Monthly"
    val wordCount: Int = 5,
    val delayedRecapEnabled: Boolean = true,
    val delayedRecapHours: Int = 12,
    val notificationEnabled: Boolean = true,
    val scheduleHour: Int = 10,
    val scheduleMinute: Int = 0,
    val dayOfWeek: Int = 2, // Calendar.MONDAY is 2. 1 is Sunday, 2 is Monday...
    val dayOfMonth: Int = 1, // 1 to 31
    // Special Review Type options
    val reviewScope: String = "All previous words", // "All previous words" or "Only forgotten words"
    val sortByLastFlagged: Boolean = false, // handle based on last flagged time
    val reviewAlgorithm: String = "Least practiced first"
)

