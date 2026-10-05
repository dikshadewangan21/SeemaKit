package com.seemakit.field
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class App : Application() { val db by lazy { Room.databaseBuilder(this, Db::class.java, "seema.db").build() } }

object Secure {
    fun prefs(c: Context) = EncryptedSharedPreferences.create(c, "secure",
        MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
}

class SyncWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        val pr = Secure.prefs(applicationContext)
        val url = pr.getString("url", "")?.trimEnd('/') ?: ""; val tok = pr.getString("token", "") ?: ""
        if (!url.startsWith("https://")) return Result.failure()
        val dao = (applicationContext as App).db.dao()
        for (p in dao.unsynced()) {
            val body = Export.json(p, dao.cornersNow(p.id))
            val ok = try {
                val cn = URL("$url/api/sessions").openConnection() as HttpURLConnection
                cn.requestMethod = "POST"; cn.doOutput = true; cn.connectTimeout = 15000; cn.readTimeout = 15000
                cn.setRequestProperty("Content-Type", "application/json"); cn.setRequestProperty("Authorization", "Bearer $tok")
                cn.outputStream.use { it.write(body.toByteArray()) }
                cn.responseCode in 200..299
            } catch (e: Exception) { false }
            if (ok) dao.mark(p.id, true) else return Result.retry()
        }
        return Result.success()
    }
}

class VM(app: Application) : AndroidViewModel(app) {
    private val dao = (app as App).db.dao()
    val rover = Rover(app)
    val parcels = dao.parcels().map<List<Parcel>, List<Parcel>?> { it }.stateIn(viewModelScope, SharingStarted.Lazily, null)
    fun parcel(id: Long) = dao.parcel(id)
    fun corners(id: Long) = dao.corners(id)
    fun addParcel(s: String, v: String, a: Double) = viewModelScope.launch { dao.addParcel(Parcel(surveyNo = s, village = v, rorAreaSqm = a)) }
    fun delete(id: Long) = viewModelScope.launch { dao.delCorners(id); dao.delParcel(id) }
    fun connect(d: android.bluetooth.BluetoothDevice) { viewModelScope.launch { rover.connect(d) } }
    fun capture(pid: Long, seq: Int, f: Fix, gcp: Boolean, o: String, n: String) = viewModelScope.launch {
        dao.addCorner(Corner(parcelId = pid, seq = seq, lat = f.lat, lon = f.lon, quality = f.quality, hdop = f.hdop, sats = f.sats,
            time = System.currentTimeMillis(), isGcp = gcp, ownerWitness = o, neighbourWitness = n)); dao.mark(pid, false)
    }
    fun removeCorner(c: Corner) = viewModelScope.launch { dao.delCorner(c); dao.mark(c.parcelId, false) }
    suspend fun cornersNow(id: Long) = dao.cornersNow(id)
    fun sync() = WorkManager.getInstance(getApplication()).enqueueUniqueWork("sync", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())

    fun toggleSimulation() {
        if (rover.isSimulating.value) rover.stopSimulation() else rover.startSimulation()
    }

    fun stepDemoCorner() = rover.stepNextDemoCorner()

    fun loadDemoData() = viewModelScope.launch {
        val pid = dao.addParcel(Parcel(surveyNo = "142/1", village = "Rampur", rorAreaSqm = 2450.0))
        val now = System.currentTimeMillis()
        dao.addCorner(Corner(parcelId = pid, seq = 1, lat = 21.251480, lon = 81.629500, quality = 4, hdop = 0.7, sats = 24, time = now, isGcp = true, ownerWitness = "Ramesh Kumar", neighbourWitness = "Suresh Patel"))
        dao.addCorner(Corner(parcelId = pid, seq = 2, lat = 21.251930, lon = 81.629520, quality = 4, hdop = 0.8, sats = 23, time = now + 120000, isGcp = false, ownerWitness = "Ramesh Kumar", neighbourWitness = "Kailash Verma"))
        dao.addCorner(Corner(parcelId = pid, seq = 3, lat = 21.251900, lon = 81.630010, quality = 4, hdop = 0.7, sats = 25, time = now + 240000, isGcp = false, ownerWitness = "Ramesh Kumar", neighbourWitness = "Dinesh Sahu"))
        dao.addCorner(Corner(parcelId = pid, seq = 4, lat = 21.251450, lon = 81.629980, quality = 4, hdop = 0.8, sats = 24, time = now + 360000, isGcp = false, ownerWitness = "Ramesh Kumar", neighbourWitness = "Suresh Patel"))
    }

    override fun onCleared() { rover.close() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (android.os.Build.VERSION.SDK_INT >= 31)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        setContent { SeemaKitTheme { Nav(viewModel()) } }
    }
}
