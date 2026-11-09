package com.sminkeliste.app.data

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.lang.ref.WeakReference

/**
 * Asks ChatGPT (chatgpt.com, no login) in an invisible web page behind the app and reads its
 * answer back as plain text, so it can be shown in the app's own answer panel. Follow-up
 * questions are typed into the same conversation.
 */
object ChatGptBridge {
    enum class Status { STARTING, THINKING, DONE, FAILED }
    data class Turn(val user: Boolean, val text: String)

    val turns = MutableStateFlow<List<Turn>>(emptyList())
    val status = MutableStateFlow(Status.STARTING)
    val error = MutableStateFlow<String?>(null)
    /** The conversation's address, to open it visibly if something goes wrong. */
    val pageUrl = MutableStateFlow<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var web: WebView? = null
    private var host: WeakReference<Activity>? = null
    private var poll: Job? = null
    @Volatile private var firstQuestion = ""

    // Reads ChatGPT's answers (two ways, in case the page layout differs), whether it's still
    // writing, and whether our question is still sitting unsent in the input box.
    private const val READ = "(function(){" +
        // Cookie banner (Norway/EU): choose "reject non-essential" (or close it) so it never blocks or gets read.
        // Cookie banner and "log in" pop-ups: reject non-essential cookies / stay logged out.
        "var bs=document.querySelectorAll('button, a, [role=\"button\"]');for(var x=0;x<bs.length;x++){var bt=(bs[x].innerText||'').trim();" +
        "if(bt.length<45&&/(avvis|reject|forbli utlogget|stay logged out|fortsett uten|continue without|ikke nå|not now|maybe later|kanskje senere)/i.test(bt)){bs[x].click();}" +
        // "Clear current chat?" (a new link while a chat is open): yes, start the new one.
        "}" +
        // "Log in or sign up" sheets: close them (the X), we carry on logged out.
        "var dl=document.querySelectorAll('[role=\"dialog\"],dialog[open]');for(var y=0;y<dl.length;y++){var dt=dl[y].innerText||'';" +
        "if(/(log in|sign up|logg inn|registrer)/i.test(dt)&&!/(clear current chat|tøm)/i.test(dt)){var xb=dl[y].querySelector('button[aria-label*=\"lose\"],button[aria-label*=\"ukk\"],[data-testid=\"close-button\"]');if(xb)xb.click();}}" +
        "var a=document.querySelectorAll('[data-message-role=\"assistant\"]');" +
        "if(!a.length)a=document.querySelectorAll('[data-message-author-role=\"assistant\"]');" +
        "if(!a.length)a=document.querySelectorAll('[data-turn=\"assistant\"]');" +
        "var out=[];for(var i=0;i<a.length;i++)out.push(mdOf(a[i])||a[i].innerText);" +
        "if(!out.length){var m=document.querySelectorAll('article .markdown, .markdown.prose');for(var j=0;j<m.length;j++)out.push(m[j].innerText);}" +
        // Last resort: the page text itself – each answer follows a "ChatGPT said:" label.
        "if(!out.length&&document.body){var parts=document.body.innerText.split(/ChatGPT (?:said|sa):/);" +
        "for(var k=1;k<parts.length;k++){var t=parts[k].split(/(?:You said|Du sa):/)[0];" +
        "t=t.split(/(?:ChatGPT is AI and can make mistakes|ChatGPT can make mistakes|ChatGPT kan gjøre feil|ChatGPT er (?:KI|AI)|Chat with ChatGPT|Chat med ChatGPT|Ask anything|Spør om hva som helst|Log in|Sign up|Logg inn|Registrer deg|We use cookies|Vi bruker informasjonskapsler)/)[0];out.push(t.trim());}}" +
        "var busy=!!document.querySelector('[data-testid=\"stop-button\"],[data-message-streaming]');" +
        "var c=document.querySelector('#prompt-textarea')||document.querySelector('textarea');" +
        "var pending=c?((c.innerText||c.value||'').trim().length):0;" +
        "var users=document.querySelectorAll('[data-message-role=\"user\"],[data-message-author-role=\"user\"]').length;" +
        "if(!users&&/\\/(uc|c)\\//.test(location.pathname))users=1;" +
        "var complete=!!document.querySelector('[data-message-role=\"assistant\"][data-message-complete]');" +
        "return JSON.stringify({a:out,busy:busy,complete:complete,pending:pending,users:users,input:!!c,url:location.href,title:document.title," +
        "body:(document.body?document.body.innerText:'').slice(0,240)," +
        "tail:(document.body?document.body.innerText:'').slice(-300)," +
        "dialog:[].slice.call(document.querySelectorAll('[role=\"dialog\"], [aria-modal=\"true\"]')).map(function(e){return (e.innerText||'').replace(/\\s+/g,' ').slice(0,200);}).join(' | ')});})()"
    /**
     * Turns an answer's page elements back into light Markdown, so dish names (headings and bold
     * text) and lists survive. Plain innerText dropped that, and the titles looked like body text.
     */
    private val MD = """
        function mdN(n){
          if(n.nodeType===3)return n.nodeValue;
          if(n.nodeType!==1)return '';
          var t=n.tagName;
          if(t==='IMG'){var u=n.currentSrc||n.getAttribute('src')||'';var w=n.naturalWidth||n.width||0;
            return (u.indexOf('http')===0&&w>=80&&!/avatar|favicon|icon/i.test(u))?'\n![]('+u+')\n':'';}
          if(t==='BUTTON'||t==='svg'||t==='SVG'||t==='STYLE'||t==='SCRIPT'||n.getAttribute('aria-hidden')==='true'||n.hasAttribute('data-message-actions'))return '';
          var s='';for(var i=0;i<n.childNodes.length;i++)s+=mdN(n.childNodes[i]);
          if(/^H[1-6]$/.test(t))return '\n\n### '+s.trim()+'\n';
          if(t==='STRONG'||t==='B')return s.trim()?'**'+s.trim()+'** ':'';
          if(t==='LI')return '\n• '+s.trim();
          if(t==='BR')return '\n';
          if(t==='P'||t==='UL'||t==='OL'||t==='DIV'||t==='SECTION'||t==='TABLE'||t==='TR'||t==='PRE'||t==='BLOCKQUOTE')return '\n'+s+'\n';
          if(t==='TD'||t==='TH')return s+'  ';
          return s;
        }
        function mdOf(el){var s='';for(var i=0;i<el.childNodes.length;i++)s+=mdN(el.childNodes[i]);
          return s.replace(/\*\*\s+\*\*/g,' ').replace(/[ \t]+\n/g,'\n').replace(/\n{3,}/g,'\n\n').trim();}
    """.trimIndent()
    /** [READ] with the Markdown helper inside it. */
    private val READ_MD by lazy { READ.replaceFirst("(function(){", "(function(){$MD\n") }

    // How the page is built (for the diagnostics when no answer is found): which conversation markers
    // exist, the input box, and whether our question appears anywhere.
    private const val PAGE_SHAPE = "(function(){function n(s){return document.querySelectorAll(s).length;}" +
        "var ids={};[].slice.call(document.querySelectorAll('[data-testid]')).forEach(function(e){var k=e.getAttribute('data-testid').replace(/[0-9]+/g,'#');ids[k]=(ids[k]||0)+1;});" +
        "var c=document.querySelector('#prompt-textarea')||document.querySelector('textarea');" +
        "return JSON.stringify({role_user:n('[data-message-author-role=\"user\"]'),role_assistant:n('[data-message-author-role=\"assistant\"]')," +
        "turn:n('[data-turn]'),article:n('article'),markdown:n('.markdown'),composer:c?((c.innerText||c.value||'').length):-1," +
        "url:location.pathname,testids:Object.keys(ids).slice(0,25).join(',')});})()"

    // Sends what's in ChatGPT's input box. Tries every kind of send button ChatGPT has used, then the
    // form's own submit, then a real Enter key (its layout changes now and then).
    private const val SEND = "(function(){var e=document.querySelector('#prompt-textarea')||document.querySelector('textarea');" +
        "if(e){try{e.dispatchEvent(new InputEvent('input',{bubbles:true}));}catch(x){}}" +
        "var sels=['[data-testid=\"send-button\"]','#composer-submit-button','[data-testid=\"composer-send-button\"]'," +
        "'button[aria-label=\"Send prompt\"]','button[aria-label*=\"Send\"]','button[aria-label*=\"send\"]','button[aria-label*=\"Send inn\"]'," +
        "'form button[type=\"submit\"]'];" +
        "for(var i=0;i<sels.length;i++){var b=document.querySelector(sels[i]);if(b&&!b.disabled&&b.getAttribute('aria-disabled')!=='true'){b.click();return 'clicked '+sels[i];}}" +
        "var f=e&&e.closest?e.closest('form'):null;if(f&&f.requestSubmit){try{f.requestSubmit();return 'form';}catch(x){}}" +
        "if(e){try{e.focus();}catch(x){}['keydown','keypress','keyup'].forEach(function(t){e.dispatchEvent(new KeyboardEvent(t,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true}));});return 'enter';}" +
        "return 'none';})()"

    /** Debug: show ChatGPT's hidden page (and make the chat see-through) to watch what it does. */
    val seeThrough = MutableStateFlow(false)
    fun setSeeThrough(on: Boolean) {
        seeThrough.value = on
        (web?.parent as? android.view.View)?.let { place(it, on) }
    }

    /**
     * Where the hidden page sits. Android's WebView doesn't fully honour transparency, so the page
     * showed faintly through the app. It's now moved down until only 2 pixels are left on screen
     * (still "visible" to Android, so ChatGPT keeps writing), unless see-through mode is on.
     */
    private fun place(h: android.view.View, seeThrough: Boolean, hide: Boolean = !seeThrough && !fallback) {
        h.alpha = if (seeThrough) 0.6f else 0.01f
        h.translationY = if (hide) ((h.parent as? android.view.View)?.height ?: h.height).toFloat().let { (it - 2f).coerceAtLeast(0f) } else 0f
    }
    /** Set if ChatGPT stays silent while pushed down: then the page goes back on screen (at 1 %). */
    @Volatile private var fallback = false

    @SuppressLint("SetJavaScriptEnabled")
    private fun webView(a: Activity): WebView? {
        if (web != null && host?.get() === a) return web
        web?.let { w -> (w.parent as? ViewGroup)?.let { h -> (h.parent as? ViewGroup)?.removeView(h); h.removeView(w) }; w.destroy() }
        val root = a.findViewById<ViewGroup>(android.R.id.content) ?: return null
        host = WeakReference(a)
        // On top of the app but practically transparent, and every touch passes straight through to
        // the app underneath. (Behind the app, ChatGPT never drew its streaming answer.)
        val holder = object : FrameLayout(a) {
            override fun dispatchTouchEvent(ev: android.view.MotionEvent?) = false
            override fun onInterceptTouchEvent(ev: android.view.MotionEvent?) = false
        }.apply {
            // WebView draws itself outside its parent's alpha unless the parent has its own layer;
            // without this the "hidden" ChatGPT page showed faintly through the app.
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            alpha = if (seeThrough.value) 0.6f else 0.01f; isClickable = false; isFocusable = false; importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        root.addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        holder.post { place(holder, seeThrough.value) }
        return WebView(a).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = settings.userAgentString.replace("; wv", "")
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            isFocusable = false; isFocusableInTouchMode = false
            holder.addView(this, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }.also { web = it }
    }

    /** Which conversation is open (so reopening the chat continues it instead of asking again). */
    val session = MutableStateFlow<String?>(null)

    /** A photo (JPEG, base64) to attach to the first question, or null. */
    @Volatile private var pendingImage: String? = null

    /**
     * Starts a new conversation about a photo: ChatGPT opens empty, the photo is attached to its input
     * box, and then [prompt] is typed in and sent.
     */
    fun askWithImage(a: Activity, prompt: String, shown: String, jpegBase64: String) {
        session.value = "img:" + jpegBase64.hashCode() + prompt.hashCode()
        firstQuestion = prompt
        val w = webView(a) ?: run { fail("No window"); return }
        turns.value = listOf(Turn(true, shown))
        history = turns.value
        sent.clear(); sent += firstQuestion
        currentQ = prompt
        pendingImage = jpegBase64
        error.value = null
        status.value = Status.THINKING
        pageUrl.value = "https://chatgpt.com/"
        freshLoad(w, "https://chatgpt.com/")
    }

    /** Puts the photo into ChatGPT's file input (or pastes it into the input box if there's no file input). */
    private fun attachJs(b64: String): String =
        "(function(b){try{var bin=atob(b);var u=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);" +
            "var f=new File([u],'bilde.jpg',{type:'image/jpeg'});var dt=new DataTransfer();dt.items.add(f);" +
            "var inp=document.querySelector('input[type=\"file\"][accept*=\"image\"]')||document.querySelector('input[type=\"file\"]');" +
            "if(inp){inp.files=dt.files;inp.dispatchEvent(new Event('change',{bubbles:true}));return 'input';}" +
            "var e=document.querySelector('#prompt-textarea')||document.querySelector('textarea');" +
            "if(e){e.dispatchEvent(new ClipboardEvent('paste',{bubbles:true,cancelable:true,clipboardData:dt}));return 'paste';}" +
            "return 'none';}catch(x){return 'err '+x;}})('" + b64 + "')"

    // Has the photo shown up in the input box (a preview), and is it still uploading?
    private const val ATTACHED = "(function(){var c=document.querySelector('form')||document.body;" +
        "var img=c.querySelector('img[src^=\"blob:\"],img[src^=\"data:\"],[data-testid*=\"attachment\"],[data-testid*=\"file-tile\"]');" +
        "var busy=!!c.querySelector('[role=\"progressbar\"],circle[stroke-dasharray],.animate-spin');" +
        "return JSON.stringify({img:!!img,busy:busy});})()"

    /** Starts a new conversation with [prompt] (the full question), shown in the chat as [shown]. */
    fun ask(a: Activity, prompt: String, shown: String, key: String) {
        pendingImage = null
        if (session.value == key && turns.value.isNotEmpty() && status.value != Status.FAILED) return
        session.value = key
        val url = android.net.Uri.parse("https://chatgpt.com/").buildUpon().appendQueryParameter("q", prompt).build().toString()
        firstQuestion = prompt
        val w = webView(a) ?: run { fail("No window"); return }
        turns.value = listOf(Turn(true, shown))
        history = turns.value
        sent.clear(); sent += firstQuestion
        currentQ = firstQuestion
        error.value = null
        status.value = Status.THINKING
        pageUrl.value = url
        freshLoad(w, url)
    }

    /**
     * Every question is a brand-new, logged-out ChatGPT: cookies and site storage are wiped first,
     * so ChatGPT never counts earlier chats and asks us to log in.
     */
    private fun freshLoad(@Suppress("UNUSED_PARAMETER") w: WebView, url: String) {
        // A brand-new browser for every question: the old page is closed first, and only then are
        // ChatGPT's cookies and storage wiped. Wiping while the old page still ran left the next
        // page hanging, which is why follow-up questions never got an answer.
        poll?.cancel()
        val a = host?.get()
        web?.let { old ->
            old.stopLoading()
            (old.parent as? ViewGroup)?.let { h -> (h.parent as? ViewGroup)?.removeView(h); h.removeView(old) }
            old.destroy()
        }
        web = null
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            val fresh = a?.let { webView(it) }
            if (fresh == null) { fail(com.sminkeliste.app.util.tr("ChatGPT couldn't start. Try again.", "ChatGPT kunne ikke starte. Prøv igjen.")); return@removeAllCookies }
            // (The HTTP cache is kept: ChatGPT's scripts load from it, which saves several seconds.)
            fresh.loadUrl(url)
            watch() // start reading only once the new page is on its way
        }
    }

    /**
     * Opens ChatGPT quietly in the background before you ask (when the dinner screen opens), so its
     * scripts are already loaded and cached. The question itself still goes to a fresh, wiped browser.
     */
    fun prewarm(a: Activity) {
        if (web != null || status.value == Status.THINKING) return
        webView(a)?.loadUrl("https://chatgpt.com/")
    }

    fun reset() { poll?.cancel(); session.value = null; turns.value = emptyList(); status.value = Status.STARTING; error.value = null }

    /** Everything shown before the answer being written now; the new answer is added after it. */
    @Volatile private var history: List<Turn> = emptyList()
    /** What was actually sent to ChatGPT for each question (with transcript etc.). */
    private val sent = mutableListOf<String>()
    @Volatile private var currentQ = ""

    /** Types a follow-up question into the same conversation. */
    /**
     * A follow-up in the same conversation. [shown] is what the panel shows (short), [send] what
     * ChatGPT gets (e.g. with 5 minutes of transcript).
     */
    fun followUp(shown: String, send: String = shown) {
        val w = web ?: return
        // Typing into ChatGPT's page gets blocked by its log-in/cookie pop-ups for logged-out users,
        // so a follow-up goes the same way as the first question: a fresh ChatGPT link that carries
        // the video, the conversation so far and the new question. In the panel it's one chat.
        pendingImage = null
        val q = followUpPrompt(send)
        turns.value = turns.value + Turn(true, shown)
        history = turns.value
        sent += send
        currentQ = q
        status.value = Status.THINKING
        error.value = null
        val url = android.net.Uri.parse("https://chatgpt.com/").buildUpon().appendQueryParameter("q", q).build().toString()
        pageUrl.value = url
        log("ask", "follow-up sent as a new link (${q.length} chars)")
        freshLoad(w, url)
    }

    /** The original question + the conversation so far + the new question, kept short enough for a link. */
    private fun followUpPrompt(send: String): String {
        val no = com.sminkeliste.app.util.Lang.no
        val past = buildString {
            for ((i, t) in turns.value.withIndex()) {
                if (t.user) { if (i > 0) append(if (no) "Spørsmål: " else "Question: ").append(t.text.take(800)).append("\n") }
                else append(if (no) "Ditt svar: " else "Your answer: ").append(t.text.take(1500)).append("\n\n")
            }
        }
        val ask = (if (no) "Nytt spørsmål: " else "New question: ") + send
        val intro = if (no) "Fortsett samtalen under og svar på norsk. Følg de samme reglene for hvordan svaret skal slutte som i det første spørsmålet (de siste linjene)."
            else "Continue the conversation below. Follow the same rules for how the answer must end as in the first question (the last lines)."
        // Keep the start AND the end of the first question: the end holds the rules for the answer's last
        // lines (HANDLE:/BRUKER:), which the app turns into "add to list" and the price check.
        fun first(max: Int) = if (firstQuestion.length <= max) firstQuestion
            else firstQuestion.take(max * 55 / 100) + "\n…\n" + firstQuestion.takeLast(max * 45 / 100)
        var out = first(2800) + "\n\n" + intro + "\n\n" + past + ask
        if (out.length > 6500) out = first(2000) + "\n\n" + intro + "\n\n" + past.takeLast(3000) + ask.take(1500)
        return out
    }

    /** Puts [text] into ChatGPT's input box (no keyboard focus needed) and presses send. */
    private fun typeJs(text: String): String =
        "(function(q){var e=document.querySelector('#prompt-textarea')||document.querySelector('textarea');" +
            "if(!e)return 'noinput';" +
            "if(e.tagName==='TEXTAREA'){var set=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;set.call(e,q);" +
            "e.dispatchEvent(new Event('input',{bubbles:true}));}" +
            "else{try{e.focus();}catch(x){}var ok=false;try{ok=document.execCommand('insertText',false,q);}catch(x){}" +
            "if(!ok||!(e.innerText||'').trim()){var esc=q.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');" +
            "e.innerHTML=esc.split('\\n').map(function(l){return '<p>'+(l||'<br>')+'</p>';}).join('');" +
            "e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:q}));}}" +
            "var tries=0;(function send(){var b=document.querySelector('[data-testid=\"send-button\"]')||document.querySelector('#composer-submit-button');" +
            "if(b&&!b.disabled){b.click();return;}if(++tries<30)setTimeout(send,400);" +
            "else e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,bubbles:true}));})();" +
            "return 'ok';})(${JSONObject.quote(text)})"

    /** Does ChatGPT's latest answer say it needs more context? */
    fun wantsMoreContext(): Boolean = turns.value.lastOrNull()?.takeIf { !it.user }?.text?.let {
        Regex("(?i)(mer kontekst|mer sammenheng|more context|ikke nok (informasjon|kontekst|sammenheng)|uklart|unclear|vanskelig å (si|vite|avgjøre)|hard to (say|tell)|without more|need more|trenger mer)").containsMatchIn(it)
    } == true

    /** Reads the page every ~0.6 s; the answer streams into the panel and is done when it stops growing. */
    private fun watch() {
        val expected = 1 // every question is a fresh ChatGPT page with exactly one answer
        poll?.cancel()
        poll = scope.launch {
            val t0 = System.currentTimeMillis()
            var last = ""
            var stableSince = 0L
            var nudged = 0L
            var nudges = 0
            var typed = false      // the question is typed in at most once (never twice into the chat)
            var snapshot = false   // one look at how the page is built, if no answer shows up
            var attachedAt = 0L    // when the photo was put into ChatGPT's input box
            var attachTries = 0
            while (true) {
                delay(300)
                val w = web ?: return@launch
                val raw = kotlinx.coroutines.suspendCancellableCoroutine<String?> { c -> w.evaluateJavascript(READ_MD) { c.resumeWith(Result.success(it)) } }
                val o = runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }.getOrNull() ?: continue
                o.optString("url").takeIf { it.startsWith("http") }?.let { pageUrl.value = it }
                val answers = o.optJSONArray("a") ?: JSONArray()
                // Every question is a fresh page, so all assistant parts belong to this one answer.
                val text = (if (answers.length() >= expected) (0 until answers.length()).joinToString("\n\n") { answers.optString(it).trim() }.trim() else "").let(::clean)
                if (text.isNotEmpty()) {
                    // Everything before + the answer as it's being written.
                    turns.value = history + Turn(false, text)
                    if (last.isEmpty()) log("ask", "ChatGPT started answering after ${(System.currentTimeMillis() - t0) / 1000.0} s")
                    if (text != last) { last = text; stableSince = System.currentTimeMillis() }
                    val busy = o.optBoolean("busy")
                    // Finished: ChatGPT's stop button is gone and the text hasn't changed for 2.5 s.
                    // Done: ChatGPT marks the answer complete (instant), or the text stops changing for 1.5 s.
                    val complete = o.optBoolean("complete")
                    if (!busy && text.length >= 20 && (complete || System.currentTimeMillis() - stableSince > 1500)) {
                        status.value = Status.DONE
                        log("ask", "ChatGPT answered in ${(System.currentTimeMillis() - t0) / 1000.0} s (${text.length} chars)")
                        return@launch
                    }
                } else {
                    val waited = System.currentTimeMillis() - t0
                    // A photo question: attach the photo, wait until it's uploaded, then type the question.
                    val img = pendingImage
                    if (img != null && !typed) {
                        if (attachedAt == 0L && o.optBoolean("input") && waited > 2_500 && attachTries < 6) {
                            attachTries++
                            val r = kotlinx.coroutines.suspendCancellableCoroutine<String?> { c -> w.evaluateJavascript(attachJs(img)) { c.resumeWith(Result.success(it)) } }
                            log("ask", "attach photo: $r")
                            if (r != null && (r.contains("input") || r.contains("paste"))) attachedAt = System.currentTimeMillis()
                            else delay(1500)
                        } else if (attachedAt > 0L) {
                            val st = kotlinx.coroutines.suspendCancellableCoroutine<String?> { c -> w.evaluateJavascript(ATTACHED) { c.resumeWith(Result.success(it)) } }
                            val j = runCatching { JSONObject(JSONTokener(st).nextValue() as String) }.getOrNull()
                            val ready = j != null && j.optBoolean("img") && !j.optBoolean("busy")
                            val since = System.currentTimeMillis() - attachedAt
                            if ((ready && since > 2_000) || since > 15_000) {
                                typed = true
                                pendingImage = null
                                log("ask", "photo ready=$ready after ${since / 1000.0} s – typing the question")
                                w.evaluateJavascript(typeJs(currentQ), null)
                            }
                        } else if (attachTries >= 6 || waited > 25_000) {
                            fail(com.sminkeliste.app.util.tr(
                                "ChatGPT didn't accept the photo without logging in. Tap Open ChatGPT, log in once, and try again.",
                                "ChatGPT tok ikke imot bildet uten innlogging. Trykk «Åpne ChatGPT», logg inn én gang og prøv igjen."))
                            return@launch
                        }
                        continue
                    }
                    // The question is filled in but wasn't sent (happens when the page isn't on screen): press send.
                    // ChatGPT sends a question from the link by itself (usually within 2 s). Only if it's still
                    // sitting unsent and no conversation started after 10 s, press send (at most twice).
                    if (o.optInt("pending") > 0 && o.optInt("users") == 0 && waited > 10_000 && nudges < 2 &&
                        System.currentTimeMillis() - nudged > 8_000) {
                        nudges++
                        nudged = System.currentTimeMillis()
                        w.evaluateJavascript(SEND) { r -> log("ask", "question was waiting unsent – pressed send ($r)") }
                    } else if (o.optInt("users") == 0 && o.optInt("pending") == 0 && o.optBoolean("input") &&
                        waited > 14_000 && !typed && currentQ.isNotBlank()) {
                        typed = true
                        // The link's question never arrived (e.g. a pop-up was in the way): type it in ourselves.
                        nudged = System.currentTimeMillis()
                        log("ask", "question didn't arrive from the link – typing it in")
                        w.evaluateJavascript(typeJs(currentQ), null)
                    }
                    if (waited > 12_000 && !fallback && !seeThrough.value) {
                        fallback = true
                        (w.parent as? android.view.View)?.let { place(it, false, hide = false) }
                        log("ask", "no answer while the page was pushed down – put it back on screen")
                    }
                    if (waited > 12_000 && !snapshot) {
                        snapshot = true
                        w.evaluateJavascript(PAGE_SHAPE) { r -> log("ask", "page shape: ${runCatching { JSONTokener(r).nextValue() as String }.getOrDefault(r)}") }
                    }
                    if (waited > (if (attachedAt > 0L) 80_000 else 45_000)) {
                        // What's blocking: a pop-up if there is one, else the end of the page (limits / log-in show there).
                        val dialog = o.optString("dialog").trim()
                        val tail = o.optString("tail").replace("\n", " ").replace(Regex("\\s+"), " ").trim().takeLast(220)
                        val seen = dialog.ifBlank { tail }.ifBlank { o.optString("body").replace("\n", " ").take(160) }
                        val limit = Regex("(?i)(limit|too many|for mange|try again later|prøv igjen senere|grense|reached)").containsMatchIn(dialog + " " + tail)
                        log("ask", "ChatGPT gave no answer: $seen")
                        fail(
                            if (limit) com.sminkeliste.app.util.tr(
                                "ChatGPT wants you to log in, or the free limit without an account is reached. Tap Open ChatGPT and log in once – then it answers here again.",
                                "ChatGPT vil at du logger inn, eller grensen uten konto er nådd. Trykk «Åpne ChatGPT» og logg inn én gang – da svarer den her igjen.")
                            // What the page showed goes to the diagnostics log, not on screen.
                            else com.sminkeliste.app.util.tr("ChatGPT didn't answer. Try again in a moment.", "ChatGPT svarte ikke. Prøv igjen om litt.")
                        )
                        return@launch
                    }
                }
            }
        }
    }

    /** Drops page furniture that isn't part of the answer (disclaimers, buttons). */
    private val junk = Regex("(ChatGPT is AI and can make mistakes\\.?|ChatGPT can make mistakes\\.?.*|Check important info\\.?|Chat with ChatGPT|Chat med ChatGPT|ChatGPT er (KI|AI) og kan gjøre feil\\.?.*|ChatGPT kan gjøre feil\\.?.*)", RegexOption.IGNORE_CASE)
    // Whole paragraphs/lines that are page furniture: cookie banners, login prompts, buttons.
    private val junkLine = Regex("(?i)(informasjonskapsl|cookie|Avvis ikke nødvendige|Godta alle|Accept all|Reject non-essential|Administrer innstillingene|Manage (cookie|preferences)|^Logg inn$|^Log in$|^Registrer deg|^Sign up|^Chat (med|with) ChatGPT$|Vi bruker informasjonskapsler|We use cookies)")
    private fun clean(s: String): String = s.replace(junk, "")
        .lines().filterNot { it.isNotBlank() && junkLine.containsMatchIn(it.trim()) }.joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n").trim()

    @Suppress("UNUSED_PARAMETER") private fun log(tag: String, msg: String) = android.util.Log.d("ChatGpt", msg)

    private fun fail(msg: String) { error.value = msg; status.value = Status.FAILED }
}
