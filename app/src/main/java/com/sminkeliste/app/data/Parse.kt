package com.sminkeliste.app.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Pure parsing and matching code, with no Android in it, so the cloud build can unit-test it.

/** A weekly-flyer deal from eTilbudsavis / Tjek. */
data class Offer(
    val id: String,
    val heading: String,
    val description: String,
    val store: String,
    val price: Double,
    val prePrice: Double?,
    val currency: String,
    val runTill: String,
    val image: String?,
    val runFrom: String = "",
    val imageLarge: String? = null,
) {
    val savingsPct: Int? get() = prePrice?.takeIf { it > price }?.let { (((it - price) / it) * 100).toInt() }
}

/** A beauty shop or pharmacy from OpenStreetMap. */
data class Shop(
    val id: String,
    val name: String,
    val brand: String,
    val kind: String,
    val lat: Double,
    val lng: Double,
    /** Buildings (ways) are measured from their middle, so they get a bit more room. */
    val isArea: Boolean,
)

/** A kind of makeup or skincare, with the words that give it away. */
data class Category(val key: String, val emoji: String, val en: String, val no: String, val words: List<String>)

/** A product's price in an official web shop (Vita, Blush, Boots …). */
data class WebPrice(val shop: String, val name: String, val price: Double, val prePrice: Double?, val url: String, val image: String?) {
    val savingsPct: Int? get() = prePrice?.takeIf { it > price }?.let { (((it - price) / it) * 100).toInt() }
}

/** One product the AI suggested ("PRODUKT: … | KATEGORI: … | PRIS: … | HVORFOR: …"). */
data class Pick(val name: String, val category: String, val price: String, val why: String)

object Parse {

    val CATEGORIES = listOf(
        Category("face", "✨", "Face", "Ansikt", listOf("foundation", "concealer", "pudder", "powder", "primer", "bb cream", "cc cream", "bbkrem",
            "cc krem", "skin tint", "setting spray", "fixing spray", "corrector", "korrektor")),
        Category("cheeks", "🌸", "Cheeks", "Kinn", listOf("blush", "rouge", "bronzer", "highlighter", "contour", "kontur", "solpudder")),
        Category("eyes", "👁️", "Eyes", "Øyne", listOf("mascara", "eyeliner", "kajal", "øyenskygge", "eyeshadow", "eye shadow", "palett", "palette",
            "bryn", "brow", "brynsblyant", "øyenvipper", "lashes", "vipper", "øyenbryn")),
        Category("lips", "💄", "Lips", "Lepper", listOf("leppestift", "lipstick", "lipgloss", "lip gloss", "leppeglans", "lip liner", "lipliner",
            "leppepomade", "lip balm", "lip oil", "leppeolje", "lip", "leppe")),
        Category("nails", "💅", "Nails", "Negler", listOf("neglelakk", "nail polish", "negl", "nail", "gellakk", "topcoat", "base coat")),
        Category("tools", "🖌️", "Tools", "Verktøy", listOf("sminkekost", "makeup brush", "sminkesvamp", "beauty blender", "vippebøyer", "pinsett")),
    )

    /** Makeup and skincare brands you'll meet in Norwegian shops and flyers. */
    val BRANDS = listOf(
        "maybelline", "l'oréal", "loreal", "l'oreal", "max factor", "rimmel", "nyx", "essence", "catrice", "isadora", "revlon", "bourjois",
        "wet n wild", "e.l.f.", "elf", "garnier", "nivea", "neutrogena", "la roche-posay", "la roche posay", "cerave", "the ordinary",
        "clinique", "estée lauder", "estee lauder", "lancôme", "lancome", "mac", "benefit", "urban decay", "too faced", "nars",
        "rare beauty", "fenty", "charlotte tilbury", "bobbi brown", "dior", "chanel", "ysl", "sleek", "w7",
        "makeup revolution", "l.a. girl", "la girl", "physicians formula", "gosh", "idun", "lumene", "anastasia", "huda", "kiko",
        "sminkeriet", "vichy", "eucerin", "bioderma", "paula's choice", "inika", "zoeva", "real techniques",
        "tarte", "smashbox", "rituals", "the body shop", "lyko", "skin79", "cosrx", "beauty of joseon", "milani", "colourpop",
        "covergirl", "max factor", "gosh copenhagen", "nuxe", "clarins", "shiseido", "artdeco", "pupa", "deborah",
    )

    /** Brands that only make makeup, so their name alone says it's makeup. */
    val MAKEUP_BRANDS = listOf(
        "maybelline", "max factor", "rimmel", "nyx", "essence", "catrice", "isadora", "gosh", "gosh copenhagen", "sleek", "w7",
        "makeup revolution", "l.a. girl", "la girl", "wet n wild", "e.l.f.", "elf", "mac", "benefit", "urban decay", "too faced", "nars",
        "rare beauty", "fenty", "charlotte tilbury", "bobbi brown", "anastasia", "huda", "kiko", "milani", "colourpop", "covergirl",
        "smashbox", "tarte", "zoeva", "real techniques", "sminkeriet", "artdeco", "pupa", "deborah", "physicians formula", "bourjois", "revlon",
    )

    /** Words that mean it's not makeup (food, hair, body, household …), even from a makeup brand's maker. */
    private val NOT_MAKEUP = listOf("sjampo", "shampoo", "balsam", "conditioner", "dusj", "shower", "deodorant", "deo", "hårfarge", "hair",
        "hår", "tannkrem", "bleie", "vask", "barbering", "body lotion", "bodylotion", "håndkrem", "solkrem", "parfyme", "perfume",
        "dagkrem", "nattkrem", "serum", "rens", "sjokolade", "kaffe", "pizza", "kylling", "brus")

    private val BEAUTY_WORDS = listOf("sminke", "makeup", "make-up")

    private fun rx(w: String) = Regex("(^|[^\\p{L}])" + Regex.escape(w) + "($|[^\\p{L}])")
    private val brandRx = BRANDS.distinct().map { it to rx(it) }
    private val catRx = CATEGORIES.map { c -> c to c.words.map { rx(it) } }
    private val beautyRx = BEAUTY_WORDS.map { rx(it) }
    private val makeupBrandRx = MAKEUP_BRANDS.map { rx(it) }
    private val notMakeupRx = NOT_MAKEUP.map { rx(it) }

    /** Which kind of thing this is ("Maybelline Sky High mascara" → eyes), or null if it doesn't say. */
    fun categoryOf(text: String): Category? {
        val t = text.lowercase()
        // Longest word first, so "lip liner" beats "lip" and "øyenskygge" beats "skygge".
        return catRx.flatMap { (c, rs) -> c.words.zip(rs).filter { it.second.containsMatchIn(t) }.map { c to it.first.length } }
            .maxByOrNull { it.second }?.first
            ?: CATEGORIES.firstOrNull { c -> c.words.any { w -> w.length >= 6 && t.contains(w) } } // øyenSKYGGEpalett
    }

    fun categoryByKey(key: String?): Category? = CATEGORIES.firstOrNull { it.key == key }

    fun brandOf(text: String): String? {
        val t = text.lowercase()
        return brandRx.filter { it.second.containsMatchIn(t) }.maxByOrNull { it.first.length }?.first
    }

    /** Is this makeup (and only makeup: no food, skincare, hair or perfume)? */
    fun isMakeup(text: String): Boolean {
        val t = text.lowercase()
        if (notMakeupRx.any { it.containsMatchIn(t) }) return false
        return categoryOf(t) != null || makeupBrandRx.any { it.containsMatchIn(t) } || beautyRx.any { it.containsMatchIn(t) }
    }

    /** Is this flyer offer makeup? */
    fun isBeauty(o: Offer): Boolean = isMakeup(o.heading + " " + o.description)

    fun tjekOffers(json: String): List<Offer> {
        val arr = JSONArray(json)
        val out = ArrayList<Offer>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pricing = o.optJSONObject("pricing") ?: continue
            val price = pricing.optDouble("price", Double.NaN)
            if (price.isNaN() || price <= 0) continue
            val pre = pricing.optDouble("pre_price", Double.NaN).takeIf { !it.isNaN() && it > price }
            val store = o.optJSONObject("branding")?.optString("name").orEmpty()
                .ifBlank { o.optJSONObject("dealer")?.optString("name").orEmpty() }
            val images = o.optJSONObject("images")
            out += Offer(
                id = o.optString("id"),
                heading = o.optString("heading").trim(),
                description = o.optString("description").takeIf { it != "null" }.orEmpty().trim(),
                store = store.trim(),
                price = price,
                prePrice = pre,
                currency = pricing.optString("currency", "NOK").ifBlank { "NOK" },
                runTill = o.optString("run_till"),
                runFrom = o.optString("run_from"),
                imageLarge = images?.optString("zoom")?.takeIf { it.startsWith("http") }
                    ?: images?.optString("view")?.takeIf { it.startsWith("http") },
                image = images?.optString("thumb")?.takeIf { it.startsWith("http") }
                    ?: images?.optString("view")?.takeIf { it.startsWith("http") },
            )
        }
        return out
    }

    fun overpassShops(json: String): List<Shop> {
        val els = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Shop>()
        for (i in 0 until els.length()) {
            val e = els.optJSONObject(i) ?: continue
            val type = e.optString("type")
            val center = e.optJSONObject("center")
            val lat = if (center != null) center.optDouble("lat") else e.optDouble("lat", Double.NaN)
            val lng = if (center != null) center.optDouble("lon") else e.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue
            val tags = e.optJSONObject("tags") ?: JSONObject()
            val brand = tags.optString("brand")
            val kind = tags.optString("shop").ifBlank { tags.optString("amenity") }
            val name = tags.optString("name").ifBlank { brand }.ifBlank { kind }
            out += Shop(
                id = "$type/${e.optLong("id")}",
                name = name,
                brand = brand.ifBlank { name },
                kind = kind,
                lat = lat, lng = lng,
                isArea = type != "node",
            )
        }
        return out.distinctBy { it.id }
    }

    /** The AI answer's "PRODUKT: … | KATEGORI: … | PRIS: … | HVORFOR: …" lines. */
    private val PICK_LINE = Regex("(?im)^\\W*(PRODUKT|PRODUCT)\\s*\\d*[*\\s]*:[*\\s]*([^\\n]+)$")

    fun picks(answer: String): List<Pick> = PICK_LINE.findAll(answer).mapNotNull { m ->
        val parts = m.groupValues[2].split("|").map { it.replace("*", "").trim() }
        val name = parts.first().trimEnd('.').trim()
        if (name.isBlank() || name.length > 80) return@mapNotNull null
        fun part(vararg keys: String) = parts.drop(1).firstOrNull { p -> keys.any { p.uppercase().startsWith(it) } }
            ?.substringAfter(":")?.trim().orEmpty()
        Pick(name, part("KATEGORI", "CATEGORY"), part("PRIS", "PRICE"), part("HVORFOR", "WHY"))
    }.distinctBy { it.name.lowercase() }.toList()

    /** The answer without the machine lines (they become product cards). */
    fun display(answer: String): String = answer.replace(PICK_LINE, "").replace(Regex("\\n{3,}"), "\n\n").trim()

    // ---- web shops ----

    /** "1 234,50 kr" → 1234.5 */
    fun priceText(t: String): Double? {
        val c = t.replace(Regex("[\\s\u00A0]"), "").replace(Regex("[^0-9,.]"), "")
        if (c.isBlank()) return null
        val n = if (c.contains(",")) c.replace(".", "").replace(",", ".") else c
        return n.toDoubleOrNull()?.takeIf { it > 0 }
    }

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace(Regex("&#x([0-9A-Fa-f]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }.trim()

    /** Vita's search page: the products are in `window.CURRENT_PAGE = {…}`. */
    fun vitaProducts(html: String): List<WebPrice> {
        val start = html.indexOf("window.CURRENT_PAGE")
        if (start < 0) return emptyList()
        val open = html.indexOf('{', start)
        val end = html.indexOf("</script>", open)
        if (open < 0 || end < 0) return emptyList()
        val json = html.substring(open, end).trim().trimEnd(';').trim()
        val arr = runCatching { JSONObject(json).optJSONArray("products") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val price = p.optJSONObject("price")
            val now = price?.optJSONObject("current")?.optDouble("inclVat", Double.NaN) ?: Double.NaN
            if (now.isNaN() || now <= 0) return@mapNotNull null
            val was = price?.optJSONObject("original")?.optDouble("inclVat", Double.NaN)?.takeIf { !it.isNaN() && it > now }
            val name = p.optString("displayName").trim()
            if (name.isBlank()) return@mapNotNull null
            val img = p.optJSONArray("images")?.optJSONObject(0)?.optString("url").orEmpty()
            WebPrice("Vita", name, now, was, "https://www.vita.no" + p.optString("url"), if (img.startsWith("/")) "https://www.vita.no$img" else null)
        }
    }

    /** Blush / NordicFeel's search page: one product card per `<div id="product-…">`. */
    fun nordicfeelProducts(html: String): List<WebPrice> =
        html.split("<div id=\"product-").drop(1).mapNotNull { card ->
            val name = Regex("aria-label=\"([^\"]+)\"").find(card)?.groupValues?.get(1)?.let { unescape(it) } ?: return@mapNotNull null
            val url = Regex("href=\"(https://www\\.nordicfeel\\.com/no/product/[^\"]+)\"").find(card)?.groupValues?.get(1) ?: return@mapNotNull null
            val now = Regex("price__now\">([^<]+)<").find(card)?.groupValues?.get(1)?.let { priceText(it) } ?: return@mapNotNull null
            val was = Regex("price__value\">[^<]*<span>([^<]+)<").find(card)?.groupValues?.get(1)?.let { priceText(it) }?.takeIf { it > now }
            val img = Regex("<img[^>]*\\ssrc=\"(https://[^\"]+)\"").find(card)?.groupValues?.get(1)?.let { unescape(it) }
            WebPrice("Blush", name, now, was, unescape(url), img)
        }

    /** Boots' search (Loop54): every product has a list of named attributes. */
    fun loop54Products(json: String, shop: String = "Boots"): List<WebPrice> {
        val items = JSONObject(json).optJSONObject("results")?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val attrs = items.optJSONObject(i)?.optJSONArray("attributes") ?: return@mapNotNull null
            val a = HashMap<String, String>()
            for (j in 0 until attrs.length()) {
                val o = attrs.optJSONObject(j) ?: continue
                o.optJSONArray("values")?.let { v -> if (v.length() > 0) a[o.optString("name")] = v.optString(0) }
            }
            val price = a["Price"]?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val name = a["Name"]?.trim().orEmpty().ifBlank { return@mapNotNull null }
            val brand = a["Brand"].orEmpty()
            WebPrice(shop, if (brand.isNotBlank() && !name.startsWith(brand, true)) "$brand $name" else name, price,
                null, a["ProductURL"].orEmpty(), a["ThumbnailURL"] ?: a["ImageURL"])
        }
    }

    /** The same product in different shops gets the same key ("Maybelline Lash Sensational Sky High Mascara Black" ≈ "… Mascara"). */
    fun productKey(name: String): String =
        name.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(" ")
            .filter { it.length >= 2 && it !in setOf("black", "sort", "ml", "new", "york", "the", "og", "and") && it.toDoubleOrNull() == null }
            .distinct().sorted().joinToString(" ")

    /** The web shop products that are really what you searched for, cheapest first. */
    fun matchWeb(term: String, products: List<WebPrice>, limit: Int = 6): List<WebPrice> {
        val asOffers = products.mapIndexed { i, w -> Offer("$i", w.name, "", w.shop, w.price, w.prePrice, "NOK", "", w.image) }
        val hit = matchOffers(term, asOffers, limit = 40).map { products[it.id.toInt()] }.filter { isMakeup(it.name) }
        return hit.sortedBy { it.price }.distinctBy { it.shop + "|" + it.name.lowercase() }.take(limit)
    }

    // ---- matching ----

    /** "2 stk Maybelline mascara!" → "maybelline mascara". Drops amounts and units. */
    fun searchTerm(item: String): String {
        val units = setOf("stk", "pk", "pakke", "ml", "g", "x", "pcs", "pack", "nyanse", "shade", "farge")
        return item.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}' ]"), " ")
            .split(" ").filter { it.isNotBlank() && it.toDoubleOrNull() == null && it !in units && !it.matches(Regex("\\d+\\p{L}{0,2}")) }
            .joinToString(" ").trim()
            .ifBlank { item.trim().lowercase() }
    }

    /** Flyer catalogs near you → (catalog id, chain) for chains that may sell makeup. */
    fun beautyCatalogs(json: String): List<Pair<String, String>> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            val chain = c.optJSONObject("branding")?.optString("name").orEmpty()
            if (c.optInt("offer_count", 0) <= 0 || !sellsBeauty(chain)) null else c.optString("id") to chain
        }.distinctBy { it.first }
    }

    /** Beauty chains, pharmacies and the shops that also carry drugstore makeup. */
    private val BEAUTY_CHAINS = listOf(
        "vita", "kicks", "lyko", "normal", "apotek", "vitus", "boots", "farmasiet", "bodyshop", "parfym", "beauty", "cosmetic", "sminke",
        "europris", "coopobs", "coopextra", "coopmega", "coopprix", "rema1000", "kiwi", "meny", "spar", "joker", "bunnpris", "lidl",
        "cubus", "hm", "lindex", "clasohlson", "nille", "tigeroflying", "flyingtiger", "sostrenegrene", "søstrenegrene", "biltema",
    )

    fun sellsBeauty(chain: String): Boolean {
        val k = chainKey(chain)
        return k.isNotBlank() && BEAUTY_CHAINS.any { k.contains(it) }
    }

    /**
     * Deals for one thing on your list, best match first. Brand and kind both count: "Maybelline mascara"
     * finds "MAYBELLINE LASH SENSATIONAL MASCARA", and most of the words you wrote must be on the deal.
     * Only makeup and skincare offers count, so "lip" never matches a grocery "flip".
     */
    fun matchOffers(term: String, offers: List<Offer>, nowIso: String = "", limit: Int = 10): List<Offer> {
        val words = term.lowercase().split(" ").filter { it.length >= 2 }.distinct()
        if (words.isEmpty()) return emptyList()
        val whole = words.map { rx(it) }
        val need = maxOf(1, ceil(words.size * 0.6).toInt())
        val brand = brandOf(term)
        val cat = categoryOf(term)
        fun score(o: Offer): Int {
            val head = o.heading.lowercase()
            val hay = head + " " + o.description.lowercase()
            var hits = 0
            var total = 0
            for ((i, w) in words.withIndex()) {
                val s = when {
                    whole[i].containsMatchIn(head) -> 6
                    whole[i].containsMatchIn(hay) -> 3
                    w.length >= 4 && hay.contains(w) -> 2 // øyenskyggePALETT
                    else -> 0
                }
                if (s > 0) hits++
                total += s
            }
            if (hits < need) return 0
            // You named a brand: it has to be that brand. You named a kind: it has to be that kind.
            if (brand != null && brandOf(hay) != brand && !hay.contains(brand)) return 0
            if (cat != null && brand == null && categoryOf(hay)?.key != cat.key) return 0
            return total
        }
        return offers.asSequence()
            .filter { nowIso.isEmpty() || it.runTill.isEmpty() || it.runTill >= nowIso }
            .filter { isBeauty(it) }
            .map { it to score(it) }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Offer, Int>> { it.second }.thenBy { it.first.price })
            .map { it.first }.distinctBy { it.store + "|" + it.heading.lowercase() + "|" + it.price }
            .take(limit).toList()
    }

    /** Every makeup offer this week, biggest discount first (optionally of one kind). */
    fun beautyOffers(offers: List<Offer>, nowIso: String = "", categoryKey: String? = null): List<Offer> =
        offers.asSequence()
            .filter { nowIso.isEmpty() || it.runTill.isEmpty() || it.runTill >= nowIso }
            .filter { isBeauty(it) }
            .filter { categoryKey == null || categoryOf(it.heading + " " + it.description)?.key == categoryKey }
            .distinctBy { it.store + "|" + it.heading.lowercase() + "|" + it.price }
            .sortedWith(compareByDescending<Offer> { it.savingsPct ?: 0 }.thenBy { it.price })
            .toList()

    /** "REMA 1000", "Rema1000" and "rema" all become keys that can be compared. */
    fun chainKey(name: String): String {
        val k = name.lowercase().replace(Regex("[^a-z0-9æøå]"), "")
        return when {
            k.startsWith("rema") -> "rema1000"
            k.startsWith("kiwi") -> "kiwi"
            k.startsWith("vitus") -> "vitusapotek"
            k.startsWith("apotek1") -> "apotek1"
            k.contains("extra") && k.contains("coop") -> "coopextra"
            k.contains("obs") && k.contains("coop") -> "coopobs"
            else -> k
        }
    }

    fun sameChain(a: String, b: String): Boolean {
        val x = chainKey(a); val y = chainKey(b)
        if (x.isBlank() || y.isBlank()) return false
        return x == y || (x.length >= 4 && y.startsWith(x)) || (y.length >= 4 && x.startsWith(y))
    }

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** The shop you are standing in, if any: close enough to it, and your position is precise enough. */
    fun shopAt(shops: List<Shop>, lat: Double, lng: Double, accuracyM: Float): Shop? {
        if (accuracyM > 65f) return null
        return shops
            .map { it to distanceM(lat, lng, it.lat, it.lng) }
            .filter { (s, d) -> d <= (if (s.isArea) 55.0 else 35.0) + (accuracyM / 3.0) }
            .minByOrNull { it.second }?.first
    }

    /** Splits "mascara, concealer og leppestift" into three items. */
    fun splitItems(input: String): List<String> =
        input.split(Regex(",|;|\\n| og | and ")).map { it.trim() }.filter { it.isNotBlank() }
            .map { it.replaceFirstChar { c -> c.uppercase() } }
}
