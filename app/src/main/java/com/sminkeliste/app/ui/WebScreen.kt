package com.sminkeliste.app.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sminkeliste.app.data.ListRepo
import com.sminkeliste.app.util.Http
import com.sminkeliste.app.util.tr

/** A web page shown inside the app (a shop), with a button to put the product on the list. */
object WebUi {
    var url by mutableStateOf<String?>(null); private set
    var title by mutableStateOf(""); private set
    var product by mutableStateOf(""); private set
    fun open(url: String, title: String, product: String = "") { this.url = url; this.title = title; this.product = product }
    fun close() { url = null }


@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen() {
    val ctx = LocalContext.current
    AnimatedVisibility(WebUi.url != null, enter = slideInVertically(tween(300)) { it / 4 } + fadeIn(tween(240)),
        exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180))) {
        val start = remember { WebUi.url.orEmpty() }
        var progress by remember { mutableFloatStateOf(0f) }
        var view by remember { mutableStateOf<WebView?>(null) }
        var added by remember { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { val w = view; if (w != null && w.canGoBack()) w.goBack() else WebUi.close() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "Tilbake"))
                    }
                    Text(WebUi.product.ifBlank { WebUi.title }, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { openUrl(ctx, view?.url ?: start) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, tr("Open in browser", "Åpne i nettleser")) }
                    IconButton(onClick = { WebUi.close() }) { Icon(Icons.Filled.Close, tr("Close", "Lukk")) }
                }
                if (progress < 1f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                AndroidView(factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        webChromeClient = object : android.webkit.WebChromeClient() {
                            override fun onProgressChanged(v: WebView?, p: Int) { progress = p / 100f }
                        }
                        loadUrl(start)
                        view = this
                    }
                }, modifier = Modifier.weight(1f).fillMaxWidth(), onRelease = { it.destroy() })
                if (WebUi.product.isNotBlank()) Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Found it?", "Fant du den?"), Modifier.weight(1f), fontSize = 14.sp)
                    Button(onClick = { ListRepo.add(WebUi.product); added = true }, enabled = !added) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (added) tr("On the list ✓", "På listen ✓") else tr("Put on my list", "Legg på listen"))
                    }
                }
            }
        }
    }
}
