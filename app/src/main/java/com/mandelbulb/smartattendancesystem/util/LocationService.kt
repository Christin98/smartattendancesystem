package com.mandelbulb.smartattendancesystem.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        5000L
    ).apply {
        setMinUpdateIntervalMillis(2000L)
        setMaxUpdates(1)
    }.build()

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData? {
        if (!hasLocationPermission()) {
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            try {
                // First try to get last known location
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null &&
                            System.currentTimeMillis() - location.time < 60000) { // Less than 1 minute old
                            val locationData = LocationData(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                                address = getAddressFromLocation(location.latitude, location.longitude)
                            )
                            continuation.resume(locationData)
                        } else {
                            // Request fresh location
                            requestNewLocation { newLocation ->
                                if (newLocation != null) {
                                    val locationData = LocationData(
                                        latitude = newLocation.latitude,
                                        longitude = newLocation.longitude,
                                        accuracy = newLocation.accuracy,
                                        address = getAddressFromLocation(newLocation.latitude, newLocation.longitude)
                                    )
                                    continuation.resume(locationData)
                                } else {
                                    continuation.resume(null)
                                }
                            }
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocation(callback: (Location?) -> Unit) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                fusedLocationClient.removeLocationUpdates(this)
                callback(locationResult.lastLocation)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                buildString {
                    // Add relevant parts of the address
                    address.featureName?.let { append(it).append(", ") }
                    address.thoroughfare?.let { append(it).append(", ") }
                    address.locality?.let { append(it).append(", ") }
                    address.adminArea?.let { append(it).append(", ") }
                    address.countryName?.let { append(it) }
                }.trim().trimEnd(',')
            } else {
                "$latitude, $longitude"
            }
        } catch (e: Exception) {
            "$latitude, $longitude"
        }
    }

    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val address: String
    ) {
        fun toFormattedString(): String {
            return if (address != "$latitude, $longitude") {
                address
            } else {
                "Lat: %.6f, Lon: %.6f".format(latitude, longitude)
            }
        }

        fun toJsonString(): String {
            return """{"lat":$latitude,"lon":$longitude,"accuracy":$accuracy,"address":"$address"}"""
        }
    }
}