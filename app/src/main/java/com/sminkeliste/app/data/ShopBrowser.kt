package com.sminkeliste.app.data

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.sminkeliste.app.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import java.lang.ref.WeakReference

/**
 * Shops whose search pages only show products once the page has run in a browser (Kicks, Fredrik & Louisa,
 * Parfym.no …). The app opens their ordinary search page in an invisible browser, waits until the products
 * are on the page, and reads them off it (assets/extract.js): name, price, before-price, picture and link.
 */
object ShopBrowser {
    class Shop(val name: String, val url: (String) -> String)

    val SHOPS = listOf(
        Shop("Kicks") { "https://www.kicks.no/sok?search-query=$it" },
        Shop("Fredrik & Louisa") { "https://fredrikoglouisa.no/sokeresultater/?searchterm=$it" },
        Shop("Parfym.no") { "https://www.parfym.no/s?q=$it" },
    )

    private const val POOL = 2
    private var host: WeakReference<Activity>? = null
    private val views = arrayOfNulls<WebView>(POOL)
    private val locks = Array(POOL) { Mutex() }
    private var script: String? = null
    private val cache = HashMap<String, Pair<Long, List<WebPrice>>>()
    private const val MAX_AGE_MS = 6 * 3600 * 1000L

    /** Called by the app's screen; without it (e.g. the background alert check) these shops are skipped. */
    fun attach(a: Activity) {
        if (host?.get() !== a) { views.indices.forEach { i -> views[i]?.destroy(); views[i] = null } }
        host = WeakReference(a)
        if (script == null) script = runCatching { a.assets.open("extract.js").bufferedReader().use { it.readText() } }.getOrNull()
    }

    val available: Boolean get() = host?.get() != null && script != null

    @SuppressLint("SetJavaScriptEnabled")
    private fun view(i: Int): WebView? {
        views[i]?.let { return it }
        val a = host?.get() ?: return null
        val root = a.findViewById<ViewGroup>(android.R.id.content) ?: return null
        // Laid out like a phone screen so the shop shows its normal mobile page, but practically invisible,
        // pushed down to a sliver, and every touch passes through to the app.
        val holder = object : FrameLayout(a) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent?) = false
            override fun onInterceptTouchEvent(ev: android.view.MotionEvent?) = false
        }.apply {
            alpha = 0.01f; isClickable = false; isFocusable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        root.addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        holder.post { holder.translationY = ((holder.parent as? android.view.View)?.height ?: holder.height).toFloat().let { (it - 2f).coerceAtLeast(0f) } }
        return WebView(a).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            webViewClient = WebViewClient()
            isFocusable = false; isFocusableInTouchMode = false
            holder.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }.also { views[i] = it }
    }

    /** Products on one shop's search page for [term] (empty if the shop can't be reached or has none). */
    suspend fun search(shop: Shop, term: String): List<WebPrice> {
        val key = shop.name + "|" + term.lowercase()
        synchronized(cache) { cache[key] }?.takeIf { System.currentTimeMillis() - it.first < MAX_AGE_MS }?.let { return it.second }
        val js = script ?: return emptyList()
        // Wait for a free browser (they're shared by all searches).
        var slot = -1
        while (slot < 0) {
            slot = locks.indexOfFirst { it.tryLock() }
            if (slot < 0) delay(150)
        }
        try {
            val found = withContext(Dispatchers.Main) {
                val w = view(slot) ?: return@withContext emptyList<WebPrice>()
                var best = emptyList<WebPrice>()
                try {
                    withTimeoutOrNull(16_000) {
                        w.loadUrl(shop.url(Http.enc(term)))
                        val t0 = System.currentTimeMillis()
                        var same = 0
                        while (true) {
                            delay(800)
                            val raw = suspendCancellableCoroutine<String?> { c -> w.evaluateJavascript(js) { c.resumeWith(Result.success(it)) } }
                            val text = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: continue
                            val now = Parse.pageProducts(text, shop.name)
                            if (now.size > best.size) { best = now; same = 0 } else if (best.isNotEmpty()) same++
                            // Done when the list stops growing (the page has finished loading its products).
                            if (best.isNotEmpty() && same >= 2 && System.currentTimeMillis() - t0 > 2_500) break
                        }
                    }
                    best // what was on the page when time ran out counts too
                } finally {
                    runCatching { w.stopLoading(); w.loadUrl("about:blank") }
                }
            }
            if (found.isNotEmpty()) synchronized(cache) { cache[key] = System.currentTimeMillis() to found }
            return found
        } finally {
            locks[slot].unlock()
        }
    }
}
