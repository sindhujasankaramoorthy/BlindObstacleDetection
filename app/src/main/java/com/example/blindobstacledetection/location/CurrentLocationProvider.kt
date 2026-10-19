package com.example.blindobstacledetection.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Lifecycle-safe location provider wrapping Google Play Services FusedLocationProviderClient
 * with a resilient fallback to Android framework LocationManager.
 *
 * Requests the current fresh location with timeout handling and does not perform
 * continuous battery-draining tracking.
 */
class CurrentLocationProvider(context: Context) {

    private val appContext: Context = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private var activeCancellationToken: CancellationTokenSource? = null

    /**
     * Checks if device location services (GPS hardware toggle) are enabled.
     */
    fun isLocationServicesEnabled(): Boolean {
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    /**
     * Checks if at least one location permission (Fine or Coarse) is granted.
     */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Checks if precise (Fine) location permission is granted.
     */
    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Requests the single current location fix asynchronously.
     * Guaranteed to execute off the main thread.
     *
     * @param timeoutMs Maximum duration in milliseconds to await GPS fix before failing.
     * @return Result containing [UserLocation] on success, or an informative [Exception] on failure.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMs: Long = 15000L): Result<UserLocation> = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext Result.failure(SecurityException("Location permission is not granted."))
        }

        if (!isLocationServicesEnabled()) {
            return@withContext Result.failure(IllegalStateException("Device location services (GPS) are turned off."))
        }

        val priority = if (hasFineLocationPermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val tokenSource = CancellationTokenSource()
        activeCancellationToken = tokenSource

        val location = withTimeoutOrNull(timeoutMs) {
            try {
                fetchLocationFromFused(priority, tokenSource)
                    ?: fetchLastKnownLocation()
                    ?: fetchFromFrameworkLocationManager()
            } catch (e: Exception) {
                Log.w("CurrentLocationProvider", "Fused location attempt failed: ${e.message}, attempting framework fallback")
                fetchFromFrameworkLocationManager()
            }
        }

        activeCancellationToken = null

        if (location != null) {
            val userLocation = UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                timestamp = location.time,
                provider = location.provider ?: "fused",
                isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock else false
            )
            Result.success(userLocation)
        } else {
            Result.failure(Exception("Location acquisition timed out after ${timeoutMs / 1000}s. Please check GPS settings or step outside for a clear sky view."))
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocationFromFused(
        priority: Int,
        tokenSource: CancellationTokenSource
    ): Location? = suspendCancellableCoroutine { continuation ->
        fusedLocationClient.getCurrentLocation(priority, tokenSource.token)
            .addOnSuccessListener { loc ->
                if (continuation.isActive) continuation.resume(loc)
            }
            .addOnFailureListener { error ->
                Log.w("CurrentLocationProvider", "getCurrentLocation failed: ${error.message}")
                if (continuation.isActive) continuation.resume(null)
            }
            .addOnCanceledListener {
                if (continuation.isActive) continuation.resume(null)
            }

        continuation.invokeOnCancellation {
            tokenSource.cancel()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLastKnownLocation(): Location? = suspendCancellableCoroutine { continuation ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { loc ->
                if (continuation.isActive) continuation.resume(loc)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private fun fetchFromFrameworkLocationManager(): Location? {
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val l = locationManager.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                    bestLocation = l
                }
            }
            bestLocation
        } catch (e: Exception) {
            Log.w("CurrentLocationProvider", "Framework LocationManager error: ${e.message}")
            null
        }
    }

    /**
     * Cancels any pending location request immediately.
     */
    fun cancel() {
        activeCancellationToken?.cancel()
        activeCancellationToken = null
    }
}
