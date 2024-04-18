package com.example.taller2_lapiceselite

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.Writer
import java.util.Date
import kotlin.math.roundToInt

class MapsActivity : AppCompatActivity(), SensorEventListener {
    private var latitud: Double = 0.0
    private var longitud: Double = 0.0
    private var lastLocation: GeoPoint = GeoPoint(latitud, longitud)
    private lateinit var lastLocation1: MyLocationNewOverlay
    var check: Boolean = false
    private lateinit var map: MapView
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var lastMarker: GeoPoint = GeoPoint(latitud, longitud)
    lateinit var roadManager: RoadManager
    private var roadOverlay: Polyline? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        var locationName = findViewById<EditText>(R.id.editTextText)

        // Verifica y solicita el permiso de ubicación
        checkLocationPermission()

        handlePermissions()
        buscarUbicacion(locationName)
        roadManager = OSRMRoadManager(this, "ANDROID")
        Configuration.getInstance().setUserAgentValue(BuildConfig.BUILD_TYPE)
        map = findViewById(R.id.osmMap)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setBuiltInZoomControls(true)
        setUpSensorStuff()
        map.overlays.add(createOverlayEvents())
    }

    private fun readJSONArray(fileName: String): JSONArray {
        val file = File(baseContext.getExternalFilesDir(null), fileName)
        if (!file.exists()) {
            return JSONArray()
        }
        val jsonString = file.readText()
        return JSONArray(jsonString)
    }

    private fun writeJSON(location: ubicacionLive) {
        val ubicaciones = readJSONArray("ubicaciones.json")
        ubicaciones.put(location.toJSON())
        var output: Writer?
        val filename = "ubicaciones.json"
        try {
            val file = File(baseContext.getExternalFilesDir(null), filename)
            output = BufferedWriter(FileWriter(file))
            output.write(ubicaciones.toString())
            output.close()
            Toast.makeText(applicationContext, "Ubicación guardada", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buscarUbicacion(locationName: EditText) {
        locationName.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.action == KeyEvent.ACTION_DOWN &&
                event.keyCode == KeyEvent.KEYCODE_ENTER
            ) {
                val nombre = locationName.text.toString()
                val location = obtenerDireccionNombre(nombre)
                if (location != null) {
                    val mapControl = map.controller
                    mapControl.setCenter(location)
                    mapControl.setZoom(20.0)

                    Toast.makeText(
                        applicationContext,
                        "Ubicación encontrada! $location",
                        Toast.LENGTH_LONG
                    ).show()

                    val marker = Marker(map)
                    marker.position = location
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                    val geocoder = Geocoder(this)
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (addresses!!.isNotEmpty()) {
                        val address = addresses[0]
                        marker.title = address.getAddressLine(0) // Establecer el título del marcador con la dirección
                    }

                    map.overlays.add(marker)

                    val location1 = GeoPoint(location.latitude, location.longitude)
                    lastMarker = location1
                    if (lastLocation != null) {
                        drawRoute(lastLocation, lastMarker)
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun obtenerDireccionNombre(text: String): GeoPoint? {
        val geocoder = Geocoder(this)
        val addresses = geocoder.getFromLocationName(text, 1)
        if (addresses!!.isNotEmpty()) {
            val address = addresses[0]
            return GeoPoint(address.latitude, address.longitude)
        } else {
            Toast.makeText(applicationContext, "No se encontraron resultados", Toast.LENGTH_LONG).show()
            return null
        }
    }

    private fun drawRoute(start: GeoPoint, finish: GeoPoint) {
        val routePoints = ArrayList<GeoPoint>()
        routePoints.add(start)
        routePoints.add(finish)
        GlobalScope.launch(Dispatchers.IO) {
            val road = roadManager.getRoad(routePoints)
            withContext(Dispatchers.Main) {
                Log.i("OSM_acticity", "Route length: ${road.mLength} klm")
                Log.i("OSM_acticity", "Duration: ${road.mDuration / 60} min")
                if (map != null) {
                    roadOverlay?.let { map.overlays.remove(it) }
                    roadOverlay = RoadManager.buildRoadOverlay(road)
                    roadOverlay?.outlinePaint?.color = Color.RED
                    roadOverlay?.outlinePaint?.strokeWidth = 10f
                    map.overlays.add(roadOverlay)
                    val distance = start.distanceToAsDouble(finish)
                    Toast.makeText(
                        this@MapsActivity,
                        "Distancia al punto : $distance metros",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun createOverlayEvents(): MapEventsOverlay {
        val overlayEventos = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                return false
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                longPressOnMap(p)
                return true
            }
        })
        return overlayEventos
    }

    private fun longPressOnMap(p: GeoPoint) {
        val pGeo = Geocoder(this)
        val addresses: List<Address>?
        addresses = pGeo.getFromLocation(p.latitude, p.longitude, 1)
        if (addresses!!.isNotEmpty()) {
            val address = addresses[0]
            val marker = Marker(map)
            marker.position = p
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = address.getAddressLine(0)
            map.overlays.add(marker)
            lastMarker = p
            if (lastLocation != null) {
                drawRoute(lastLocation, lastMarker)
            }
        }
    }

    // Verifica el permiso de ubicación y solicítalo si es necesario
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun handlePermissions() {
        when {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // El permiso ya ha sido concedido
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                requestPermissions(
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            else -> {
                requestPermissions(
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido
                } else {
                    // Permiso denegado
                    Toast.makeText(this, "Permiso de ubicación necesario para continuar.", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                // Otros casos
            }
        }
    }


    override fun onPause() {
        super.onPause()
        map.overlays.remove(locationOverlay)
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        val mapController = map.controller
        setupLocationOverlay(mapController)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (event.sensor.type == Sensor.TYPE_LIGHT) {
                val lux = event.values[0]
                if (lux < 1000) {
                    map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                } else {
                    map.overlayManager.tilesOverlay.setColorFilter(null)
                }
            }
        }
    }

    private fun setupLocationOverlay(mapController: IMapController) {
        val locationProvider = GpsMyLocationProvider(this)
        locationOverlay = MyLocationNewOverlay(locationProvider, map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()

        locationOverlay.runOnFirstFix {
            runOnUiThread {
                val currentLocation = locationOverlay.myLocation
                val latitude = currentLocation.latitude
                val longitude = currentLocation.longitude

                if (locationOverlay.myLocation != null) {
                    if (!check) {
                        check = true
                        lastLocation = GeoPoint(latitude, longitude)
                        lastLocation1 = locationOverlay
                        map.overlays.add(locationOverlay)
                        mapController.setZoom(15.0)
                        mapController.setCenter(locationOverlay.myLocation)
                    } else {
                        if (calcDistance(
                                lastLocation.latitude,
                                lastLocation.longitude,
                                latitude,
                                longitude
                            ) > 30
                        ) {
                            lastLocation = GeoPoint(latitude, longitude)
                            lastLocation1 = locationOverlay
                            map.overlays.add(locationOverlay)
                            mapController.setCenter(locationOverlay.myLocation)
                            val currentDate = Date()
                            val ubicacion = ubicacionLive(currentDate, latitude, longitude)
                            writeJSON(ubicacion)
                        }
                    }
                }
            }
        }
    }

    private fun calcDistance(lat1: Double, long1: Double, lat2: Double, long2: Double): Double {
        val RADIUS_OF_EARTH_KM = 6371.0
        val latDist = Math.toRadians(lat1 - lat2)
        val lngDist = Math.toRadians(long1 - long2)
        val a = (Math.sin(latDist / 2) * Math.sin(latDist / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDist / 2) * Math.sin(lngDist / 2))
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        val result = RADIUS_OF_EARTH_KM * c * 1000
        return (result * 100.0).roundToInt() / 100.0
    }

    class ubicacionLive(var fecha: Date, var latitud: Double, var longitud: Double) {
        fun toJSON(): JSONObject {
            val obj = JSONObject()
            try {
                obj.put("latitud", latitud)
                obj.put("longitud", longitud)
                obj.put("date", fecha)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            return obj
        }
    }

    private fun setUpSensorStuff() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        return
    }
}