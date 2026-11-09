package com.sminkeliste.app.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sminkeliste.app.data.Settings

/** Starts the store reminder again after a restart or an app update (needs "Allow all the time"). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Settings.watchOn(ctx) && StoreWatchService.hasBackground(ctx)) StoreWatchService.start(ctx)
    }
}
