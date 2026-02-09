package com.meshintercom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var meshManager: MeshNetworkManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        meshManager = MeshNetworkManager(this)
        
        setContent {
            var peersList by remember { mutableStateOf(emptyList<String>()) }
            
            // Observe peers from MeshManager
            DisposableEffect(Unit) {
                meshManager?.onPeersChanged = { peers ->
                    peersList = peers
                }
                onDispose { }
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeshIntercomApp(
                        nativeString = stringFromJNI(),
                        peers = peersList,
                        onStartAudio = { secret, nickname ->
                            meshManager?.start(secret, nickname)
                            nativeStartAudio() 
                        },
                        onStopAudio = { 
                            nativeStopAudio()
                            meshManager?.stop()
                        },
                        context = this
                    )
                }
            }
        }
        
        // Link Network Receive -> Native Inject
        meshManager?.onAudioPacketReceived = { data ->
            nativeInjectAudioPacket(data)
        }

        // Initialize JNI Global Reference for callback
        nativeInitJni()
    }

    override fun onDestroy() {
        super.onDestroy()
        nativeStopAudio()
        meshManager?.stop()
    }
    
    // Called from C++ to send data
    fun onNativeAudioData(data: ByteArray) {
        meshManager?.sendAudioPacket(data)
    }

    external fun stringFromJNI(): String
    external fun nativeStartAudio()
    external fun nativeStopAudio()
    external fun nativeInjectAudioPacket(data: ByteArray)
    external fun nativeInitJni()

    companion object {
        init {
            System.loadLibrary("meshintercom")
        }
    }
}

@Composable
fun MeshIntercomApp(
    nativeString: String, 
    peers: List<String>,
    onStartAudio: (String, String) -> Unit, 
    onStopAudio: () -> Unit,
    context: Context
) {
    var isAudioRunning by remember { mutableStateOf(false) }
    val prefs = context.getSharedPreferences("mesh_prefs", Context.MODE_PRIVATE)
    
    // Secret Word State
    var secretWord by remember { mutableStateOf(prefs.getString("secret_word", "") ?: "") }
    var isEditingSecret by remember { mutableStateOf(secretWord.isEmpty()) }
    
    // Nickname State
    val randomName = "User_${(1000..9999).random()}"
    var nickname by remember { mutableStateOf(prefs.getString("nickname", randomName) ?: randomName) }
    var isEditingNickname by remember { mutableStateOf(false) }

    // Permission State
    val permissions = remember {
        mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                 add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val allGranted = permissionsMap.values.all { it }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (isAudioRunning) Arrangement.Top else Arrangement.Center
    ) {
        // --- Header Section (Inputs) ---
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_logo_custom),
            contentDescription = "App Logo",
            modifier = Modifier.size(100.dp),
            tint = Color.Unspecified // Use original image colors
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Set up your channel", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Secret Word Input
        OutlinedTextField(
            value = secretWord,
            onValueChange = { if (isEditingSecret) secretWord = it },
            label = { Text("Secret Channel Word") },
            enabled = isEditingSecret,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    if (isEditingSecret) {
                        prefs.edit().putString("secret_word", secretWord).apply()
                        isEditingSecret = false
                    } else {
                        if (!isAudioRunning) isEditingSecret = true
                    }
                }, enabled = !isAudioRunning) {
                    Icon(
                        if (isEditingSecret) Icons.Filled.Save else Icons.Filled.Edit,
                        contentDescription = "Edit Secret"
                    )
                }
            }
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // Nickname Input
        OutlinedTextField(
            value = nickname,
            onValueChange = { if (isEditingNickname) nickname = it },
            label = { Text("Your Nickname") },
            enabled = isEditingNickname,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    if (isEditingNickname) {
                        prefs.edit().putString("nickname", nickname).apply()
                        isEditingNickname = false
                    } else {
                        if (!isAudioRunning) isEditingNickname = true
                    }
                }, enabled = !isAudioRunning) {
                    Icon(
                        if (isEditingNickname) Icons.Filled.Save else Icons.Filled.Edit,
                        contentDescription = "Edit Nickname"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Middle Section (Peer List) ---
        if (isAudioRunning) {
            Text(
                text = "Connected Peers: ${peers.size}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Me (Local User)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "👤", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = "$nickname (You)", style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }

                // Remote Peers
                items(peers) { peerName ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "📡", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = peerName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        } else {
            // Spacer to push content to center/bottom when stopped
            Spacer(modifier = Modifier.weight(1f))
        }

        // --- Bottom Section (Power Button) ---
        Button(
            onClick = {
                val allGranted = permissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                
                if (secretWord.isBlank() || nickname.isBlank()) return@Button

                if (allGranted) {
                    if (isAudioRunning) {
                        onStopAudio()
                        isAudioRunning = false
                    } else {
                        onStartAudio(secretWord, nickname)
                        isAudioRunning = true
                    }
                } else {
                    launcher.launch(permissions)
                }
            },
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAudioRunning) Color(0xFF4CAF50) else Color(0xFFF44336) // Green / Red
            ),
            enabled = secretWord.isNotBlank() && !isEditingSecret && !isEditingNickname
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = "Power",
                modifier = Modifier.size(64.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (!isAudioRunning) {
             Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme {
        MeshIntercomApp("Preview", listOf("User A", "User B"), { _, _ -> }, {}, LocalContext.current)
    }
}
