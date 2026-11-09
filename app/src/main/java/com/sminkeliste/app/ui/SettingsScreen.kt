package com.sminkeliste.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sminkeliste.app.BuildConfig
import com.sminkeliste.app.data.Profile
import com.sminkeliste.app.data.Settings
import com.sminkeliste.app.update.Updater
import com.sminkeliste.app.util.Lang
import com.sminkeliste.app.util.tr
import com.sminkeliste.app.watch.StoreAlert
import com.sminkeliste.app.watch.StoreWatchService
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val enableWatch = rememberEnableWatch()
    var refresh by remember { mutableIntStateOf(0) }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        if (!it) openAppSettings(ctx)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PinkHeader(tr("Settings", "Innstillinger"), "")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section(tr("My skin", "Min hud")) {
                Text(Profile.summary().ifBlank { tr("Not filled in", "Ikke fylt ut") })
                TextButton(onClick = { ProfileUi.open = true }) { Text(tr("Edit my skin profile", "Endre hudprofilen")) }
            }

            Section(tr("Language", "Språk")) {
                val modes = listOf(Lang.Mode.AUTO to tr("Phone", "Telefon"), Lang.Mode.NO to "Norsk", Lang.Mode.EN to "English")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (m, label) ->
                        SegmentedButton(selected = Lang.mode == m, onClick = { Lang.set(ctx, m); StoreAlert.channels(ctx) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(label) }
                    }
                }
            }

            Section(tr("Shop reminder", "Butikkvarsel")) {
                key(refresh) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Show my list when I'm in a beauty shop", "Vis listen når jeg er i en sminkebutikk"), fontWeight = FontWeight.SemiBold)
                            Text(tr("Vita, Kicks, Normal, Lyko, pharmacies and more. Uses your location and free OpenStreetMap data.",
                                "Vita, Kicks, Normal, Lyko, apotek og flere. Bruker posisjonen din og gratis kartdata fra OpenStreetMap."),
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = WatchState.on, onCheckedChange = { on ->
                            if (on) enableWatch() else { Settings.setWatch(ctx, false); StoreWatchService.stop(ctx); WatchState.refresh(ctx) }
                        })
                    }
                    if (WatchState.on) {
                        Spacer(Modifier.height(8.dp))
                        if (!StoreWatchService.hasBackground(ctx) && Build.VERSION.SDK_INT >= 29) {
                            Text(tr("Tip: choose \"Allow all the time\" so the reminder keeps working after a restart.",
                                "Tips: velg «Tillat hele tiden», så fortsetter varselet å virke etter omstart."), fontSize = 13.sp)
                            TextButton(onClick = { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                                Text(tr("Allow all the time", "Tillat hele tiden"))
                            }
                        }
                        Text(tr("If the reminder stops coming, set Sminkeliste's battery to \"Unrestricted\".",
                            "Hvis varselet slutter å komme, sett batteriet for Sminkeliste til «Ubegrenset»."),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { openBatterySettings(ctx) }) { Text(tr("Battery settings", "Batteriinnstillinger")) }
                    }
                    OutlinedButton(onClick = { StoreAlert.show(ctx, "Vita (test)") }) {
                        Text(tr("Try it: pretend I'm in a shop", "Prøv: lat som jeg er i en butikk"))
                    }
                }
            }

            Section(tr("Deals", "Tilbud")) {
                var km by remember { mutableFloatStateOf(Settings.radiusKm(ctx).toFloat()) }
                Text(tr("Search flyers within ${km.toInt()} km", "Søk i tilbudsaviser innen ${km.toInt()} km"))
                Slider(value = km, onValueChange = { km = it }, valueRange = 25f..100f,
                    onValueChangeFinished = { Settings.setRadiusKm(ctx, km.toInt()) })
            }

            Section(tr("About", "Om appen")) {
                Text(tr("Version ${BuildConfig.VERSION_NAME}", "Versjon ${BuildConfig.VERSION_NAME}"))
                Text(tr("Updates install themselves from GitHub.", "Oppdateringer installeres automatisk fra GitHub."),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { scope.launch { Updater.check(ctx, force = true) } }) { Text(tr("Check for update", "Se etter oppdatering")) }
                Text(tr("Map data © OpenStreetMap contributors. Deals: eTilbudsavis/Tjek. Advice: ChatGPT.",
                    "Kartdata © OpenStreetMap-bidragsytere. Tilbud: eTilbudsavis/Tjek. Råd: ChatGPT."),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
