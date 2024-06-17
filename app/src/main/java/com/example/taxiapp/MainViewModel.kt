package com.example.mycurrentlocation

import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainViewModel(
    private val application: Application,
) : AndroidViewModel(application) {

    private var _latLng = MutableStateFlow<LatLng?>(null)
    val latLng = _latLng.asStateFlow()

    private val locationClient by lazy {
        LocationServices.getFusedLocationProviderClient(application)
    }

    @RequiresPermission(
        anyOf = [android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION],
    )
    fun fetchCurrentLocation(usePreciseLocation: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val priority =
                if (usePreciseLocation) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val result = locationClient.getCurrentLocation(
                priority,
                CancellationTokenSource().token
            ).await()
            _latLng.value = LatLng(result.latitude, result.longitude)
        }
    }
}