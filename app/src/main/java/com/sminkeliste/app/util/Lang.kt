package com.sminkeliste.app.util

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** App language: follows the phone (Norwegian or English) until you pick one in Settings. */
object Lang {
    enum class Mode { AUTO, EN, NO }

    /** Compose state, so the whole app switches language the moment you change it. */
    var no by mutableStateOf(false)
        private set
    var mode by mutableStateOf(Mode.AUTO)
        private set

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("lang", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        mode = runCatching { Mode.valueOf(prefs(ctx).getString("mode", "AUTO")!!) }.getOrDefault(Mode.AUTO)
        no = when (mode) {
            Mode.NO -> true
            Mode.EN -> false
            Mode.AUTO -> Locale.getDefault().language in setOf("nb", "no", "nn")
        }
    }

    fun set(ctx: Context, m: Mode) {
        prefs(ctx).edit().putString("mode", m.name).apply()
        init(ctx)
    }
}

/** Picks the English or Norwegian text. */
fun tr(en: String, no: String): String = if (Lang.no) no else en
