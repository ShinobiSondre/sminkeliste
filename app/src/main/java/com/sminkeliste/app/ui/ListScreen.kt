package com.sminkeliste.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sminkeliste.app.data.Advice
import com.sminkeliste.app.data.Alerts
import com.sminkeliste.app.data.Deals
import com.sminkeliste.app.data.Item
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.data.Parse
import com.sminkeliste.app.data.Profile
import com.sminkeliste.app.util.tr

private val QUICK_EN = listOf("Mascara", "Foundation", "Concealer", "Lipstick", "Lip gloss", "Primer", "Powder", "Blush", "Bronzer",
    "Eyeliner", "Eyeshadow palette", "Brow pencil", "Setting spray", "Makeup remover", "Moisturizer", "Nail polish")
private val QUICK_NO = listOf("Mascara", "Foundation", "Concealer", "Leppestift", "Lipgloss", "Primer", "Pudder", "Rouge", "Bronzer",
    "Eyeliner", "Øyenskyggepalett", "Brynsblyant", "Setting spray", "Sminkefjerner", "Dagkrem", "Neglelakk")

@Composable
fun ListScreen(onDeals: () -> Unit, onAdvice: () -> Unit) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var openItem by remember { mutableStateOf<String?>(null) }
    var listMenu by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }
    val cur = ListRepo.items
    val open = cur.filter { !it.checked }
    val done = cur.filter { it.checked }
    fun add() { if (input.isNotBlank()) { ListRepo.add(input); input = "" } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PinkHeader(
                tr("Makeup list", "Sminkeliste"),
                Profile.summary().ifBlank { tr("${ListRepo.open.size} things to get", "${ListRepo.open.size} ting å kjøpe") },
            ) {
                IconButton(onClick = onAdvice) { Icon(Icons.Filled.AutoAwesome, tr("Beauty advice", "Skjønnhetsråd"), tint = Color.White) }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ListRepo.lists, key = { it.id }) { l ->
                    val n = ListRepo.itemsOf(l.id).count { !it.checked }
                    FilterChip(selected = l.id == ListRepo.currentId, onClick = {
                        if (l.id == ListRepo.currentId) listMenu = true else ListRepo.select(l.id)
                    }, label = { Text(if (n > 0) "${l.name} · $n" else l.name) }, shape = RoundedCornerShape(50))
                }
                item {
                    FilterChip(selected = false, onClick = { newList = true }, label = { Text(tr("New list", "Ny liste")) },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) }, shape = RoundedCornerShape(50))
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f),
                    placeholder = { Text(tr("Add makeup, e.g. Maybelline mascara", "Legg til, f.eks. Maybelline mascara")) },
                    singleLine = true, shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { add() }, enabled = input.isNotBlank(), shape = RoundedCornerShape(50), contentPadding = PaddingValues(14.dp)) {
                    Icon(Icons.Filled.Add, tr("Add", "Legg til"))
                }
            }
        }
        item {
            val quick = (if (com.sminkeliste.app.util.Lang.no) QUICK_NO else QUICK_EN).filter { q -> cur.none { it.name.equals(q, true) } }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(quick) { q ->
                    val cat = Parse.categoryOf(q)
                    Surface(onClick = { ListRepo.add(q) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text("${cat?.emoji ?: "✨"} $q", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 13.sp)
                    }
                }
            }
        }
        if (cur.isEmpty()) item { EmptyList() }

        // Open items, grouped by kind (face, eyes, lips …).
        val groups = open.groupBy { it.category }.toList()
            .sortedBy { (k, _) -> Parse.CATEGORIES.indexOfFirst { it.key == k }.let { if (it < 0) 99 else it } }
        groups.forEach { (key, list) ->
            val cat = Parse.categoryByKey(key)
            item(key = "h-$key") {
                Text("${cat?.emoji ?: "✨"}  ${cat?.let { tr(it.en, it.no) } ?: tr("Other", "Annet")}",
                    Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp), fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
            }
            items(list, key = { it.id }) { ItemRow(it, onOpen = { openItem = it.id }) }
        }
        if (open.isNotEmpty()) item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val onSale = open.count { Deals.forItem(it.name).isNotEmpty() }
                if (onSale > 0) FilledTonalButton(onClick = onDeals, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("🏷️ $onSale on sale near you – see deals", "🏷️ $onSale på tilbud nær deg – se tilbud"))
                }
                OutlinedButton(onClick = {
                    AdviceUi.ask(ctx, Advice.checkList(open.map { it.name + if (it.note.isNotBlank()) " (${it.note})" else "" }, AdviceUi.onSale()),
                        tr("Does my list suit me?", "Passer listen min for meg?"))
                    onAdvice()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(tr("Ask AI: does my list suit my skin?", "Spør AI: passer listen for huden min?"))
                }
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done-h") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Bought (${done.size})", "Kjøpt (${done.size})"), Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { ListRepo.clearChecked() }) { Text(tr("Clear", "Fjern")) }
                }
            }
            items(done, key = { it.id }) { ItemRow(it, onOpen = { openItem = it.id }) }
        }
    }

    openItem?.let { id -> ListRepo.item(id)?.let { ItemDialog(it, onClose = { openItem = null }, onAdvice = onAdvice) } ?: run { openItem = null } }
    if (newList) NameDialog(tr("New list", "Ny liste"), "", onClose = { newList = false }) { ListRepo.newList(it) }
    if (listMenu) ListMenu(onClose = { listMenu = false })
}

@Composable
private fun EmptyList() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("💄✨", fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(tr("Your list is empty", "Listen er tom"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(tr("Add what you want or are running low on. You'll see when it's on sale near you.",
            "Legg til det du ønsker deg eller holder på å gå tom for. Du ser når det er på tilbud nær deg."),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ItemRow(item: Item, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val deals = if (item.checked) emptyList() else Deals.forItem(item.name)
    val followed = Alerts.isFollowed(item.name)
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).animateContentSize(),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.checked, { ListRepo.toggle(item.id) },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (item.note.isNotBlank()) Text(item.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!item.checked) {
                    androidx.compose.runtime.LaunchedEffect(item.name) { com.sminkeliste.app.data.WebPrices.load(item.name) }
                    com.sminkeliste.app.data.WebPrices.of(item.name)?.firstOrNull()?.let { w ->
                        Text(tr("🌐 online from ${kr(w.price)} at ${w.shop}", "🌐 nett fra ${kr(w.price)} hos ${w.shop}"),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                deals.minByOrNull { it.price }?.let { best ->
                    Text(tr("🏷️ from ${kr(best.price)} at ${best.store}", "🏷️ fra ${kr(best.price)} hos ${best.store}") +
                        if (deals.size > 1) tr(" · ${deals.size} deals", " · ${deals.size} tilbud") else "",
                        fontSize = 12.sp, color = Sale, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!item.checked) IconButton(onClick = { Alerts.toggle(ctx, item.name) }) {
                Icon(if (followed) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    if (followed) tr("Stop deal alerts", "Slå av tilbudsvarsel") else tr("Tell me when it's on sale", "Si fra når det er på tilbud"),
                    tint = if (followed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ItemDialog(item: Item, onClose: () -> Unit, onAdvice: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember(item.id) { mutableStateOf(item.name) }
    var note by remember(item.id) { mutableStateOf(item.note) }
    var cat by remember(item.id) { mutableStateOf(item.category) }
    val deals = Deals.forItem(item.name)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(tr("Product", "Produkt")) }, singleLine = true)
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text(tr("Shade or note", "Nyanse eller notat")) },
                    placeholder = { Text(tr("e.g. 120 Ivory", "f.eks. 120 Ivory")) }, singleLine = true)
                ChoiceChips(Parse.CATEGORIES.map { com.sminkeliste.app.data.Opt(it.key, "${it.emoji} ${it.en}", "${it.emoji} ${it.no}") },
                    setOfNotNull(cat)) { k -> cat = if (cat == k) null else k }
                HorizontalDivider()
                if (deals.isNotEmpty()) {
                    Text(tr("On sale in shops near you", "På tilbud i butikker nær deg"), fontWeight = FontWeight.Bold)
                    deals.take(4).forEach { OfferRow(it) }
                } else Text(tr("No deals near you this week.", "Ingen tilbud nær deg denne uken."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                OnlinePrices(item.name, Modifier.padding(top = 4.dp))
                TextButton(onClick = {
                    AdviceUi.ask(ctx, Advice.dupes(item.name, AdviceUi.onSale()), tr("Cheaper alternatives to ${item.name}?", "Billigere alternativer til ${item.name}?"))
                    onClose(); onAdvice()
                }) {
                    Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(tr("Find cheaper alternatives (AI)", "Finn billigere alternativer (AI)"))
                }
                TextButton(onClick = { ListRepo.remove(item.id); onClose() }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp))
                    Text(tr("Remove", "Fjern"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(onClick = { ListRepo.edit(item.id, name, note, cat); onClose() }) { Text(tr("Save", "Lagre")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Close", "Lukk")) } },
    )
}

@Composable
private fun ListMenu(onClose: () -> Unit) {
    val cur = ListRepo.current ?: return
    var rename by remember { mutableStateOf(false) }
    if (rename) { NameDialog(tr("Rename list", "Gi nytt navn"), cur.name, onClose = onClose) { ListRepo.renameList(cur.id, it) }; return }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(cur.name) },
        text = {
            Column {
                TextButton(onClick = { rename = true }) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Rename", "Gi nytt navn"))
                }
                if (ListRepo.lists.size > 1) TextButton(onClick = { ListRepo.deleteList(cur.id); onClose() }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp))
                    Text(tr("Delete list", "Slett listen"), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Box { OutlinedTextField(v, { v = it }, Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)) }
        },
        confirmButton = { Button(onClick = { onSave(v); onClose() }, enabled = v.isNotBlank()) { Text(tr("Save", "Lagre")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Cancel", "Avbryt")) } },
    )
}
