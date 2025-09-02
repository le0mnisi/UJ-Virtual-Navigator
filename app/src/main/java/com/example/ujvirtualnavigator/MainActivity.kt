package com.example.ujvirtualnavigator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.*
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location

class MainActivity : ComponentActivity() {

    private var hasLocationPermission = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasLocationPermission = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔐 Request location permission
        hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            UjVirtualNavigatorApp(hasLocationPermission)
        }
    }
}

@Composable
fun UjVirtualNavigatorApp(hasLocationPermission: Boolean) {
    val context = LocalContext.current
    var currentLocation by remember { mutableStateOf<Point?>(null) }
    var firstGpsFix by remember { mutableStateOf(false) }

    // Create a MapViewportState to control camera movements
    val mapViewportState = rememberMapViewportState {
        setCameraOptions(
            CameraOptions.Builder()
                .center(Point.fromLngLat(28.0182, -26.1865)) // UJ APB Campus
                .zoom(17.0)
                .pitch(55.0)
                .bearing(0.0)
                .build()
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Map composable
        MapboxMap(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            mapViewportState = mapViewportState,
            style = { MapStyle("mapbox://styles/slick16/cmf1jwwo8001201sg3ve1av3v") }
        ) {
            MapEffect(Unit) { mapView ->
                if (hasLocationPermission) {
                    mapView.location.updateSettings {
                        enabled = true
                        locationPuck = createDefault2DPuck(withBearing = true)
                    }

                    mapView.location.addOnIndicatorPositionChangedListener { point ->
                        currentLocation = point
                        if (!firstGpsFix) {
                            mapViewportState.flyTo(
                                CameraOptions.Builder()
                                    .center(point)
                                    .zoom(17.0)
                                    .pitch(55.0)
                                    .bearing(0.0)
                                    .build()
                            )
                            firstGpsFix = true
                        }
                    }
                } else {
                    Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Recenter button
        Button(
            onClick = {
                currentLocation?.let { point ->
                    mapViewportState.flyTo(
                        CameraOptions.Builder()
                            .center(point)
                            .zoom(17.0)
                            .pitch(55.0)
                            .bearing(0.0)
                            .build()
                    )
                } ?: run {
                    Toast.makeText(context, "Waiting for GPS...", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Recenter")
        }
    }
}
