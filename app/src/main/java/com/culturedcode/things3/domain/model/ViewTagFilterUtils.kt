package com.culturedcode.things3.domain.model

/**
 * Pure domain helper that resolves the ordered list of [Tag]s actively used by the tasks
 * visible in the current screen/view, ensuring unused global tags never clutter the filter bar.
 */
object ViewTagFilterUtils {

    fun resolveUsedTagsForView(
        allTags: List<Tag>,
        rawPrimaryTasks: List<TaskItem>,
        eveningTasks: List<TaskItem>,
        isTodaySmartView: Boolean
    ): List<Tag> {
        val usedIds = HashSet<String>()
        val fallbackTagsById = LinkedHashMap<String, Tag>()

        rawPrimaryTasks.forEach { task ->
            task.tags.forEach { tag ->
                usedIds.add(tag.id)
                fallbackTagsById.putIfAbsent(tag.id, tag)
            }
        }

        if (isTodaySmartView) {
            eveningTasks.forEach { task ->
                task.tags.forEach { tag ->
                    usedIds.add(tag.id)
                    fallbackTagsById.putIfAbsent(tag.id, tag)
                }
            }
        }

        if (usedIds.isEmpty()) return emptyList()

        val orderedFromAll = allTags.filter { it.id in usedIds }
        if (orderedFromAll.size == usedIds.size) return orderedFromAll

        val includedIds = orderedFromAll.mapTo(HashSet()) { it.id }
        return orderedFromAll + fallbackTagsById.values.filter { it.id !in includedIds }
    }
}
