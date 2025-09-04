package com.example.ujvirtualnavigator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
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
    var mapboxMapRef by remember { mutableStateOf<MapboxMap?>(null) }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions(
            CameraOptions.Builder()
                .center(Point.fromLngLat(28.0182, -26.1865)) // Default UJ APB Campus
                .zoom(17.0)
                .pitch(55.0)
                .bearing(0.0)
                .build()
        )
    }

    // Custom locations
    val locations = listOf(
        "Gloria Sekwena" to Point.fromLngLat(28.01823906384884, -26.18984792639419),
        "School of Tourism and Hospitality" to Point.fromLngLat(28.017863130010053, -26.189468821922002),
        "UJ APB FADA" to Point.fromLngLat(28.017480031552612, -26.188543541014976),
        "UJ APB Student Center" to Point.fromLngLat(28.016342989984075, -26.188448539883026),
        "UJ APB Library" to Point.fromLngLat(28.015274047655343, -26.18627565139178),
        "Bus Station" to Point.fromLngLat(28.01430758573417, -26.188145140693273)
        // Add all other locations here...
    )

    var selectedLocation by remember { mutableStateOf<Pair<String, Point>?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // Dropdown menu
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Button(onClick = { dropdownExpanded = true }) {
                Text(selectedLocation?.first ?: "Select Destination")
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                locations.forEach { location ->
                    DropdownMenuItem(
                        text = { Text(location.first) },
                        onClick = {
                            selectedLocation = location
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Map
        MapboxMap(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            mapViewportState = mapViewportState,
            style = { MapStyle("mapbox://styles/slick16/cmf1jwwo8001201sg3ve1av3v") }
        ) {
            MapEffect(Unit) { mapView ->
                val mapboxMap = mapView.getMapboxMap()
                mapboxMapRef = mapboxMap

                mapboxMap.getStyle { style ->
                    var routeSource = style.getSourceAs<GeoJsonSource>("route-source")
                    if (routeSource == null) {
                        style.addSource(
                            geoJsonSource("route-source") {
                                featureCollection(FeatureCollection.fromFeatures(arrayOf()))
                            }
                        )
                        style.addLayer(
                            lineLayer("route-layer", "route-source") {
                                lineColor("#ff6600")
                                lineWidth(5.0)
                            }
                        )
                    }
                }

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

                        selectedLocation?.let { dest ->
                            drawRoute(mapboxMap, point, dest.second)
                        }
                    }
                } else {
                    Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Buttons
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
                } ?: Toast.makeText(context, "Waiting for GPS...", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Recenter")
        }

        Button(
            onClick = {
                val start = currentLocation
                val dest = selectedLocation
                val mapboxMap = mapboxMapRef
                if (start != null && dest != null && mapboxMap != null) {
                    drawRoute(mapboxMap, start, dest.second)
                } else {
                    Toast.makeText(context, "Select destination and wait for GPS fix", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("Draw Route")
        }
    }
}

fun drawRoute(mapboxMap: MapboxMap, start: Point, end: Point) {
    val line = LineString.fromLngLats(listOf(start, end))
    val feature = Feature.fromGeometry(line)

    mapboxMap.getStyle { style ->
        var routeSource = style.getSourceAs<GeoJsonSource>("route-source")
        if (routeSource == null) {
            style.addSource(
                geoJsonSource("route-source") {
                    featureCollection(FeatureCollection.fromFeature(feature))
                }
            )
            style.addLayer(
                lineLayer("route-layer", "route-source") {
                    lineColor("#ff6600")
                    lineWidth(5.0)
                }
            )
        } else {
            routeSource.featureCollection(FeatureCollection.fromFeature(feature))
        }
    }
}
