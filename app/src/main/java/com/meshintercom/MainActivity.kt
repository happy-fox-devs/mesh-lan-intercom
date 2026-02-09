package com.meshintercom

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.meshlanintercom.R
import kotlinx.coroutines.launch

// --- Custom Colors ---
val DarkBackground = Color(0xFF0E161D)
val SurfaceColor = Color(0xFF1F2C34)
val NeonGreen = Color(0xFF00E676)
val InactiveGrey = Color(0xFF536471)
val TextColor = Color(0xFFE7E9EA)

class MainActivity : AppCompatActivity() {

    // Use MutableState to trigger recomposition on bind
    private val _meshService = mutableStateOf<MeshService?>(null)

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName, service: IBinder) {
                val binder = service as MeshService.LocalBinder
                _meshService.value = binder.getService()
            }

            override fun onServiceDisconnected(arg0: ComponentName) {
                _meshService.value = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to MeshService
        Intent(this, MeshService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            var peersList by remember { mutableStateOf(emptyList<String>()) }
            var isAudioActive by remember { mutableStateOf(false) } // From Service

            // Observe the Service instance state
            val currentService = _meshService.value

            LaunchedEffect(currentService) {
                if (currentService != null) {
                    launch { currentService.peers.collect { peersList = it } }
                    launch { currentService.isAudioRunning.collect { isAudioActive = it } }
                }
            }

            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        background = DarkBackground,
                        surface = SurfaceColor,
                        primary = NeonGreen,
                        onBackground = TextColor,
                        onSurface = TextColor
                    )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass state explicitly to avoid passing nullable service
                    MainNavigationWrapper(
                        peers = peersList,
                        isAudioRunningState = isAudioActive, // New parameter to drive UI state
                        onStartAudio = { secret, nickname ->
                            val intent =
                                Intent(this@MainActivity, MeshService::class.java).apply {
                                    action = MeshService.ACTION_START_AUDIO
                                    putExtra(MeshService.EXTRA_SECRET, secret)
                                    putExtra(MeshService.EXTRA_NICKNAME, nickname)
                                }
                            startService(intent) // Start Foreground Service
                        },
                        onStopAudio = {
                            val intent =
                                Intent(this@MainActivity, MeshService::class.java).apply {
                                    action = MeshService.ACTION_STOP_AUDIO
                                }
                            startService(intent) // Send Stop action
                        },
                        context = this,
                        onMicMuteToggle = { muted -> _meshService.value?.isMicMuted = muted },
                        onDeafenToggle = { deafened ->
                            _meshService.value?.isDeafened = deafened
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (_meshService.value != null) {
            unbindService(connection)
            _meshService.value = null
        }
    }
}

@Composable
fun MainNavigationWrapper(
    peers: List<String>,
    isAudioRunningState: Boolean,
    onStartAudio: (String, String) -> Unit,
    onStopAudio: () -> Unit,
    context: Context,
    onMicMuteToggle: (Boolean) -> Unit,
    onDeafenToggle: (Boolean) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf("Home") }

    // Drawer content
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceColor,
                drawerContentColor = TextColor,
                modifier = Modifier.width(300.dp) // Not full width
            ) {
                // Header / User Section
                Spacer(modifier = Modifier.height(24.dp))
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_home)) },
                    selected = currentScreen == "Home",
                    onClick = {
                        currentScreen = "Home"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Home, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Spacer(modifier = Modifier.weight(1f)) // Push settings to bottom

                Divider(color = InactiveGrey.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Settings Section
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.menu_settings)) },
                    selected = currentScreen == "Settings",
                    onClick = {
                        currentScreen = "Settings"
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        },
        gesturesEnabled = true
    ) {
        // Main Content Area
        Box(modifier = Modifier.fillMaxSize()) {
            if (currentScreen == "Home") {
                MeshIntercomApp(
                    peers = peers,
                    isAudioRunningState = isAudioRunningState,
                    onStartAudio = onStartAudio,
                    onStopAudio = onStopAudio,
                    context = context,
                    onMicMuteToggle = onMicMuteToggle,
                    onDeafenToggle = onDeafenToggle,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            } else {
                SettingsScreen(onBack = { currentScreen = "Home" })
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextColor)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = TextColor
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Language Selector
        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.titleMedium,
            color = NeonGreen
        )
        Spacer(modifier = Modifier.height(16.dp))

        LanguageOption(name = stringResource(R.string.language_auto), code = null)
        LanguageOption(name = stringResource(R.string.language_en), code = "en")
        LanguageOption(name = stringResource(R.string.language_es), code = "es")
    }
}

@Composable
fun LanguageOption(name: String, code: String?) {
    val context = LocalContext.current
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val isSelected =
        if (code == null) currentLocales.isEmpty
        else currentLocales.toLanguageTags().contains(code)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    val localeList =
                        if (code != null) {
                            LocaleListCompat.forLanguageTags(code)
                        } else {
                            LocaleListCompat.getEmptyLocaleList()
                        }
                    AppCompatDelegate.setApplicationLocales(localeList)
                }
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null, // Handled by Row
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = NeonGreen,
                    unselectedColor = InactiveGrey
                )
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = name, color = TextColor, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshIntercomApp(
    peers: List<String>,
    isAudioRunningState: Boolean,
    onStartAudio: (String, String) -> Unit,
    onStopAudio: () -> Unit,
    context: Context,
    onMicMuteToggle: (Boolean) -> Unit,
    onDeafenToggle: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit
) {
    // isAudioRunning is now driven by the Service (isAudioRunningState)
    val isAudioRunning = isAudioRunningState

    var isMicMuted by remember { mutableStateOf(false) }
    var isDeafened by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("mesh_prefs", Context.MODE_PRIVATE)

    // Secret Word State
    var secretWord by remember { mutableStateOf(prefs.getString("secret_word", "") ?: "") }

    // Nickname State
    val randomName = "User_${(1000..9999).random()}"
    var nickname by remember {
        mutableStateOf(prefs.getString("nickname", randomName) ?: randomName)
    }

    // Permission State
    val permissions = remember {
        mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .toTypedArray()
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsMap ->
            val allGranted = permissionsMap.values.all { it }
        }

    LaunchedEffect(Unit) { launcher.launch(permissions) }

    // Colors for Inputs
    val inputColors =
        TextFieldDefaults.colors(
            focusedContainerColor = SurfaceColor,
            unfocusedContainerColor = SurfaceColor,
            disabledContainerColor = SurfaceColor.copy(alpha = 0.5f),
            focusedTextColor = TextColor,
            unfocusedTextColor = TextColor,
            cursorColor = NeonGreen,
            focusedIndicatorColor = Color.Transparent, // Remove underline
            unfocusedIndicatorColor = Color.Transparent
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = InactiveGrey)
            }

            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isAudioRunning) NeonGreen else InactiveGrey)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.app_title_small),
                style = MaterialTheme.typography.labelSmall,
                color = InactiveGrey
            )
        }

        // --- Inputs ---
        // Channel Input
        TextField(
            value = secretWord,
            onValueChange = {
                secretWord = it
                prefs.edit().putString("secret_word", it).apply()
            },
            placeholder = {
                Text(stringResource(R.string.secret_channel_hint), color = InactiveGrey)
            },
            leadingIcon = { Text("#", color = InactiveGrey, fontSize = 18.sp) },
            enabled = !isAudioRunning,
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp)),
            colors = inputColors
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Nickname Input
        TextField(
            value = nickname,
            onValueChange = {
                nickname = it
                prefs.edit().putString("nickname", it).apply()
            },
            placeholder = {
                Text(stringResource(R.string.nickname_hint), color = InactiveGrey)
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint = InactiveGrey,
                    modifier = Modifier.size(20.dp)
                )
            },
            enabled = !isAudioRunning,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            colors = inputColors
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Peer List / Status ---
        if (isAudioRunning) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${peers.size + 1} " + stringResource(R.string.connected_peers),
                    style = MaterialTheme.typography.labelMedium,
                    color = InactiveGrey,
                    letterSpacing = 2.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Me Entry
                item {
                    PeerCard(
                        name = "$nickname " + stringResource(R.string.you_suffix),
                        isMe = true,
                        isActive = true
                    )
                }
                // Peers
                items(peers) { peerName ->
                    PeerCard(name = peerName, isMe = false, isActive = true)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.disconnected_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = InactiveGrey,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.connect_instructions),
                color = InactiveGrey,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- Bottom Controls ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mic Mute Button
            if (isAudioRunning) {
                ControlButton(
                    icon = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    isActive = !isMicMuted,
                    isDeafened = isDeafened,
                    onClick = {
                        isMicMuted = !isMicMuted
                        onMicMuteToggle(isMicMuted)
                    },
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.width(32.dp))
            }

            // Power Button (Center)
            BigPowerButton(
                isAudioRunning = isAudioRunning,
                onClick = {
                    val allGranted =
                        permissions.all {
                            ContextCompat.checkSelfPermission(context, it) ==
                                    PackageManager.PERMISSION_GRANTED
                        }
                    if (secretWord.isNotBlank() && nickname.isNotBlank()) {
                        if (allGranted) {
                            if (isAudioRunning) {
                                onStopAudio()
                                // isAudioRunning = false // Removed: State driven by Service
                            } else {
                                onStartAudio(secretWord, nickname)
                                // isAudioRunning = true // Removed: State driven by Service
                            }
                        } else {
                            launcher.launch(permissions)
                        }
                    }
                }
            )

            // Deafen Button
            if (isAudioRunning) {
                Spacer(modifier = Modifier.width(32.dp))
                ControlButton(
                    icon = if (isDeafened) Icons.Filled.HeadsetOff else Icons.Filled.Headset,
                    isActive = !isDeafened,
                    isDeafened = false,
                    onClick = {
                        isDeafened = !isDeafened
                        onDeafenToggle(isDeafened)
                        if (isDeafened) isMicMuted = true else isMicMuted = false
                        onMicMuteToggle(isMicMuted)
                    },
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

@Composable
fun PeerCard(name: String, isMe: Boolean, isActive: Boolean) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isMe) NeonGreen.copy(alpha = 0.1f) else SurfaceColor)
                .border(
                    1.dp,
                    if (isMe) NeonGreen.copy(alpha = 0.3f) else Color.Transparent,
                    RoundedCornerShape(8.dp)
                )
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMe) NeonGreen.copy(alpha = 0.2f)
                        else InactiveGrey.copy(alpha = 0.2f)
                    ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew, // Placeholder for user icon
                contentDescription = null,
                tint = if (isMe) NeonGreen else InactiveGrey,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            color = if (isMe) NeonGreen else TextColor,
            fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    isDeafened: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = SurfaceColor
    val iconColor = if (isDeafened) InactiveGrey else TextColor

    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        modifier = modifier,
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun BigPowerButton(isAudioRunning: Boolean, onClick: () -> Unit) {
    val borderColor = animateColorAsState(if (isAudioRunning) NeonGreen else SurfaceColor)
    val circleColor = animateColorAsState(if (isAudioRunning) DarkBackground else SurfaceColor)

    Button(
        onClick = onClick,
        shape = CircleShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ), // We draw custom
        modifier = Modifier.size(100.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(4.dp, borderColor.value, CircleShape)
                    .border(8.dp, DarkBackground, CircleShape) // Inner gap
                    .clip(CircleShape)
                    .background(circleColor.value)
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = "Power",
                tint = if (isAudioRunning) NeonGreen else InactiveGrey,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Previ() {
    MaterialTheme(colorScheme = darkColorScheme(background = DarkBackground)) {
        Surface(color = DarkBackground) {
            // Preview logic
        }
    }
}
