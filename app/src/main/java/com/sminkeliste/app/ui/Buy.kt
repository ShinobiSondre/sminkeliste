package com.sminkeliste.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sminkeliste.app.data.Alerts
import com.sminkeliste.app.data.Deals
import com.sminkeliste.app.data.Offer
import com.sminkeliste.app.data.Parse
import com.sminkeliste.app.data.WebPrice
import com.sminkeliste.app.data.WebPrices
import com.sminkeliste.app.util.tr
import kotlinx.coroutines.delay

/** Web shops that also have physical shops in Norway. */
private val ALSO_IN_SHOPS = setOf("Vita", "Boots")

/** One place to buy a product: a shop deal (from the flyers) or an online price. */
sealed class Buy(val price: Double, val prePrice: Double?, val where: String, val image: String?, val title: String) {
    class Shop(val offer: Offer) : Buy(offer.price, offer.prePrice, offer.store, offer.image, offer.heading.lowercase().replaceFirstChar { it.uppercase() })
    class Web(val web: WebPrice) : Buy(web.price, web.prePrice, web.shop, web.image, web.name)
    val savingsPct: Int? get() = prePrice?.takeIf { it > price }?.let { (((it - price) / it) * 100).toInt() }
}

/** Shops first (cheapest first), then online (cheapest first). */
fun buyOptions(shop: List<Offer>, web: List<WebPrice>): List<Buy> =
    shop.sortedBy { it.price }.map { Buy.Shop(it) } + web.sortedBy { it.price }.map { Buy.Web(it) }

/**
 * Where you can buy it: deals in shops near you on top, online prices underneath (with a note when that
 * web shop also has physical shops, and how far the nearest one is). Rows slide in one after another.
 */
@Composable
fun WhereToBuy(searchName: String, itemName: String = searchName, modifier: Modifier = Modifier, max: Int = 8) {
    LaunchedEffect(searchName) { WebPrices.load(searchName) }
    val shop = Deals.forItem(itemName)
    val web = WebPrices.of(searchName)
    val all = buyOptions(shop, web.orEmpty()).take(max)
    val cheapest = all.minByOrNull { it.price }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (shop.isNotEmpty()) Label("🏬 " + tr("In shops near you", "I butikker nær deg"))
        else Text(tr("🏬 Not on sale in shops near you this week", "🏬 Ikke på tilbud i butikker nær deg denne uken"), fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        var webLabel = false
        all.forEachIndexed { i, b ->
            if (b is Buy.Web && !webLabel) { webLabel = true; Spacer(Modifier.height(4.dp)); Label("🌐 " + tr("Online", "På nett")) }
            Appear(i) { BuyRow(b, best = b === cheapest && all.size > 1) }
        }
        when {
            web == null -> { if (!webLabel) { Spacer(Modifier.height(4.dp)); Label("🌐 " + tr("Online", "På nett")) }; Shimmer(); Shimmer() }
            web.isEmpty() -> Text(tr("🌐 Not found at Vita, Lyko, Blush or Boots", "🌐 Fant den ikke hos Vita, Lyko, Blush eller Boots"), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)

/** Slides and fades in, a little after the row above it. */
@Composable
fun Appear(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(60L * index.coerceAtMost(8)); a.animateTo(1f, tween(340)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 24f }) { content() }
}

@Composable
fun BuyRow(b: Buy, best: Boolean = false) {
    val ctx = LocalContext.current
    val near = if (b is Buy.Web) NearShops.list.firstOrNull { Parse.sameChain(it.first.name, b.where) } else null
    Surface(
        onClick = { when (b) { is Buy.Web -> WebUi.open(b.web.url, b.web.shop); is Buy.Shop -> WebUi.open("https://etilbudsavis.no", "eTilbudsavis") } },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = if (best) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                if (b.image != null) AsyncImage(b.image, null, Modifier.size(48.dp), contentScale = ContentScale.Fit)
                else Text(if (b is Buy.Shop) "🏬" else "🌐", fontSize = 20.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(b.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(if (b is Buy.Shop) tr("Shop", "Butikk") else tr("Online", "Nett"), if (b is Buy.Shop) Rose else MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(6.dp))
                    Text(b.where, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    b is Buy.Shop && b.offer.runTill.isNotBlank() -> Text(tr("until ${shortDate(b.offer.runTill)}", "til ${shortDate(b.offer.runTill)}"),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    near != null -> Text(tr("🏬 Also in shops – ${dist(near.second)} away", "🏬 Finnes også i butikk – ${dist(near.second)} unna"),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    b.where in ALSO_IN_SHOPS -> Text(tr("🏬 Also sold in their shops", "🏬 Selges også i butikkene deres"),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (best) Text(tr("Cheapest", "Billigst"), fontSize = 10.sp, fontWeight = FontWeight.Black, color = Sale)
                Text(kr(b.price), fontWeight = FontWeight.Black, fontSize = 15.sp)
                b.prePrice?.takeIf { it > b.price }?.let {
                    Text(kr(it), fontSize = 11.sp, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                b.savingsPct?.takeIf { it in 1..95 }?.let { Tag("−$it %", Sale) }
            }
        }
    }
}

fun dist(m: Double) = if (m < 1000) "${m.toInt()} m" else "${"%.1f".format(m / 1000)} km"

@Composable
fun Tag(t: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(t, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

/** A pulsing placeholder row while prices load. */
@Composable
fun Shimmer() {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-300f, 900f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "x")
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp))
        .background(Brush.linearGradient(listOf(base, Blush.copy(alpha = 0.9f), base),
            start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x + 300f, 0f))))
}

/** The bell: follow deals on this product, in shops and online. Bounces when switched on. */
@Composable
fun FollowBell(name: String, tint: Color = MaterialTheme.colorScheme.primary) {
    val ctx = LocalContext.current
    val on = Alerts.isFollowed(name)
    val s by animateFloatAsState(if (on) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium), label = "bell")
    val t = rememberInfiniteTransition(label = "ring")
    val swing by t.animateFloat(-12f, 12f, infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "swing")
    val ringing = remember { Animatable(0f) }
    LaunchedEffect(on) { if (on) { ringing.snapTo(1f); ringing.animateTo(0f, tween(900)) } }
    IconButton(onClick = { Alerts.toggle(ctx, name) }) {
        Icon(if (on) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
            if (on) tr("Stop deal alerts", "Slå av tilbudsvarsel") else tr("Tell me when it's on sale", "Si fra når det er på tilbud"),
            tint = if (on) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.scale(s).graphicsLayer { rotationZ = swing * ringing.value })
    }
}

/** A round product picture (or the kind's emoji) for list rows. */
@Composable
fun ProductThumb(image: String?, emoji: String, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        if (image != null) AsyncImage(image, null, Modifier.size(size.dp).clip(CircleShape).background(Color.White), contentScale = ContentScale.Fit)
        else Text(emoji, fontSize = (size / 2).sp)
    }
}
