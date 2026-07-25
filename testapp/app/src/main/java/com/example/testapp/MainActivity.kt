package com.example.testapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testapp.theme.TestAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TestAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NetworkTester()
                }
            }
        }
    }
}

@Composable
fun NetworkTester() {
    val coroutineScope = rememberCoroutineScope()
    var responseText by remember { mutableStateOf("No request made yet.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "mitmproxy Test App", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = androidx.compose.ui.graphics.Color.Gray)
        Text(text = "✅ 正常可被抓包 (能看到请求记录)", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.Green)
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            coroutineScope.launch {
                responseText = "Sending HTTP GET request..."
                responseText = sendRequest("GET", "http://103.11.77.126:8080/test?param1=value1")
            }
        }) {
            Text(text = "Send HTTP GET (VPS)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            coroutineScope.launch {
                responseText = "Sending HTTP POST request..."
                responseText = sendRequest("POST", "http://103.11.77.126:8080/test")
            }
        }) {
            Text(text = "Send HTTP POST (VPS)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            coroutineScope.launch {
                responseText = "Sending HTTPS GET request..."
                responseText = sendRequest("GET", "https://test.pengproxy.dpdns.org:2096/test?param1=value1")
            }
        }) {
            Text(text = "Send HTTPS GET (Custom Domain)")
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            coroutineScope.launch {
                responseText = "Sending HTTPS POST request..."
                responseText = sendRequest("POST", "https://test.pengproxy.dpdns.org:2096/test")
            }
        }) {
            Text(text = "Send HTTPS POST (Custom Domain)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Payload Encryption Test Button (Can be captured, but encrypted)
        Button(onClick = {
            coroutineScope.launch {
                responseText = "Testing App-Level Encryption..."
                responseText = sendRequestEncrypted("POST", "http://103.11.77.126:8080/test")
            }
        }) {
            Text(text = "Test Payload Encryption (E2EE) - 全是乱码")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. WebSocket Test Button
        Button(onClick = {
            coroutineScope.launch {
                responseText = "Connecting to WebSocket..."
                responseText = testWebSocket()
            }
        }) {
            Text(text = "Test WebSocket (ws://103.11.77.126:2053)")
        }

        Spacer(modifier = Modifier.height(24.dp))
        androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = androidx.compose.ui.graphics.Color.Gray)
        Text(text = "❌ 模拟商业防护 (完全抓不到包)", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color.Red)
        Spacer(modifier = Modifier.height(8.dp))

        // SSL Pinning Test Button
        Button(onClick = {
            coroutineScope.launch {
                responseText = "Testing SSL Pinning..."
                responseText = sendRequest("GET", "https://httpbin.org/get")
            }
        }) {
            Text(text = "Test SSL Pinning (httpbin.org)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Proxy Bypass (No Proxy) Test Button
        Button(onClick = {
            coroutineScope.launch {
                responseText = "Testing Proxy Bypass (NO_PROXY)..."
                responseText = sendRequestNoProxy("GET", "http://103.11.77.126:8080/test")
            }
        }) {
            Text(text = "Test Proxy Bypass (NO_PROXY)")
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = responseText)
    }
}

suspend fun sendRequest(method: String, urlString: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (method == "POST") {
                connection.doOutput = true
                val output = connection.outputStream
                output.write("{\"key\": \"value\"}".toByteArray())
                output.flush()
                output.close()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                "Success ($responseCode):\n$response"
            } else {
                "Error: HTTP $responseCode"
            }
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }
}

suspend fun sendRequestNoProxy(method: String, urlString: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            // 核心防御：强制不走系统代理 (Proxy.NO_PROXY)
            val connection = url.openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                "Proxy Bypass Success ($responseCode)! mitmproxy could not see this."
            } else {
                "Error: HTTP $responseCode"
            }
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }
}

suspend fun sendRequestEncrypted(method: String, urlString: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true

            // 核心防御：应用层端到端加密 (这里用 Base64 模拟 AES 加密)
            val originalPayload = "{\"secret_user_id\": \"12345\", \"password\": \"my_super_secret\"}"
            val encryptedPayload = android.util.Base64.encodeToString(originalPayload.toByteArray(), android.util.Base64.NO_WRAP)
            
            val output = connection.outputStream
            output.write("{\"encrypted_data\": \"$encryptedPayload\"}".toByteArray())
            output.flush()
            output.close()

            val responseCode = connection.responseCode
            "Encrypted POST Sent ($responseCode). Check mitmproxy to see the gibberish body!"
        } catch (e: Exception) {
            "Exception: ${e.message}"
        }
    }
}

suspend fun testWebSocket(): String = suspendCoroutine { continuation ->
    try {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://103.11.77.126:2053").build()
        
        var receivedMessage = ""
        
        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Connection opened, send a message
                webSocket.send("Hello mitmproxy! Can you see this WebSocket frame?")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Received echo message from server
                receivedMessage = text
                webSocket.close(1000, "Done")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                continuation.resume("WebSocket Success! Server Echoed:\n$receivedMessage")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                continuation.resume("WebSocket Error: ${t.message}")
            }
        })
    } catch (e: Exception) {
        continuation.resume("Exception: ${e.message}")
    }
}
