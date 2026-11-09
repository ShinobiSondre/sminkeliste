package com.sminkeliste.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sminkeliste.app.MainActivity
import com.sminkeliste.app.R
import com.sminkeliste.app.util.Lang
import com.sminkeliste.app.util.tr
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Something you follow: a product ("Fenty Pro Filt'r"), a brand ("Maybelline") or a kind ("mascara"). */
data class Follow(val id: String, val name: String)

/**
 * "Tell me when it's on sale": what you follow is checked against this week's flyers near you
 * every 6 hours in the background, and you get one notification per new deal.
 */
object Alerts {
    val follows = mutableStateListOf<Follow>()
    private var loaded = false
    private const val CHANNEL = "deal_alerts"
    const val EXTRA_TAB = "open_tab"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    @Synchronized
    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val a = JSONArray(prefs(ctx).getString("follows", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                follows += Follow(o.getString("id"), o.getString("name"))
            }
        }
    }

    private fun save(ctx: Context) {
        val a = JSONArray()
        follows.forEach { f -> a.put(JSONObject().put("id", f.id).put("name", f.name)) }
        prefs(ctx).edit().putString("follows", a.toString()).apply()
    }

    fun isFollowed(name: String) = follows.any { it.name.equals(name.trim(), true) }

    fun follow(ctx: Context, name: String) {
        init(ctx)
        if (name.isBlank() || isFollowed(name)) return
        follows += Follow(UUID.randomUUID().toString(), name.trim().replaceFirstChar { it.uppercase() })
        save(ctx)
        schedule(ctx, checkNow = true)
    }

    fun unfollow(ctx: Context, name: String) {
        init(ctx)
        follows.removeAll { it.name.equals(name.trim(), true) }
        save(ctx)
        if (follows.isEmpty()) WorkManager.getInstance(ctx).cancelUniqueWork("deal-alerts")
    }

    fun toggle(ctx: Context, name: String) = if (isFollowed(name)) unfollow(ctx, name) else follow(ctx, name)

    fun schedule(ctx: Context, checkNow: Boolean = false) {
        init(ctx)
        if (follows.isEmpty()) return
        val wm = WorkManager.getInstance(ctx)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork("deal-alerts", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DealAlertWorker>(6, TimeUnit.HOURS).setConstraints(net).build())
        if (checkNow) wm.enqueueUniqueWork("deal-alerts-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DealAlertWorker>().setConstraints(net).build())
    }

    /** Deals right now for one thing you follow (from the flyers already on the phone). */
    fun current(ctx: Context, name: String): List<Offer> =
        Parse.matchOffers(Parse.searchTerm(name), Flyers.cached(ctx), Deals.nowIso())

    /** Checks everything you follow and notifies about deals not told about before. */
    suspend fun check(ctx: Context) {
        init(ctx)
        if (follows.isEmpty()) return
        val loc = Settings.lastLocation(ctx)
        val flyers = Flyers.near(ctx, loc?.first ?: Deals.FALLBACK_LAT, loc?.second ?: Deals.FALLBACK_LNG, Settings.radiusKm(ctx) * 1000)
        if (flyers.isEmpty()) return
        val now = Deals.nowIso()
        val p = prefs(ctx)
        val told = p.getStringSet("told", emptySet())!!.toMutableSet()
        follows.toList().forEach { f ->
            val fresh = Parse.matchOffers(Parse.searchTerm(f.name), flyers, now).filter { "${f.id}:${it.id}" !in told }
            if (fresh.isNotEmpty()) {
                notify(ctx, f, fresh)
                fresh.forEach { told += "${f.id}:${it.id}" }
            }
        }
        p.edit().putStringSet("told", told.toList().takeLast(600).toSet()).apply()
    }

    private fun price(v: Double) = "kr " + String.format(java.util.Locale("nb", "NO"), "%.2f", v)

    private fun notify(ctx: Context, f: Follow, offers: List<Offer>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CHANNEL, tr("Deals on things you follow", "Tilbud på det du følger"), NotificationManager.IMPORTANCE_DEFAULT))
        val best = offers.minBy { it.price }
        val pct = best.savingsPct?.takeIf { it in 1..95 }?.let { " (−$it %)" }.orEmpty()
        val open = PendingIntent.getActivity(ctx, f.id.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_TAB, 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val style = NotificationCompat.InboxStyle()
        offers.sortedBy { it.price }.take(5).forEach {
            style.addLine("${it.store}: ${it.heading.lowercase().replaceFirstChar { c -> c.uppercase() }} · ${price(it.price)}")
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify).setColor(0xFFD6336C.toInt())
            .setContentTitle(tr("💄 ${f.name} is on sale", "💄 ${f.name} er på tilbud"))
            .setContentText("${price(best.price)} ${tr("at", "hos")} ${best.store}$pct" +
                if (offers.size > 1) tr(" · ${offers.size} deals", " · ${offers.size} tilbud") else "")
            .setStyle(style).setAutoCancel(true).setContentIntent(open).build()
        NotificationManagerCompat.from(ctx).notify(f.id.hashCode(), n)
    }
}

class DealAlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Lang.init(applicationContext)
        return runCatching { Alerts.check(applicationContext) }.fold({ Result.success() }, { Result.retry() })
    }
}
