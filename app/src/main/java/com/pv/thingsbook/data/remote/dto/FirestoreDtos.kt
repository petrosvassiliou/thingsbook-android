package com.pv.thingsbook.data.remote.dto

import com.pv.thingsbook.data.local.entity.AreaEntity
import com.pv.thingsbook.data.local.entity.AttachmentEntity
import com.pv.thingsbook.data.local.entity.ChecklistItemEntity
import com.pv.thingsbook.data.local.entity.HeadingEntity
import com.pv.thingsbook.data.local.entity.ProjectEntity
import com.pv.thingsbook.data.local.entity.ProjectTemplateEntity
import com.pv.thingsbook.data.local.entity.TagEntity
import com.pv.thingsbook.data.local.entity.TaskEntity
import com.pv.thingsbook.domain.model.EffortLevel
import com.pv.thingsbook.domain.model.MAX_TASK_DURATION_MINUTES
import com.pv.thingsbook.domain.model.ProjectStatus
import com.pv.thingsbook.domain.model.RecurrenceRule
import com.pv.thingsbook.domain.model.ScheduleState
import com.pv.thingsbook.domain.model.TaskStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

private val firestoreJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun Double.finiteOrZero(): Double = if (isFinite()) this else 0.0

@Serializable
data class CloudUserAuthEnvelope(
    val userId: String = "",
    val email: String = "",
    val passwordVerifierHash: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class CloudDeltaBatchPayload(
    val userId: String = "",
    val deviceId: String = "",
    val batchTimestamp: Long = 0L,
    val wakeOnly: Boolean = false,
    val tags: List<TagFirestoreDto> = emptyList(),
    val areas: List<AreaFirestoreDto> = emptyList(),
    val projects: List<ProjectFirestoreDto> = emptyList(),
    val headings: List<HeadingFirestoreDto> = emptyList(),
    val tasks: List<TaskFirestoreDto> = emptyList(),
    val checklistItems: List<ChecklistItemFirestoreDto> = emptyList(),
    val attachments: List<AttachmentFirestoreDto> = emptyList(),
    val projectTemplates: List<ProjectTemplateFirestoreDto> = emptyList()
) {
    companion object {
        fun decodeWithHints(rawJson: String, codec: Json = firestoreJson): CloudDeltaBatchPayload {
            val base = codec.decodeFromString<CloudDeltaBatchPayload>(rawJson)
            if (base.tasks.isEmpty() && base.headings.isEmpty() && base.projects.isEmpty()) return base
            val rootObj = runCatching { codec.parseToJsonElement(rawJson).jsonObject }.getOrNull()
                ?: return base

            var omittedTimeblockIds = emptySet<String>()
            var omittedPinIds = emptySet<String>()
            var omittedV30TaskIds = emptySet<String>()
            runCatching {
                val arr = rootObj["tasks"]?.jsonArray ?: return@runCatching
                val tbSet = HashSet<String>()
                val pinSet = HashSet<String>()
                val v30Set = HashSet<String>()
                for (el in arr) {
                    val obj = el.jsonObject
                    val tid = obj["id"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (tid.isEmpty()) continue
                    if (!obj.containsKey("scheduledStartMinutes")) {
                        tbSet.add(tid)
                    }
                    if (!obj.containsKey("isPinned")) {
                        pinSet.add(tid)
                    }
                    if (!obj.containsKey("effortLevel") &&
                        !obj.containsKey("blockedByTaskIds") &&
                        !obj.containsKey("workspaceId") &&
                        !obj.containsKey("assigneeId")
                    ) {
                        v30Set.add(tid)
                    }
                }
                omittedTimeblockIds = tbSet
                omittedPinIds = pinSet
                omittedV30TaskIds = v30Set
            }

            var omittedEndDateHeadingIds = emptySet<String>()
            var omittedStartDateHeadingIds = emptySet<String>()
            runCatching {
                val arr = rootObj["headings"]?.jsonArray ?: return@runCatching
                val endSet = HashSet<String>()
                val startSet = HashSet<String>()
                for (el in arr) {
                    val obj = el.jsonObject
                    val hid = obj["id"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (hid.isEmpty()) continue
                    if (!obj.containsKey("endDate")) {
                        endSet.add(hid)
                    }
                    if (!obj.containsKey("startDate")) {
                        startSet.add(hid)
                    }
                }
                omittedEndDateHeadingIds = endSet
                omittedStartDateHeadingIds = startSet
            }

            var omittedV30ProjectIds = emptySet<String>()
            runCatching {
                val arr = rootObj["projects"]?.jsonArray ?: return@runCatching
                val pSet = HashSet<String>()
                for (el in arr) {
                    val obj = el.jsonObject
                    val pid = obj["id"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (pid.isEmpty()) continue
                    if (!obj.containsKey("workspaceId") && !obj.containsKey("viewMode")) {
                        pSet.add(pid)
                    }
                }
                omittedV30ProjectIds = pSet
            }

            if (
                omittedTimeblockIds.isEmpty() &&
                omittedPinIds.isEmpty() &&
                omittedV30TaskIds.isEmpty() &&
                omittedEndDateHeadingIds.isEmpty() &&
                omittedStartDateHeadingIds.isEmpty() &&
                omittedV30ProjectIds.isEmpty()
            ) {
                return base
            }

            return base.copy(
                projects = if (omittedV30ProjectIds.isEmpty()) {
                    base.projects
                } else {
                    base.projects.map { dto ->
                        if (dto.id in omittedV30ProjectIds) dto.copy(omittedV30ProjectFields = true) else dto
                    }
                },
                headings = if (omittedEndDateHeadingIds.isEmpty() && omittedStartDateHeadingIds.isEmpty()) {
                    base.headings
                } else {
                    base.headings.map { dto ->
                        val omitEnd = dto.id in omittedEndDateHeadingIds
                        val omitStart = dto.id in omittedStartDateHeadingIds
                        if (omitEnd || omitStart) {
                            dto.copy(
                                omittedEndDate = dto.omittedEndDate || omitEnd,
                                omittedStartDate = dto.omittedStartDate || omitStart
                            )
                        } else {
                            dto
                        }
                    }
                },
                tasks = if (omittedTimeblockIds.isEmpty() && omittedPinIds.isEmpty() && omittedV30TaskIds.isEmpty()) {
                    base.tasks
                } else {
                    base.tasks.map { dto ->
                        val omitTb = dto.id in omittedTimeblockIds
                        val omitPin = dto.id in omittedPinIds
                        val omitV30 = dto.id in omittedV30TaskIds
                        if (omitTb || omitPin || omitV30) {
                            dto.copy(
                                omittedTimeblockFields = dto.omittedTimeblockFields || omitTb,
                                omittedPinField = dto.omittedPinField || omitPin,
                                omittedV30TaskFields = dto.omittedV30TaskFields || omitV30
                            )
                        } else {
                            dto
                        }
                    }
                }
            )
        }
    }
}

@Serializable
data class AreaFirestoreDto(
    val id: String = "",
    val title: String = "",
    val sortOrder: Double = 0.0,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): AreaEntity = AreaEntity(
        id = id,
        title = title,
        sortOrder = sortOrder.finiteOrZero(),
        tagIdsJson = firestoreJson.encodeToString(tagIds),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "sortOrder" to sortOrder.finiteOrZero(),
        "tagIds" to tagIds,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    companion object {
        fun fromEntity(entity: AreaEntity): AreaFirestoreDto = AreaFirestoreDto(
            id = entity.id,
            title = entity.title,
            sortOrder = entity.sortOrder.finiteOrZero(),
            tagIds = runCatching { firestoreJson.decodeFromString<List<String>>(entity.tagIdsJson) }.getOrDefault(emptyList()),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )

        fun fromFirestoreMap(map: Map<String, Any?>?): AreaFirestoreDto? {
            if (map == null) return null
            val id = (map["id"] as? String)?.trim() ?: return null
            if (id.isEmpty()) return null
            return AreaFirestoreDto(
                id = id,
                title = map["title"] as? String ?: "",
                sortOrder = ((map["sortOrder"] as? Number)?.toDouble() ?: 0.0).finiteOrZero(),
                tagIds = (map["tagIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                isDeleted = map["isDeleted"] as? Boolean ?: false
            )
        }
    }
}

@Serializable
data class ProjectFirestoreDto(
    val id: String = "",
    val areaId: String? = null,
    val title: String = "",
    val notes: String = "",
    val status: String = ProjectStatus.ACTIVE.name,
    val scheduleState: String = ScheduleState.ANYTIME.name,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val completedAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val sortOrder: Double = 0.0,
    val tagIds: List<String> = emptyList(),
    val workspaceId: String? = null,
    val viewMode: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val purgedFromTrash: Boolean = false,
    @Transient
    val omittedV30ProjectFields: Boolean = false
) {
    fun toEntity(): ProjectEntity {
        val isPurgedTombstone = isDeleted && (purgedFromTrash || (title.isEmpty() && notes.isEmpty()))
        return ProjectEntity(
            id = id,
            areaId = if (isPurgedTombstone) null else areaId?.takeIf { it.isNotBlank() },
            title = if (isPurgedTombstone) "" else title,
            notes = if (isPurgedTombstone) "" else notes,
            status = runCatching { ProjectStatus.valueOf(status.trim().uppercase(Locale.ROOT)) }.getOrDefault(ProjectStatus.ACTIVE),
            scheduleState = runCatching { ScheduleState.valueOf(scheduleState.trim().uppercase(Locale.ROOT)) }.getOrDefault(ScheduleState.ANYTIME),
            whenDate = whenDate,
            deadline = deadline,
            completedAt = completedAt,
            lastReviewedAt = lastReviewedAt,
            sortOrder = sortOrder.finiteOrZero(),
            tagIdsJson = firestoreJson.encodeToString(if (isPurgedTombstone) emptyList() else tagIds),
            workspaceId = if (isPurgedTombstone) null else workspaceId?.takeIf { it.isNotBlank() },
            viewMode = if (isPurgedTombstone) null else viewMode?.takeIf { it.isNotBlank() },
            createdAt = createdAt,
            updatedAt = updatedAt,
            isDeleted = isDeleted,
            isDirty = false
        )
    }

    fun toFirestoreMap(): Map<String, Any?> {
        val isPurgedTombstone = isDeleted && (purgedFromTrash || (title.isEmpty() && notes.isEmpty()))
        return mapOf(
            "id" to id,
            "areaId" to areaId,
            "title" to title,
            "notes" to notes,
            "status" to status,
            "scheduleState" to scheduleState,
            "whenDate" to whenDate,
            "deadline" to deadline,
            "completedAt" to completedAt,
            "lastReviewedAt" to lastReviewedAt,
            "sortOrder" to sortOrder.finiteOrZero(),
            "tagIds" to tagIds,
            "workspaceId" to (if (isPurgedTombstone) null else workspaceId),
            "viewMode" to (if (isPurgedTombstone) null else viewMode),
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "isDeleted" to isDeleted,
            "purgedFromTrash" to isPurgedTombstone
        )
    }

    companion object {
        fun fromEntity(entity: ProjectEntity): ProjectFirestoreDto {
            val isPurgedTombstone = entity.isDeleted && entity.title.isEmpty() && entity.notes.isEmpty()
            return ProjectFirestoreDto(
                id = entity.id,
                areaId = entity.areaId,
                title = entity.title,
                notes = entity.notes,
                status = entity.status.name,
                scheduleState = entity.scheduleState.name,
                whenDate = entity.whenDate,
                deadline = entity.deadline,
                completedAt = entity.completedAt,
                lastReviewedAt = entity.lastReviewedAt,
                sortOrder = entity.sortOrder.finiteOrZero(),
                tagIds = runCatching { firestoreJson.decodeFromString<List<String>>(entity.tagIdsJson) }.getOrDefault(emptyList()),
                workspaceId = if (isPurgedTombstone) null else entity.workspaceId,
                viewMode = if (isPurgedTombstone) null else entity.viewMode,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isDeleted = entity.isDeleted,
                purgedFromTrash = isPurgedTombstone
            )
        }

        fun fromFirestoreMap(map: Map<String, Any?>?): ProjectFirestoreDto? {
            if (map == null) return null
            val id = (map["id"] as? String)?.trim() ?: return null
            if (id.isEmpty()) return null
            val title = map["title"] as? String ?: ""
            val notes = map["notes"] as? String ?: ""
            val isDeleted = map["isDeleted"] as? Boolean ?: false
            val purged = (map["purgedFromTrash"] as? Boolean == true) || (isDeleted && title.isEmpty() && notes.isEmpty())
            val omittedV30 = !map.containsKey("workspaceId") && !map.containsKey("viewMode")
            return ProjectFirestoreDto(
                id = id,
                areaId = (map["areaId"] as? String)?.takeIf { it.isNotBlank() },
                title = title,
                notes = notes,
                status = map["status"] as? String ?: ProjectStatus.ACTIVE.name,
                scheduleState = map["scheduleState"] as? String ?: ScheduleState.ANYTIME.name,
                whenDate = (map["whenDate"] as? Number)?.toLong(),
                deadline = (map["deadline"] as? Number)?.toLong(),
                completedAt = (map["completedAt"] as? Number)?.toLong(),
                lastReviewedAt = (map["lastReviewedAt"] as? Number)?.toLong(),
                sortOrder = ((map["sortOrder"] as? Number)?.toDouble() ?: 0.0).finiteOrZero(),
                tagIds = (map["tagIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                workspaceId = if (purged) null else (map["workspaceId"] as? String)?.takeIf { it.isNotBlank() },
                viewMode = if (purged) null else (map["viewMode"] as? String)?.takeIf { it.isNotBlank() },
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                isDeleted = isDeleted,
                purgedFromTrash = purged,
                omittedV30ProjectFields = omittedV30
            )
        }
    }
}

@Serializable
data class HeadingFirestoreDto(
    val id: String = "",
    val projectId: String = "",
    val title: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val sortOrder: Double = 0.0,
    val isArchived: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val omittedEndDate: Boolean = false,
    @Transient
    val omittedStartDate: Boolean = false
) {
    fun toEntity(): HeadingEntity = HeadingEntity(
        id = id,
        projectId = projectId,
        title = title,
        startDate = if (isDeleted && title.isEmpty()) null else startDate,
        endDate = if (isDeleted && title.isEmpty()) null else endDate,
        sortOrder = sortOrder.finiteOrZero(),
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "projectId" to projectId,
        "title" to title,
        "startDate" to startDate,
        "endDate" to endDate,
        "sortOrder" to sortOrder.finiteOrZero(),
        "isArchived" to isArchived,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    companion object {
        fun fromEntity(entity: HeadingEntity): HeadingFirestoreDto = HeadingFirestoreDto(
            id = entity.id,
            projectId = entity.projectId,
            title = entity.title,
            startDate = entity.startDate,
            endDate = entity.endDate,
            sortOrder = entity.sortOrder.finiteOrZero(),
            isArchived = entity.isArchived,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )

        fun fromFirestoreMap(map: Map<String, Any?>?): HeadingFirestoreDto? {
            if (map == null) return null
            val id = (map["id"] as? String)?.trim() ?: return null
            val projectId = (map["projectId"] as? String)?.trim() ?: return null
            if (id.isEmpty() || projectId.isEmpty()) return null
            val omittedEndDate = !map.containsKey("endDate")
            val omittedStartDate = !map.containsKey("startDate")
            return HeadingFirestoreDto(
                id = id,
                projectId = projectId,
                title = map["title"] as? String ?: "",
                startDate = (map["startDate"] as? Number)?.toLong(),
                endDate = (map["endDate"] as? Number)?.toLong(),
                sortOrder = ((map["sortOrder"] as? Number)?.toDouble() ?: 0.0).finiteOrZero(),
                isArchived = map["isArchived"] as? Boolean ?: false,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                isDeleted = map["isDeleted"] as? Boolean ?: false,
                omittedEndDate = omittedEndDate,
                omittedStartDate = omittedStartDate
            )
        }
    }
}

@Serializable
data class TaskFirestoreDto(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val status: String = TaskStatus.OPEN.name,
    val scheduleState: String = ScheduleState.INBOX.name,
    val whenDate: Long? = null,
    val deadline: Long? = null,
    val reminderTime: Long? = null,
    val scheduledStartMinutes: Int? = null,
    val durationMinutes: Int? = null,
    val completedAt: Long? = null,
    val projectId: String? = null,
    val headingId: String? = null,
    val areaId: String? = null,
    val recurrenceRule: String? = null,
    val sortOrder: Double = 0.0,
    val isPinned: Boolean = false,
    val isMilestone: Boolean? = null,
    val effortLevel: String? = null,
    val blockedByTaskIds: List<String> = emptyList(),
    val workspaceId: String? = null,
    val assigneeId: String? = null,
    val tagIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val purgedFromTrash: Boolean = false,
    @Transient
    val omittedTimeblockFields: Boolean = false,
    @Transient
    val omittedPinField: Boolean = false,
    @Transient
    val omittedV30TaskFields: Boolean = false
) {
    fun toEntity(existingLocal: TaskEntity? = null): TaskEntity {
        val isPurgedTombstone = isDeleted && (purgedFromTrash || (title.isEmpty() && notes.isEmpty()))
        val cleanBlockers = if (isPurgedTombstone) {
            emptyList()
        } else {
            blockedByTaskIds.map { it.trim() }.filter { it.isNotEmpty() && it != id }.distinct()
        }
        return TaskEntity(
            id = id,
            title = if (isPurgedTombstone) "" else title,
            notes = if (isPurgedTombstone) "" else notes,
            status = runCatching { TaskStatus.valueOf(status.trim().uppercase(Locale.ROOT)) }.getOrDefault(TaskStatus.OPEN),
            scheduleState = runCatching { ScheduleState.valueOf(scheduleState.trim().uppercase(Locale.ROOT)) }.getOrDefault(ScheduleState.INBOX),
            whenDate = whenDate,
            deadline = deadline,
            reminderTime = if (isPurgedTombstone) null else reminderTime,
            scheduledStartMinutes = if (isPurgedTombstone) null else scheduledStartMinutes?.takeIf { it in 0..1439 },
            durationMinutes = if (isPurgedTombstone) null else durationMinutes?.takeIf { it in 1..MAX_TASK_DURATION_MINUTES },
            completedAt = completedAt,
            projectId = if (isPurgedTombstone) null else projectId?.takeIf { it.isNotBlank() },
            headingId = if (isPurgedTombstone) null else headingId?.takeIf { it.isNotBlank() },
            areaId = if (isPurgedTombstone) null else areaId?.takeIf { it.isNotBlank() },
            recurrenceRuleJson = if (isPurgedTombstone) null else recurrenceRule?.takeIf {
                it.isNotBlank() && runCatching { firestoreJson.decodeFromString<RecurrenceRule>(it) }.isSuccess
            },
            sortOrder = sortOrder.finiteOrZero(),
            isPinned = if (isPurgedTombstone) false else isPinned,
            isMilestone = if (isPurgedTombstone) false else (isMilestone ?: existingLocal?.isMilestone ?: false),
            effortLevel = if (isPurgedTombstone) null else EffortLevel.normalize(effortLevel),
            blockedByJson = firestoreJson.encodeToString(cleanBlockers),
            workspaceId = if (isPurgedTombstone) null else workspaceId?.takeIf { it.isNotBlank() },
            assigneeId = if (isPurgedTombstone) null else assigneeId?.takeIf { it.isNotBlank() },
            tagIdsJson = firestoreJson.encodeToString(if (isPurgedTombstone) emptyList() else tagIds),
            createdAt = createdAt,
            updatedAt = updatedAt,
            isDeleted = isDeleted,
            isDirty = false
        )
    }

    fun toFirestoreMap(): Map<String, Any?> {
        val isPurgedTombstone = isDeleted && (purgedFromTrash || (title.isEmpty() && notes.isEmpty()))
        val cleanBlockers = if (isPurgedTombstone) {
            emptyList()
        } else {
            blockedByTaskIds.map { it.trim() }.filter { it.isNotEmpty() && it != id }.distinct()
        }
        return mapOf(
            "id" to id,
            "title" to title,
            "notes" to notes,
            "status" to status,
            "scheduleState" to scheduleState,
            "whenDate" to whenDate,
            "deadline" to deadline,
            "reminderTime" to reminderTime,
            "scheduledStartMinutes" to scheduledStartMinutes,
            "durationMinutes" to durationMinutes,
            "completedAt" to completedAt,
            "projectId" to projectId,
            "headingId" to headingId,
            "areaId" to areaId,
            "recurrenceRule" to recurrenceRule,
            "sortOrder" to sortOrder.finiteOrZero(),
            "isPinned" to (if (isPurgedTombstone) false else isPinned),
            "isMilestone" to (if (isPurgedTombstone) false else (isMilestone ?: false)),
            "effortLevel" to (if (isPurgedTombstone) null else EffortLevel.normalize(effortLevel)),
            "blockedByTaskIds" to cleanBlockers,
            "workspaceId" to (if (isPurgedTombstone) null else workspaceId),
            "assigneeId" to (if (isPurgedTombstone) null else assigneeId),
            "tagIds" to tagIds,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
            "isDeleted" to isDeleted,
            "purgedFromTrash" to isPurgedTombstone
        )
    }

    companion object {
        fun fromEntity(entity: TaskEntity): TaskFirestoreDto {
            val isPurgedTombstone = entity.isDeleted && entity.title.isEmpty() && entity.notes.isEmpty()
            val decodedBlockers = if (isPurgedTombstone) {
                emptyList()
            } else {
                runCatching { firestoreJson.decodeFromString<List<String>>(entity.blockedByJson) }
                    .getOrDefault(emptyList())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it != entity.id }
                    .distinct()
            }
            return TaskFirestoreDto(
                id = entity.id,
                title = entity.title,
                notes = entity.notes,
                status = entity.status.name,
                scheduleState = entity.scheduleState.name,
                whenDate = entity.whenDate,
                deadline = entity.deadline,
                reminderTime = entity.reminderTime,
                scheduledStartMinutes = entity.scheduledStartMinutes,
                durationMinutes = entity.durationMinutes,
                completedAt = entity.completedAt,
                projectId = entity.projectId,
                headingId = entity.headingId,
                areaId = entity.areaId,
                recurrenceRule = entity.recurrenceRuleJson,
                sortOrder = entity.sortOrder.finiteOrZero(),
                isPinned = if (isPurgedTombstone) false else entity.isPinned,
                isMilestone = if (isPurgedTombstone) false else entity.isMilestone,
                effortLevel = if (isPurgedTombstone) null else EffortLevel.normalize(entity.effortLevel),
                blockedByTaskIds = decodedBlockers,
                workspaceId = if (isPurgedTombstone) null else entity.workspaceId,
                assigneeId = if (isPurgedTombstone) null else entity.assigneeId,
                tagIds = runCatching { firestoreJson.decodeFromString<List<String>>(entity.tagIdsJson) }.getOrDefault(emptyList()),
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                isDeleted = entity.isDeleted,
                purgedFromTrash = isPurgedTombstone
            )
        }

        fun fromFirestoreMap(map: Map<String, Any?>?): TaskFirestoreDto? {
            if (map == null) return null
            val id = (map["id"] as? String)?.trim() ?: return null
            if (id.isEmpty()) return null
            val title = map["title"] as? String ?: ""
            val notes = map["notes"] as? String ?: ""
            val isDeleted = map["isDeleted"] as? Boolean ?: false
            val purged = (map["purgedFromTrash"] as? Boolean == true) || (isDeleted && title.isEmpty() && notes.isEmpty())
            val omittedTimeblock = !map.containsKey("scheduledStartMinutes") && !map.containsKey("durationMinutes")
            val omittedPin = !map.containsKey("isPinned")
            val omittedV30 = !map.containsKey("effortLevel") &&
                !map.containsKey("blockedByTaskIds") &&
                !map.containsKey("blockedByJson") &&
                !map.containsKey("workspaceId") &&
                !map.containsKey("assigneeId")
            val rawIsPinned = map["isPinned"] as? Boolean ?: false
            val rawIsMilestone = map["isMilestone"] as? Boolean
            val rawBlockers = (map["blockedByTaskIds"] as? List<*>)?.filterIsInstance<String>()
                ?: (map["blockedByJson"] as? String)?.let { raw ->
                    runCatching { firestoreJson.decodeFromString<List<String>>(raw) }.getOrNull()
                }
                ?: emptyList()
            return TaskFirestoreDto(
                id = id,
                title = title,
                notes = notes,
                status = map["status"] as? String ?: TaskStatus.OPEN.name,
                scheduleState = map["scheduleState"] as? String ?: ScheduleState.INBOX.name,
                whenDate = (map["whenDate"] as? Number)?.toLong(),
                deadline = (map["deadline"] as? Number)?.toLong(),
                reminderTime = (map["reminderTime"] as? Number)?.toLong(),
                scheduledStartMinutes = (map["scheduledStartMinutes"] as? Number)?.toInt(),
                durationMinutes = (map["durationMinutes"] as? Number)?.toInt(),
                completedAt = (map["completedAt"] as? Number)?.toLong(),
                projectId = (map["projectId"] as? String)?.takeIf { it.isNotBlank() },
                headingId = (map["headingId"] as? String)?.takeIf { it.isNotBlank() },
                areaId = (map["areaId"] as? String)?.takeIf { it.isNotBlank() },
                recurrenceRule = (map["recurrenceRule"] as? String)?.takeIf { it.isNotBlank() },
                sortOrder = ((map["sortOrder"] as? Number)?.toDouble() ?: 0.0).finiteOrZero(),
                isPinned = if (purged) false else rawIsPinned,
                isMilestone = if (purged) false else rawIsMilestone,
                effortLevel = if (purged) null else EffortLevel.normalize(map["effortLevel"] as? String),
                blockedByTaskIds = if (purged) emptyList() else rawBlockers.map { it.trim() }.filter { it.isNotEmpty() && it != id }.distinct(),
                workspaceId = if (purged) null else (map["workspaceId"] as? String)?.takeIf { it.isNotBlank() },
                assigneeId = if (purged) null else (map["assigneeId"] as? String)?.takeIf { it.isNotBlank() },
                tagIds = (map["tagIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                isDeleted = isDeleted,
                purgedFromTrash = purged,
                omittedTimeblockFields = omittedTimeblock,
                omittedPinField = omittedPin,
                omittedV30TaskFields = omittedV30
            )
        }
    }
}

@Serializable
data class ChecklistItemFirestoreDto(
    val id: String = "",
    val taskId: String = "",
    val title: String = "",
    val isCompleted: Boolean = false,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): ChecklistItemEntity = ChecklistItemEntity(
        id = id,
        taskId = taskId,
        title = title,
        isCompleted = isCompleted,
        sortOrder = sortOrder.finiteOrZero(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "taskId" to taskId,
        "title" to title,
        "isCompleted" to isCompleted,
        "sortOrder" to sortOrder.finiteOrZero(),
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    companion object {
        fun fromEntity(entity: ChecklistItemEntity): ChecklistItemFirestoreDto = ChecklistItemFirestoreDto(
            id = entity.id,
            taskId = entity.taskId,
            title = entity.title,
            isCompleted = entity.isCompleted,
            sortOrder = entity.sortOrder.finiteOrZero(),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )

        fun fromFirestoreMap(map: Map<String, Any?>?): ChecklistItemFirestoreDto? {
            if (map == null) return null
            val id = (map["id"] as? String)?.trim() ?: return null
            val taskId = (map["taskId"] as? String)?.trim() ?: return null
            if (id.isEmpty() || taskId.isEmpty()) return null
            return ChecklistItemFirestoreDto(
                id = id,
                taskId = taskId,
                title = map["title"] as? String ?: "",
                isCompleted = map["isCompleted"] as? Boolean ?: false,
                sortOrder = ((map["sortOrder"] as? Number)?.toDouble() ?: 0.0).finiteOrZero(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                isDeleted = map["isDeleted"] as? Boolean ?: false
            )
        }
    }
}

@Serializable
data class TagFirestoreDto(
    val id: String = "",
    val title: String = "",
    val parentTagId: String? = null,
    val shortcut: String? = null,
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): TagEntity = TagEntity(
        id = id,
        title = title,
        parentTagId = parentTagId?.takeIf { it.isNotBlank() },
        shortcut = shortcut?.takeIf { it.isNotBlank() },
        sortOrder = sortOrder.finiteOrZero(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "parentTagId" to parentTagId,
        "shortcut" to shortcut,
        "sortOrder" to sortOrder.finiteOrZero(),
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    companion object {
        fun fromEntity(entity: TagEntity): TagFirestoreDto = TagFirestoreDto(
            id = entity.id,
            title = entity.title,
            parentTagId = entity.parentTagId,
            shortcut = entity.shortcut,
            sortOrder = entity.sortOrder.finiteOrZero(),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )

        fun fromFirestoreMap(map: Map<String, Any?>?): TagFirestoreDto? {
            if (map == null) return null
            val id = (map["id"] as? String)?.trim() ?: return null
            if (id.isEmpty()) return null
            return TagFirestoreDto(
                id = id,
                title = map["title"] as? String ?: "",
                parentTagId = (map["parentTagId"] as? String)?.takeIf { it.isNotBlank() },
                shortcut = (map["shortcut"] as? String)?.takeIf { it.isNotBlank() },
                sortOrder = ((map["sortOrder"] as? Number)?.toDouble() ?: 0.0).finiteOrZero(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                isDeleted = map["isDeleted"] as? Boolean ?: false
            )
        }
    }
}

@Serializable
data class AttachmentFirestoreDto(
    val id: String = "",
    val taskId: String? = null,
    val projectId: String? = null,
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val sortOrder: Double = 0.0,
    val localRelativePath: String = "",
    val dataBase64: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(resolvedRelativePath: String = localRelativePath): AttachmentEntity = AttachmentEntity(
        id = id,
        taskId = taskId?.takeIf { it.isNotBlank() },
        projectId = projectId?.takeIf { it.isNotBlank() },
        fileName = fileName,
        mimeType = mimeType,
        sizeBytes = sizeBytes.coerceAtLeast(0L),
        localRelativePath = resolvedRelativePath,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "taskId" to taskId,
        "projectId" to projectId,
        "fileName" to fileName,
        "mimeType" to mimeType,
        "sizeBytes" to sizeBytes,
        "sortOrder" to sortOrder.finiteOrZero(),
        "localRelativePath" to "",
        "dataBase64" to dataBase64,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    companion object {
        fun fromEntity(entity: AttachmentEntity, dataBase64: String = ""): AttachmentFirestoreDto = AttachmentFirestoreDto(
            id = entity.id,
            taskId = entity.taskId,
            projectId = entity.projectId,
            fileName = entity.fileName,
            mimeType = entity.mimeType,
            sizeBytes = entity.sizeBytes,
            sortOrder = entity.createdAt.toDouble().finiteOrZero(),
            localRelativePath = "",
            dataBase64 = dataBase64,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )

        fun fromFirestoreMap(map: Map<String, Any?>?): AttachmentFirestoreDto? {
            if (map == null) return null
            val id = (map["id"] as? String)?.trim() ?: return null
            if (id.isEmpty()) return null
            val fileName = map["fileName"] as? String ?: "attachment"
            val createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
            val sortOrder = ((map["sortOrder"] as? Number)?.toDouble() ?: createdAt.toDouble()).finiteOrZero()
            return AttachmentFirestoreDto(
                id = id,
                taskId = (map["taskId"] as? String)?.takeIf { it.isNotBlank() },
                projectId = (map["projectId"] as? String)?.takeIf { it.isNotBlank() },
                fileName = fileName,
                mimeType = map["mimeType"] as? String ?: "application/octet-stream",
                sizeBytes = (map["sizeBytes"] as? Number)?.toLong() ?: 0L,
                sortOrder = sortOrder,
                localRelativePath = "",
                dataBase64 = map["dataBase64"] as? String ?: "",
                createdAt = createdAt,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                isDeleted = map["isDeleted"] as? Boolean ?: false
            )
        }
    }
}

@Serializable
data class ProjectTemplateFirestoreDto(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    val defaultAreaId: String? = null,
    val tagIds: List<String> = emptyList(),
    val blueprintJson: String = "{}",
    val sortOrder: Double = 0.0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false
) {
    fun toEntity(): ProjectTemplateEntity = ProjectTemplateEntity(
        id = id,
        title = title,
        notes = notes,
        defaultAreaId = defaultAreaId?.takeIf { it.isNotBlank() },
        tagIdsJson = firestoreJson.encodeToString(tagIds),
        blueprintJson = blueprintJson.ifBlank { "{}" },
        sortOrder = sortOrder.finiteOrZero(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        isDirty = false
    )

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "notes" to notes,
        "defaultAreaId" to defaultAreaId,
        "tagIds" to tagIds,
        "blueprintJson" to blueprintJson,
        "sortOrder" to sortOrder.finiteOrZero(),
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "isDeleted" to isDeleted
    )

    companion object {
        fun fromEntity(entity: ProjectTemplateEntity): ProjectTemplateFirestoreDto = ProjectTemplateFirestoreDto(
            id = entity.id,
            title = entity.title,
            notes = entity.notes,
            defaultAreaId = entity.defaultAreaId,
            tagIds = runCatching { firestoreJson.decodeFromString<List<String>>(entity.tagIdsJson) }.getOrDefault(emptyList()),
            blueprintJson = entity.blueprintJson,
            sortOrder = entity.sortOrder.finiteOrZero(),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            isDeleted = entity.isDeleted
        )

        fun fromFirestoreMap(map: Map<String, Any?>?): ProjectTemplateFirestoreDto? {
            if (map == null) return null
            val id = (map["id"] as? String)?.trim() ?: return null
            if (id.isEmpty()) return null
            return ProjectTemplateFirestoreDto(
                id = id,
                title = map["title"] as? String ?: "",
                notes = map["notes"] as? String ?: "",
                defaultAreaId = (map["defaultAreaId"] as? String)?.takeIf { it.isNotBlank() },
                tagIds = (map["tagIds"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                blueprintJson = map["blueprintJson"] as? String ?: "{}",
                sortOrder = ((map["sortOrder"] as? Number)?.toDouble() ?: 0.0).finiteOrZero(),
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
                isDeleted = map["isDeleted"] as? Boolean ?: false
            )
        }
    }
}

fun AttachmentEntity.toFirestoreDto(dataBase64: String = ""): AttachmentFirestoreDto =
    AttachmentFirestoreDto.fromEntity(this, dataBase64)



