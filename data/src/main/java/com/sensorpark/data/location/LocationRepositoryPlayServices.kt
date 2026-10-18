package com.sensorpark.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
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

    override fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "SensorParkLocation"
        const val TIMEOUT_MS = 15_000L
    }
}
