package com.tefillin.alerts

import android.content.Context
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlertApp(
                onFetchCurrent = { fetchCurrent() },
                onFetchHistory = { fetchHistory() },
                onVibrate = { vibrate() }
            )
        }
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
        } catch (e: Exception) {
            null
        }
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
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val result = mutableListOf<AlertItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val date = obj.optString("alertDate", "")
                if (date.startsWith(today)) {
                    val time = if (date.length >= 16) date.substring(11, 16) else ""
                    result.add(AlertItem(
                        time = time,
                        title = obj.optString("title", ""),
                        area = obj.optString("data", "")
                    ))
                }
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 150, 500, 150, 500), -1))
    }
}

@Composable
fun AlertApp(
    onFetchCurrent: suspend () -> JSONObject?,
    onFetchHistory: suspend () -> List<AlertItem>,
    onVibrate: () -> Unit
) {
    var screen by remember { mutableStateOf("splash") }
    var currentAlert by remember { mutableStateOf<JSONObject?>(null) }
    var history by remember { mutableStateOf<List<AlertItem>>(emptyList()) }
    var lastUpdate by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(true) }
    var lastAlertId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        delay(2000)
        history = onFetchHistory()
        screen = "main"
        while (true) {
            try {
                val data = onFetchCurrent()
                isConnected = true
                val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                lastUpdate = now
                val id = data?.optString("id", "") ?: ""
                if (id.isNotEmpty() && id != lastAlertId) {
                    lastAlertId = id
                    currentAlert = data
                    onVibrate()
                    history = onFetchHistory()
                } else if (id.isEmpty()) {
                    currentAlert = null
                }
            } catch (e: Exception) {
                isConnected = false
            }
            delay(3000)
        }
    }

    when (screen) {
        "splash" -> SplashScreen()
        "main" -> MainScreen(
            currentAlert = currentAlert,
            history = history,
            lastUpdate = lastUpdate,
            isConnected = isConnected,
            onShowHistory = { screen = "history" }
        )
        "history" -> HistoryScreen(
            history = history,
            onBack = { screen = "main" }
        )
    }
}

@Composable
fun SplashScreen() {
    val infinite = rememberInfiniteTransition(label = "")
    val scale by infinite.animateFloat(
        initialValue = 0.8f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = ""
    )
    Box(
        modifier = Modifier.fillMaxSize().background(BG),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "* * *",
                color = RED,
                fontSize = 18.sp,
                modifier = Modifier.scale(scale)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "פיקוד העורף",
                color = RED,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "מאסטר קוד משחקים בעמ",
                color = GRAY,
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MainScreen(
    currentAlert: JSONObject?,
    history: List<AlertItem>,
    lastUpdate: String,
    isConnected: Boolean,
    onShowHistory: () -> Unit
) {
    val hasAlert = currentAlert != null
    val bgColor by animateColorAsState(
        targetValue = if (hasAlert) Color(0xFF2D0000) else BG,
        animationSpec = tween(500),
        label = ""
    )
    val dotColor = if (isConnected) GREEN else RED

    Box(
        modifier = Modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor)
                )
                Text(
                    text = "  " + lastUpdate,
                    color = GRAY,
                    fontSize = 9.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            if (hasAlert) {
                AlertActiveContent(currentAlert!!)
            } else {
                SafeContent(history.size)
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CARD)
                    .clickable { onShowHistory() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "היסטוריה (" + history.size.toString() + ")",
                    color = BLUE,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun AlertActiveContent(alert: JSONObject) {
    val infinite = rememberInfiniteTransition(label = "")
    val alpha by infinite.animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = ""
    )
    val title = alert.optString("title", "אזעקה")
    val dataArr = alert.optJSONArray("data")
    val areas = if (dataArr != null) {
        val sb = StringBuilder()
        for (i in 0 until dataArr.length()) {
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append(dataArr.getString(i))
        }
        sb.toString()
    } else {
        alert.optString("data", "")
    }

    Text(
        text = "!! " + title + " !!",
        color = RED.copy(alpha = alpha),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = areas,
        color = Color.White,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SafeContent(count: Int) {
    Box(
        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF003322)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "V", color = GREEN, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(6.dp))
    Text(text = "בטוח", color = GREEN, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Text(text = count.toString() + " התרעות היום", color = GRAY, fontSize = 10.sp)
}

@Composable
fun HistoryScreen(
    history: List<AlertItem>,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(BG)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "< חזור",
                    color = BLUE,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onBack() }
                )
                Text(
                    text = "התרעות היום",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CARD)
            .padding(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    color = RED,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(text = item.time, color = GRAY, fontSize = 10.sp)
            }
            Text(
                text = item.area,
                color = Color.White,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
