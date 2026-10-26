package com.sensorpark.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sensorpark.domain.model.Coordinates
import com.sensorpark.domain.model.LocationReading
import com.sensorpark.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pide una lectura nueva y de alta precisión con FusedLocationProvider. No usa la última conocida:
 * para el check-in importa dónde está el conductor ahora.
 */
@Singleton
class LocationRepositoryPlayServices @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationRepository {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationReading? {
        if (!hasLocationPermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
                    ?.let { LocationReading(Coordinates(it.latitude, it.longitude), it.accuracy) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer la ubicación", e)
            null
        } finally {
            cancellation.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun approximateLocation(): LocationReading? {
        if (!hasApproximatePermission()) return null
        val cancellation = CancellationTokenSource()
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            // Una lectura de hace menos de un minuto sirve: no hace falta prender el GPS de nuevo.
            .setMaxUpdateAgeMillis(MAX_AGE_MS)
            .setDurationMillis(APPROXIMATE_TIMEOUT_MS)
            .build()
        return try {
            withTimeoutOrNull(APPROXIMATE_TIMEOUT_MS + 2_000) {
                (client.getCurrentLocation(request, cancellation.token).await() ?: client.lastLocation.await())
                    ?.let { LocationReading(Coordinates(it.latitude, it.longitude), it.accuracy) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer la ubicación aproximada", e)
            null
        } finally {
            cancellation.cancel()
        }
    }

    override fun hasApproximatePermission(): Boolean =
        hasLocationPermission() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "SensorParkLocation"
        const val TIMEOUT_MS = 15_000L
        const val APPROXIMATE_TIMEOUT_MS = 8_000L
        const val MAX_AGE_MS = 60_000L
    }
}
