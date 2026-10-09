package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VocabVault", appName)
  }

  @Test
  fun `test exercise config review scopes default to all three`() {
    val config = com.example.data.model.ExerciseConfig(
      name = "Test Review",
      type = "Review",
      mode = "Daily"
    )
    val effectiveScopes = config.getEffectiveReviewScopes()
    assertEquals(3, effectiveScopes.size)
    assertTrue(effectiveScopes.contains("Learning"))
    assertTrue(effectiveScopes.contains("Fully learned"))
    assertTrue(effectiveScopes.contains("Troublesome words"))
  }

  @Test
  fun `test exercise config weekly multi-select days`() {
    val satSunConfig = com.example.data.model.ExerciseConfig(
      name = "Weekend Review",
      type = "Review",
      mode = "Weekly",
      daysOfWeek = listOf(java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)
    )
    val effectiveDays = satSunConfig.getEffectiveDaysOfWeek()
    assertEquals(2, effectiveDays.size)
    assertTrue(effectiveDays.contains(java.util.Calendar.SATURDAY))
    assertTrue(effectiveDays.contains(java.util.Calendar.SUNDAY))
  }

  @Test
  fun `test trail logs filter includes recap in learning logs`() {
    val learnLog = com.example.ui.viewmodel.TrailLogEntry(
      timestamp = 1000L,
      sessionType = "Learn",
      totalWords = 5,
      passedCount = 5
    )
    val recapLog = com.example.ui.viewmodel.TrailLogEntry(
      timestamp = 2000L,
      sessionType = "Recap",
      totalWords = 5,
      passedCount = 4
    )
    val reviewLog = com.example.ui.viewmodel.TrailLogEntry(
      timestamp = 3000L,
      sessionType = "Review",
      totalWords = 5,
      passedCount = 3
    )
    val allLogs = listOf(learnLog, recapLog, reviewLog)
    
    // Tab 0 is Learning tab: includes Learn, Learning, and Recap
    val learningTabLogs = allLogs.filter { it.sessionType == "Learn" || it.sessionType == "Learning" || it.sessionType == "Recap" }
    assertEquals(2, learningTabLogs.size)
    assertTrue(learningTabLogs.contains(learnLog))
    assertTrue(learningTabLogs.contains(recapLog))
    assertFalse(learningTabLogs.contains(reviewLog))
  }
}
