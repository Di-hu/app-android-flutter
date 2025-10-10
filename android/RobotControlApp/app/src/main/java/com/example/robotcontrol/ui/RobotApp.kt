package com.example.robotcontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.robotcontrol.webrtc.RtcClient
import kotlinx.coroutines.launch

@Composable
fun RobotApp() {
    val scope = rememberCoroutineScope()
    val rtcClient = remember { RtcClient() }
    var signalingUrl by remember { mutableStateOf("ws://10.0.0.2:8080/ws") }
    var roomId by remember { mutableStateOf("robot-1") }
    var isConnected by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Robot Control") })
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            // Placeholder for video view (SurfaceViewRenderer requires Android View interop)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isConnected) "Live Video" else "Disconnected",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = signalingUrl,
                    onValueChange = { signalingUrl = it },
                    label = { Text("Signaling URL") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = roomId,
                    onValueChange = { roomId = it },
                    label = { Text("Room/Robot ID") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(Modifier.padding(horizontal = 16.dp)) {
                Button(onClick = {
                    scope.launch {
                        rtcClient.connect(signalingUrl, roomId)
                        isConnected = true
                    }
                }) { Text("Connect") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = {
                    rtcClient.disconnect()
                    isConnected = false
                }) { Text("Disconnect") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { rtcClient.sendControl("{\"cmd\":\"e_stop\"}") }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                    Text("E‑Stop", color = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Simple D-Pad controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(onClick = { rtcClient.sendControl("{\"cmd\":\"forward\",\"v\":1}") }) { Text("↑") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { rtcClient.sendControl("{\"cmd\":\"left\",\"v\":1}") }) { Text("←") }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = { rtcClient.sendControl("{\"cmd\":\"stop\"}") }) { Text("■") }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = { rtcClient.sendControl("{\"cmd\":\"right\",\"v\":1}") }) { Text("→") }
                }
                Button(onClick = { rtcClient.sendControl("{\"cmd\":\"back\",\"v\":1}") }) { Text("↓") }
            }
        }
    }
}
