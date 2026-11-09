package com.sminkeliste.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sminkeliste.app.data.Deals
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.util.tr
import com.sminkeliste.app.watch.StoreAlert

/** Full-screen "you're in the shop" list: big tick boxes, and what's on sale in this shop. */
@Composable
fun InStoreSplash() {
    val ctx = LocalContext.current
    val store = StoreAlert.splash
    LaunchedEffect(store) {
        if (store != null && ListRepo.open.isNotEmpty() && System.currentTimeMillis() - Deals.fetchedAt > 6 * 3600_000L) {
            runCatching { runDealsCheck(ctx) }
        }
    }
    AnimatedVisibility(store != null, enter = fadeIn() + slideInVertically { it / 6 }, exit = fadeOut() + slideOutVertically { it / 6 }) {
        val name = store ?: ""
        val here = Deals.atStore(name)
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(PinkGlow).statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("You're at", "Du er på"), color = Color.White.copy(alpha = 0.85f))
                            Text("$name 💄", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                            if (here.isNotEmpty()) Text(tr("${here.map { it.first }.distinct().size} on sale here", "${here.map { it.first }.distinct().size} på tilbud her"),
                                color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { StoreAlert.splash = null }) { Icon(Icons.Filled.Close, tr("Close", "Lukk"), tint = Color.White) }
                    }
                }
                LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(12.dp)) {
                    ListRepo.openByList().forEach { (list, its) ->
                        item(key = "l-${list.id}") {
                            Text(list.name, Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        items(its, key = { it.id }) { item ->
                            val deal = here.filter { it.first.equals(item.name, true) }.minByOrNull { it.second.price }?.second
                            Surface(onClick = { ListRepo.toggle(item.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(item.checked, { ListRepo.toggle(item.id) })
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                                            textDecoration = if (item.checked) TextDecoration.LineThrough else null)
                                        if (item.note.isNotBlank()) Text(item.note, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        deal?.let { Text(tr("🏷️ On sale here: ${kr(it.price)}", "🏷️ På tilbud her: ${kr(it.price)}"), color = Sale, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                    if (ListRepo.open.isEmpty()) item {
                        Text(tr("Nothing on your lists right now ✨", "Ingenting på listene akkurat nå ✨"), Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
