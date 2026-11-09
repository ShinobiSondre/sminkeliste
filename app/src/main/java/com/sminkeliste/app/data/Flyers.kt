package com.sminkeliste.app.data

import android.content.Context
import com.sminkeliste.app.util.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * This week's flyers near you (beauty chains, pharmacies, Europris, Normal, the big grocery chains …), from eTilbudsavis/Tjek.
 * All offers are downloaded at most a few times a day and kept on the phone; matching your list
 * against them happens on the phone. That finds far more than the service's own search does.
 */
object Flyers {
    private const val API = "https://squid-api.tjek.com/v2"
    private const val MAX_AGE_MS = 8 * 3600 * 1000L
    private const val MOVE_M = 15_000.0

    private var offers: List<Offer> = emptyList()
    private var lat = 0.0
    private var lng = 0.0
    private var at = 0L
    private var radius = 0
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "flyers.json")

    @Synchronized
    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(file(ctx).readText())
            lat = o.getDouble("lat"); lng = o.getDouble("lng"); at = o.getLong("at"); radius = o.optInt("radius")
            val a = o.getJSONArray("offers")
            offers = (0 until a.length()).map {
                val x = a.getJSONObject(it)
                Offer(x.optString("id"), x.optString("h"), x.optString("d"), x.optString("s"), x.optDouble("p"),
                    x.optDouble("pp", Double.NaN).takeIf { v -> !v.isNaN() }, "NOK", x.optString("t"),
                    x.optString("i").takeIf { u -> u.startsWith("http") }, x.optString("f"),
                    x.optString("z").takeIf { u -> u.startsWith("http") })
            }
        }
    }

    private fun save(ctx: Context) {
        val a = JSONArray()
        offers.forEach {
            a.put(JSONObject().put("id", it.id).put("h", it.heading).put("d", it.description).put("s", it.store)
                .put("p", it.price).put("pp", it.prePrice ?: JSONObject.NULL).put("t", it.runTill).put("f", it.runFrom)
                .put("i", it.image ?: "").put("z", it.imageLarge ?: ""))
        }
        runCatching {
            file(ctx).writeText(JSONObject().put("lat", lat).put("lng", lng).put("at", at).put("radius", radius).put("offers", a).toString())
        }
    }

    /** All current offers around this spot. Uses the phone's copy when it's recent and for this area. */
    suspend fun near(ctx: Context, la: Double, ln: Double, radiusM: Int, force: Boolean = false): List<Offer> =
        withContext(Dispatchers.IO) { nearBlocking(ctx, la, ln, radiusM, force) }

    /** This week's offers on the phone, read off the main thread (the first read is a large file). */
    suspend fun cachedAsync(ctx: Context): List<Offer> = withContext(Dispatchers.IO) { load(ctx); offers }

    private suspend fun nearBlocking(ctx: Context, la: Double, ln: Double, radiusM: Int, force: Boolean): List<Offer> {
        load(ctx)
        val fresh = System.currentTimeMillis() - at < MAX_AGE_MS && radius == radiusM &&
            Parse.distanceM(la, ln, lat, lng) < MOVE_M && offers.isNotEmpty()
        if (fresh && !force) return offers

        val q = "r_lat=$la&r_lng=$ln&r_radius=$radiusM&r_locale=nb_NO&api_av=0.3.0"
        val catalogs = Parse.beautyCatalogs(Http.get("$API/catalogs?$q&limit=100&types=paged,incito"))
        val gate = Semaphore(4)
        val all = coroutineScope {
            catalogs.map { (id, chain) ->
                async {
                    gate.withPermit {
                        val got = ArrayList<Offer>()
                        for (page in 0 until 5) {
                            val batch = runCatching {
                                Parse.tjekOffers(Http.get("$API/offers?catalog_ids=$id&limit=100&offset=${page * 100}&api_av=0.3.0"))
                            }.getOrDefault(emptyList())
                            got += batch.map { if (it.store.isBlank()) it.copy(store = chain) else it }
                            if (batch.size < 100) break
                        }
                        got
                    }
                }
            }.awaitAll().flatten()
        }
        if (all.isEmpty() && offers.isNotEmpty() && Parse.distanceM(la, ln, lat, lng) < MOVE_M) return offers
        // Only makeup and skincare is kept: the grocery flyers are mostly food.
        offers = all.distinctBy { it.id }.filter { Parse.isBeauty(it) }
        lat = la; lng = ln; at = System.currentTimeMillis(); radius = radiusM
        save(ctx)
        return offers
    }

    /** This week's offers already on the phone. Call from a background thread (see [cachedAsync]). */
    fun cached(ctx: Context): List<Offer> { load(ctx); return offers }

    val chains: List<String> get() = offers.map { it.store }.distinct().sorted()
}
