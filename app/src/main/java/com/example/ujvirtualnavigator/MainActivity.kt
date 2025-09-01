package com.example.ujvirtualnavigator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location

class MainActivity : ComponentActivity() {

    private lateinit var mapView: MapView
    private var currentLocation: Point? = null
    private var hasLocationPermission = false
    private var firstGpsFixHandled = false   // 👈 only fly once

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasLocationPermission = granted
            if (granted) enableLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        val recenterButton: Button = findViewById(R.id.btnRecenter)

        // 🔐 Check location permission
        hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            enableLocation()
        }

        // 1️⃣ Start camera at UJ APB Campus
        val ujCampus = Point.fromLngLat(28.0182, -26.1865)
        mapView.mapboxMap.setCamera(
            CameraOptions.Builder()
                .center(ujCampus)
                .zoom(17.0)
                .pitch(55.0)
                .bearing(0.0)
                .build()
        )

        // 2️⃣ Recenter button → fly to user location
        recenterButton.setOnClickListener {
            currentLocation?.let { point ->
                mapView.mapboxMap.flyTo(
                    CameraOptions.Builder()
                        .center(point)
                        .zoom(17.0)
                        .pitch(55.0)
                        .bearing(0.0)
                        .build()
                )
            } ?: run {
                Toast.makeText(this, "Waiting for GPS...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Setup location puck + GPS listener
    private fun enableLocation() {
        mapView.location.updateSettings {
            enabled = true
            locationPuck = createDefault2DPuck(withBearing = true)
            puckBearingEnabled = true
            puckBearing = PuckBearing.HEADING
        }

        // 👇 Listen for GPS updates
        mapView.location.addOnIndicatorPositionChangedListener { point ->
            currentLocation = point

            // 📌 Fly to user only once on first GPS fix
            if (!firstGpsFixHandled) {
                mapView.mapboxMap.flyTo(
                    CameraOptions.Builder()
                        .center(point)
                        .zoom(17.0)
                        .pitch(55.0)
                        .bearing(0.0)
                        .build()
                )
                firstGpsFixHandled = true
            }
        }
    }
}
