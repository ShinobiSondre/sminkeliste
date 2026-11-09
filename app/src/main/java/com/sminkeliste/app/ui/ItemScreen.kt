package com.sminkeliste.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sminkeliste.app.data.Advice
import com.sminkeliste.app.data.Alerts
import com.sminkeliste.app.data.Exact
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.data.Opt
import com.sminkeliste.app.data.Parse
import com.sminkeliste.app.data.WebPrice
import com.sminkeliste.app.data.WebPrices
import com.sminkeliste.app.util.tr
import kotlinx.coroutines.launch

/** Which product page is open (drawn over the whole app by MainActivity). */
object ItemUi { var openId by mutableStateOf<String?>(null) }

/** The "find the exact product" search: for an item on the list, or (with no item) to add a new one. */
object PickerUi {
    var open by mutableStateOf(false); private set
    var itemId by mutableStateOf<String?>(null); private set
    var query by mutableStateOf(""); private set
    fun show(query: String, itemId: String? = null) { this.query = query; this.itemId = itemId; open = true }
    fun close() { open = false }
}

/** One product's page: picture, the exact product, follow, and where to buy it (shops first, then online). */
@Composable
fun ItemScreen(onAdvice: () -> Unit) {
    val ctx = LocalContext.current
    val id = ItemUi.openId
    val item = id?.let { ListRepo.item(it) }
    AnimatedVisibility(item != null, enter = slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(260)),
        exit = slideOutVertically(tween(240)) { it / 3 } + fadeOut(tween(200))) {
        // Keep showing the last item while the page slides away.
        var shown by remember { mutableStateOf(item) }
        if (item != null) shown = item
        val it = shown ?: return@AnimatedVisibility
        var note by remember(it.id) { mutableStateOf(it.note) }
        val cat = Parse.categoryByKey(it.category)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
                // Header: the product's picture on the pink glow.
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)).background(PinkGlow).statusBarsPadding()) {
                    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { ItemUi.openId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "Tilbake"), tint = Color.White) }
                            Spacer(Modifier.weight(1f))
                            FollowBell(it.searchName, tint = Color.White)
                        }
                        AnimatedContent(it.exact?.image, transitionSpec = { (fadeIn(tween(300)) + scaleIn(tween(300), 0.85f)) togetherWith fadeOut(tween(150)) },
                            label = "pic") { img ->
                            Box(Modifier.size(150.dp).clip(RoundedCornerShape(28.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                if (img != null) AsyncImage(img, null, Modifier.size(140.dp), contentScale = ContentScale.Fit)
                                else Text(cat?.emoji ?: "💄", fontSize = 64.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(it.exact?.name ?: it.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (it.exact != null && !it.exact.name.equals(it.name, true))
                            Text(tr("On your list as \"${it.name}\"", "På listen som «${it.name}»"), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Exact product
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (it.exact == null) tr("Which one exactly?", "Hvilken nøyaktig?") else tr("Exact product", "Eksakt produkt"),
                                    fontWeight = FontWeight.Bold)
                                Text(if (it.exact == null) tr("Pick the exact product, so deals and alerts are for that one.",
                                    "Velg det nøyaktige produktet, så gjelder tilbud og varsler akkurat det.")
                                else "${it.exact.shop} · ${kr(it.exact.price)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(onClick = { PickerUi.show(it.exact?.name ?: it.name, it.id) }) {
                                Icon(Icons.Filled.Search, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                                Text(if (it.exact == null) tr("Find it", "Finn den") else tr("Change", "Bytt"))
                            }
                        }
                    }
                    // Follow
                    val followed = Alerts.isFollowed(it.searchName)
                    Surface(onClick = { Alerts.toggle(ctx, it.searchName) }, shape = RoundedCornerShape(18.dp),
                        color = if (followed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (followed) "🔔" else "🔕", fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (followed) tr("Following deals", "Følger tilbud") else tr("Tell me when it's on sale", "Si fra når den er på tilbud"),
                                    fontWeight = FontWeight.Bold)
                                Text(tr("In shops near you and online (Vita, Lyko, Blush, Boots), checked every 6 hours",
                                    "I butikker nær deg og på nett (Vita, Lyko, Blush, Boots), sjekkes hver 6. time"),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Switch(followed, { _ -> Alerts.toggle(ctx, it.searchName) })
                        }
                    }
                    // Where to buy
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        WhereToBuy(it.searchName, it.name, Modifier.padding(12.dp))
                    }
                    WebPriceLinks(it.searchName)
                    // Shade / note and kind
                    OutlinedTextField(note, { v -> note = v }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(tr("Shade or note", "Nyanse eller notat")) }, placeholder = { Text(tr("e.g. 120 Ivory", "f.eks. 120 Ivory")) })
                    ChoiceChips(Parse.CATEGORIES.map { c -> Opt(c.key, "${c.emoji} ${c.en}", "${c.emoji} ${c.no}") }, setOfNotNull(it.category)) { k ->
                        ListRepo.edit(it.id, it.name, note, if (it.category == k) null else k)
                    }
                    if (note != it.note) Button(onClick = { ListRepo.edit(it.id, it.name, note, it.category) }) { Text(tr("Save note", "Lagre notat")) }
                    OutlinedButton(onClick = {
                        AdviceUi.ask(ctx, Advice.dupes(it.searchName, AdviceUi.onSale()), tr("Cheaper alternatives to ${it.searchName}?", "Billigere alternativer til ${it.searchName}?"))
                        ItemUi.openId = null; onAdvice()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(tr("Find cheaper alternatives (AI)", "Finn billigere alternativer (AI)"))
                    }
                    TextButton(onClick = { ListRepo.remove(it.id); ItemUi.openId = null }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp))
                        Text(tr("Remove from list", "Fjern fra listen"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Search Vita, Lyko, Blush and Boots for the exact product (with pictures), and pick one. */
@Composable
fun ProductPicker() {
    AnimatedVisibility(PickerUi.open, enter = slideInVertically(tween(300)) { it / 4 } + fadeIn(tween(240)),
        exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180))) {
        val scope = rememberCoroutineScope()
        var q by remember { mutableStateOf(PickerUi.query) }
        fun search() { if (q.isNotBlank()) scope.launch { LiveSearch.run(q) } }
        // Searches by itself a moment after you stop typing.
        var first by remember { mutableStateOf(true) }
        LaunchedEffect(q) {
            if (q.trim().length < 2) return@LaunchedEffect
            kotlinx.coroutines.delay(if (first) 0L else 500L)
            first = false
            LiveSearch.run(q)
        }
        val results = LiveSearch.of(q)
        val loading = LiveSearch.isBusy(q)
        fun pick(w: WebPrice) {
            val e = Exact(w.name, w.image, w.url, w.shop, w.price)
            val id = PickerUi.itemId
            if (id != null) ListRepo.setExact(id, e) else ListRepo.addExact(e)
            PickerUi.close()
        }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Find the exact product", "Finn nøyaktig produkt"), Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 20.sp)
                    IconButton(onClick = { PickerUi.close() }) { Icon(Icons.Filled.Close, tr("Close", "Lukk")) }
                }
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
                    shape = RoundedCornerShape(24.dp), leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(tr("Brand and product, e.g. Maybelline Sky High", "Merke og produkt, f.eks. Maybelline Sky High")) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                Text(tr("From Vita, Lyko, Kicks, Blush, Boots, Fredrik & Louisa and Parfym.no. Pick one, and deals and alerts are for exactly that product.",
                    "Fra Vita, Lyko, Kicks, Blush, Boots, Fredrik & Louisa og Parfym.no. Velg ett, så gjelder tilbud og varsler akkurat det produktet."),
                    Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val names = remember(q) { Parse.suggest(q) }.filter { !it.equals(q.trim(), true) }
                if (names.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(names.size) { k ->
                        Surface(onClick = { q = names[k] }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(names[k], Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
                if (loading) Text(tr("Searching ${WebPrices.shopCount} shops – more results come in as each one answers…",
                    "Søker i ${WebPrices.shopCount} butikker – flere treff kommer etter hvert som hver butikk svarer …"),
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val r = results
                when {
                    r.isNullOrEmpty() && loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(5) { Shimmer() } }
                    r == null -> Unit
                    r.isEmpty() -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 40.sp)
                        Text(tr("Not found in ${WebPrices.shopCount} shops. Try the brand and a shorter name.",
                            "Fant den ikke i ${WebPrices.shopCount} butikker. Prøv merket og et kortere navn."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(r, key = { i, p -> "$i|" + p.first.url }) { i, (best, all) ->
                            Appear(i) {
                                Surface(onClick = { pick(best) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                            if (best.image != null) AsyncImage(best.image, null, Modifier.size(60.dp), contentScale = ContentScale.Fit)
                                            else Text(Parse.categoryOf(best.name)?.emoji ?: "💄", fontSize = 26.sp)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(best.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(all.joinToString(" · ") { "${it.shop} ${kr(it.price)}" }, fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(kr(best.price), fontWeight = FontWeight.Black)
                                            best.savingsPct?.takeIf { it in 1..95 }?.let { Tag("−$it %", Sale) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}
