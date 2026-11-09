package com.sminkeliste.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sminkeliste.app.data.Offer
import com.sminkeliste.app.data.Opt
import com.sminkeliste.app.util.Http
import com.sminkeliste.app.util.tr

/** The pink gradient header on top of each tab. */
@Composable
fun PinkHeader(title: String, subtitle: String, trailing: @Composable () -> Unit = {}) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(PinkGlow)
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
            }
            trailing()
        }
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Pick one (or, with [multi], several) of [opts]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(opts: List<Opt>, selected: Set<String>, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        opts.forEach { o ->
            val on = o.key in selected
            FilterChip(
                selected = on, onClick = { onClick(o.key) },
                label = { Text(o.label) },
                leadingIcon = when {
                    o.swatch != null -> { { Box(Modifier.size(18.dp).clip(CircleShape).background(Color(o.swatch ?: 0L))) } }
                    on -> { { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) } }
                    else -> null
                },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** One flyer deal: picture, name, shop, price and discount. */
@Composable
fun OfferRow(o: Offer, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                if (o.image != null) AsyncImage(o.image, null, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                else Text("💄", fontSize = 26.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(o.heading.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (o.description.isNotBlank()) Text(o.description.lines().first(), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(o.store + if (o.runTill.isNotBlank()) tr(" · until ${shortDate(o.runTill)}", " · til ${shortDate(o.runTill)}") else "",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(kr(o.price), fontWeight = FontWeight.Black, fontSize = 16.sp)
                o.prePrice?.let { Text(kr(it), fontSize = 12.sp, textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                o.savingsPct?.takeIf { it in 1..95 }?.let {
                    Surface(shape = RoundedCornerShape(50), color = Sale) {
                        Text("−$it %", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) { body() }
    else Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) { body() }
}

/** Official beauty web shops: (name, search address). Shops whose own search address isn't known go through a site search. */
private fun site(domain: String) = "https://www.google.com/search?q=site%3A$domain+"
val WEB_SHOPS = listOf(
    "Vita" to "https://www.vita.no/search?q=",
    "Kicks" to site("kicks.no"),
    "Lyko" to site("lyko.com/no"),
    "Blush" to "https://www.nordicfeel.com/no/search?q=",
    "Apotek 1" to "https://www.apotek1.no/sok?q=",
    "Boots" to site("boots.no"),
    "Bangerhead" to site("bangerhead.no"),
)

/** "Online prices": Prisjakt (every shop's price in one list) and the official web shops, one tap each. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebPriceLinks(product: String, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(modifier) {
        Text(tr("Compare online prices", "Sammenlign nettpriser"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("Prisjakt" to "https://www.prisjakt.no/search?search=") + WEB_SHOPS).forEach { (name, url) ->
                Surface(onClick = { openUrl(ctx, url + Http.enc(product)) }, shape = RoundedCornerShape(50),
                    color = if (name == "Prisjakt") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Text(name, Modifier.padding(horizontal = 11.dp, vertical = 6.dp), fontSize = 12.sp,
                        fontWeight = if (name == "Prisjakt") FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Opens the price comparison site for a product (every Norwegian web shop's price in one place). */
fun openPriceSearch(ctx: Context, product: String) = openUrl(ctx, "https://www.prisjakt.no/search?search=" + Http.enc(product))

/** Opens the map app at a shop. */
fun openMap(ctx: Context, lat: Double, lng: Double, name: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(" + Uri.encode(name) + ")"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { openUrl(ctx, "https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=18/$lat/$lng") }
}
