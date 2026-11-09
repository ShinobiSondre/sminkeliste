package com.sminkeliste.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sminkeliste.app.data.Alerts
import com.sminkeliste.app.data.Deals
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.data.Parse
import com.sminkeliste.app.data.Shop
import com.sminkeliste.app.util.tr
import com.sminkeliste.app.watch.Shops
import kotlinx.coroutines.launch

/** Checks deals for everything open on your lists (and all beauty deals near you). */
suspend fun runDealsCheck(ctx: Context, force: Boolean = false) {
    val loc = currentLocation(ctx)
    Deals.check(ctx, ListRepo.open, loc?.first, loc?.second, force)
}

/** Beauty shops and pharmacies near you, with how far away they are. */
object NearShops {
    var list by mutableStateOf<List<Pair<Shop, Double>>>(emptyList())
    suspend fun load(ctx: Context) {
        val loc = currentLocation(ctx) ?: return
        list = runCatching { Shops.near(ctx, loc.first, loc.second) }.getOrDefault(emptyList())
            .map { it to Parse.distanceM(loc.first, loc.second, it.lat, it.lng) }.sortedBy { it.second }.take(12)
    }
}

@Composable
fun DealsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf<String?>(null) }
    var follow by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - Deals.fetchedAt > 3600_000L) runDealsCheck(ctx)
        NearShops.load(ctx)
    }
    val mine = Deals.results.filter { it.offers.isNotEmpty() }
    val browse = Deals.beauty.filter { cat == null || Parse.categoryOf(it.heading + " " + it.description)?.key == cat }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PinkHeader(tr("Deals", "Tilbud"), tr("Makeup on sale near you this week", "Sminke på tilbud nær deg denne uken")) {
                if (Deals.loading) CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), color = Color.White, strokeWidth = 2.dp)
                else IconButton(onClick = { scope.launch { runDealsCheck(ctx, force = true); NearShops.load(ctx) } }) {
                    Icon(Icons.Filled.Refresh, tr("Refresh", "Oppdater"), tint = Color.White)
                }
            }
        }
        Deals.error?.let { e -> item { Text(e, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) } }
        if (Deals.usedFallbackLocation) item {
            Text(tr("Showing deals around Oslo. Allow location (Settings) to see your area.",
                "Viser tilbud rundt Oslo. Tillat posisjon (Innstillinger) for å se ditt område."),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- your list: shops first, online underneath ----
        item { Title(tr("Your lists – shops and online", "Listene dine – butikk og nett")) }
        if (ListRepo.open.isEmpty()) item {
            Text(tr("Add makeup to a list to see where it's cheapest here.", "Legg sminke på en liste for å se hvor den er billigst her."),
                Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        val best = Deals.bestStores().firstOrNull()
        if (best != null && mine.size > 1) item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer) {
                Text(tr("⭐ ${best.first} has the most of your list on sale (${best.second})", "⭐ ${best.first} har mest av listen din på tilbud (${best.second})"),
                    Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
            }
        }
        // Items with a shop deal first.
        val open = ListRepo.open.distinctBy { it.searchName.lowercase() }.sortedByDescending { Deals.forItem(it.name).isNotEmpty() }
        items(open, key = { "i-${it.id}" }) { i ->
            Surface(onClick = { ItemUi.openId = i.id }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).animateItem(),
                shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProductThumb(i.exact?.image, Parse.categoryByKey(i.category)?.emoji ?: "💄", 36)
                        Spacer(Modifier.width(10.dp))
                        Text(i.exact?.name ?: i.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        FollowBell(i.searchName)
                    }
                    Spacer(Modifier.size(6.dp))
                    WhereToBuy(i.searchName, i.name, max = 5)
                }
            }
        }

        // ---- follows ----
        item { Title(tr("🔔 Deal alerts", "🔔 Tilbudsvarsler")) }
        item {
            Text(tr("Follow a product, brand or kind. The app checks the flyers every 6 hours and tells you when it's on sale.",
                "Følg et produkt, merke eller type. Appen sjekker tilbudsavisene hver 6. time og sier fra når det er på tilbud."),
                Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(follow, { follow = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(24.dp),
                    placeholder = { Text(tr("e.g. Maybelline, mascara, CeraVe", "f.eks. Maybelline, mascara, CeraVe")) })
                IconButton(onClick = { Alerts.follow(ctx, follow); follow = "" }, enabled = follow.isNotBlank()) {
                    Icon(Icons.Filled.Add, tr("Follow", "Følg"), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(Alerts.follows.toList(), key = { "f-${it.id}" }) { f ->
            val now = Deals.beauty.let { Parse.matchOffers(Parse.searchTerm(f.name), it, Deals.nowIso()) }
            Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.SemiBold)
                        Text(if (now.isEmpty()) tr("Not on sale now", "Ikke på tilbud nå")
                            else tr("🏷️ ${now.size} on sale · from ${kr(now.minOf { it.price })}", "🏷️ ${now.size} på tilbud · fra ${kr(now.minOf { it.price })}"),
                            fontSize = 12.sp, color = if (now.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Sale)
                    }
                    IconButton(onClick = { Alerts.unfollow(ctx, f.name) }) { Icon(Icons.Filled.Close, tr("Stop following", "Slutt å følge")) }
                }
            }
        }

        // ---- browse ----
        item { Title(tr("All makeup deals in shops (${Deals.beauty.size})", "Alle sminketilbud i butikker (${Deals.beauty.size})")) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text(tr("All", "Alle")) }, shape = RoundedCornerShape(50)) }
                items(Parse.CATEGORIES) { c ->
                    FilterChip(selected = cat == c.key, onClick = { cat = if (cat == c.key) null else c.key },
                        label = { Text("${c.emoji} ${tr(c.en, c.no)}") }, shape = RoundedCornerShape(50))
                }
            }
        }
        if (browse.isEmpty() && !Deals.loading) item {
            Text(tr("No deals found in the flyers near you right now.", "Fant ingen tilbud i tilbudsavisene nær deg akkurat nå."),
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        itemsIndexed(browse.take(60), key = { _, o -> "b-${o.id}" }) { n, o ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 3.dp).animateItem()) {
                Appear(n) { OfferRow(o, onClick = { ListRepo.add(o.heading.lowercase().replaceFirstChar { it.uppercase() }) }) }
            }
        }

        // ---- shops ----
        if (NearShops.list.isNotEmpty()) {
            item { Title(tr("Beauty shops & pharmacies near you", "Sminkebutikker og apotek nær deg")) }
            items(NearShops.list, key = { "s-${it.first.id}" }) { (s, d) ->
                Surface(onClick = { openMap(ctx, s.lat, s.lng, s.name) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (s.kind == "pharmacy") "💊" else "💄", fontSize = 20.sp); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold)
                            Text(if (d < 1000) "${d.toInt()} m" else "${"%.1f".format(d / 1000)} km", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.Map, tr("Open in map", "Åpne i kart"), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            TextButton(onClick = { openUrl(ctx, "https://etilbudsavis.no") }, Modifier.padding(horizontal = 8.dp)) {
                Text(tr("Deals from eTilbudsavis · shops from OpenStreetMap", "Tilbud fra eTilbudsavis · butikker fra OpenStreetMap"), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Title(t: String) {
    Text(t, Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp), style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary)
}
