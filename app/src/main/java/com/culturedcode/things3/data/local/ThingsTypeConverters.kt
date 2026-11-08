package com.culturedcode.things3.data.local

import androidx.room.TypeConverter
import com.culturedcode.things3.domain.model.ProjectStatus
import com.culturedcode.things3.domain.model.RecurrenceRule
import com.culturedcode.things3.domain.model.ScheduleState
import com.culturedcode.things3.domain.model.TaskStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

class ThingsTypeConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromScheduleState(state: ScheduleState): String = state.name

    @TypeConverter
    fun toScheduleState(value: String): ScheduleState =
        runCatching { ScheduleState.valueOf(value.trim().uppercase(Locale.ROOT)) }.getOrDefault(ScheduleState.INBOX)

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus =
        runCatching { TaskStatus.valueOf(value.trim().uppercase(Locale.ROOT)) }.getOrDefault(TaskStatus.OPEN)

    @TypeConverter
    fun fromProjectStatus(status: ProjectStatus): String = status.name

    @TypeConverter
    fun toProjectStatus(value: String): ProjectStatus =
        runCatching { ProjectStatus.valueOf(value.trim().uppercase(Locale.ROOT)) }.getOrDefault(ProjectStatus.ACTIVE)

    @TypeConverter
    fun fromStringList(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())

    @TypeConverter
    fun fromRecurrenceRule(rule: RecurrenceRule?): String? =
        rule?.let { json.encodeToString(it) }

    @TypeConverter
    fun toRecurrenceRule(value: String?): RecurrenceRule? =
        if (value.isNullOrBlank()) null
        else runCatching { json.decodeFromString<RecurrenceRule>(value) }.getOrNull()
}
