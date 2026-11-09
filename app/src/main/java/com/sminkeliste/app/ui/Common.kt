package com.sminkeliste.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sminkeliste.app.data.Settings
import com.sminkeliste.app.watch.StoreWatchService
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.util.Locale

/** Store-reminder on/off, kept as Compose state so every screen shows the same thing. */
object WatchState {
    var on by mutableStateOf(false)
    fun refresh(ctx: Context) { on = Settings.watchOn(ctx) && StoreWatchService.hasLocation(ctx) }
}

/** Returns a function that asks for location (and notifications), then turns the store reminder on. */
@Composable
fun rememberEnableWatch(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val loc = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            StoreWatchService.hasLocation(ctx)
        if (loc) {
            Settings.setWatch(ctx, true)
            StoreWatchService.start(ctx)
        }
        WatchState.refresh(ctx)
    }
    return {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        launcher.launch(perms.toTypedArray())
    }
}

/** The phone's position right now (or the last one known), or null without permission. */
@SuppressLint("MissingPermission")
suspend fun currentLocation(ctx: Context): Pair<Double, Double>? {
    if (!StoreWatchService.hasLocation(ctx)) return Settings.lastLocation(ctx)
    val fused = LocationServices.getFusedLocationProviderClient(ctx)
    val loc = runCatching {
        withTimeoutOrNull(8000) {
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
        } ?: fused.lastLocation.await()
    }.getOrNull()
    if (loc != null) {
        Settings.setLastLocation(ctx, loc.latitude, loc.longitude)
        return loc.latitude to loc.longitude
    }
    return Settings.lastLocation(ctx)
}

fun openUrl(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun openAppSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun openBatterySettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { openAppSettings(ctx) }
}

private val nok = NumberFormat.getNumberInstance(Locale("nb", "NO")).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
fun kr(v: Double): String = "kr " + nok.format(v)

/** "2026-11-15T22:59:59+0000" → "15.11" */
fun shortDate(iso: String): String {
    val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(iso) ?: return ""
    return "${m.groupValues[3]}.${m.groupValues[2]}"
}
