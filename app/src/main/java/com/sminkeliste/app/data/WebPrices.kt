package com.sminkeliste.app.data

import androidx.compose.runtime.mutableStateMapOf
import com.sminkeliste.app.util.Http
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.util.UUID

/**
 * Today's online prices in the official beauty web shops, for comparing with the shop deals:
 * Vita (its search page), Blush (its search page) and Boots (its search service). Searched from the
 * phone, the same way the shop's own site does it, and kept for a few hours.
 */
object WebPrices {
    /** Search word → what was found (empty list = searched, nothing found). */
    val found = mutableStateMapOf<String, List<WebPrice>>()
    private val at = HashMap<String, Long>()
    private val busy = HashSet<String>()
    private val gate = Semaphore(2)
    private const val MAX_AGE_MS = 6 * 3600 * 1000L

    // The shops' own sites are made for browsers; this says it's a phone browser, like the app's web view.
    private val BROWSER = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/json", "Accept-Language" to "nb-NO",
    )
    private val userId = UUID.randomUUID().toString()

    fun key(name: String) = Parse.searchTerm(name)

    /** What's known for this product right now (null = not searched yet). */
    fun of(name: String): List<WebPrice>? = found[key(name)]

    fun isLoading(name: String) = synchronized(busy) { key(name) in busy }

    suspend fun load(name: String, force: Boolean = false) {
        val term = key(name)
        if (term.isBlank()) return
        if (!force && found.containsKey(term) && System.currentTimeMillis() - (at[term] ?: 0) < MAX_AGE_MS) return
        synchronized(busy) { if (!busy.add(term)) return }
        try {
            gate.withPermit {
                val all = coroutineScope {
                    listOf(
                        async { runCatching { vita(term) }.getOrDefault(emptyList()) },
                        async { runCatching { blush(term) }.getOrDefault(emptyList()) },
                        async { runCatching { boots(term) }.getOrDefault(emptyList()) },
                    ).map { it.await() }.flatten()
                }
                found[term] = Parse.matchWeb(term, all)
                at[term] = System.currentTimeMillis()
            }
        } finally {
            synchronized(busy) { busy.remove(term) }
        }
    }

    private suspend fun vita(term: String) =
        Parse.vitaProducts(Http.get("https://www.vita.no/search?q=" + Http.enc(term), BROWSER, timeoutMs = 20000))

    private suspend fun blush(term: String) =
        Parse.nordicfeelProducts(Http.get("https://www.nordicfeel.com/no/search?q=" + Http.enc(term), BROWSER, timeoutMs = 20000))

    private suspend fun boots(term: String): List<WebPrice> {
        val body = JSONObject().put("query", term).put("resultsOptions", JSONObject().put("take", 24)).toString()
        return Parse.loop54Products(Http.post("https://boots-no-prod.54proxy.com/search", body, "application/json",
            BROWSER + mapOf("Api-Version" to "V3", "User-Id" to userId), timeoutMs = 20000))
    }
}
