package com.sminkeliste.app.watch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sminkeliste.app.MainActivity
import com.sminkeliste.app.R
import com.sminkeliste.app.data.Deals
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.util.tr

/** Shows your list when you walk into a store: a pop-up notification, and the in-store screen in the app. */
object StoreAlert {
    const val CH_STORE = "in_store"
    const val CH_WATCH = "watching"
    const val EXTRA_STORE = "store_name"
    private const val ID_ALERT = 2001

    /** The store you're in right now (shown as the in-store screen while the app is open). */
    var inStore by mutableStateOf<String?>(null)
    /** Set when the in-store screen should be on top. */
    var splash by mutableStateOf<String?>(null)

    fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STORE, tr("In a store", "I butikken"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = tr("Shows your shopping list when you walk into a store", "Viser sminkelisten når du går inn i en butikk")
        })
        nm.createNotificationChannel(NotificationChannel(CH_WATCH, tr("Store reminder running", "Butikkvarsel er på"), NotificationManager.IMPORTANCE_MIN).apply {
            description = tr("The quiet notification Android requires while the app watches for stores", "Det stille varselet Android krever mens appen ser etter butikker")
            setShowBadge(false)
        })
    }

    fun openIntent(ctx: Context, store: String?): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (store != null) i.putExtra(EXTRA_STORE, store)
        return PendingIntent.getActivity(ctx, if (store != null) 1 else 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** You're in [store]: pop up the list (if there's anything on it). */
    fun show(ctx: Context, store: String) {
        ListRepo.init(ctx)
        inStore = store
        splash = store // if the app is open, the in-store screen appears straight away
        val open = ListRepo.open
        if (open.isEmpty()) return
        channels(ctx)
        val deals = Deals.atStore(store).map { it.first }.distinct()
        val title = tr("You're at $store 💄", "Du er på $store 💄")
        val summary = tr("${open.size} things on your list", "${open.size} ting på listen") +
            if (deals.isNotEmpty()) tr(" · ${deals.size} on sale here", " · ${deals.size} på tilbud her") else ""
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText(summary)
        open.take(7).forEach { item -> style.addLine((if (item.name in deals) "🏷️ " else "• ") + item.name) }
        if (open.size > 7) style.addLine(tr("…and ${open.size - 7} more", "…og ${open.size - 7} til"))
        val n = NotificationCompat.Builder(ctx, CH_STORE)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFD6336C.toInt())
            .setContentTitle(title)
            .setContentText(open.joinToString(", ") { it.name })
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, store))
            .addAction(0, tr("Open list", "Åpne listen"), openIntent(ctx, store))
            .build()
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(ctx).notify(ID_ALERT, n)
        }
    }

    fun left(ctx: Context) {
        inStore = null
        splash = null
        NotificationManagerCompat.from(ctx).cancel(ID_ALERT)
    }
}
