package com.culturedcode.things3.data.local.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class TaskWithDetails(
    @Embedded val task: TaskEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "taskId"
    )
    val checklistItems: List<ChecklistItemEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskTagCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

data class ProjectWithProgressTuple(
    @Embedded val project: ProjectEntity,
    val totalTaskCount: Int,
    val completedTaskCount: Int
)

data class ProjectWithDetails(
    @Embedded val project: ProjectEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val headings: List<HeadingEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val tasks: List<TaskEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ProjectTagCrossRef::class,
            parentColumn = "projectId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

data class AreaWithProjectsAndTasks(
    @Embedded val area: AreaEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "areaId"
    )
    val projects: List<ProjectEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "areaId"
    )
    val tasks: List<TaskEntity>
)
