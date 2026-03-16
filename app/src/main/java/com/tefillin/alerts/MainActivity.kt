package com.tefillin.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val BG = Color(0xFF0A0A1A)
val CARD = Color(0xFF16162A)
val RED = Color(0xFFFF2D55)
val GREEN = Color(0xFF00FF87)
val BLUE = Color(0xFF00E5FF)
val GRAY = Color(0xFF888888)

data class AlertItem(val time: String, val title: String, val area: String)

class MainActivity : ComponentActivity() {

    private val client = OkHttpClient()
    private var alertReceiver: BroadcastReceiver? = null

    private val currentAlert = mutableStateOf<JSONObject?>(null)
    private val history = mutableStateOf<List<AlertItem>>(emptyList())
    private val lastUpdate = mutableStateOf("")
    private val isConnected = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startForegroundService(Intent(this, AlertService::class.java))

        alertReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.tefillin.alerts.NEW_ALERT") {
                    val obj = JSONObject()
                    obj.put("id", intent.getStringExtra("id") ?: "")
                    obj.put("title", intent.getStringExtra("title") ?: "")
                    obj.put("data", intent.getStringExtra("areas") ?: "")
                    currentAlert.value = obj
                }
            }
        }
        registerReceiver(alertReceiver, IntentFilter("com.tefillin.alerts.NEW_ALERT"),
            RECEIVER_NOT_EXPORTED)

        setContent {
            AlertApp(
                currentAlertState = currentAlert.value,
                historyState = history.value,
                lastUpdateState = lastUpdate.value,
                isConnectedState = isConnected.value,
                onFetchHistory = { fetchHistory() },
                onFetchCurrent = { fetchCurrent() },
                onUpdateStates = { alert, hist, update, connected ->
                    currentAlert.value = alert
                    history.value = hist
                    lastUpdate.value = update
                    isConnected.value = connected
                },
                onVibrate = { vibrate() },
                onSaveCity = { city ->
                    getSharedPreferences("alerts_prefs", Context.MODE_PRIVATE)
                        .edit().putString("city_filter", city).apply()
                },
                onGetCity = {
                    getSharedPreferences("alerts_prefs", Context.MODE_PRIVATE)
                        .getString("city_filter", "") ?: ""
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        alertReceiver?.let { unregisterReceiver(it) }
    }

    private suspend fun fetchCurrent(): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.oref.org.il/WarningMessages/alert/alerts.json")
                .header("Referer", "https://www.oref.org.il/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val body = client.newCall(req).execute().body?.string()
                ?.trimStart('\uFEFF')?.trim() ?: ""
            if (body.isNotEmpty() && body.startsWith("{")) JSONObject(body) else null
        } catch (e: Exception) { null }
    }

    private suspend fun fetchHistory(): List<AlertItem> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.oref.org.il/WarningMessages/alert/History/AlertsHistory.json")
                .header("Referer", "https://www.oref.org.il/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val body = client.newCall(req).execute().body?.string()
                ?.trimStart('\uFEFF') ?: "[]"
            val arr = JSONArray(body)
            val result = mutableListOf<AlertItem>()
            for (i in 0 until minOf(arr.length(), 30)) {
                val obj = arr.getJSONObject(i)
                val date = obj.optString("alertDate", "")
                val time = if (date.length >= 16) date.substring(11, 16) else date
                result.add(AlertItem(
                    time = time,
                    title = obj.optString("title", "API OK"),
                    area = obj.optString("data", "")
                ))
            }
            result
        } catch (e: Exception) {
            listOf(AlertItem("--", "שגיאת API", e.message ?: "unknown"))
        }
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 150, 500, 150, 500), -1))
    }
}

@Composable
fun AlertApp(
    currentAlertState: JSONObject?,
    historyState: List<AlertItem>,
    lastUpdateState: String,
    isConnectedState: Boolean,
    onFetchHistory: suspend () -> List<AlertItem>,
    onFetchCurrent: suspend () -> JSONObject?,
    onUpdateStates: (JSONObject?, List<AlertItem>, String, Boolean) -> Unit,
    onVibrate: () -> Unit,
    onSaveCity: (String) -> Unit,
    onGetCity: () -> String
) {
    var screen by remember { mutableStateOf("splash") }
    var lastAlertId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        delay(2000)
        val hist = onFetchHistory()
        onUpdateStates(null, hist, "", true)
        screen = "main"
        while (true) {
            try {
                val data = onFetchCurrent()
                val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val id = data?.optString("id", "") ?: ""
                val newHist = if (id.isNotEmpty() && id != lastAlertId) {
                    lastAlertId = id
                    onVibrate()
                    onFetchHistory()
                } else historyState
                onUpdateStates(if (id.isEmpty()) null else data, newHist, now, true)
            } catch (e: Exception) {
                onUpdateStates(currentAlertState, historyState, lastUpdateState, false)
            }
            delay(3000)
        }
    }

    when (screen) {
        "splash" -> SplashScreen()
        "main" -> MainScreen(
            currentAlert = currentAlertState,
            history = historyState,
            lastUpdate = lastUpdateState,
            isConnected = isConnectedState,
            onShowHistory = { screen = "history" },
            onShowSettings = { screen = "settings" },
            onVibrate = onVibrate
        )
        "history" -> HistoryScreen(history = historyState, onBack = { screen = "main" })
        "settings" -> SettingsScreen(
            initialCity = onGetCity(),
            onSave = { city -> onSaveCity(city); screen = "main" },
            onBack = { screen = "main" }
        )
    }
}

@Composable
fun SplashScreen() {
    val infinite = rememberInfiniteTransition(label = "")
    val scale by infinite.animateFloat(
        initialValue = 0.8f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = ""
    )
    Box(modifier = Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "* * *", color = RED, fontSize = 18.sp, modifier = Modifier.scale(scale))
            Spacer(Modifier.height(8.dp))
            Text(text = "פיקוד העורף", color = RED, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text = "מאסטר קוד משחקים בעמ", color = GRAY, fontSize = 9.sp)
        }
    }
}

@Composable
fun MainScreen(
    currentAlert: JSONObject?,
    history: List<AlertItem>,
    lastUpdate: String,
    isConnected: Boolean,
    onShowHistory: () -> Unit,
    onShowSettings: () -> Unit,
    onVibrate: () -> Unit
) {
    var testActive by remember { mutableStateOf(false) }
    val hasAlert = currentAlert != null || testActive
    val bgColor by animateColorAsState(
        targetValue = if (hasAlert) Color(0xFF2D0000) else BG,
        animationSpec = tween(500), label = ""
    )

    Box(modifier = Modifier.fillMaxSize().background(bgColor), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColor = if (isConnected) GREEN else RED
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                Text(text = "  " + lastUpdate, color = GRAY, fontSize = 9.sp)
            }

            Spacer(Modifier.height(4.dp))

            if (hasAlert) {
                val displayAlert = if (testActive) {
                    val fake = JSONObject()
                    fake.put("title", "בדיקת מערכת")
                    fake.put("data", "קריית ים")
                    fake
                } else currentAlert!!
                AlertActiveContent(displayAlert)
            } else {
                SafeContent(history.size)
            }

            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBtn(text = "היסטוריה", color = BLUE, onClick = onShowHistory)
                SmallBtn(text = "הגדרות", color = GRAY, onClick = onShowSettings)
            }

            Spacer(Modifier.height(4.dp))

            SmallBtn(
                text = if (testActive) "X בטל בדיקה" else "בדיקה",
                color = if (testActive) RED else Color(0xFFFFE600),
                onClick = {
                    testActive = !testActive
                    if (testActive) onVibrate()
                }
            )
        }
    }
}

@Composable
fun SmallBtn(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(CARD)
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = text, color = color, fontSize = 10.sp)
    }
}

@Composable
fun AlertActiveContent(alert: JSONObject) {
    val infinite = rememberInfiniteTransition(label = "")
    val alpha by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse), label = ""
    )
    Text(
        text = "!! " + alert.optString("title", "אזעקה") + " !!",
        color = RED.copy(alpha = alpha), fontSize = 14.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = alert.optString("data", ""),
        color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center,
        maxLines = 3, overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SafeContent(count: Int) {
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF003322)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "V", color = GREEN, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(4.dp))
    Text(text = "בטוח", color = GREEN, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Text(text = count.toString() + " התרעות היום", color = GRAY, fontSize = 10.sp)
}

@Composable
fun HistoryScreen(history: List<AlertItem>, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BG)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "< חזור", color = BLUE, fontSize = 11.sp,
                    modifier = Modifier.clickable { onBack() })
                Text(text = "התרעות היום", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold)
            }
            if (history.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "אין התרעות היום", color = GREEN, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                    items(history) { item ->
                        AlertRow(item)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AlertRow(item: AlertItem) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(CARD).padding(8.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = item.title, color = RED, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(text = item.time, color = GRAY, fontSize = 10.sp)
            }
            Text(text = item.area, color = Color.White, fontSize = 10.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SettingsScreen(initialCity: String, onSave: (String) -> Unit, onBack: () -> Unit) {
    var city by remember { mutableStateOf(initialCity) }
    Box(modifier = Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
            Text(text = "סינון לפי עיר", color = BLUE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text = "התרעה רק כשהעיר שלך מופיעה", color = GRAY, fontSize = 10.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(CARD).padding(10.dp)
            ) {
                if (city.isEmpty()) {
                    Text(text = "למשל: קריית ים", color = GRAY, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                BasicTextField(
                    value = city, onValueChange = { city = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp,
                        textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(BLUE),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(text = "ריק = כל ההתרעות", color = GRAY, fontSize = 9.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallBtn(text = "בטל", color = GRAY, onClick = onBack)
                SmallBtn(text = "שמור", color = GREEN, onClick = { onSave(city) })
            }
        }
    }
}
