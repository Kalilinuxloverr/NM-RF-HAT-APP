package com.nmrf.remote.detect

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nmrf.remote.ble.BleSource
import com.nmrf.remote.ui.components.EmptyState
import com.nmrf.remote.ui.components.HeaderChip
import com.nmrf.remote.ui.components.MetricTile
import com.nmrf.remote.ui.components.ScreenHeader
import com.nmrf.remote.ui.components.StatusPill
import com.nmrf.remote.ui.components.heat
import com.nmrf.remote.ui.theme.MatrixGreen
import com.nmrf.remote.ui.theme.MatrixText
import com.nmrf.remote.ui.theme.MatrixTextDim
import com.nmrf.remote.ui.theme.StatusActive
import com.nmrf.remote.ui.theme.StatusAlert
import com.nmrf.remote.ui.theme.StatusData
import com.nmrf.remote.wifi.PermissionInfo
import com.nmrf.remote.wifi.WifiSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// Datenmodell + reine Exporter (testbar, ohne Android)
// ---------------------------------------------------------------------------

data class Fix(val lat: Double, val lon: Double, val alt: Double, val speedMps: Float, val accM: Float, val time: Long, val bearing: Float)

data class GeoObs(val kind: String, val id: String, val name: String, val rssi: Int, val lat: Double, val lon: Double, val time: Long, val channel: Int = 0, val auth: String = "")

/** Great-circle-Distanz in Metern (Haversine). */
fun haversineM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

private fun d6(v: Double) = String.format(Locale.US, "%.6f", v)
private fun stamp(t: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t))

/** Google-Earth-Overlay: ein Placemark je Beobachtung. */
fun toKml(obs: List<GeoObs>): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
    append("""<kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>NMRF Wardrive</name>""").append('\n')
    obs.forEach { o ->
        append("<Placemark><name>").append(xml(o.name.ifBlank { o.id })).append("</name>")
        append("<description>").append(xml("${o.kind} ${o.id} · ${o.rssi} dBm")).append("</description>")
        append("<Point><coordinates>").append(d6(o.lon)).append(',').append(d6(o.lat)).append(",0</coordinates></Point>")
        append("</Placemark>\n")
    }
    append("</Document></kml>")
}

/** WiGLE-kompatibles CSV (WigleWifi-1.4). */
fun toWigleCsv(obs: List<GeoObs>): String = buildString {
    append("WigleWifi-1.4,appRelease=nmrf,model=android,release=,device=,display=,board=,brand=\n")
    append("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type\n")
    obs.forEach { o ->
        val type = if (o.kind == "WIFI") "WIFI" else "BT"
        append(o.id).append(',').append('"').append(o.name.replace("\"", "'")).append('"').append(',')
        append('"').append(o.auth).append('"').append(',')
        append(stamp(o.time, "yyyy-MM-dd HH:mm:ss")).append(',')
        append(o.channel).append(',').append(o.rssi).append(',')
        append(d6(o.lat)).append(',').append(d6(o.lon)).append(",0,0,").append(type).append('\n')
    }
}

/** GPX-Track (die gefahrene Route). */
fun toGpx(track: List<Fix>): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
    append("""<gpx version="1.1" creator="NMRF"><trk><name>NMRF Wardrive</name><trkseg>""").append('\n')
    track.forEach { f ->
        append("<trkpt lat=\"").append(d6(f.lat)).append("\" lon=\"").append(d6(f.lon)).append("\">")
        append("<ele>").append(String.format(Locale.US, "%.1f", f.alt)).append("</ele>")
        append("<time>").append(stamp(f.time, "yyyy-MM-dd'T'HH:mm:ss'Z'")).append("</time></trkpt>\n")
    }
    append("</trkseg></trk></gpx>")
}

private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

// ---------------------------------------------------------------------------
// GPS-Quelle (LocationManager, ohne Play Services)
// ---------------------------------------------------------------------------

class LocationEngine(context: Context) {
    private val app = context.applicationContext
    private val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission")
    val fixes: Flow<Fix?> = callbackFlow {
        if (lm == null) { trySend(null); awaitClose { }; return@callbackFlow }
        runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()?.let { trySend(it.toFix()) }
        val listener = object : LocationListener {
            override fun onLocationChanged(l: Location) { trySend(l.toFix()) }
            override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
            override fun onProviderEnabled(p: String) {}
            override fun onProviderDisabled(p: String) {}
        }
        val loop = Looper.getMainLooper()
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, loop) }
        runCatching { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 0f, listener, loop) }
        awaitClose { runCatching { lm.removeUpdates(listener) } }
    }
}

private fun Location.toFix() = Fix(latitude, longitude, if (hasAltitude()) altitude else 0.0, if (hasSpeed()) speed else 0f, accuracy, time, if (hasBearing()) bearing else 0f)

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@Composable
fun WardriveScreen(onBack: () -> Unit, ble: BleSource, wifi: WifiSource, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    if (!hasPermission) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("WARDRIVE", "GPS-Kartierung", action = { HeaderChip("‹ SENTINEL", onBack) })
            PermissionInfo("Standort- und Bluetooth-Berechtigung nötig.", onRequestPermission)
        }
        return
    }
    val ctx = LocalContext.current
    val loc = remember { LocationEngine(ctx.applicationContext) }
    val current by remember { loc.fixes }.collectAsState(initial = null)
    val track = remember { mutableStateListOf<Fix>() }
    val obs = remember { mutableStateMapOf<String, GeoObs>() }
    var recording by remember { mutableStateOf(false) }

    LaunchedEffect(recording, current?.time) {
        val f = current ?: return@LaunchedEffect
        if (recording) {
            val last = track.lastOrNull()
            if (last == null || haversineM(last.lat, last.lon, f.lat, f.lon) >= 3.0) track.add(f)
        }
    }
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        wifi.results.collect { list ->
            val f = current ?: return@collect
            list.forEach { ap ->
                val prev = obs["W:${ap.bssid}"]
                if (prev == null || ap.rssi > prev.rssi) {
                    obs["W:${ap.bssid}"] = GeoObs("WIFI", ap.bssid, ap.ssid, ap.rssi, f.lat, f.lon, f.time, ap.channel, ap.capabilities)
                }
            }
        }
    }
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        ble.devices.collect { list ->
            val f = current ?: return@collect
            list.forEach { d ->
                val prev = obs["B:${d.address}"]
                if (prev == null || d.rssi > prev.rssi) {
                    obs["B:${d.address}"] = GeoObs("BLE", d.address, d.name ?: "", d.rssi, f.lat, f.lon, f.time)
                }
            }
        }
    }

    fun share(name: String, mime: String, body: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_SUBJECT, name); putExtra(Intent.EXTRA_TEXT, body) }
        ctx.startActivity(Intent.createChooser(i, name))
    }

    val obsList = obs.values.toList()
    val wifiN = obsList.count { it.kind == "WIFI" }
    val bleN = obsList.count { it.kind == "BLE" }
    val distM = remember(track.size) { var d = 0.0; for (i in 1 until track.size) d += haversineM(track[i - 1].lat, track[i - 1].lon, track[i].lat, track[i].lon); d }
    val kmh = ((current?.speedMps ?: 0f) * 3.6f)
    val fixOk = current != null

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("WARDRIVE", if (recording) "● läuft · ${track.size} Punkte" else "bereit", action = { HeaderChip("‹ SENTINEL", onBack) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            StatusPill(if (fixOk) "GPS ${current?.accM?.toInt() ?: 0}m" else "KEIN FIX", if (fixOk) MatrixGreen else StatusAlert, filled = true)
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Netze", "$wifiN", accent = StatusData, modifier = Modifier.weight(1f))
            MetricTile("BLE", "$bleN", accent = StatusActive, modifier = Modifier.weight(1f))
            MetricTile("Strecke", String.format(Locale.US, "%.2f", distM / 1000.0), unit = "km", accent = MatrixGreen, modifier = Modifier.weight(1f))
            MetricTile("Tempo", "${kmh.toInt()}", unit = "km/h", accent = StatusData, modifier = Modifier.weight(1f))
        }
        TrackView(track.toList(), obsList, Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 12.dp))
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!recording) HeaderChip("⏺ START") { recording = true } else HeaderChip("⏹ STOP") { recording = false }
            HeaderChip("⟲ leeren") { obs.clear(); track.clear() }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderChip("KML") { if (obsList.isNotEmpty()) share("nmrf-wardrive.kml", "application/vnd.google-earth.kml+xml", toKml(obsList)) }
            HeaderChip("WiGLE") { if (obsList.isNotEmpty()) share("nmrf-wardrive.csv", "text/csv", toWigleCsv(obsList)) }
            HeaderChip("GPX") { if (track.isNotEmpty()) share("nmrf-track.gpx", "application/gpx+xml", toGpx(track.toList())) }
        }
        if (obsList.isEmpty()) {
            EmptyState("🛰", if (recording) "Warte auf GPS-Fix + Netze…" else "Wardrive bereit", "START drücken und loslaufen/fahren. Jedes Netz wird mit deiner Position geloggt. Export als KML (Google Earth), WiGLE-CSV oder GPX-Track.")
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                items(obsList.sortedByDescending { it.time }) { o ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(o.kind, if (o.kind == "WIFI") StatusData else StatusActive)
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(o.name.ifBlank { "<ohne Name>" }, color = MatrixText, style = MaterialTheme.typography.bodyMedium)
                            Text("${d6(o.lat)}, ${d6(o.lon)}", color = MatrixTextDim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        Text("${o.rssi}", color = MatrixGreen, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

/** Selbstgezeichneter Breadcrumb-Track (offline, ohne Kartenkacheln). */
@Composable
private fun TrackView(track: List<Fix>, obs: List<GeoObs>, modifier: Modifier) {
    Canvas(modifier) {
        val pts = track
        if (pts.size < 2 && obs.isEmpty()) return@Canvas
        val lats = (pts.map { it.lat } + obs.map { it.lat })
        val lons = (pts.map { it.lon } + obs.map { it.lon })
        val minLat = lats.min(); val maxLat = lats.max()
        val minLon = lons.min(); val maxLon = lons.max()
        val spanLat = (maxLat - minLat).let { if (it < 1e-6) 1e-6 else it }
        val spanLon = (maxLon - minLon).let { if (it < 1e-6) 1e-6 else it }
        val pad = 12f
        fun px(lon: Double) = pad + (size.width - 2 * pad) * ((lon - minLon) / spanLon).toFloat()
        fun py(lat: Double) = size.height - pad - (size.height - 2 * pad) * ((lat - minLat) / spanLat).toFloat()
        obs.forEach { o ->
            drawCircle(if (o.kind == "WIFI") StatusData else StatusActive, radius = 2.5f, center = Offset(px(o.lon), py(o.lat)))
        }
        for (i in 1 until pts.size) {
            drawLine(MatrixGreen, Offset(px(pts[i - 1].lon), py(pts[i - 1].lat)), Offset(px(pts[i].lon), py(pts[i].lat)), 2f, cap = StrokeCap.Round)
        }
        pts.lastOrNull()?.let { drawCircle(heat(1f), radius = 5f, center = Offset(px(it.lon), py(it.lat))) }
    }
}
