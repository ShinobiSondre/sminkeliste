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
 * Vita and Lyko (their search pages), Blush (its search page), Boots (its search service), and through the
 * invisible browser Kicks, Fredrik & Louisa and Parfym.no. Searched from the
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
                // The quick shops first; the browser shops add to the list as each one finishes.
                all(term) { sofar -> found[term] = Parse.matchWeb(term, sofar, limit = 10) }
                at[term] = System.currentTimeMillis()
            }
        } finally {
            synchronized(busy) { busy.remove(term) }
        }
    }

    /** Shops searched, for the screen ("Searching 7 shops …"). */
    val shopCount: Int get() = 4 + if (ShopBrowser.available) ShopBrowser.SHOPS.size else 0

    /**
     * Everything the shops' searches return for [term]. [progress] gets the list so far each time a shop
     * answers (the quick ones together, then each browser shop), so the screen can fill in step by step.
     */
    private suspend fun all(term: String, progress: (List<WebPrice>) -> Unit = {}): List<WebPrice> = coroutineScope {
        val got = java.util.Collections.synchronizedList(ArrayList<WebPrice>())
        val quick = listOf(
            async { runCatching { vita(term) }.getOrDefault(emptyList()) },
            async { runCatching { blush(term) }.getOrDefault(emptyList()) },
            async { runCatching { boots(term) }.getOrDefault(emptyList()) },
            async { runCatching { lyko(term) }.getOrDefault(emptyList()) },
        )
        val slow = if (ShopBrowser.available) ShopBrowser.SHOPS.map { shop ->
            async { runCatching { ShopBrowser.search(shop, term) }.getOrDefault(emptyList()).also { r -> got.addAll(r); progress(got.toList()) } }
        } else emptyList()
        got.addAll(quick.map { it.await() }.flatten())
        progress(got.toList())
        slow.forEach { it.await() }
        got.toList()
    }

    /** Online prices for one product, without touching the screen's state (for the background alert check). */
    suspend fun fetch(name: String): List<WebPrice> = Parse.matchWeb(key(name), all(key(name)), limit = 10)

    /**
     * Products for the "pick the exact product" search: the shops' own results (makeup only), the same
     * product from several shops shown once, with its cheapest price.
     */
    suspend fun search(query: String, progress: (List<Pair<WebPrice, List<WebPrice>>>) -> Unit = {}): List<Pair<WebPrice, List<WebPrice>>> {
        val term = key(query)
        if (term.isBlank()) return emptyList()
        return group(term, all(term) { sofar -> progress(group(term, sofar)) })
    }

    /** Only real matches (a shop that doesn't have it shows its best-sellers instead), the same product once. */
    private fun group(term: String, got: List<WebPrice>): List<Pair<WebPrice, List<WebPrice>>> {
        val list = Parse.matchWeb(term, got.filter { Parse.isMakeup(it.name) }, limit = 80)
        return list.groupBy { Parse.productKey(it.name) }.values
            .map { same -> same.minBy { it.price } to same.sortedBy { it.price } }
            .sortedBy { (best, _) -> list.indexOf(best) }
            .take(40)
    }

    private suspend fun lyko(term: String) =
        Parse.lykoProducts(Http.get("https://lyko.com/no/sok?q=" + Http.enc(term), BROWSER, timeoutMs = 20000))

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
