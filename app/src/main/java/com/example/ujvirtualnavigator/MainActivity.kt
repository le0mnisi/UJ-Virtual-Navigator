package com.example.ujvirtualnavigator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.MultiLineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
                .center(Point.fromLngLat(28.0182, -26.1865))
                .zoom(17.0)
                .pitch(55.0)
                .bearing(0.0)
                .build()
        )
    }

    val locations = remember { loadLocationsFromJson(context) }
    var selectedLocation by remember { mutableStateOf<Pair<String, Point>?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // --- Map ---
        com.mapbox.maps.extension.compose.MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = { MapStyle("mapbox://styles/slick16/cmf1jwwo8001201sg3ve1av3v") }
        ) {
            MapEffect(Unit) { mapView ->
                val mapboxMap = mapView.mapboxMap
                mapboxMapRef = mapboxMap

                mapboxMap.getStyle { style ->
                    if (style.getSourceAs<GeoJsonSource>("route-source") == null) {
                        style.addSource(
                            geoJsonSource("route-source") {
                                featureCollection(FeatureCollection.fromFeatures(arrayOf()))
                            }
                        )
                        style.addLayer(
                            lineLayer("route-layer", "route-source") {
                                lineColor("#3b82f6")
                                lineWidth(6.0)
                                lineCap(LineCap.ROUND)
                                lineJoin(LineJoin.ROUND)
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

                        // Draw path only when a destination is selected
                        selectedLocation?.let { dest ->
                            drawPath(mapboxMap, context, point, dest.second)
                        }
                    }
                }
            }
        }

        // --- Search bar ---
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0x80FFFFFF),
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.padding(end = 8.dp))
                Text(selectedLocation?.first ?: "Search Destination")
            }

            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier.background(Color(0x80FFFFFF), RoundedCornerShape(16.dp))
            ) {
                locations.forEach { location ->
                    DropdownMenuItem(
                        text = { Text(location.first) },
                        onClick = {
                            selectedLocation = location
                            dropdownExpanded = false

                            // Draw path when a new destination is selected
                            currentLocation?.let { curr ->
                                drawPath(mapboxMapRef ?: return@DropdownMenuItem, context, curr, location.second)
                            }
                        }
                    )
                }
            }
        }

        // --- Recenter button ---
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
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .shadow(4.dp, CircleShape),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = "Recenter", tint = Color.Black)
        }
    }
}

// --- Draw only subpath ---
fun drawPath(mapboxMap: MapboxMap, context: Context, start: Point, destination: Point) {
    val pathPoints = getPathPointsFromFile(context)
    if (pathPoints.isEmpty()) return

    val subPath = getSubPath(pathPoints, start, destination)
    if (subPath.isEmpty()) return

    val routeSource = mapboxMap.getStyle()?.getSourceAs<GeoJsonSource>("route-source") ?: return

    CoroutineScope(Dispatchers.Main).launch {
        routeSource.featureCollection(
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(subPath)))
        )
    }
}

// --- Helper to get subpath between current location and destination ---
fun getSubPath(pathPoints: List<Point>, start: Point, end: Point): List<Point> {
    val startIndex = pathPoints.indexOfMinBy { it.distanceTo(start) } ?: 0
    val endIndex = pathPoints.indexOfMinBy { it.distanceTo(end) } ?: pathPoints.size - 1

    return if (startIndex <= endIndex) {
        pathPoints.subList(startIndex, endIndex + 1)
    } else {
        pathPoints.subList(endIndex, startIndex + 1).reversed()
    }
}

// --- Load GeoJSON path points ---
fun getPathPointsFromFile(context: Context, filename: String = "paths.geojson"): List<Point> {
    val geoJsonString = try { context.assets.open(filename).bufferedReader().use { it.readText() } } catch (e: Exception) { e.printStackTrace(); "" }
    if (geoJsonString.isEmpty()) return emptyList()

    val featureCollection = FeatureCollection.fromJson(geoJsonString)
    val points = mutableListOf<Point>()
    featureCollection.features()?.forEach { feature ->
        when (val geometry = feature.geometry()) {
            is LineString -> points.addAll(geometry.coordinates())
            is MultiLineString -> geometry.lineStrings().forEach { line -> points.addAll(line.coordinates()) }
        }
    }
    return points
}

// --- Load locations from JSON ---
fun loadLocationsFromJson(context: Context, filename: String = "locations.json"): List<Pair<String, Point>> {
    val jsonString = try { context.assets.open(filename).bufferedReader().use { it.readText() } } catch (e: Exception) { e.printStackTrace(); return emptyList() }
    val list = mutableListOf<Pair<String, Point>>()
    val jsonObject = JSONObject(jsonString)
    val locationsArray = jsonObject.getJSONArray("locations")
    for (i in 0 until locationsArray.length()) {
        val loc = locationsArray.getJSONObject(i)
        val name = loc.getString("name")
        val lat = loc.getDouble("latitude")
        val lon = loc.getDouble("longitude")
        list.add(name to Point.fromLngLat(lon, lat))
    }
    return list
}

// --- Extensions ---
fun List<Point>.indexOfMinBy(selector: (Point) -> Double): Int? {
    if (isEmpty()) return null
    var minIndex = 0
    var minValue = selector(this[0])
    for (i in 1 until size) {
        val v = selector(this[i])
        if (v < minValue) { minIndex = i; minValue = v }
    }
    return minIndex
}

fun Point.distanceTo(other: Point): Double {
    val lat1 = this.latitude(); val lon1 = this.longitude()
    val lat2 = other.latitude(); val lon2 = other.longitude()
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat/2) * sin(dLat/2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon/2) * sin(dLon/2)
    val c = 2 * atan2(sqrt(a), sqrt(1-a))
    return earthRadius * c
}
