package com.example

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import com.example.data.repository.WordRepository

class ExampleUnitTest {
  @Test
  fun testFetchAutoTranslation() {
    val repository = WordRepository(null as? com.example.data.local.WordDao ?: mockDao())
    val log = StringBuilder()
    try {
      log.append("--- CALLING fetchAutoTranslation('statutory') ---\n")
      val result = kotlinx.coroutines.runBlocking {
        repository.fetchAutoTranslation("statutory")
      }
      log.append("RESULT: $result\n")
    } catch (e: Exception) {
      log.append("Threw exception: ${e.javaClass.name}: ${e.message}\n")
      val sw = java.io.StringWriter()
      e.printStackTrace(java.io.PrintWriter(sw))
      log.append("Stack trace:\n$sw\n")
    }
    
    try {
      log.append("\n--- CALLING fetchContextExamples('statutory') ---\n")
      val result = kotlinx.coroutines.runBlocking {
        repository.fetchContextExamples("statutory")
      }
      log.append("RESULT: $result\n")
    } catch (e: Exception) {
      log.append("Threw exception: ${e.javaClass.name}: ${e.message}\n")
      val sw = java.io.StringWriter()
      e.printStackTrace(java.io.PrintWriter(sw))
      log.append("Stack trace:\n$sw\n")
    }
    
    File("src/test/java/com/example/translation_results.txt").absoluteFile.writeText(log.toString())
  }

  private fun mockDao(): com.example.data.local.WordDao {
    return object : com.example.data.local.WordDao {
      override fun getAllWordsFlow(): kotlinx.coroutines.flow.Flow<List<com.example.data.model.Word>> = kotlinx.coroutines.flow.emptyFlow()
      override suspend fun getAllWords(): List<com.example.data.model.Word> = emptyList()
      override suspend fun getWordById(id: Long): com.example.data.model.Word? = null
      override suspend fun insertWord(word: com.example.data.model.Word): Long = 0L
      override suspend fun insertWords(words: List<com.example.data.model.Word>) {}
      override suspend fun deleteWord(word: com.example.data.model.Word) {}
      override suspend fun deleteWordsByIds(ids: List<Long>) {}
      override suspend fun clearAll() {}
    }
  }
}
