package com.sensorpark.data.geofence

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.sensorpark.data.local.GeofenceDao
import com.sensorpark.data.local.GeofenceEntity
import com.sensorpark.domain.model.ActiveGeofence
import com.sensorpark.domain.repository.GeofenceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Geocercas con la Geofencing API de Play Services, más una copia en Room.
 *
 * La fila de Room se guarda siempre, incluso cuando el registro en el sistema no se puede hacer
 * por falta de permiso: es la marca de que esta reserva ya fue tenida en cuenta, y es de donde
 * salen las geocercas cuando hay que volver a crearlas tras un reinicio.
 */
@Singleton
class GeofenceRepositoryPlayServices @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: GeofenceDao,
    private val clock: Clock,
) : GeofenceRepository {

    private val client by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    override suspend fun register(geofence: ActiveGeofence): Boolean {
        dao.save(GeofenceEntity.from(geofence))
        return add(geofence)
    }

    override suspend fun remove(reservationId: String) {
        dao.delete(reservationId)
        runCatching { client.removeGeofences(listOf(reservationId)).await() }
            .onFailure { Log.w(TAG, "No se pudo quitar la geocerca de $reservationId", it) }
    }

    override suspend fun active(): List<ActiveGeofence> = dao.all().map { it.toDomain() }

    override suspend fun find(reservationId: String): ActiveGeofence? = dao.find(reservationId)?.toDomain()

    override suspend fun restoreAll() {
        val now = clock.instant()
        dao.all().map { it.toDomain() }.forEach { geofence ->
            if (geofence.expiresAt <= now) remove(geofence.reservationId) else add(geofence)
        }
    }

    override fun hasBackgroundLocationPermission(): Boolean {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        // Antes de Android 10 el permiso común ya alcanzaba para el segundo plano.
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        return fine && background
    }

    @SuppressLint("MissingPermission")
    private suspend fun add(geofence: ActiveGeofence): Boolean {
        if (!hasBackgroundLocationPermission()) return false
        val durationMillis = geofence.expiresAt.toEpochMilli() - clock.millis()
        if (durationMillis <= 0) return false

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(
                Geofence.Builder()
                    .setRequestId(geofence.reservationId)
                    .setCircularRegion(
                        geofence.center.latitude,
                        geofence.center.longitude,
                        geofence.radiusMeters.toFloat(),
                    )
                    .setExpirationDuration(durationMillis)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build(),
            )
            .build()

        return runCatching { client.addGeofences(request, pendingIntent).await() }
            .onFailure { Log.w(TAG, "No se pudo registrar la geocerca de ${geofence.reservationId}", it) }
            .isSuccess
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "SensorParkGeofence"
    }
}
