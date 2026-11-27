package ru.sberbigtrades

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        setContent { Screen(this) }
    }
}

data class Trade(val id: String, val time: String, val direction: String, val price: Double, val qty: Long, val amount: Double) {
    val signedAmount: Double get() = if(direction == "BUY") amount else -amount
}

private val http = OkHttpClient()
private val jsonType = "application/json".toMediaType()

suspend fun fetchTrades(token: String, figi: String): List<Trade> = withContext(Dispatchers.IO) {
    val now = Instant.now()
    val from = now.minusSeconds(65).toString()
    val to = now.toString()
    val payload = JSONObject().put("instrumentId", figi).put("from", from).put("to", to).toString()
    val request = Request.Builder().url("https://invest-public-api.tinkoff.ru/rest/tinkoff.public.invest.api.v1.MarketDataService/GetLastTrades")
        .addHeader("Authorization", "Bearer $token").addHeader("Content-Type", "application/json")
        .post(payload.toRequestBody(jsonType)).build()
    http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("API: HTTP ${response.code} — проверь ключ и instrumentId")
        val root = JSONObject(response.body?.string() ?: "{}")
        val arr = root.optJSONArray("trades") ?: JSONArray()
        val list = mutableListOf<Trade>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val p = o.optJSONObject("price") ?: JSONObject()
            val price = p.optDouble("units", 0.0) + p.optDouble("nano", 0.0) / 1e9
            val qty = o.optLong("quantity", 0)
            val direction = when(o.optString("direction")) { "BUY", "TRADE_DIRECTION_BUY" -> "BUY"; "SELL", "TRADE_DIRECTION_SELL" -> "SELL"; else -> "?" }
            val timestamp = o.optString("time", "")
            val id = "$timestamp-${o.optString("price")}-${qty}-${direction}"
            list.add(Trade(id, timestamp, direction, price, qty, price*qty))
        }
        list
    }
}

@Composable fun Screen(context: Context) {
    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var instrument by remember { mutableStateOf(prefs.getString("instrument", "BBG004730N88") ?: "BBG004730N88") }
    var thresholdText by remember { mutableStateOf(prefs.getString("threshold", "500000") ?: "500000") }
    var threshold by remember { mutableDoubleStateOf(thresholdText.toDoubleOrNull() ?: 500000.0) }
    var trades by remember { mutableStateOf(listOf<Trade>()) }
    var status by remember { mutableStateOf("Введите API-ключ и нажмите Подключить") }
    var running by remember { mutableStateOf(false) }
    var cvd by remember { mutableDoubleStateOf(0.0) }
    val dark = Color(0xFF10151C)
    val panel = Color(0xFF1B2430)
    val green = Color(0xFF35D07F)
    val red = Color(0xFFFF647C)

    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF38BDF8), background=dark, surface=panel)) {
        Column(Modifier.fillMaxSize().background(dark).padding(16.dp)) {
            Text("SBER BIG TRADES", color=Color.White, style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
            Text("Крупные сделки · Delta · CVD", color=Color.LightGray)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(token, {token=it}, label={Text("API-ключ T-Invest")}, singleLine=true, modifier=Modifier.fillMaxWidth())
            OutlinedTextField(instrument, {instrument=it}, label={Text("FIGI / instrumentId")}, singleLine=true, modifier=Modifier.fillMaxWidth())
            OutlinedTextField(thresholdText, {thresholdText=it; threshold=it.toDoubleOrNull()?:500000.0}, label={Text("Порог сделки, ₽")}, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number), modifier=Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick={
                    prefs.edit().putString("token",token).putString("instrument",instrument).putString("threshold",thresholdText).apply()
                    running=true; status="Подключение…"
                }, enabled=!running) { Text("Подключить") }
                OutlinedButton(onClick={running=false}) { Text("Остановить") }
            }
            Text(status, color=Color.LightGray, modifier=Modifier.padding(vertical=8.dp))
            Row(Modifier.fillMaxWidth().background(panel).padding(12.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                Column { Text("Delta (сессия)",color=Color.LightGray); Text(String.format(Locale.US,"%+.0f ₽",trades.sumOf{it.signedAmount}),color=if(trades.sumOf{it.signedAmount}>=0)green else red,fontWeight=FontWeight.Bold) }
                Column { Text("CVD (накоплено)",color=Color.LightGray); Text(String.format(Locale.US,"%+.0f ₽",cvd),color=if(cvd>=0)green else red,fontWeight=FontWeight.Bold) }
            }
            Spacer(Modifier.height(10.dp))
            Text("Сделки выше ${String.format(Locale.US,"%.0f",threshold)} ₽", color=Color.White,fontWeight=FontWeight.Bold)
            LazyColumn(Modifier.fillMaxSize()) {
                items(trades.filter{it.amount>=threshold}.sortedByDescending{it.time}, key={it.id}) { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical=5.dp).background(panel).padding(10.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(t.direction, color=if(t.direction=="BUY")green else red,fontWeight=FontWeight.Bold)
                            Text(t.time.replace("T"," ").take(19),color=Color.LightGray)
                        }
                        Column {
                            Text(String.format(Locale.US,"%.2f ₽",t.price),color=Color.White)
                            Text(String.format(Locale.US,"%.0f ₽  ·  %d шт",t.amount,t.qty),color=Color.White)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(running, token, instrument, threshold) {
        if (running) {
            val seen = mutableSetOf<String>(); cvd=0.0
            while(running) {
                try {
                    val fetched=fetchTrades(token,instrument)
                    val fresh=fetched.filter{seen.add(it.id)}
                    cvd += fresh.sumOf{it.signedAmount}
                    trades=(fresh+trades).distinctBy{it.id}.take(500)
                    status="Подключено · ${Instant.now().toString().take(19)} UTC · проверка каждые 5 сек"
                } catch(e: Exception) { status="Ошибка: ${e.message ?: "не удалось получить данные"}" }
                delay(5000)
            }
        }
    }
}
