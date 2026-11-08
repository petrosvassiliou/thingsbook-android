package com.culturedcode.things3.domain.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Coroutine-safe equivalent of [kotlin.runCatching] that ALWAYS rethrows [CancellationException]
 * to preserve Kotlin structured concurrency while catching all other [Throwable]s into [Result].
 */
inline fun <R> runCatchingCancellable(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
