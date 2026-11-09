package com.culturedcode.things3.domain.model

/**
 * Pure domain resolver for the Thingsbook Taskbar & Launcher Icon "Today" (`Heute`) count badge.
 *
 * Maps `(todayCount, enabled)` to:
 * - The resource badge key (`"0"`, `"1"`..`"20"`, `"20plus"`),
 * - The `<activity-alias>` class name (`LauncherBadge0`, `LauncherBadge1`..`LauncherBadge20`, `LauncherBadge20Plus`),
 * - The visible badge label (`null`, `"1"`..`"20"`, `"20+"`),
 * - The window / taskbar `TaskDescription` title (`"Thingsbook (3)"` vs `"Thingsbook"`).
 */
object TaskbarBadgeResolver {

    const val MAX_EXACT_BADGE_COUNT: Int = 20

    private val ALL_ALIAS_SUFFIXES: List<String> = buildList {
        add("0")
        for (i in 1..MAX_EXACT_BADGE_COUNT) {
            add(i.toString())
        }
        add("20Plus")
    }

    @JvmStatic
    fun allAliasSuffixes(): List<String> = ALL_ALIAS_SUFFIXES

    @JvmStatic
    fun resolveEffectiveCount(todayCount: Int, enabled: Boolean): Int {
        if (!enabled || todayCount <= 0) return 0
        return todayCount
    }

    @JvmStatic
    fun resolveBadgeKey(todayCount: Int, enabled: Boolean): String {
        val effective = resolveEffectiveCount(todayCount, enabled)
        return when {
            effective <= 0 -> "0"
            effective <= MAX_EXACT_BADGE_COUNT -> effective.toString()
            else -> "20plus"
        }
    }

    @JvmStatic
    fun resolveAliasSuffix(todayCount: Int, enabled: Boolean): String {
        val effective = resolveEffectiveCount(todayCount, enabled)
        return when {
            effective <= 0 -> "0"
            effective <= MAX_EXACT_BADGE_COUNT -> effective.toString()
            else -> "20Plus"
        }
    }

    @JvmStatic
    fun resolveAliasClassName(packageName: String, todayCount: Int, enabled: Boolean): String {
        val suffix = resolveAliasSuffix(todayCount, enabled)
        return "$packageName.LauncherBadge$suffix"
    }

    @JvmStatic
    fun resolveBadgeDisplayText(todayCount: Int, enabled: Boolean): String? {
        val effective = resolveEffectiveCount(todayCount, enabled)
        return when {
            effective <= 0 -> null
            effective <= MAX_EXACT_BADGE_COUNT -> effective.toString()
            else -> "${MAX_EXACT_BADGE_COUNT}+"
        }
    }

    @JvmStatic
    fun resolveTaskDescriptionTitle(appName: String, todayCount: Int, enabled: Boolean): String {
        val effective = resolveEffectiveCount(todayCount, enabled)
        return if (effective > 0) {
            "$appName ($effective)"
        } else {
            appName
        }
    }
}
