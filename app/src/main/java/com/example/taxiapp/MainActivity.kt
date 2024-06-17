package com.example.taxiapp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import com.example.mycurrentlocation.MainViewModel
import com.google.android.gms.maps.model.LatLng
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.DrivingOptions
import com.yandex.mapkit.directions.driving.DrivingRoute
import com.yandex.mapkit.directions.driving.DrivingRouter
import com.yandex.mapkit.directions.driving.DrivingSection
import com.yandex.mapkit.directions.driving.DrivingSession
import com.yandex.mapkit.directions.driving.VehicleOptions
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.SubpolylineHelper
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.Error
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), DrivingSession.DrivingRouteListener {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var mapView: MapView
    private var routeUpdatesJob: Job? = null
    private val requestPoints: ArrayList<RequestPoint> = ArrayList()
    private var mapObjectList: MapObjectCollection? = null
    private var drivingRouter: DrivingRouter? = null
    private var drivingSession: DrivingSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionRequest.launch(locationPermissions)
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAP_KEY)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapview)
        drivingRouter = DirectionsFactory.getInstance().createDrivingRouter()
        lifecycleScope.launch {
            viewModel.latLng.collect {
                it?.let {
                    redrawRoute(it)
                }
            }
        }
    }

    private fun redrawRoute(latLng: LatLng) {
        routeUpdatesJob?.cancel()
        routeUpdatesJob = lifecycleScope.launchWhenResumed {
            // UserLocationLayer#cameraPosition() is always the position of user

            if (viewModel.latLng.value != null) {
                mapView.map.move(
                    CameraPosition(
                        Point(
                            latLng.latitude ?: 0.0,
                            latLng.longitude ?: 0.0
                        ),
                        /* zoom = */ 17.0f,
                        /* azimuth = */ 150.0f,
                        /* tilt = */ 30.0f
                    )
                )
                clearMapObjects()
                submitRequest()
            } else {
                //wait until location is not null
                redrawRoute(latLng)
            }
        }
    }

    private fun clearMapObjects() {
        requestPoints.clear()
        mapObjectList = mapView.map.mapObjects.addCollection()
    }

    private fun submitRequest() {
        val options = DrivingOptions()

        requestPoints.add(
            RequestPoint(
                Point(56.833742, 60.635716),
                RequestPointType.WAYPOINT,
                null, null,
            )
        )
        addPlaceMark(LatLng(56.833742, 60.635716))

        viewModel.latLng.value?.let {
            requestPoints.add(
                RequestPoint(
                    Point(it.latitude, it.longitude), RequestPointType.WAYPOINT, null, null,
                )
            )
        }

        drivingSession = drivingRouter?.requestRoutes(
            requestPoints,
            options,
            VehicleOptions(),
            this@MainActivity
        )
    }

    private fun addPlaceMark(latLng: LatLng) {
        val imageProvider = ImageProvider.fromResource(this, R.drawable.ic_locc)
        mapView.map.mapObjects.addPlacemark().apply {
            geometry = Point(latLng.latitude, latLng.longitude)
            setIcon(imageProvider)
        }

        mapView.map.move(
            CameraPosition(
                Point(latLng.latitude, latLng.longitude),
                17.0f,
                150.0f,
                30.0f
            )
        )

    }

    @SuppressLint("MissingPermission")
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted.
                viewModel.fetchCurrentLocation(usePreciseLocation = true)
            }

            permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                // Only approximate location access granted.
                viewModel.fetchCurrentLocation(usePreciseLocation = false)
            }

            else -> {
                // No location access granted.
            }
        }
    }

    override fun onResume() {
        super.onResume()
        when (PermissionChecker.PERMISSION_GRANTED) {
            PermissionChecker.checkSelfPermission(
                this,
                locationPermissions.first()
            ),
            -> {
                viewModel.fetchCurrentLocation(true)
            }

            PermissionChecker.checkSelfPermission(
                this,
                locationPermissions.last()
            ),
            -> {
                viewModel.fetchCurrentLocation(false)
            }

            else -> Unit
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    companion object {
        private val locationPermissions = arrayOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    override fun onDrivingRoutes(p0: MutableList<DrivingRoute>) {
        val sections: List<DrivingSection> = p0.getOrNull(0)?.sections ?: emptyList()
        for (section in sections) {
            val polylineMapObject = mapObjectList?.addPolyline(
                SubpolylineHelper.subpolyline(
                    p0[0].geometry, section.geometry
                )
            )
            polylineMapObject?.setStrokeColor(-0x10000)
        }
    }

    override fun onDrivingRoutesError(p0: Error) {

    }
}