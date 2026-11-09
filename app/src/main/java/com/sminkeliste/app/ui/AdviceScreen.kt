package com.sminkeliste.app.ui

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sminkeliste.app.data.Advice
import com.sminkeliste.app.data.Alerts
import com.sminkeliste.app.data.ChatGptBridge
import com.sminkeliste.app.data.Deals
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.data.Opt
import com.sminkeliste.app.data.Parse
import com.sminkeliste.app.data.Pick
import com.sminkeliste.app.data.Profile
import com.sminkeliste.app.util.tr

/** Whether the advice chat is on screen (drawn over the whole app by MainActivity). */
object AdviceUi {
    var open by mutableStateOf(false)

    fun ask(ctx: Context, prompt: String, shown: String) {
        val a = ctx as? Activity ?: return
        ChatGptBridge.reset()
        ChatGptBridge.ask(a, prompt, shown, prompt)
        open = true
    }

    /** A few makeup deals near you, so the AI can point them out. */
    fun onSale(): List<String> = Deals.beauty.take(15).map { "${it.heading.lowercase()} (${it.store}, ${kr(it.price)})" }
}

@Composable
fun AdviceScreen(onEditProfile: () -> Unit) {
    val ctx = LocalContext.current
    val turns by ChatGptBridge.turns.collectAsState()
    LaunchedEffect(Unit) { (ctx as? Activity)?.let { ChatGptBridge.prewarm(it) } }
    var cat by remember { mutableStateOf<String?>("face") }
    var wish by remember { mutableStateOf("") }
    var dupe by remember { mutableStateOf("") }
    var look by remember { mutableStateOf("everyday") }
    var question by remember { mutableStateOf("") }
    val looks = listOf(Opt("everyday", "Everyday", "Hverdag"), Opt("work", "Work", "Jobb"), Opt("party", "Party", "Fest"),
        Opt("date", "Date night", "Date"), Opt("nomakeup", "No-makeup look", "Usminket look"), Opt("summer", "Summer glow", "Sommerglød"))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PinkHeader(tr("Beauty advice", "Skjønnhetsråd"), tr("Tips picked for your skin", "Tips tilpasset huden din"))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section(tr("My skin", "Min hud")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Profile.summary().ifBlank { tr("Tell the app about your skin for better suggestions.", "Fortell appen om huden din for bedre forslag.") },
                        Modifier.weight(1f))
                    TextButton(onClick = onEditProfile) {
                        Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (Profile.isEmpty) tr("Set up", "Fyll ut") else tr("Edit", "Endre"))
                    }
                }
            }
            if (turns.isNotEmpty()) FilledTonalButton(onClick = { AdviceUi.open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Back to the conversation", "Tilbake til samtalen"))
            }
            Section(tr("What suits me?", "Hva passer meg?")) {
                Text(tr("Popular picks for your skin: budget, mid-range and luxury, with shades.",
                    "Populære valg for huden din: billig, mellom og luksus, med nyanseforslag."), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChoiceChips(Parse.CATEGORIES.map { Opt(it.key, "${it.emoji} ${it.en}", "${it.emoji} ${it.no}") }, setOfNotNull(cat)) { cat = it }
                OutlinedTextField(wish, { wish = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                    placeholder = { Text(tr("Wishes, e.g. dewy finish, waterproof", "Ønsker, f.eks. glødende finish, vannfast")) })
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val c = Parse.categoryByKey(cat)
                    AdviceUi.ask(ctx, Advice.recommend(c, wish, AdviceUi.onSale()),
                        tr("What ${c?.en?.lowercase() ?: "makeup"} suits me?", "Hva slags ${c?.no?.lowercase() ?: "sminke"} passer meg?") +
                            if (wish.isNotBlank()) " ($wish)" else "")
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Suggest for me", "Foreslå for meg"))
                }
            }
            Section(tr("Cheaper alternative (dupe)", "Billigere alternativ (dupe)")) {
                OutlinedTextField(dupe, { dupe = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(tr("e.g. Charlotte Tilbury Flawless Filter", "f.eks. Charlotte Tilbury Flawless Filter")) })
                Spacer(Modifier.height(8.dp))
                Button(onClick = { AdviceUi.ask(ctx, Advice.dupes(dupe, AdviceUi.onSale()), tr("Cheaper alternatives to $dupe?", "Billigere alternativer til $dupe?")) },
                    enabled = dupe.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(tr("Find dupes", "Finn dupes")) }
            }
            Section(tr("A routine for me", "En rutine for meg")) {
                ChoiceChips(looks, setOf(look)) { look = it }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    val l = looks.first { it.key == look }
                    AdviceUi.ask(ctx, Advice.routine(l.label, AdviceUi.onSale()), tr("A ${l.en.lowercase()} routine for me", "En ${l.no.lowercase()}-rutine for meg"))
                }, modifier = Modifier.fillMaxWidth()) { Text(tr("Make my routine", "Lag rutinen min")) }
            }
            Section(tr("Ask anything", "Spør om hva som helst")) {
                OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), maxLines = 4,
                    placeholder = { Text(tr("e.g. Which concealer hides dark circles?", "f.eks. Hvilken concealer skjuler mørke ringer?")) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { AdviceUi.ask(ctx, Advice.free(question, AdviceUi.onSale()), question.trim()); question = "" },
                    enabled = question.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(tr("Ask", "Spør")) }
            }
            Text(tr("Answers come from ChatGPT (no login). They're suggestions: test shades in the shop, and check ingredients if your skin reacts easily.",
                "Svarene kommer fra ChatGPT (uten innlogging). De er forslag: test nyansen i butikken, og sjekk ingrediensene hvis huden din reagerer lett."),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private sealed class AdviceEntry(val key: String) {
    class Msg(i: Int, val user: Boolean, val text: String) : AdviceEntry("m$i")
    class Picks(i: Int, val picks: List<Pick>) : AdviceEntry("p$i")
}

/** The app's own chat with ChatGPT: answers stream in, product suggestions become cards. */
@Composable
fun AdviceChat(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val turns by ChatGptBridge.turns.collectAsState()
    val status by ChatGptBridge.status.collectAsState()
    val error by ChatGptBridge.error.collectAsState()
    val pageUrl by ChatGptBridge.pageUrl.collectAsState()
    val seeThrough by ChatGptBridge.seeThrough.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val thinking = status == ChatGptBridge.Status.THINKING
    val shown = if (thinking && turns.lastOrNull()?.user == false) turns.dropLast(1) else turns
    val entries = buildList {
        shown.forEachIndexed { i, t ->
            add(AdviceEntry.Msg(i, t.user, if (t.user) t.text else Parse.display(t.text)))
            if (!t.user && (i != shown.lastIndex || status == ChatGptBridge.Status.DONE)) {
                val p = Parse.picks(t.text)
                if (p.isNotEmpty()) add(AdviceEntry.Picks(i, p))
            }
        }
    }
    LaunchedEffect(shown.size, status) {
        if (entries.isEmpty()) return@LaunchedEffect
        if (thinking) listState.animateScrollToItem(entries.size)
        else if (status == ChatGptBridge.Status.DONE) {
            val idx = entries.indexOfLast { it is AdviceEntry.Msg && !it.user }
            if (idx >= 0) listState.animateScrollToItem(idx)
        }
    }
    fun send(q: String) {
        if (q.isBlank() || thinking) return
        ChatGptBridge.followUp(q.trim()); input = ""
    }

    // Drawn inside the app's own window (not a dialog), so ChatGPT's hidden page keeps writing its answer.
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = if (seeThrough) 0.25f else 1f))
        .pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(PinkGlow), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Beauty advice", "Skjønnhetsråd"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(if (thinking) tr("ChatGPT is writing…", "ChatGPT skriver …") else "ChatGPT", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { ChatGptBridge.setSeeThrough(!seeThrough) }) {
                    Icon(if (seeThrough) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        tr("Show the ChatGPT page behind", "Vis ChatGPT-siden bak"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("Close", "Lukk")) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(Modifier.weight(1f).clipToBounds(), state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(entries, key = { it.key }) { e ->
                    when (e) {
                        is AdviceEntry.Msg -> Bubble(e.text, mine = e.user)
                        is AdviceEntry.Picks -> PickCards(e.picks)
                    }
                }
                if (thinking) item(key = "typing") { Typing() }
                error?.let { e ->
                    item {
                        Column {
                            Text(e, color = MaterialTheme.colorScheme.error)
                            pageUrl?.let { u -> TextButton(onClick = { openUrl(ctx, u) }) {
                                Text(tr("Open ChatGPT", "Åpne ChatGPT")); Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                            } }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            if (status == ChatGptBridge.Status.DONE) {
                val quick = listOf(tr("Cheaper options", "Billigere alternativer"), tr("Which shade for me?", "Hvilken nyanse for meg?"),
                    tr("Vegan options", "Veganske alternativer"), tr("Fragrance-free?", "Parfymefritt?"), tr("How do I apply it?", "Hvordan legger jeg det på?"))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quick) { q ->
                        Surface(onClick = { send(q) }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(q, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f),
                    placeholder = { Text(tr("Ask a follow-up…", "Spør videre …")) },
                    shape = RoundedCornerShape(24.dp), maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) })
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !thinking) {
                    Icon(Icons.AutoMirrored.Filled.Send, tr("Send", "Send"), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** The products ChatGPT suggested, each with "add to list", "follow deals" and "compare prices". */
@Composable
private fun PickCards(picks: List<Pick>) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Suggested products", "Foreslåtte produkter"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        picks.forEach { p ->
            val onList = ListRepo.has(p.name)
            val followed = Alerts.isFollowed(p.name)
            val emoji = (Parse.categoryOf(p.category + " " + p.name))?.emoji ?: "✨"
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            val sub = listOf(p.category, p.price).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.why.isNotBlank()) Text(p.why, fontSize = 13.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { ListRepo.add(p.name) }, enabled = !onList) {
                            Icon(if (onList) Icons.Filled.Check else Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                            Text(if (onList) tr("On list", "På listen") else tr("Add", "Legg til"))
                        }
                        TextButton(onClick = { Alerts.toggle(ctx, p.name) }) {
                            Icon(if (followed) Icons.Filled.Notifications else Icons.Filled.NotificationsNone, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                            Text(if (followed) tr("Following", "Følger") else tr("Follow", "Følg"))
                        }
                        TextButton(onClick = { openPriceSearch(ctx, p.name) }) { Text(tr("Prices", "Priser")) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Typing() {
    val t = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    val y by t.animateFloat(
                        initialValue = 0f, targetValue = 0f,
                        animationSpec = infiniteRepeatable(keyframes {
                            durationMillis = 1200
                            0f at 0 + i * 150
                            -7f at 200 + i * 150 using FastOutSlowInEasing
                            0f at 400 + i * 150
                        }), label = "dot$i"
                    )
                    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse), label = "a$i")
                    Box(Modifier.offset(y = y.dp).size(9.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = a)))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(tr("Finding what suits you…", "Finner det som passer deg …"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = if (mine) maxWidth * 0.85f else maxWidth
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp),
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                tonalElevation = if (mine) 0.dp else 1.dp,
                modifier = Modifier.widthIn(max = maxW)
            ) {
                Text(if (mine) AnnotatedString(text) else markdown(text), Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 16.sp, lineHeight = 23.sp)
            }
        }
    }
}

/** Just enough Markdown for ChatGPT's answers: **bold**, ### headings, bullet lines. */
private fun markdown(s: String): AnnotatedString = buildAnnotatedString {
    s.lines().forEachIndexed { i, raw ->
        if (i > 0) append("\n")
        var line = raw
        val heading = Regex("^\\s*#{1,6}\\s+").find(line)
        if (heading != null) line = line.substring(heading.range.last + 1)
        line = line.replace(Regex("^(\\s*)[-*]\\s+"), "$1• ")
        if (heading != null) { withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) { append(line.replace("**", "")) }; return@forEachIndexed }
        val parts = line.split("**")
        parts.forEachIndexed { j, p -> if (j % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(p) } else append(p) }
    }
}
