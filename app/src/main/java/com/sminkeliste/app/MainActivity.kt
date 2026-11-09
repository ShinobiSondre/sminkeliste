package com.sminkeliste.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sminkeliste.app.data.Alerts
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.data.Profile
import com.sminkeliste.app.data.Settings
import com.sminkeliste.app.ui.AdviceChat
import com.sminkeliste.app.ui.AdviceScreen
import com.sminkeliste.app.ui.AdviceUi
import com.sminkeliste.app.ui.DealsScreen
import com.sminkeliste.app.ui.InStoreSplash
import com.sminkeliste.app.ui.ItemScreen
import com.sminkeliste.app.ui.ItemUi
import com.sminkeliste.app.ui.PickerUi
import com.sminkeliste.app.ui.ProductPicker
import com.sminkeliste.app.ui.ListScreen
import com.sminkeliste.app.ui.ProfileScreen
import com.sminkeliste.app.ui.ProfileUi
import com.sminkeliste.app.ui.SettingsScreen
import com.sminkeliste.app.ui.SminkeTheme
import com.sminkeliste.app.ui.WatchState
import com.sminkeliste.app.ui.runDealsCheck
import com.sminkeliste.app.update.UpdateOverlay
import com.sminkeliste.app.update.Updater
import com.sminkeliste.app.util.Lang
import com.sminkeliste.app.util.tr
import com.sminkeliste.app.watch.StoreAlert
import com.sminkeliste.app.watch.StoreWatchService
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val openTab = mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Lang.init(this)
        Profile.init(this)
        ListRepo.init(this)
        Alerts.init(this)
        StoreAlert.channels(this)
        enableEdgeToEdge()
        handle(intent)
        Updater.start(this)
        Alerts.schedule(this)
        setContent {
            SminkeTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                LaunchedEffect(openTab.intValue) { if (openTab.intValue >= 0) { tab = openTab.intValue; openTab.intValue = -1 } }
                // Deals are checked by themselves: when the app opens and a moment after the lists change.
                val openNames = ListRepo.open.map { it.name }.sorted().joinToString("|")
                LaunchedEffect(openNames) { delay(1200); runDealsCheck(this@MainActivity) }
                BackHandler(enabled = tab != 0 || StoreAlert.splash != null || AdviceUi.open || ProfileUi.open || PickerUi.open || ItemUi.openId != null) {
                    when {
                        ProfileUi.open -> ProfileUi.open = false
                        PickerUi.open -> PickerUi.close()
                        ItemUi.openId != null -> ItemUi.openId = null
                        AdviceUi.open -> AdviceUi.open = false
                        StoreAlert.splash != null -> StoreAlert.splash = null
                        else -> tab = 0
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                val tabs = listOf(
                                    Triple(Icons.AutoMirrored.Filled.List, tr("List", "Liste"), 0),
                                    Triple(Icons.Filled.LocalOffer, tr("Deals", "Tilbud"), 1),
                                    Triple(Icons.Filled.AutoAwesome, tr("Advice", "Råd"), 2),
                                    Triple(Icons.Filled.Settings, tr("Settings", "Innstillinger"), 3),
                                )
                                tabs.forEach { (icon, label, i) ->
                                    NavigationBarItem(selected = tab == i, onClick = { tab = i },
                                        icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                                }
                            }
                        }
                    ) { pad ->
                        Box(Modifier.padding(pad)) {
                            AnimatedContent(tab, transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260))) togetherWith
                                    (slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160)))
                            }, label = "tabs") { shown ->
                                when (shown) {
                                    0 -> ListScreen(onDeals = { tab = 1 }, onAdvice = { tab = 2 })
                                    1 -> DealsScreen()
                                    2 -> AdviceScreen(onEditProfile = { ProfileUi.open = true })
                                    else -> SettingsScreen()
                                }
                            }
                        }
                    }
                    if (AdviceUi.open) AdviceChat(onClose = { AdviceUi.open = false })
                    ItemScreen(onAdvice = { tab = 2 })
                    ProductPicker()
                    InStoreSplash()
                    if (!Profile.done) ProfileScreen(first = true, onDone = { })
                    else if (ProfileUi.open) ProfileScreen(first = false, onDone = { ProfileUi.open = false })
                    UpdateOverlay()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Updater.onResume(this)
        WatchState.refresh(this)
        if (Settings.watchOn(this) && StoreWatchService.hasLocation(this)) StoreWatchService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(StoreAlert.EXTRA_STORE)?.let { StoreAlert.splash = it }
        intent?.getIntExtra(Alerts.EXTRA_TAB, -1)?.takeIf { it >= 0 }?.let { openTab.intValue = it }
    }
}
