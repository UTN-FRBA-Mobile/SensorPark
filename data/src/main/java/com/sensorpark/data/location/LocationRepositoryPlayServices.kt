package com.sensorpark.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
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

/** La ubicación con FusedLocationProvider, que combina GPS, Wi-Fi y antenas. */
@Singleton
class LocationRepositoryPlayServices @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationRepository {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /** Para el check-in: lectura nueva y precisa. No sirve la última conocida. */
    override suspend fun currentLocation(): LocationReading? =
        fresh(Priority.PRIORITY_HIGH_ACCURACY)

    /**
     * Para buscar cerca: precisión balanceada, que no prende el GPS y gasta mucho menos batería.
     * Si no llega una lectura nueva, alcanza con la última conocida.
     */
    @SuppressLint("MissingPermission")
    override suspend fun approximateLocation(): LocationReading? {
        if (!hasLocationPermission()) return null
        return fresh(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            ?: runCatching { client.lastLocation.await()?.toReading() }.getOrNull()
    }

    override fun hasLocationPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    @SuppressLint("MissingPermission")
    private suspend fun fresh(priority: Int): LocationReading? {
        if (!hasLocationPermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                client.getCurrentLocation(priority, cancellation.token).await()?.toReading()
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer la ubicación", e)
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toReading() = LocationReading(Coordinates(latitude, longitude), accuracy)

    private companion object {
        const val TAG = "SensorParkLocation"
        const val TIMEOUT_MS = 15_000L
    }
}
