package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.ExerciseConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            NotificationScheduler.rescheduleOnBoot(context)
            return
        }

        val channelId = "exercise_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VocabVault Practice Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you to practice vocabulary list"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, text) = when {
            action?.startsWith("com.example.ACTION_EXERCISE_REMINDER_") == true -> {
                val configId = action.substringAfter("com.example.ACTION_EXERCISE_REMINDER_")
                val configName = loadConfigName(context, configId)
                Pair(
                    "Time for $configName! 🧠",
                    "Your scheduled learning session is waiting. Test yourself now!"
                )
            }
            action?.startsWith("com.example.ACTION_RECAP_REMINDER_") == true -> {
                val configId = action.substringAfter("com.example.ACTION_RECAP_REMINDER_")
                val configName = loadConfigName(context, configId)
                Pair(
                    "Recap Time for $configName! 🔄",
                    "Revise your last session to store these words in your long-term memory!"
                )
            }
            action == "com.example.ACTION_EXERCISE_REMINDER" -> {
                Pair(
                    "Time for Vocab Practice! 🧠",
                    "Your scheduled learning session is waiting. Test yourself now!"
                )
            }
            action == "com.example.ACTION_RECAP_REMINDER" -> {
                Pair(
                    "Recap Time! 🔄",
                    "Revise today's batch to store these words in your long-term memory!"
                )
            }
            else -> {
                Pair(
                    "VocabVault Active Review",
                    "Boost your vocabulary now!"
                )
            }
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = if (action?.contains("RECAP") == true) 102 else 101
        notificationManager.notify(notificationId, notification)
        
        // Trigger rescheduling of all alarms properly (moves standard alarms to their next cycle, schedules new recaps, etc.)
        NotificationScheduler.scheduleAlarms(context)
    }

    private fun loadConfigName(context: Context, configId: String): String {
        try {
            val prefs = context.getSharedPreferences("vocab_vault_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("custom_exercises", null)
            if (json != null) {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = Types.newParameterizedType(List::class.java, ExerciseConfig::class.java)
                val adapter = moshi.adapter<List<ExerciseConfig>>(type)
                val list = adapter.fromJson(json) ?: emptyList()
                val config = list.find { it.id == configId }
                if (config != null) return config.name
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Vocab Session"
    }
}
