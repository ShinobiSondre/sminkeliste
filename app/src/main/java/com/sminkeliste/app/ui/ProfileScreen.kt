package com.sminkeliste.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sminkeliste.app.data.Profile
import com.sminkeliste.app.util.tr

object ProfileUi { var open by mutableStateOf(false) }

/** "Tell us about your skin": the first screen on a new phone, and later from the advice tab. */
@Composable
fun ProfileScreen(first: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var skin by remember { mutableStateOf(Profile.skin) }
    var tone by remember { mutableStateOf(Profile.tone) }
    var under by remember { mutableStateOf(Profile.undertone) }
    var eyes by remember { mutableStateOf(Profile.eyes) }
    var budget by remember { mutableStateOf(Profile.budget) }
    var concerns by remember { mutableStateOf(Profile.concerns) }
    var wishes by remember { mutableStateOf(Profile.wishes) }
    var age by remember { mutableStateOf(Profile.age) }
    fun one(cur: String?, k: String) = if (cur == k) null else k
    fun many(cur: Set<String>, k: String) = if (k in cur) cur - k else cur + k

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            PinkHeader(if (first) tr("Welcome 💖", "Velkommen 💖") else tr("My skin", "Min hud"),
                tr("So suggestions and shades fit you. Everything stays on your phone.",
                    "Så forslag og nyanser passer deg. Alt blir liggende på telefonen din."))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section(tr("Skin type", "Hudtype")) { ChoiceChips(Profile.SKIN_TYPES, setOfNotNull(skin)) { skin = one(skin, it) } }
                Section(tr("Skin tone", "Hudtone")) {
                    ChoiceChips(Profile.TONES, setOfNotNull(tone)) { tone = one(tone, it) }
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Undertone", "Undertone"), fontSize = 14.sp)
                    Text(tr("Tip: veins look blue → cool, green → warm, both → neutral. Silver suits you → cool, gold → warm.",
                        "Tips: blå årer → kjølig, grønne → varm, begge → nøytral. Sølv kler deg → kjølig, gull → varm."),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    ChoiceChips(Profile.UNDERTONES, setOfNotNull(under)) { under = one(under, it) }
                }
                Section(tr("Skin concerns", "Hudutfordringer")) { ChoiceChips(Profile.CONCERNS, concerns) { concerns = many(concerns, it) } }
                Section(tr("Eye colour", "Øyenfarge")) { ChoiceChips(Profile.EYES, setOfNotNull(eyes)) { eyes = one(eyes, it) } }
                Section(tr("What matters to you", "Hva er viktig for deg")) {
                    ChoiceChips(Profile.WISHES, wishes) { wishes = many(wishes, it) }
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Budget", "Budsjett"), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    ChoiceChips(Profile.BUDGETS, setOf(budget)) { budget = it }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(age, { v -> age = v.filter { it.isDigit() }.take(3) }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(tr("Age (optional)", "Alder (valgfritt)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Button(onClick = {
                    Profile.save(ctx, skin, tone, under, eyes, budget, concerns, wishes, age); onDone()
                }, modifier = Modifier.fillMaxWidth()) { Text(tr("Save", "Lagre")) }
                Row {
                    TextButton(onClick = { if (first) Profile.skip(ctx); onDone() }) {
                        Text(if (first) tr("Skip for now", "Hopp over") else tr("Cancel", "Avbryt"))
                    }
                }
            }
        }
    }
}
