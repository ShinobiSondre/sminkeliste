package com.sminkeliste.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.sminkeliste.app.util.Lang
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** The exact product picked for a list item (from a web shop's range), with its picture. */
data class Exact(val name: String, val image: String?, val url: String, val shop: String, val price: Double)

/** One product on a list: "Mascara", or exactly "Maybelline Lash Sensational Sky High Mascara", with an optional shade or note. */
data class Item(
    val id: String,
    val name: String,
    val checked: Boolean = false,
    val added: Long = System.currentTimeMillis(),
    val note: String = "",
    val category: String? = Parse.categoryOf(name)?.key,
    val exact: Exact? = null,
) {
    /** What deals, prices and alerts look for: the exact product if one is picked. */
    val searchName: String get() = exact?.name ?: name
}

data class BeautyList(val id: String, val name: String)

/**
 * Your makeup lists (Ønskeliste, Går tom snart, Til festen …), saved on the phone.
 * [items] is the list on screen; the shop pop-up and deals use what's open on all lists.
 */
object ListRepo {
    val lists = mutableStateListOf<BeautyList>()
    private val byList = HashMap<String, SnapshotStateList<Item>>()
    var currentId by mutableStateOf("")
        private set
    private var loaded = false
    private lateinit var app: Context

    private fun prefs() = app.getSharedPreferences("list", Context.MODE_PRIVATE)

    val items: SnapshotStateList<Item> get() = byList.getOrPut(currentId) { mutableStateListOf() }
    val current: BeautyList? get() = lists.firstOrNull { it.id == currentId }

    fun itemsOf(listId: String): List<Item> = byList[listId].orEmpty()

    @Synchronized
    fun init(ctx: Context) {
        if (loaded) return
        app = ctx.applicationContext
        val saved = prefs().getString("lists", null)
        if (saved != null) runCatching {
            val o = JSONObject(saved)
            val arr = o.getJSONArray("lists")
            for (i in 0 until arr.length()) {
                val l = arr.getJSONObject(i)
                val meta = BeautyList(l.getString("id"), l.getString("name"))
                lists += meta
                val its = mutableStateListOf<Item>()
                val a = l.optJSONArray("items") ?: JSONArray()
                for (j in 0 until a.length()) {
                    val x = a.optJSONObject(j) ?: continue
                    val e = x.optJSONObject("exact")?.let { o ->
                        Exact(o.optString("name"), o.optString("image").takeIf { it.startsWith("http") }, o.optString("url"),
                            o.optString("shop"), o.optDouble("price", 0.0))
                    }?.takeIf { it.name.isNotBlank() }
                    its += Item(x.optString("id"), x.optString("name"), x.optBoolean("checked"), x.optLong("added"),
                        x.optString("note"), x.optString("cat").takeIf { it.isNotBlank() } ?: Parse.categoryOf(x.optString("name"))?.key, e)
                }
                byList[meta.id] = its
            }
            currentId = o.optString("current")
        }
        if (lists.isEmpty()) {
            val first = BeautyList(UUID.randomUUID().toString(), if (Lang.no) "Ønskeliste" else "Wishlist")
            val second = BeautyList(UUID.randomUUID().toString(), if (Lang.no) "Går tom snart" else "Running low")
            lists += first; lists += second
            byList[first.id] = mutableStateListOf()
            byList[second.id] = mutableStateListOf()
        }
        if (lists.none { it.id == currentId }) currentId = lists.first().id
        loaded = true
        save()
    }

    private fun save() {
        val arr = JSONArray()
        lists.forEach { meta ->
            val its = JSONArray()
            byList[meta.id].orEmpty().forEach {
                its.put(JSONObject().put("id", it.id).put("name", it.name).put("checked", it.checked).put("added", it.added)
                    .put("note", it.note).put("cat", it.category ?: "")
                    .put("exact", it.exact?.let { e -> JSONObject().put("name", e.name).put("image", e.image ?: "").put("url", e.url)
                        .put("shop", e.shop).put("price", e.price) } ?: JSONObject.NULL))
            }
            arr.put(JSONObject().put("id", meta.id).put("name", meta.name).put("items", its))
        }
        prefs().edit().putString("lists", JSONObject().put("lists", arr).put("current", currentId).toString()).apply()
    }

    fun item(id: String): Item? = find(id)?.let { (l, i) -> l[i] }

    // ---- lists ----

    fun select(id: String) { if (lists.any { it.id == id }) { currentId = id; save() } }

    fun newList(name: String): String {
        val meta = BeautyList(UUID.randomUUID().toString(), name.trim().ifBlank { if (Lang.no) "Ny liste" else "New list" })
        lists += meta
        byList[meta.id] = mutableStateListOf()
        currentId = meta.id
        save()
        return meta.id
    }

    fun renameList(id: String, name: String) {
        val i = lists.indexOfFirst { it.id == id }
        if (i >= 0 && name.isNotBlank()) { lists[i] = lists[i].copy(name = name.trim()); save() }
    }

    fun deleteList(id: String) {
        if (lists.size <= 1) return
        val i = lists.indexOfFirst { it.id == id }
        if (i < 0) return
        lists.removeAt(i); byList.remove(id)
        if (currentId == id) currentId = lists[(i - 1).coerceAtLeast(0)].id
        save()
    }

    // ---- items ----

    /** Not yet bought, on every list. */
    val open: List<Item> get() = lists.flatMap { byList[it.id].orEmpty() }.filter { !it.checked }

    fun openByList(): List<Pair<BeautyList, List<Item>>> =
        lists.map { it to byList[it.id].orEmpty().filter { i -> !i.checked } }.filter { it.second.isNotEmpty() }

    fun add(text: String, listId: String = currentId, category: String? = null) {
        val target = byList.getOrPut(listId) { mutableStateListOf() }
        Parse.splitItems(text).forEach { name ->
            val i = target.indexOfFirst { it.name.equals(name, true) }
            if (i >= 0) target[i] = target[i].copy(checked = false)
            else target.add(0, Item(UUID.randomUUID().toString(), name, category = category ?: Parse.categoryOf(name)?.key))
        }
        save()
    }

    fun has(name: String): Boolean = open.any { it.name.equals(name.trim(), true) || it.searchName.equals(name.trim(), true) }

    private fun find(id: String): Pair<SnapshotStateList<Item>, Int>? {
        for (l in byList.values) { val i = l.indexOfFirst { it.id == id }; if (i >= 0) return l to i }
        return null
    }

    fun toggle(id: String) { val (l, i) = find(id) ?: return; l[i] = l[i].copy(checked = !l[i].checked); save() }

    fun remove(id: String) { val (l, i) = find(id) ?: return; l.removeAt(i); save() }

    fun edit(id: String, name: String, note: String, category: String?) {
        val (l, i) = find(id) ?: return
        if (name.isBlank()) return
        l[i] = l[i].copy(name = name.trim(), note = note.trim(), category = category)
        save()
    }

    /** Picks (or clears, with null) the exact product. Deal alerts move along to the exact product. */
    fun setExact(id: String, exact: Exact?) {
        val (l, i) = find(id) ?: return
        val before = l[i].searchName
        l[i] = l[i].copy(exact = exact, category = l[i].category ?: exact?.let { Parse.categoryOf(it.name)?.key })
        val after = l[i].searchName
        if (before != after && Alerts.isFollowed(before)) { Alerts.unfollow(app, before); Alerts.follow(app, after) }
        save()
    }

    /** Adds an exact product straight from a search or an AI suggestion. */
    fun addExact(exact: Exact, listId: String = currentId) {
        val target = byList.getOrPut(listId) { mutableStateListOf() }
        if (target.none { it.searchName.equals(exact.name, true) && !it.checked })
            target.add(0, Item(UUID.randomUUID().toString(), exact.name, exact = exact, category = Parse.categoryOf(exact.name)?.key))
        save()
    }

    fun clearChecked() { items.removeAll { it.checked }; save() }
}

/** Small settings, saved on the phone. */
object Settings {
    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun watchOn(ctx: Context) = p(ctx).getBoolean("watch", false)
    fun setWatch(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("watch", on).apply()

    /** Deals radius in km. The flyer service needs at least ~25 km to find the regional flyers. */
    fun radiusKm(ctx: Context) = p(ctx).getInt("radius", 30).coerceAtLeast(25)
    fun setRadiusKm(ctx: Context, km: Int) = p(ctx).edit().putInt("radius", km).apply()

    fun lastLocation(ctx: Context): Pair<Double, Double>? {
        val s = p(ctx).getString("lastLoc", null) ?: return null
        val (a, b) = s.split(",").mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 2 } ?: return null
        return a to b
    }
    fun setLastLocation(ctx: Context, lat: Double, lng: Double) = p(ctx).edit().putString("lastLoc", "$lat,$lng").apply()
}
