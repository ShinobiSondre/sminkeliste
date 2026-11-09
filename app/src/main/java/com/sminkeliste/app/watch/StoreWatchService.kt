package com.sminkeliste.app.watch

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sminkeliste.app.R
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.data.Parse
import com.sminkeliste.app.data.Settings
import com.sminkeliste.app.data.Shop
import com.sminkeliste.app.util.Lang
import com.sminkeliste.app.util.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Watches for "you just walked into a beauty shop or pharmacy".
 *
 * Battery: far from any store it asks for a rough position every 2 minutes (Wi-Fi/cell).
 * Within ~300 m of a store it switches to GPS every 20 s, and back again when you leave.
 * You count as "in" a store when you're at it for two checks at least 30 s apart, so driving
 * past doesn't trigger it. Each store pops up at most once every 2 hours.
 */
class StoreWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val lock = Mutex()
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var near = false
    private var subscribed = false

    private var candidate: Shop? = null
    private var candidateSince = 0L
    private var current: Shop? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc -> scope.launch { onLocation(loc) } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Lang.init(this)
        ListRepo.init(this)
        StoreAlert.channels(this)
        val n = NotificationCompat.Builder(this, StoreAlert.CH_WATCH)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(tr("Store reminder is on", "Butikkvarsel er på"))
            .setContentText(tr("Your makeup list pops up when you walk into a beauty shop", "Sminkelisten dukker opp når du går inn i en sminkebutikk"))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(StoreAlert.openIntent(this, null))
            .build()
        try {
            ServiceCompat.startForeground(this, 1001, n,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } catch (_: Exception) {
            // Android refused (e.g. started from the background without "Allow all the time").
            stopSelf(); return START_NOT_STICKY
        }
        if (!hasLocation(this)) { stopSelf(); return START_NOT_STICKY }
        if (!subscribed) subscribe(false)
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun subscribe(precise: Boolean) {
        near = precise
        subscribed = true
        fused.removeLocationUpdates(callback)
        val req = if (precise) {
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20_000L)
                .setMinUpdateIntervalMillis(10_000L).build()
        } else {
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 120_000L)
                .setMinUpdateIntervalMillis(45_000L).setMinUpdateDistanceMeters(40f).build()
        }
        try { fused.requestLocationUpdates(req, callback, Looper.getMainLooper()) } catch (_: Exception) { stopSelf() }
    }

    private suspend fun onLocation(loc: Location) = lock.withLock {
        Settings.setLastLocation(this, loc.latitude, loc.longitude)
        val shops = Shops.near(this, loc.latitude, loc.longitude)

        // Close to a store? Check precisely; otherwise save battery.
        val wantPrecise = Shops.nearestDistance(loc.latitude, loc.longitude) < 300.0
        if (wantPrecise != near) subscribe(wantPrecise)

        // Left the store we were in?
        current?.let { c ->
            if (Parse.distanceM(loc.latitude, loc.longitude, c.lat, c.lng) > 150.0) {
                current = null
                StoreAlert.left(this)
            }
        }

        val shop = if (loc.hasAccuracy()) Parse.shopAt(shops, loc.latitude, loc.longitude, loc.accuracy) else null
        val now = System.currentTimeMillis()
        if (shop == null) { candidate = null; return@withLock }
        if (shop.id == current?.id) return@withLock
        if (candidate?.id != shop.id) { candidate = shop; candidateSince = now; return@withLock }
        if (now - candidateSince < 30_000L) return@withLock

        // Confirmed: you're in this store.
        current = shop
        candidate = null
        val prefs = getSharedPreferences("alerts", MODE_PRIVATE)
        val last = prefs.getLong(shop.id, 0L)
        if (now - last > 2 * 3600 * 1000L) {
            prefs.edit().putLong(shop.id, now).apply()
            StoreAlert.show(this, shop.name)
        } else {
            StoreAlert.inStore = shop.name
        }
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun hasLocation(ctx: Context): Boolean =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun hasBackground(ctx: Context): Boolean =
            Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context) {
            if (!hasLocation(ctx)) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, StoreWatchService::class.java)) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, StoreWatchService::class.java))
            StoreAlert.left(ctx)
        }
    }
}
