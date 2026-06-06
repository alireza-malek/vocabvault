package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.ExerciseConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Calendar

object NotificationScheduler {
    private const val PREFS_NAME = "vocab_vault_prefs"

    private fun loadConfigs(context: Context): List<ExerciseConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("custom_exercises", null)
        if (json != null) {
            try {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val type = Types.newParameterizedType(List::class.java, ExerciseConfig::class.java)
                val adapter = moshi.adapter<List<ExerciseConfig>>(type)
                return adapter.fromJson(json) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return emptyList()
    }

    private fun getLastRunTimestamp(context: Context, configId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("last_run_$configId", 1L)
    }

    private fun getRecapLastRun(context: Context, configId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("recap_last_run_$configId", 0L)
    }

    private fun getNextScheduledTime(config: ExerciseConfig, lastRunTimestamp: Long): Long {
        val now = System.currentTimeMillis()
        val baseTime = Math.max(now, lastRunTimestamp)
        
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, config.scheduleHour)
            set(Calendar.MINUTE, config.scheduleMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        fun isSamePeriodAsScheduled(timestamp: Long): Boolean {
            if (timestamp <= 1L) return false
            val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp }
            return when (config.mode) {
                "Daily" -> {
                    cal1.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
                }
                "Weekly" -> {
                    cal1.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    cal1.get(Calendar.WEEK_OF_YEAR) == calendar.get(Calendar.WEEK_OF_YEAR)
                }
                "Monthly" -> {
                    cal1.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                    cal1.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
                }
                else -> false
            }
        }
        
        when (config.mode) {
            "Daily" -> {
                if (isSamePeriodAsScheduled(lastRunTimestamp)) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                while (calendar.timeInMillis <= baseTime) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "Weekly" -> {
                calendar.set(Calendar.DAY_OF_WEEK, config.dayOfWeek)
                if (isSamePeriodAsScheduled(lastRunTimestamp)) {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                }
                while (calendar.timeInMillis <= baseTime) {
                    calendar.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            "Monthly" -> {
                val targetDay = config.dayOfMonth
                val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(maxDay))
                if (isSamePeriodAsScheduled(lastRunTimestamp)) {
                    calendar.add(Calendar.MONTH, 1)
                }
                while (calendar.timeInMillis <= baseTime) {
                    calendar.add(Calendar.MONTH, 1)
                    val nextMaxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    calendar.set(Calendar.DAY_OF_MONTH, targetDay.coerceAtMost(nextMaxDay))
                }
            }
        }
        return calendar.timeInMillis
    }

    fun scheduleAlarms(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel previously registered alarms to clean up properly
        val oldScheduledIds = prefs.getStringSet("scheduled_config_ids", emptySet()) ?: emptySet()
        for (id in oldScheduledIds) {
            cancelAlarm(context, "com.example.ACTION_EXERCISE_REMINDER_$id", id.hashCode() + 10000)
            cancelAlarm(context, "com.example.ACTION_RECAP_REMINDER_$id", id.hashCode() + 20000)
        }

        // Cancel legacy general alarms
        cancelAlarm(context, "com.example.ACTION_EXERCISE_REMINDER", 201)
        cancelAlarm(context, "com.example.ACTION_RECAP_REMINDER", 202)

        val currentConfigs = loadConfigs(context)
        val newScheduledIds = mutableSetOf<String>()

        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        for (config in currentConfigs) {
            if (!config.notificationEnabled) continue
            
            val lastRun = getLastRunTimestamp(context, config.id)
            val nextTime = getNextScheduledTime(config, lastRun)
            
            // 1. Standard Study Schedule alarm
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.example.ACTION_EXERCISE_REMINDER_${config.id}"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                config.id.hashCode() + 10000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (canScheduleExact) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                    }
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                }
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, nextTime, pendingIntent)
                }
            }
            
            newScheduledIds.add(config.id)
            
            // 2. Delayed Recap Schedule alarm (if enabled and recap is still pending)
            if (config.type == "Learn" && config.delayedRecapEnabled) {
                if (lastRun > 1L) {
                    val recapLastRun = getRecapLastRun(context, config.id)
                    if (recapLastRun < lastRun) {
                        val recapScheduledTime = lastRun + config.delayedRecapHours * 60 * 60 * 1000L
                        if (recapScheduledTime > System.currentTimeMillis()) {
                            val recapIntent = Intent(context, AlarmReceiver::class.java).apply {
                                action = "com.example.ACTION_RECAP_REMINDER_${config.id}"
                            }
                            val recapPendingIntent = PendingIntent.getBroadcast(
                                context,
                                config.id.hashCode() + 20000,
                                recapIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    if (canScheduleExact) {
                                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, recapScheduledTime, recapPendingIntent)
                                    } else {
                                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, recapScheduledTime, recapPendingIntent)
                                    }
                                } else {
                                    alarmManager.set(AlarmManager.RTC_WAKEUP, recapScheduledTime, recapPendingIntent)
                                }
                            } catch (e: SecurityException) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, recapScheduledTime, recapPendingIntent)
                                } else {
                                    alarmManager.set(AlarmManager.RTC_WAKEUP, recapScheduledTime, recapPendingIntent)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        prefs.edit().putStringSet("scheduled_config_ids", newScheduledIds).apply()
    }

    private fun cancelAlarm(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun rescheduleOnBoot(context: Context) {
        scheduleAlarms(context)
    }

    fun rescheduleNext(context: Context, action: String) {
        scheduleAlarms(context)
    }
}
