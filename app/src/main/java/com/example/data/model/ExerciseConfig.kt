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
    val dayOfWeek: Int = 2, // Calendar.MONDAY is 2. 1 is Sunday, 2 is Monday... (for backwards compatibility)
    val daysOfWeek: List<Int> = listOf(2), // Multi-select days of week: 1=Sun, 2=Mon, 3=Tue, 4=Wed, 5=Thu, 6=Fri, 7=Sat
    val dayOfMonth: Int = 1, // 1 to 31
    // Special Review Type options
    val reviewScope: String = "All previous words", // Kept for backwards compatibility
    val reviewScopes: List<String> = listOf("Learning", "Fully learned", "Troublesome words"), // Multi-select scopes
    val sortByLastFlagged: Boolean = false, // handle based on last flagged time
    val reviewAlgorithm: String = "Least practiced first"
) {
    fun getEffectiveDaysOfWeek(): List<Int> {
        if (daysOfWeek.isNotEmpty()) {
            if (daysOfWeek == listOf(2) && dayOfWeek != 2) {
                return listOf(dayOfWeek)
            }
            return daysOfWeek
        }
        return listOf(dayOfWeek)
    }

    fun getEffectiveReviewScopes(): List<String> {
        if (reviewScopes.isNotEmpty()) {
            if (reviewScopes == listOf("Learning", "Fully learned", "Troublesome words") &&
                (reviewScope == "Only Troublesome words" || reviewScope == "Only forgotten words")) {
                return listOf("Troublesome words")
            }
            return reviewScopes
        }
        return if (reviewScope == "Only forgotten words" || reviewScope == "Only Troublesome words") {
            listOf("Troublesome words")
        } else {
            listOf("Learning", "Fully learned", "Troublesome words")
        }
    }
}

data class OverdueSessionItem(
    val sessionKey: String,
    val config: ExerciseConfig,
    val isRecap: Boolean,
    val scheduledTime: Long,
    val name: String,
    val type: String // "Learn", "Review", "Recap"
)
