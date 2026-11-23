package com.naeem.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private val TrailBg = Color(0xFF090B0E)
private val TrailPanel = Color(0xFF191D23)
private val TrailSilver = Color(0xFFD5D9DE)
private val TrailMuted = Color(0xFF929BA6)
private data class TrailPoint(val lat: Double, val lon: Double, val altitude: Double, val time: Long)
private data class TrailWaypoint(val title: String, val lat: Double, val lon: Double)

private class TrailStore(context: Context) {
    private val prefs = context.getSharedPreferences("naeem_trail_local", Context.MODE_PRIVATE)
    fun points(): List<TrailPoint> = runCatching {
        val a = JSONArray(prefs.getString("points", "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { TrailPoint(it.optDouble("lat"), it.optDouble("lon"), it.optDouble("alt"), it.optLong("time")) } }
    }.getOrDefault(emptyList())
    fun savePoints(points: List<TrailPoint>) {
        val a = JSONArray()
        points.takeLast(50000).forEach { a.put(JSONObject().put("lat", it.lat).put("lon", it.lon).put("alt", it.altitude).put("time", it.time)) }
        prefs.edit().putString("points", a.toString()).apply()
    }
    fun waypoints(): List<TrailWaypoint> = runCatching {
        val a = JSONArray(prefs.getString("waypoints", "[]"))
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { TrailWaypoint(it.optString("title"), it.optDouble("lat"), it.optDouble("lon")) } }
    }.getOrDefault(emptyList())
    fun saveWaypoints(items: List<TrailWaypoint>) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("title", it.title).put("lat", it.lat).put("lon", it.lon)) }
        prefs.edit().putString("waypoints", a.toString()).apply()
    }
}

@Composable
fun TrailNavigationDashboard(onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TrailStore(context) }
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    var location by remember { mutableStateOf<Location?>(null) }
    var recording by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf(store.points()) }
    var waypoints by remember { mutableStateOf(store.waypoints()) }
    var waypointName by remember { mutableStateOf("") }
    var showWaypointDialog by remember { mutableStateOf(false) }
    var mapStatus by remember { mutableStateOf("هنوز بسته نقشه‌ای وارد نشده") }
    var exportStatus by remember { mutableStateOf("") }

    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "trail/maps").apply { mkdirs() }
                    val target = File(dir, "iran-offline-" + System.currentTimeMillis() + ".mbtiles")
                    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } } ?: error("فایل نقشه خوانده نشد")
                    target
                }
                mapStatus = "بسته نقشه در گوشی ذخیره شد • " + android.text.format.Formatter.formatFileSize(context, file.length())
            } catch (e: Exception) { mapStatus = e.message ?: "وارد کردن نقشه ناموفق بود" }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) scope.launch {
            try {
                val gpx = buildString {
                    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<gpx version=\"1.1\" creator=\"Naeem Trail\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>Naeem Trail</name><trkseg>\n")
                    points.forEach { p ->
                        append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\"><ele>").append(p.altitude)
                            .append("</ele><time>").append(java.time.Instant.ofEpochMilli(p.time)).append("</time></trkpt>\n")
                    }
                    append("</trkseg></trk>")
                    waypoints.forEach { w ->
                        append("<wpt lat=\"").append(w.lat).append("\" lon=\"").append(w.lon).append("\"><name>")
                            .append(w.title.replace("&", "&amp;").replace("<", "&lt;")).append("</name></wpt>")
                    }
                    append("</gpx>")
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(gpx.toByteArray()) } ?: error("فایل خروجی ساخته نشد")
                }
                exportStatus = "مسیر با فرمت GPX ذخیره شد"
            } catch (e: Exception) { exportStatus = e.message ?: "خروجی ناموفق بود" }
        }
    }

    val recordingNow by rememberUpdatedState(recording)
    val pointsNow by rememberUpdatedState(points)
    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(fix: Location) {
                location = fix
                if (recordingNow) {
                    val next = pointsNow + TrailPoint(fix.latitude, fix.longitude, fix.altitude, fix.time)
                    points = next
                    store.savePoints(next)
                }
            }
        }
        try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { location = it }
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, listener)
        } catch (_: SecurityException) { }
        onDispose { runCatching { manager.removeUpdates(listener) } }
    }
    val distanceKm = remember(points) {
        var meters = 0f
        for (i in 1 until points.size) {
            val a = Location("trail").apply { latitude = points[i - 1].lat; longitude = points[i - 1].lon }
            val b = Location("trail").apply { latitude = points[i].lat; longitude = points[i].lon }
            meters += a.distanceTo(b)
        }
        meters / 1000f
    }

    Column(Modifier.fillMaxSize().background(TrailBg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NAEEM TRAIL", color = TrailMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("ناوبری طبیعت‌گردی", color = TrailSilver, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("نقشه آفلاین ایران • GPS • ثبت مسیر", color = TrailMuted, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = TrailSilver) }
        }
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = TrailPanel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Map, null, tint = TrailSilver); Spacer(Modifier.width(8.dp)); Text("نقشه آفلاین سراسر ایران", color = TrailSilver, fontWeight = FontWeight.SemiBold) }
                Text(mapStatus, color = TrailMuted, style = MaterialTheme.typography.bodySmall)
                Text("بسته نقشه MBTiles را انتخاب کن تا در حافظه داخلی برنامه نگهداری شود.", color = TrailMuted, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { mapPicker.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("وارد کردن بسته نقشه")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = TrailPanel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.MyLocation, null, tint = TrailSilver); Spacer(Modifier.width(8.dp)); Text("موقعیت GPS", color = TrailSilver, fontWeight = FontWeight.SemiBold) }
                Text(location?.let { "عرض: ${"%.5f".format(Locale.US, it.latitude)}   طول: ${"%.5f".format(Locale.US, it.longitude)}" } ?: "موقعیت GPS هنوز دریافت نشده", color = TrailMuted, style = MaterialTheme.typography.bodySmall)
                Text(location?.let { "ارتفاع GPS: ${it.altitude.roundToInt()} متر" } ?: "برای دریافت موقعیت، GPS گوشی را روشن کن", color = TrailMuted, style = MaterialTheme.typography.bodySmall)
                if (!hasPermission) Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("اجازه دسترسی به موقعیت")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Card(colors = CardDefaults.cardColors(containerColor = TrailPanel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ثبت مسیر پیاده‌روی", color = TrailSilver, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f)) { Text("مسافت", color = TrailMuted, style = MaterialTheme.typography.labelSmall); Text("${"%.2f".format(Locale.US, distanceKm)} km", color = TrailSilver, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    Column(Modifier.weight(1f)) { Text("نقاط ثبت‌شده", color = TrailMuted, style = MaterialTheme.typography.labelSmall); Text(points.size.toString(), color = TrailSilver, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!hasPermission) permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        else { if (!recording) { points = emptyList(); store.savePoints(emptyList()) }; recording = !recording }
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (recording) Color(0xFF57363A) else Color(0xFF39424D))) {
                        Icon(if (recording) Icons.Default.Stop else Icons.Default.FiberManualRecord, null); Spacer(Modifier.width(5.dp)); Text(if (recording) "توقف ثبت" else "شروع ثبت")
                    }
                    OutlinedButton(onClick = { showWaypointDialog = true }, enabled = location != null) { Icon(Icons.Default.AddLocationAlt, null); Text("نقطه") }
                }
                OutlinedButton(onClick = { exportLauncher.launch("naeem-trail.gpx") }, enabled = points.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("خروجی مسیر GPX")
                }
                if (exportStatus.isNotBlank()) Text(exportStatus, color = TrailMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("نقاط ذخیره‌شده", color = TrailSilver, fontWeight = FontWeight.SemiBold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(waypoints) { point ->
                Card(colors = CardDefaults.cardColors(containerColor = TrailPanel)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, null, tint = TrailSilver); Spacer(Modifier.width(8.dp))
                        Column { Text(point.title, color = TrailSilver); Text("${"%.5f".format(Locale.US, point.lat)}, ${"%.5f".format(Locale.US, point.lon)}", color = TrailMuted, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
        Text("این مرحله GPS، ثبت مسیر و نگهداری فایل نقشه را اضافه می‌کند؛ نمایش نقشه، مسیریابی آفلاین، خطوط ارتفاعی و هشدار خروج از مسیر هنوز تکمیل نشده‌اند. مسیرهای ثبت‌نشده را تأییدشده یا ایمن فرض نکن.", color = TrailMuted, style = MaterialTheme.typography.labelSmall)
    }

    if (showWaypointDialog) AlertDialog(
        onDismissRequest = { showWaypointDialog = false }, containerColor = TrailPanel,
        title = { Text("ثبت نقطه", color = TrailSilver) },
        text = { OutlinedTextField(waypointName, { waypointName = it }, label = { Text("نام؛ مثلاً چشمه یا کمپ") }, singleLine = true) },
        confirmButton = { TextButton(onClick = {
            val fix = location
            if (fix != null && waypointName.isNotBlank()) {
                waypoints = waypoints + TrailWaypoint(waypointName.trim(), fix.latitude, fix.longitude)
                store.saveWaypoints(waypoints); waypointName = ""; showWaypointDialog = false
            }
        }) { Text("ذخیره", color = TrailSilver) } },
        dismissButton = { TextButton(onClick = { showWaypointDialog = false }) { Text("انصراف", color = TrailMuted) } }
    )
}
