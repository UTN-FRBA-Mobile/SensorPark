package com.sensorpark.data.di

import com.sensorpark.data.firebase.AuthRepositoryFirebase
import com.sensorpark.data.firebase.DeviceRepositoryFirebase
import com.sensorpark.data.firebase.ParkingLotRepositoryFirebase
import com.sensorpark.data.firebase.ReservationRepositoryFirebase
import com.sensorpark.data.firebase.SpotAvailabilityRepositoryFirebase
import com.sensorpark.data.firebase.UserRepositoryFirebase
import com.sensorpark.data.geofence.GeofenceRepositoryPlayServices
import com.sensorpark.data.location.LocationRepositoryPlayServices
import com.sensorpark.data.notifications.AndroidNotificationRepository
import com.sensorpark.data.notifications.ResourceReservationAlertMessages
import com.sensorpark.data.preferences.NotificationPreferencesStore
import com.sensorpark.data.work.WorkManagerReminderScheduler
import com.sensorpark.domain.notification.ReservationAlertMessages
import com.sensorpark.domain.repository.AuthRepository
import com.sensorpark.domain.repository.DeviceRepository
import com.sensorpark.domain.repository.GeofenceRepository
import com.sensorpark.domain.repository.LocationRepository
import com.sensorpark.domain.repository.NotificationPreferencesRepository
import com.sensorpark.domain.repository.NotificationRepository
import com.sensorpark.domain.repository.ParkingLotRepository
import com.sensorpark.domain.repository.ReminderScheduler
import com.sensorpark.domain.repository.ReservationRepository
import com.sensorpark.domain.repository.SpotAvailabilityRepository
import com.sensorpark.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Une cada interfaz de `domain` con su implementación. Los ViewModels piden la interfaz;
 * para sumar un repositorio se agrega acá su @Binds, y en un test se reemplaza por uno falso.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoriesModule {
    @Binds
    @Singleton
    abstract fun authRepository(impl: AuthRepositoryFirebase): AuthRepository

    @Binds
    @Singleton
    abstract fun deviceRepository(impl: DeviceRepositoryFirebase): DeviceRepository

    @Binds
    @Singleton
    abstract fun userRepository(impl: UserRepositoryFirebase): UserRepository

    @Binds
    @Singleton
    abstract fun parkingLotRepository(impl: ParkingLotRepositoryFirebase): ParkingLotRepository

    @Binds
    @Singleton
    abstract fun spotAvailabilityRepository(impl: SpotAvailabilityRepositoryFirebase): SpotAvailabilityRepository

    @Binds
    @Singleton
    abstract fun reservationRepository(impl: ReservationRepositoryFirebase): ReservationRepository

    @Binds
    @Singleton
    abstract fun notificationRepository(impl: AndroidNotificationRepository): NotificationRepository

    @Binds
    @Singleton
    abstract fun notificationPreferencesRepository(
        impl: NotificationPreferencesStore,
    ): NotificationPreferencesRepository

    @Binds
    @Singleton
    abstract fun geofenceRepository(impl: GeofenceRepositoryPlayServices): GeofenceRepository

    @Binds
    @Singleton
    abstract fun locationRepository(impl: LocationRepositoryPlayServices): LocationRepository

    @Binds
    @Singleton
    abstract fun reminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun reservationAlertMessages(impl: ResourceReservationAlertMessages): ReservationAlertMessages
}
