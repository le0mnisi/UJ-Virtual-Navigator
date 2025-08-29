package com.example.ujvirtualnavigator
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.plugin.PuckBearing // <- correct package
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location

class MainActivity : ComponentActivity() {

    private var hasLocationPermission by mutableStateOf(false)

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasLocationPermission = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

// Initial permission check
        hasLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            val viewportState = rememberMapViewportState {
                setCameraOptions {
                    zoom(14.0)
                    center(Point.fromLngLat(28.0473, -26.2041)) // Johannesburg
                }
            }

            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = viewportState
// No style param needed — Standard is the default in v11
            ) {
                if (hasLocationPermission) {
                    MapEffect(Unit) { mapView ->
                        mapView.location.updateSettings {
                            enabled = true
                            locationPuck = createDefault2DPuck(withBearing = true)
                            puckBearingEnabled = true
                            puckBearing = PuckBearing.HEADING
                        }
                        viewportState.transitionToFollowPuckState()
                    }
                }
            }
        }
    }
}