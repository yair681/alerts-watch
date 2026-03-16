package com.tefillin.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AlertService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val client = OkHttpClient()
    private var lastAlertId = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        startForeground(1, buildNotification("מאזין לאזעקות..."))
        startPolling()
        return START_STICKY
    }

    private fun startPolling() {
        scope.launch {
            while (true) {
                checkAlert()
                delay(3000)
            }
        }
    }

    private suspend fun checkAlert() {
        try {
            val req = Request.Builder()
                .url("https://www.oref.org.il/WarningMessages/alert/alerts.json")
                .header("Referer", "https://www.oref.org.il/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()
            val body = withContext(Dispatchers.IO) {
                client.newCall(req).execute().body?.string()
            }?.trimStart('\uFEFF')?.trim() ?: ""

            if (body.isNotEmpty() && body.startsWith("{")) {
                val data = JSONObject(body)
                val id = data.optString("id", "")
                if (id.isNotEmpty() && id != lastAlertId) {
                    val prefs = getSharedPreferences("alerts_prefs", Context.MODE_PRIVATE)
                    val cityFilter = prefs.getString("city_filter", "") ?: ""
                    val arr = data.optJSONArray("data")
                    val areasList = mutableListOf<String>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) areasList.add(arr.getString(i))
                    } else {
                        val single = data.optString("data", "")
                        if (single.isNotEmpty()) areasList.add(single)
                    }
                    val areasText = areasList.joinToString(", ")
                    val matchesFilter = cityFilter.isEmpty() ||
                            areasList.any { it.contains(cityFilter) }

                    if (matchesFilter) {
                        lastAlertId = id
                        val title = data.optString("title", "אזעקה")
                        vibrate()
                        updateNotification(title + " - " + areasText)
                        val broadcast = Intent("com.tefillin.alerts.NEW_ALERT")
                        broadcast.putExtra("id", id)
                        broadcast.putExtra("title", title)
                        broadcast.putExtra("areas", areasText)
                        sendBroadcast(broadcast)
                    }
                }
            }
        } catch (e: Exception) {
            // silent fail
        }
    }

    private fun vibrate() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 150, 500, 150, 500), -1))
    }

    private fun createChannel() {
        val ch = NotificationChannel("alerts_ch", "אזעקות פיקוד העורף",
            NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        return Notification.Builder(this, "alerts_ch")
            .setContentTitle("פיקוד העורף")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
