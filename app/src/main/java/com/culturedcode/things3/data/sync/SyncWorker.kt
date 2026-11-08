package com.culturedcode.things3.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.culturedcode.things3.data.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            syncRepository.syncBidirectionalDeltas().fold(
                onSuccess = { Result.success() },
                onFailure = { err ->
                    Log.w("SyncWorker", "Delta sync failed on attempt $runAttemptCount", err)
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w("SyncWorker", "Unexpected SyncWorker failure", t)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_ONE_TIME_SYNC = "things3_immediate_delta_sync"
        private const val UNIQUE_PERIODIC_SYNC = "things3_periodic_delta_sync"

        fun enqueueImmediateDeltaSync(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_ONE_TIME_SYNC,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }.onFailure { err ->
                Log.w("SyncWorker", "Failed to enqueue immediate delta sync", err)
            }
        }

        fun schedulePeriodicDeltaSync(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setInitialDelay(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_SYNC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
            }.onFailure { err ->
                Log.w("SyncWorker", "Failed to schedule periodic delta sync", err)
            }
        }
    }
}
