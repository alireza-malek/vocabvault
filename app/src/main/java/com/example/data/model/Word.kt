package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReviewMark(
    val timestamp: Long,
    val status: String // "Remembered" or "Forgotten"
)

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val english: String,
    val synonymsMeaning: String,
    val farsiMeaning: String,
    val reviewHistory: List<ReviewMark> = emptyList(),
    val nextReviewTimestamp: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val usageExamples: List<String> = emptyList(),
    val customOrder: Long = dateAdded
) {
    // Calculated Properties
    val forgottenCount: Int
        get() = reviewHistory.count { it.status == "Forgotten" }

    val rememberedCount: Int
        get() = reviewHistory.count { it.status == "Remembered" }

    val totalReviews: Int
        get() = reviewHistory.size

    val forgottenRatio: Double
        get() {
            if (totalReviews == 0) return 0.0
            return forgottenCount.toDouble() / totalReviews.toDouble()
        }

    val rememberedRatio: Double
        get() {
            if (totalReviews == 0) return 1.0
            return rememberedCount.toDouble() / totalReviews.toDouble()
        }

    val consecutiveRememberedCount: Int
        get() {
            var count = 0
            for (mark in reviewHistory.asReversed()) {
                if (mark.status == "Remembered") {
                    count++
                } else {
                    break
                }
            }
            return count
        }

    val consecutiveForgottenCount: Int
        get() {
            var count = 0
            for (mark in reviewHistory.asReversed()) {
                if (mark.status == "Forgotten") {
                    count++
                } else {
                    break
                }
            }
            return count
        }

    val masteryLevel: String
        get() {
            if (totalReviews == 0) return "Unlearned"
            return when {
                consecutiveForgottenCount >= troublesomeThreshold -> "Troublesome words"
                consecutiveRememberedCount >= fullyLearnedThreshold -> "Fully learned"
                else -> "Learning"
            }
        }

    companion object {
        var fullyLearnedThreshold: Int = 4
        var troublesomeThreshold: Int = 1
    }
}
