package com.sminkeliste.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sminkeliste.app.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Deals for one thing on your list. */
data class ItemDeals(val item: String, val offers: List<Offer>)

/**
 * Finds makeup and skincare on sale near you, from this week's flyers (eTilbudsavis/Tjek: free, no key).
 * Both for what's on your lists and everything beauty-related on sale, to browse.
 */
object Deals {
    var results by mutableStateOf<List<ItemDeals>>(emptyList()); private set
    /** Every makeup and skincare offer near you this week. */
    var beauty by mutableStateOf<List<Offer>>(emptyList()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var fetchedAt by mutableLongStateOf(0L); private set
    var usedFallbackLocation by mutableStateOf(false); private set
    var chains by mutableStateOf<List<String>>(emptyList()); private set

    /** Oslo sentrum, used only when the phone's location isn't available. */
    const val FALLBACK_LAT = 59.9133
    const val FALLBACK_LNG = 10.7389

    fun nowIso(): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())

    suspend fun check(ctx: Context, items: List<Item>, lat: Double?, lng: Double?, force: Boolean = false) {
        if (loading) return
        loading = true
        error = null
        usedFallbackLocation = lat == null
        val la = lat ?: FALLBACK_LAT
        val ln = lng ?: FALLBACK_LNG
        try {
            val (found, all, failed) = withContext(Dispatchers.Default) {
                var failedNow = false
                val flyers = runCatching { Flyers.near(ctx, la, ln, Settings.radiusKm(ctx) * 1000, force) }
                    .onFailure { failedNow = true }.getOrDefault(emptyList())
                val now = nowIso()
                val res = items.distinctBy { it.name.lowercase() }.map { item ->
                    ItemDeals(item.name, Parse.matchOffers(Parse.searchTerm(item.name), flyers, now))
                }
                Triple(res, Parse.beautyOffers(flyers, now), failedNow)
            }
            results = found
            beauty = all
            chains = Flyers.chains
            if (failed) error = tr("Couldn't reach the deals service. Check your internet and try again.",
                "Fikk ikke kontakt med tilbudstjenesten. Sjekk internett og prøv igjen.")
            else fetchedAt = System.currentTimeMillis()
        } finally {
            loading = false
        }
    }

    fun forItem(name: String): List<Offer> = results.firstOrNull { it.item.equals(name, true) }?.offers.orEmpty()

    /** Deals at one chain for things still on your lists (for the in-shop screen). */
    fun atStore(storeName: String): List<Pair<String, Offer>> =
        results.flatMap { d -> d.offers.filter { Parse.sameChain(it.store, storeName) }.map { d.item to it } }

    /** Which shops have the most of your list on sale right now. */
    fun bestStores(): List<Pair<String, Int>> =
        results.flatMap { d -> d.offers.map { it.store }.distinct().map { it to d.item } }
            .groupBy { it.first }.map { (s, l) -> s to l.size }
            .sortedByDescending { it.second }
}
