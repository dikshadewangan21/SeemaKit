@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seemakit.field

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import kotlinx.coroutines.launch

const val MAX_HDOP = 2.0
fun qualityName(q: Int) = when (q) { 4 -> "RTK Fixed (cm-level)"; 5 -> "RTK Float (dm)"; 2 -> "DGPS"; 1 -> "GPS only"; else -> "No fix" }
fun gateOk(f: Fix?) = f != null && f.quality == 4 && f.hdop <= MAX_HDOP
fun gateMsg(f: Fix?) = when {
    f == null -> "No GNSS stream. Connect RTK rover or activate Demo Simulation."
    f.quality != 4 -> "${qualityName(f.quality)}: Requires RTK Fixed lock (±1-2 cm) for legal cadastral validity."
    f.hdop > MAX_HDOP -> "HDOP ${"%.1f".format(f.hdop)} exceeds threshold $MAX_HDOP. Satellite geometry insufficient."
    else -> "Fix Verified: Centimeter-accurate RTK Fixed lock achieved. Plumb pole and capture."
}

// Subtle Indian Tricolor stripe component
@Composable
fun GovTricolorBand(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(3.5.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().background(GovColors.TricolorSaffron))
        Box(Modifier.weight(1f).fillMaxHeight().background(GovColors.TricolorWhite))
        Box(Modifier.weight(1f).fillMaxHeight().background(GovColors.TricolorGreen))
    }
}

@Composable fun Nav(vm: VM) {
    val nc = rememberNavController()
    NavHost(nc, "parcels") {
        composable("parcels") { ParcelsScreen(vm, nc) }
        composable("survey/{id}") { SurveyScreen(vm, nc, it.arguments!!.getString("id")!!.toLong()) }
        composable("stakeout/{id}") { StakeoutScreen(vm, nc, it.arguments!!.getString("id")!!.toLong()) }
        composable("settings") { SettingsScreen(nc) }
    }
}

// ==========================================
// 1. PARCELS LIST SCREEN (DIGITAL GOV PORTAL)
// ==========================================
@Composable fun ParcelsScreen(vm: VM, nc: NavHostController) {
    val list by vm.parcels.collectAsState()
    var add by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Parcel?>(null) }
    var showSopDialog by remember { mutableStateOf(false) }
    var showBluetoothPicker by remember { mutableStateOf(false) }
    val sb = remember { SnackbarHostState() }
    val sc = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = GovColors.White,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                GovTricolorBand()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GovColors.DeepBlue)
                        .padding(20.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("भू-सीमा | BHU-SEEMA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Digital Cadastre Portal • भारत सरकार", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = GovColors.Green) {
                        Text("OFFICIAL SURVEYOR ACTIVE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = GovColors.BorderSubtle)

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Place, null, tint = GovColors.DeepBlue) },
                    label = { Text("Cadastral Parcels (भू-अभिलेख)", fontWeight = FontWeight.Medium) },
                    selected = true,
                    onClick = { sc.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Sync, null, tint = GovColors.DeepBlue) },
                    label = { Text("Sync with Central Cadastre", fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = {
                        sc.launch {
                            drawerState.close()
                            vm.sync()
                            sb.showSnackbar("Central Cadastre sync queued (HTTPS AES-256)")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Bluetooth, null, tint = GovColors.DeepBlue) },
                    label = { Text("Rover Hardware SPP (रोवर सेटिंग)", fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = {
                        sc.launch {
                            drawerState.close()
                            showBluetoothPicker = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.PlayArrow, null, tint = GovColors.Saffron) },
                    label = { Text("Load Sample Parcel (Khasra 142/1)", fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = {
                        sc.launch {
                            drawerState.close()
                            vm.loadDemoData()
                            sb.showSnackbar("Official sample cadastral parcel loaded (Khasra 142/1)")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Policy, null, tint = GovColors.DeepBlue) },
                    label = { Text("Cadastral SOPs & Tolerance", fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = {
                        sc.launch {
                            drawerState.close()
                            showSopDialog = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, null, tint = GovColors.DeepBlue) },
                    label = { Text("Portal Settings & Credentials", fontWeight = FontWeight.Medium) },
                    selected = false,
                    onClick = {
                        sc.launch {
                            drawerState.close()
                            nc.navigate("settings")
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(Modifier.weight(1f))
                Column(Modifier.padding(16.dp)) {
                    Text("Revenue Dept. • Government of India", style = MaterialTheme.typography.labelSmall, color = GovColors.TextSecondary)
                    Text("RTK Cadastral Version 1.0 (NIC Standard)", style = MaterialTheme.typography.labelSmall, color = GovColors.TextSecondary)
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    GovTricolorBand()
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = GovColors.White,
                            titleContentColor = GovColors.DeepBlue,
                            actionIconContentColor = GovColors.DeepBlue,
                            navigationIconContentColor = GovColors.DeepBlue
                        ),
                        navigationIcon = {
                            IconButton(onClick = { sc.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = GovColors.DeepBlue,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("भू-सीमा | BHU-SEEMA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Digital Cadastre & Land Records Portal", style = MaterialTheme.typography.labelSmall, color = GovColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        },
                        actions = {
                            val config = LocalConfiguration.current
                            if (config.screenWidthDp >= 600) {
                                TextButton(onClick = { vm.loadDemoData(); sc.launch { sb.showSnackbar("Sample cadastral parcel loaded (Khasra 142/1)") } }) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp), tint = GovColors.Saffron)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Demo Data", color = GovColors.DeepBlue, fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = { vm.sync(); sc.launch { sb.showSnackbar("Central Cadastre sync queued (HTTPS AES-256)") } }) {
                                    Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp), tint = GovColors.Green)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sync Cadastre", color = GovColors.DeepBlue, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            IconButton(onClick = { nc.navigate("settings") }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    )
                    HorizontalDivider(color = GovColors.BorderSubtle, thickness = 1.dp)
                }
            },
            snackbarHost = { SnackbarHost(sb) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { add = true },
                    containerColor = GovColors.DeepBlue,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                    shape = RoundedCornerShape(8.dp),
                    icon = { Icon(Icons.Default.Add, "New parcel") },
                    text = { Text("New Survey Parcel", fontWeight = FontWeight.SemiBold) }
                )
            }
        ) { pad ->
            BoxWithConstraints(
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .background(GovColors.BackgroundLight)
            ) {
                val numCols = if (maxWidth >= 960.dp) 3 else if (maxWidth >= 640.dp) 2 else 1

                when {
                    list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = GovColors.DeepBlue)
                    list!!.isEmpty() -> {
                        Column(
                            Modifier
                                .align(Alignment.Center)
                                .widthIn(max = 480.dp)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = GovColors.PrimaryContainer,
                                modifier = Modifier.size(76.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(38.dp), tint = GovColors.DeepBlue)
                                }
                            }
                            Text("No Cadastral Records Found", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                            Text(
                                "No land parcel surveys have been registered in this local session. " +
                                "Initiate a new cadastral demarcation or load the official demonstration record to inspect the Panchnama & RTK workflow.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GovColors.TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { add = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = GovColors.DeepBlue),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("New Survey")
                                }
                                OutlinedButton(
                                    onClick = { vm.loadDemoData() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GovColors.DeepBlue),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.DeepBlue),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = GovColors.Saffron)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Load Sample (142/1)")
                                }
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(numCols),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = GovColors.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.BorderSubtle),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = CircleShape, color = GovColors.PrimaryContainer, modifier = Modifier.size(32.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GovColors.DeepBlue, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text("Official Cadastral Registry (भू-अभिलेख रजिस्टर)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                                            Text("Select a surveyed khasra parcel to inspect boundary vectors, witness signatures, and legal area audit.", style = MaterialTheme.typography.bodySmall, color = GovColors.TextSecondary)
                                        }
                                    }
                                }
                            }

                            items(list!!, key = { it.id }) { p ->
                                Card(
                                    onClick = { nc.navigate("survey/${p.id}") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = GovColors.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.Border)
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // Header Row: Survey number & official sync stamp
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = GovColors.DeepBlue,
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Text(
                                                    "KHASRA",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Text("${p.surveyNo}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                                            Spacer(Modifier.weight(1f))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (p.synced) GovColors.GreenLight else GovColors.SaffronLight,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (p.synced) GovColors.Green.copy(alpha = 0.5f) else GovColors.Saffron.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    if (p.synced) "✓ VERIFIED SYNC" else "⏳ LOCAL DRAFT",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (p.synced) GovColors.GreenDark else GovColors.SaffronDark
                                                )
                                            }
                                        }

                                        HorizontalDivider(color = GovColors.BorderSubtle)

                                        // Cadastral Data Rows
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column {
                                                Text("VILLAGE / PANCHAYAT", style = MaterialTheme.typography.labelSmall, color = GovColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                                                Text(p.village, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = GovColors.TextPrimary)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("REGISTERED RoR AREA", style = MaterialTheme.typography.labelSmall, color = GovColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                                                Text("${"%.0f".format(p.rorAreaSqm)} sq m", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                                            }
                                        }

                                        HorizontalDivider(color = GovColors.BorderSubtle)

                                        // Card Footer Actions
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Tap to open demarcation map", style = MaterialTheme.typography.labelSmall, color = GovColors.TextSecondary)
                                            Spacer(Modifier.weight(1f))
                                            TextButton(
                                                onClick = { deleteTarget = p },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Delete", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (add) {
        AddParcel({ add = false }) { s, v, a -> vm.addParcel(s, v, a); add = false }
    }

    if (showSopDialog) {
        CadastralSopDialog { showSopDialog = false }
    }

    if (showBluetoothPicker) {
        val devs = remember { vm.rover.paired() }
        AlertDialog(
            onDismissRequest = { showBluetoothPicker = false },
            modifier = Modifier.widthIn(max = 480.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bluetooth, null, tint = GovColors.DeepBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Paired RTK Rover Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                if (devs.isEmpty()) {
                    Text("No paired Bluetooth SPP devices detected. Pair the ESP32 + ZED-F9P rover in Android Bluetooth settings first.", style = MaterialTheme.typography.bodyMedium, color = GovColors.TextSecondary)
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        devs.forEach { d ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GovColors.BackgroundLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { showBluetoothPicker = false; vm.connect(d) }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Sensors, null, tint = GovColors.DeepBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Text(try { d.name ?: d.address } catch (e: SecurityException) { d.address }, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBluetoothPicker = false }) { Text("Close") } }
        )
    }

    deleteTarget?.let { parcel ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            modifier = Modifier.widthIn(max = 480.dp),
            title = { Text("Delete Cadastral Parcel?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("Are you sure you want to delete Khasra No. ${parcel.surveyNo} in ${parcel.village}? All legal corner coordinates and witness Panchnama records will be permanently removed.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { vm.delete(parcel.id); deleteTarget = null }) { Text("Confirm Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

// ==========================================
// 2. ADD PARCEL DIALOG (OFFICIAL REVENUE FORM)
// ==========================================
@Composable fun AddParcel(close: () -> Unit, save: (String, String, Double) -> Unit) {
    var s by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    val area = a.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = close,
        modifier = Modifier.widthIn(max = 480.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = GovColors.DeepBlue, modifier = Modifier.size(28.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text("New Cadastral Survey Entry", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = GovColors.DeepBlue)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Register land parcel details as recorded in official Record of Rights (RoR / खतौनी / पट्टा):", style = MaterialTheme.typography.bodySmall, color = GovColors.TextSecondary)
                OutlinedTextField(
                    s, { s = it },
                    label = { Text("Khasra / Survey Number (खसरा संख्या)") },
                    placeholder = { Text("e.g. 142/1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    v, { v = it },
                    label = { Text("Village / Gram Panchayat (ग्राम / पंचायत)") },
                    placeholder = { Text("e.g. Rampur (रामपुर)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    a, { a = it },
                    label = { Text("RoR Registered Area (sq m) (रकबा)") },
                    placeholder = { Text("e.g. 2450") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = a.isNotEmpty() && (area == null || area <= 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = s.isNotBlank() && area != null && area > 0,
                onClick = { save(s.trim(), v.trim(), area!!) },
                colors = ButtonDefaults.buttonColors(containerColor = GovColors.DeepBlue),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Register Survey")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

// ==========================================
// 3. SURVEY & MAP SCREEN (RESPONSIVE WORKSTATION)
// ==========================================
@Composable fun SurveyScreen(vm: VM, nc: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val parcel by vm.parcel(id).collectAsState(null)
    val corners by vm.corners(id).collectAsState(emptyList())
    val fix by vm.rover.fix.collectAsState()
    val status by vm.rover.status.collectAsState()
    val isSimulating by vm.rover.isSimulating.collectAsState()

    var pick by remember { mutableStateOf(false) }
    var owner by rememberSaveable { mutableStateOf("") }
    var neigh by rememberSaveable { mutableStateOf("") }
    var gcp by rememberSaveable { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }

    val p = parcel
    if (p == null) { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = GovColors.DeepBlue) }; return }

    val area = Geo.area(corners)
    val ok = gateOk(fix) && owner.isNotBlank() && neigh.isNotBlank()

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            Column {
                GovTricolorBand()
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GovColors.White,
                        titleContentColor = GovColors.DeepBlue,
                        navigationIconContentColor = GovColors.DeepBlue,
                        actionIconContentColor = GovColors.DeepBlue
                    ),
                    title = {
                        Column {
                            Text("Cadastre Survey: Khasra ${p.surveyNo}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                            Text("${p.village} • RoR: ${"%.0f".format(p.rorAreaSqm)} sq m", style = MaterialTheme.typography.labelSmall, color = GovColors.TextSecondary)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { nc.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { nc.navigate("stakeout/$id") },
                            enabled = corners.isNotEmpty(),
                            colors = ButtonDefaults.textButtonColors(contentColor = GovColors.DeepBlue)
                        ) {
                            Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Stakeout HUD", fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
                HorizontalDivider(color = GovColors.BorderSubtle, thickness = 1.dp)
            }
        }
    ) { pad ->
        BoxWithConstraints(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(GovColors.BackgroundLight)
        ) {
            val isWide = maxWidth >= 680.dp || (isLandscape && maxWidth >= 480.dp)

            if (isWide) {
                // Adaptive Two-Pane Layout for Landscape Phones, Tablets, Foldables & Desktops
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Pane: Map and Quick Navigation/Action
                    Column(
                        Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GovColors.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = GovColors.DeepBlue)
                                Spacer(Modifier.width(8.dp))
                                Text("Official 2D Cadastral Polygon Map (भू-नक्शा)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                            }
                        }

                        ParcelCanvas(
                            corners = corners,
                            roverFix = fix,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        SurveyActionBar(
                            corners = corners,
                            onExport = {
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Export.json(p, corners)), "Export Cadastral GeoJSON"))
                            },
                            onNavigateStakeout = { nc.navigate("stakeout/$id") }
                        )
                    }

                    // Right Pane: Telemetry, Demarcation Form, Corners, Audit
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        RoverStatusCard(
                            status = status,
                            isSimulating = isSimulating,
                            fix = fix,
                            onToggleSimulation = { vm.toggleSimulation() },
                            onStepDemoCorner = {
                                val nextSeq = vm.stepDemoCorner()
                                msg = "Virtual rover moved to Peg #$nextSeq"
                            },
                            onPickHardware = { pick = true }
                        )

                        WitnessCaptureCard(
                            owner = owner,
                            onOwnerChange = { owner = it },
                            neigh = neigh,
                            onNeighChange = { neigh = it },
                            gcp = gcp,
                            onGcpChange = { gcp = it },
                            onFillDemo = {
                                owner = "Ramesh Kumar (मालिक)"
                                neigh = "Suresh Patel (पड़ोसी)"
                            },
                            canCapture = ok,
                            nextCornerSeq = corners.size + 1,
                            onCapture = {
                                vm.capture(id, corners.size + 1, fix!!, gcp, owner.trim(), neigh.trim())
                                msg = "✓ Corner #${corners.size + 1} captured with dual witness Panchnama"
                                if (isSimulating) vm.stepDemoCorner()
                            },
                            msg = msg
                        )

                        SurveyedCornersList(
                            corners = corners,
                            onDeleteCorner = { vm.removeCorner(it) }
                        )

                        AreaAuditCard(
                            corners = corners,
                            measuredArea = area,
                            rorAreaSqm = p.rorAreaSqm
                        )
                    }
                }
            } else {
                // Compact Portrait Stacked Layout
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GovColors.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = GovColors.DeepBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("Official 2D Cadastral Polygon Map (भू-नक्शा)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                        }
                    }

                    ParcelCanvas(
                        corners = corners,
                        roverFix = fix,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 260.dp, max = 340.dp)
                            .aspectRatio(1.25f, matchHeightConstraintsFirst = false)
                    )

                    RoverStatusCard(
                        status = status,
                        isSimulating = isSimulating,
                        fix = fix,
                        onToggleSimulation = { vm.toggleSimulation() },
                        onStepDemoCorner = {
                            val nextSeq = vm.stepDemoCorner()
                            msg = "Virtual rover moved to Peg #$nextSeq"
                        },
                        onPickHardware = { pick = true }
                    )

                    WitnessCaptureCard(
                        owner = owner,
                        onOwnerChange = { owner = it },
                        neigh = neigh,
                        onNeighChange = { neigh = it },
                        gcp = gcp,
                        onGcpChange = { gcp = it },
                        onFillDemo = {
                            owner = "Ramesh Kumar (मालिक)"
                            neigh = "Suresh Patel (पड़ोसी)"
                        },
                        canCapture = ok,
                        nextCornerSeq = corners.size + 1,
                        onCapture = {
                            vm.capture(id, corners.size + 1, fix!!, gcp, owner.trim(), neigh.trim())
                            msg = "✓ Corner #${corners.size + 1} captured with dual witness Panchnama"
                            if (isSimulating) vm.stepDemoCorner()
                        },
                        msg = msg
                    )

                    SurveyedCornersList(
                        corners = corners,
                        onDeleteCorner = { vm.removeCorner(it) }
                    )

                    AreaAuditCard(
                        corners = corners,
                        measuredArea = area,
                        rorAreaSqm = p.rorAreaSqm
                    )

                    SurveyActionBar(
                        corners = corners,
                        onExport = {
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Export.json(p, corners)), "Export Cadastral GeoJSON"))
                        },
                        onNavigateStakeout = { nc.navigate("stakeout/$id") }
                    )
                }
            }
        }
    }

    if (pick) {
        val devs = remember { vm.rover.paired() }
        AlertDialog(
            onDismissRequest = { pick = false },
            modifier = Modifier.widthIn(max = 480.dp),
            title = { Text("Paired Bluetooth Rovers", fontWeight = FontWeight.Bold, color = GovColors.DeepBlue) },
            text = {
                if (devs.isEmpty()) {
                    Text("No paired Bluetooth SPP devices found. Pair the ESP32 rover in phone Bluetooth settings first.", color = GovColors.TextSecondary)
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        devs.forEach { d ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GovColors.BackgroundLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.BorderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { pick = false; vm.connect(d) }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bluetooth, null, tint = GovColors.DeepBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Text(try { d.name ?: d.address } catch (e: SecurityException) { d.address }, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text("Close") } }
        )
    }
}

// ------------------------------------------
// REUSABLE SURVEY SCREEN SUB-COMPONENTS
// ------------------------------------------
@Composable
private fun RoverStatusCard(
    status: String,
    isSimulating: Boolean,
    fix: Fix?,
    onToggleSimulation: () -> Unit,
    onStepDemoCorner: () -> Unit,
    onPickHardware: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = GovColors.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (gateOk(fix)) GovColors.Green else if (fix != null) GovColors.Saffron else Color(0xFF9E9E9E),
                    modifier = Modifier.size(10.dp)
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(status, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                Spacer(Modifier.weight(1f))
                if (isSimulating) {
                    Surface(shape = RoundedCornerShape(4.dp), color = GovColors.SaffronLight, border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.Saffron.copy(alpha = 0.5f))) {
                        Text("VIRTUAL DEMO ROVER", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = GovColors.SaffronDark, fontWeight = FontWeight.Bold)
                    }
                }
            }

            fix?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(qualityName(it.quality), fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = GovColors.Green) }
                    )
                    AssistChip(onClick = {}, label = { Text("HDOP ${"%.1f".format(it.hdop)}") })
                    AssistChip(onClick = {}, label = { Text("${it.sats} Sats") })
                }
            }

            Text(gateMsg(fix), style = MaterialTheme.typography.bodySmall, color = if (gateOk(fix)) GovColors.GreenDark else GovColors.SaffronDark, fontWeight = FontWeight.Medium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onToggleSimulation,
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSimulating) MaterialTheme.colorScheme.error else GovColors.DeepBlue),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (isSimulating) Icons.Default.Close else Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isSimulating) "Stop Demo" else "Simulate Rover")
                }

                if (isSimulating) {
                    OutlinedButton(
                        onClick = onStepDemoCorner,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.DeepBlue)
                    ) {
                        Text("Next Peg", color = GovColors.DeepBlue, fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = onPickHardware,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.Border)
                ) {
                    Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp), tint = GovColors.DeepBlue)
                    Spacer(Modifier.width(4.dp))
                    Text("Hardware", color = GovColors.DeepBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun WitnessCaptureCard(
    owner: String,
    onOwnerChange: (String) -> Unit,
    neigh: String,
    onNeighChange: (String) -> Unit,
    gcp: Boolean,
    onGcpChange: (Boolean) -> Unit,
    onFillDemo: () -> Unit,
    canCapture: Boolean,
    nextCornerSeq: Int,
    onCapture: () -> Unit,
    msg: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = GovColors.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(4.dp), color = GovColors.PrimaryContainer, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.BorderColor, null, tint = GovColors.DeepBlue, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("Panchnama Demarcation (पंचनामा गवाही)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFillDemo) {
                    Text("Auto-Fill Demo", style = MaterialTheme.typography.labelSmall, color = GovColors.Saffron, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                owner, onOwnerChange,
                label = { Text("Land Owner Name (भूमि स्वामी / खातेदार)") },
                placeholder = { Text("e.g. Ramesh Kumar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                neigh, onNeighChange,
                label = { Text("Adjacent Neighbour Witness (पड़ोसी काश्तकार गवाह)") },
                placeholder = { Text("e.g. Suresh Patel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    gcp, onGcpChange,
                    colors = CheckboxDefaults.colors(checkedColor = GovColors.Saffron)
                )
                Text("Tag as Ground Control Point (GCP / भू-नियंत्रण बिंदु)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }

            Button(
                enabled = canCapture,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GovColors.DeepBlue),
                onClick = onCapture
            ) {
                Icon(Icons.Default.Place, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Capture & Record Peg #$nextCornerSeq", fontWeight = FontWeight.Bold)
            }

            if (msg.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = GovColors.GreenLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.Green.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(msg, modifier = Modifier.padding(8.dp), color = GovColors.GreenDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SurveyedCornersList(
    corners: List<Corner>,
    onDeleteCorner: (Corner) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Surveyed Corner Pegs (सीमा स्तंभ)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
            Spacer(Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(4.dp), color = GovColors.PrimaryContainer) {
                Text("${corners.size} PEGS", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
            }
        }

        if (corners.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = GovColors.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.BorderSubtle),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("No corner pegs recorded yet. Activate simulator or capture pegs with rover.", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = GovColors.TextSecondary)
            }
        }

        corners.forEach { c ->
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = GovColors.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.BorderSubtle)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = if (c.isGcp) GovColors.Saffron else GovColors.DeepBlue, modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("#${c.seq}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${"%.7f".format(c.lat)}, ${"%.7f".format(c.lon)}${if (c.isGcp) " (GCP)" else ""}", fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                        Text("Witnesses: ${c.ownerWitness} & ${c.neighbourWitness}", style = MaterialTheme.typography.bodySmall, color = GovColors.TextSecondary)
                    }
                    IconButton(onClick = { onDeleteCorner(c) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AreaAuditCard(
    corners: List<Corner>,
    measuredArea: Double,
    rorAreaSqm: Double,
    modifier: Modifier = Modifier
) {
    if (corners.size < 3) return
    val diff = (measuredArea - rorAreaSqm) / rorAreaSqm * 100
    val isDiscrepancy = kotlin.math.abs(diff) > 5.0

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDiscrepancy) Color(0xFFFFF5F5) else GovColors.GreenLight),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDiscrepancy) Color(0xFFEF5350) else GovColors.Green.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isDiscrepancy) Icons.Default.Warning else Icons.Default.CheckCircle, null, tint = if (isDiscrepancy) Color(0xFFC62828) else GovColors.GreenDark)
                Spacer(Modifier.width(8.dp))
                Text("Official Cadastral Area Audit (क्षेत्रफल मिलान)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (isDiscrepancy) Color(0xFFC62828) else GovColors.GreenDark)
            }
            HorizontalDivider(color = (if (isDiscrepancy) Color(0xFFEF5350) else GovColors.Green).copy(alpha = 0.3f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Field Measured Area:")
                Text("${"%.1f".format(measuredArea)} sq m", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Official RoR Registered Area:")
                Text("${"%.0f".format(rorAreaSqm)} sq m", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Legal Discrepancy Variance:")
                Text("${"%.2f".format(diff)}%", fontWeight = FontWeight.Bold, color = if (isDiscrepancy) Color(0xFFC62828) else GovColors.GreenDark)
            }
            Text(
                if (isDiscrepancy) "⚠ Legal Notice: Area deviates from official Record of Rights by > 5%. Mandatory revenue officer inspection & re-demarcation required."
                else "✓ Verified Compliant: Measured parcel boundary complies with official Record of Rights within 5% tolerance.",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDiscrepancy) Color(0xFFB71C1C) else GovColors.GreenDark,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SurveyActionBar(
    corners: List<Corner>,
    onExport: () -> Unit,
    onNavigateStakeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        Button(
            enabled = corners.size >= 3,
            onClick = onExport,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GovColors.DeepBlue),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Export GeoJSON")
        }
        OutlinedButton(
            enabled = corners.isNotEmpty(),
            onClick = onNavigateStakeout,
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.DeepBlue),
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Navigation, null, modifier = Modifier.size(16.dp), tint = GovColors.DeepBlue)
            Spacer(Modifier.width(4.dp))
            Text("Stakeout HUD", color = GovColors.DeepBlue, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ==========================================
// 4. STAKEOUT RADAR HUD SCREEN (RESPONSIVE)
// ==========================================
@Composable fun StakeoutScreen(vm: VM, nc: NavHostController, id: Long) {
    val corners by vm.corners(id).collectAsState(emptyList())
    val fix by vm.rover.fix.collectAsState()
    var sel by remember { mutableStateOf<Corner?>(null) }
    val isSimulating by vm.rover.isSimulating.collectAsState()

    // Default select first corner
    LaunchedEffect(corners) {
        if (sel == null && corners.isNotEmpty()) sel = corners.first()
    }

    val t = sel
    val f = fix
    val dist = if (t != null && f != null) Geo.dist(f.lat, f.lon, t.lat, t.lon) else 999.0
    val bearing = if (t != null && f != null) Geo.bearing(f.lat, f.lon, t.lat, t.lon) else 0.0

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        topBar = {
            Column {
                GovTricolorBand()
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GovColors.White,
                        titleContentColor = GovColors.DeepBlue,
                        navigationIconContentColor = GovColors.DeepBlue
                    ),
                    title = {
                        Text("Boundary Peg Relocation (निशानदेही)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                    },
                    navigationIcon = {
                        IconButton(onClick = { nc.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                HorizontalDivider(color = GovColors.BorderSubtle, thickness = 1.dp)
            }
        }
    ) { pad ->
        BoxWithConstraints(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .background(GovColors.BackgroundLight)
        ) {
            val isWide = maxWidth >= 680.dp || (isLandscape && maxWidth >= 480.dp)

            if (isWide) {
                // Adaptive Two-Pane Layout for Landscape Phones, Tablets, Foldables & Desktops
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Pane: Radar HUD
                    Box(
                        Modifier
                            .weight(1.1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        StakeoutRadar(
                            distanceMeters = dist,
                            bearingDegrees = bearing,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }

                    // Right Pane: Target Selection & Telemetry
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Select Boundary Peg to Relocate:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = GovColors.DeepBlue)

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(corners) { c ->
                                FilterChip(
                                    selected = sel?.id == c.id,
                                    onClick = { sel = c },
                                    label = { Text("Peg #${c.seq}${if (c.isGcp) " (GCP)" else ""}", fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = if (sel?.id == c.id) {
                                        { Icon(Icons.Default.Place, null, modifier = Modifier.size(16.dp), tint = GovColors.DeepBlue) }
                                    } else null
                                )
                            }
                        }

                        TargetTelemetryCard(
                            fix = f,
                            dist = dist,
                            bearing = bearing,
                            isSimulating = isSimulating,
                            targetCorner = t,
                            onStartSimulation = { vm.toggleSimulation() },
                            onJumpToTarget = { t?.let { vm.rover.setSimulatedLocation(it.lat, it.lon) } }
                        )
                    }
                }
            } else {
                // Compact Portrait Stacked Layout
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Select Boundary Peg to Relocate:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = GovColors.DeepBlue)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(corners) { c ->
                            FilterChip(
                                selected = sel?.id == c.id,
                                onClick = { sel = c },
                                label = { Text("Peg #${c.seq}${if (c.isGcp) " (GCP)" else ""}", fontWeight = FontWeight.SemiBold) },
                                leadingIcon = if (sel?.id == c.id) {
                                    { Icon(Icons.Default.Place, null, modifier = Modifier.size(16.dp), tint = GovColors.DeepBlue) }
                                } else null
                            )
                        }
                    }

                    StakeoutRadar(
                        distanceMeters = dist,
                        bearingDegrees = bearing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 320.dp)
                            .aspectRatio(1f)
                    )

                    TargetTelemetryCard(
                        fix = f,
                        dist = dist,
                        bearing = bearing,
                        isSimulating = isSimulating,
                        targetCorner = t,
                        onStartSimulation = { vm.toggleSimulation() },
                        onJumpToTarget = { t?.let { vm.rover.setSimulatedLocation(it.lat, it.lon) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetTelemetryCard(
    fix: Fix?,
    dist: Double,
    bearing: Double,
    isSimulating: Boolean,
    targetCorner: Corner?,
    onStartSimulation: () -> Unit,
    onJumpToTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dist <= 0.5) GovColors.GreenLight else GovColors.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (dist <= 0.5) GovColors.Green else GovColors.Border)
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fix == null) {
                Text("Waiting for RTK rover GNSS fix...", style = MaterialTheme.typography.bodyMedium, color = GovColors.TextSecondary)
                if (!isSimulating) {
                    Button(
                        onClick = onStartSimulation,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GovColors.DeepBlue)
                    ) {
                        Text("Start Virtual Rover")
                    }
                }
            } else {
                Text(
                    "${"%.2f".format(dist)} m",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (dist <= 0.5) GovColors.GreenDark else GovColors.DeepBlue
                )
                Text("Bearing to Boundary Stone: ${"%.0f".format(bearing)}°", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = GovColors.TextPrimary)

                if (dist <= 0.5) {
                    Surface(shape = RoundedCornerShape(6.dp), color = GovColors.Green) {
                        Text("🎯 TARGET LOCKED: Place Official Boundary Peg Here!", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Walk forward aligning with the guidance needle", style = MaterialTheme.typography.bodySmall, color = GovColors.TextSecondary)
                }

                if (isSimulating && targetCorner != null) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onJumpToTarget,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.DeepBlue)
                    ) {
                        Text("Demo: Jump to Target Peg", color = GovColors.DeepBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 5. SETTINGS SCREEN (OFFICIAL GOV CREDENTIALS)
// ==========================================
@Composable fun SettingsScreen(nc: NavHostController) {
    val c = LocalContext.current
    val pr = remember { Secure.prefs(c) }
    var url by remember { mutableStateOf(pr.getString("url", "") ?: "") }
    var tok by remember { mutableStateOf(pr.getString("token", "") ?: "") }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                GovTricolorBand()
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GovColors.White,
                        titleContentColor = GovColors.DeepBlue,
                        navigationIconContentColor = GovColors.DeepBlue
                    ),
                    title = { Text("Settings & Security Credentials", fontWeight = FontWeight.Bold, color = GovColors.DeepBlue) },
                    navigationIcon = {
                        IconButton(onClick = { nc.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                HorizontalDivider(color = GovColors.BorderSubtle, thickness = 1.dp)
            }
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .background(GovColors.BackgroundLight),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = GovColors.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.Border)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = GovColors.DeepBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("Central Cadastre Server Credentials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                        }
                        Text("Configure the secure government endpoint for uploading legally attested GeoJSON cadastral boundaries.", style = MaterialTheme.typography.bodySmall, color = GovColors.TextSecondary)

                        OutlinedTextField(
                            url, { url = it; saved = false },
                            label = { Text("Server URL (HTTPS Only)") },
                            placeholder = { Text("https://cadastre.gov.in/api") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            isError = url.isNotEmpty() && !url.startsWith("https://")
                        )
                        OutlinedTextField(
                            tok, { tok = it; saved = false },
                            label = { Text("Surveyor Official Bearer Token") },
                            placeholder = { Text("auth_token_xxx") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            enabled = url.isEmpty() || url.startsWith("https://"),
                            onClick = {
                                pr.edit().putString("url", url.trim()).putString("token", tok.trim()).apply()
                                saved = true
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GovColors.DeepBlue)
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Official Credentials")
                        }
                        if (saved) {
                            Surface(shape = RoundedCornerShape(4.dp), color = GovColors.GreenLight) {
                                Text("✓ Stored securely with hardware-backed AES-256 GCM encryption.", modifier = Modifier.padding(8.dp), color = GovColors.GreenDark, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = GovColors.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GovColors.Border)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = GovColors.DeepBlue)
                            Spacer(Modifier.width(8.dp))
                            Text("About BhuSeema / SeemaKit (भू-सीमा)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                        }
                        Surface(shape = RoundedCornerShape(4.dp), color = GovColors.PrimaryContainer) {
                            Text("Digital India Cadastral Initiative", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = GovColors.DeepBlue, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "BhuSeema is a decentralized, centimeter-accurate RTK land boundary surveying system. " +
                            "It empowers revenue patwaris, panchayats, and farmers to demarcate land parcels at 1/50th the cost of commercial Total Stations, " +
                            "preventing boundary disputes through dual-witness legal Panchnama records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GovColors.TextPrimary
                        )
                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = GovColors.BorderSubtle)
                        Text("Hardware Specifications:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = GovColors.DeepBlue)
                        Text("• ESP32 MCU + u-blox ZED-F9P RTK Multi-Band GNSS Receiver\n• Bluetooth Classic SPP (NMEA-0183 GGA stream @ 5Hz)\n• Position Accuracy: ±1 to 2 cm (RTK Fixed)", style = MaterialTheme.typography.bodySmall, color = GovColors.TextSecondary)
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. CADASTRAL SOPs & GUIDELINES MODAL
// ==========================================
@Composable
private fun CadastralSopDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 520.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Policy, contentDescription = null, tint = GovColors.DeepBlue)
                Spacer(Modifier.width(8.dp))
                Text("Cadastral Survey Standards (SOP)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = GovColors.DeepBlue)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Official Revenue Survey Guidelines:", fontWeight = FontWeight.Bold, color = GovColors.DeepBlue)
                Text("1. Centimeter Accuracy: Only fixes with RTK Fixed status (Quality 4, HDOP ≤ 2.0) qualify for legal demarcation.", style = MaterialTheme.typography.bodySmall)
                Text("2. Mandatory Panchnama: Both the registered land owner and adjacent boundary neighbor must witness each corner peg capture.", style = MaterialTheme.typography.bodySmall)
                Text("3. Area Tolerance: The surveyed polygon area must match the Record of Rights (RoR) within ±5.0%. Discrepancies >5% require mandatory inspection by Revenue Officers.", style = MaterialTheme.typography.bodySmall)
                Text("4. Ground Control Points: At least one corner per survey cluster should be designated as a Ground Control Point (GCP).", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GovColors.DeepBlue)
            ) {
                Text("Acknowledged")
            }
        }
    )
}
