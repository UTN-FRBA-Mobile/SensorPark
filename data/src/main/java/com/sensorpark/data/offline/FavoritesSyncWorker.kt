package com.sensorpark.data.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sensorpark.domain.repository.FavoritesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sube a Firestore los favoritos marcados o quitados sin red. Corre sólo con conexión (lo decide
 * el sistema) y, si algo falla, reintenta más tarde. Como [com.sensorpark.data.work.ArrivalReminderWorker],
 * toma sus dependencias con un EntryPoint para no sumar `hilt-work`.
 */
class FavoritesSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun favorites(): FavoritesRepository
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        return if (deps.favorites().syncPending()) Result.success() else Result.retry()
    }
}

@Singleton
class WorkManagerFavoritesSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : FavoritesSyncScheduler {

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<FavoritesSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Una sola subida en cola alcanza: sube todos los pendientes, los de antes y los de ahora.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "favorites-sync"
    }
}
