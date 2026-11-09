package com.sminkeliste.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sminkeliste.app.data.Parse
import com.sminkeliste.app.data.WebPrice
import com.sminkeliste.app.data.WebPrices
import com.sminkeliste.app.util.tr
import kotlinx.coroutines.delay

/** Live shop results while typing (they fill in shop by shop), kept so going back to a word is instant. */
object LiveSearch {
    val cache = mutableStateMapOf<String, List<Pair<WebPrice, List<WebPrice>>>>()
    private val busy = mutableStateMapOf<String, Boolean>()
    fun of(q: String) = cache[Parse.searchTerm(q)]
    fun isBusy(q: String) = busy[Parse.searchTerm(q)] == true
    private val done = HashSet<String>()
    suspend fun run(q: String) {
        val k = Parse.searchTerm(q)
        if (k.isBlank() || busy[k] == true || k in done) return
        busy[k] = true
        var finished = false
        try {
            cache[k] = WebPrices.search(q) { sofar -> cache[k] = sofar }
            finished = true
            done += k
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            finished = true; done += k
        } finally {
            busy.remove(k)
            if (!finished) cache.remove(k) // stopped half-way (you kept typing): search again next time
        }
    }
}

/**
 * Autofill under a search box: name suggestions straight away (brands, "brand + kind"), and after a short
 * pause real products from the shops with picture and price.
 */
@Composable
fun SuggestBox(input: String, onText: (String) -> Unit, onProduct: (WebPrice) -> Unit) {
    val names = remember(input) { Parse.suggest(input) }
    LaunchedEffect(input) {
        if (input.trim().length < 3) return@LaunchedEffect
        delay(450) // wait until you stop typing
        LiveSearch.run(input)
    }
    val live = if (input.trim().length >= 3) LiveSearch.of(input) else null
    val searching = input.trim().length >= 3 && LiveSearch.isBusy(input)
    AnimatedVisibility(input.trim().length >= 2, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (names.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(names) { n ->
                    Surface(onClick = { onText(n) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("${Parse.categoryOf(n)?.emoji ?: "✨"} $n", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            val l = live
            when {
                searching && l.isNullOrEmpty() -> Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Shimmer() }
                l != null && l.isNotEmpty() -> Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    l.take(5).forEachIndexed { i, (best, all) ->
                        Appear(i) {
                            Surface(onClick = { onProduct(best) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                        if (best.image != null) AsyncImage(best.image, null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
                                        else Text(Parse.categoryOf(best.name)?.emoji ?: "💄")
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(best.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(all.map { it.shop }.distinct().joinToString(" · "), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(kr(best.price), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
            if (searching) Text(tr("Searching ${WebPrices.shopCount} shops…", "Søker i ${WebPrices.shopCount} butikker …"),
                Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (live?.isEmpty() == true) Text(tr("Not found in ${WebPrices.shopCount} shops. Try the brand and a shorter name.",
                "Fant den ikke i ${WebPrices.shopCount} butikker. Prøv merket og et kortere navn."),
                Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
