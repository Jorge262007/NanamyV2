package com.nanamy.launcher

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import com.nanamy.launcher.voice.NanamyVoiceConfig
import com.nanamy.launcher.localllm.LocalLlmEngine
import kotlinx.coroutines.*
import okhttp3.Cookie as OkCookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.net.URLDecoder

class NanamyOsServer(private val context: Context, port: Int = 8080) : NanoHTTPD(port) {

    private val cookieJar = PersistentCookieJar(context)
    private val client = createUnsafeOkHttpClient()
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private class PersistentCookieJar(context: Context) : CookieJar {
        private val file = File(context.filesDir, "NanamyOS/cookies.json")
        private val cookies = mutableListOf<OkCookie>()

        init {
            if (file.exists()) {
                try {
                    val json = JSONArray(file.readText())
                    for (i in 0 until json.length()) {
                        val obj = json.getJSONObject(i)
                        try {
                            val builder = OkCookie.Builder()
                                .name(obj.getString("name"))
                                .value(obj.getString("value"))
                                .domain(obj.getString("domain"))
                                .path(obj.getString("path"))
                            if (obj.has("expiresAt")) builder.expiresAt(obj.getLong("expiresAt"))
                            if (obj.optBoolean("secure")) builder.secure()
                            if (obj.optBoolean("httpOnly")) builder.httpOnly()
                            cookies.add(builder.build())
                        } catch (e: Exception) {}
                    }
                } catch (e: Exception) {}
            }
        }

        @Synchronized
        private fun save() {
            try {
                val json = JSONArray()
                cookies.forEach { c ->
                    if (c.persistent) {
                        json.put(JSONObject().apply {
                            put("name", c.name)
                            put("value", c.value)
                            put("domain", c.domain)
                            put("path", c.path)
                            put("expiresAt", c.expiresAt)
                            put("secure", c.secure)
                            put("httpOnly", c.httpOnly)
                        })
                    }
                }
                file.parentFile?.mkdirs()
                file.writeText(json.toString())
            } catch (e: Exception) {}
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<OkCookie> {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            return cookies.filter { it.matches(url) }
        }

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<OkCookie>) {
            cookies.forEach { c ->
                this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                this.cookies.add(c)
            }
            save()
        }
    }

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        try {
            val tm = object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
                override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>, a: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
            val ssl = javax.net.ssl.SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf(tm), java.security.SecureRandom())
            return OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, tm)
                .hostnameVerifier { _, _ -> true }
                .cookieJar(cookieJar)
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) { return OkHttpClient() }
    }

    private val sandboxDir = context.filesDir

    init {
        // Ensure system directory exists
        val systemDir = File(sandboxDir, "NanamyOS")
        if (!systemDir.exists()) systemDir.mkdirs()

        // Sync standard Home directories (delegated to FileUtils but kept here for robustness)
        FileUtils.initHomeDirectory(context)

        // Handle index.html update in the system directory
        val indexFile = File(systemDir, "index.html")
        val needsUpdate = if (indexFile.exists()) !indexFile.readText().contains("NanamyOS Version: 2.2.0") else true
        if (needsUpdate) {
            try {
                context.assets.open("nanamyos/index.html").use { i ->
                    indexFile.outputStream().use { o -> i.copyTo(o) }
                }
            } catch (e: Exception) {
                android.util.Log.e("NanamyOS", "Failed to update index.html", e)
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val response = when {
            uri.startsWith("/api/files") -> handleListFiles(session.parameters["path"]?.firstOrNull())
            uri.startsWith("/api/raw") -> handleRawFile(session.parameters["path"]?.firstOrNull())
            uri.startsWith("/api/save") -> handleSaveFile(session)
            uri.startsWith("/api/upload") -> handleUpload(session)
            uri.startsWith("/api/mkdir") -> handleMkdir(session.parameters["path"]?.firstOrNull())
            uri.startsWith("/api/newfile") -> handleNewFile(session.parameters["path"]?.firstOrNull())
            uri.startsWith("/api/move") -> handleMove(session.parameters["src"]?.firstOrNull(), session.parameters["dst"]?.firstOrNull())
            uri.startsWith("/api/copy") -> handleCopy(session.parameters["src"]?.firstOrNull(), session.parameters["dst"]?.firstOrNull())
            uri.startsWith("/api/rename") -> handleRename(session.parameters["path"]?.firstOrNull(), session.parameters["name"]?.firstOrNull())
            uri.startsWith("/api/delete") -> handleDelete(session.parameters["path"]?.firstOrNull())
            uri.startsWith("/api/browser-home") -> handleBrowserHome()
            uri.startsWith("/api/browse") -> handleBrowse(session)
            uri.startsWith("/api/ui/load") -> handleUiLoad()
            uri.startsWith("/api/ui/save") -> handleUiSave(session)
            uri.startsWith("/api/bookmarks/load") -> handleBookmarksLoad()
            uri.startsWith("/api/bookmarks/save") -> handleBookmarksSave(session)
            uri.startsWith("/api/ai/chat") -> handleNanamyAiChat(session)
            uri.startsWith("/api/shutdown") -> { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ stop() }, 500); okResponse() }
            else -> {
                if (session.method == Method.POST) try { session.parseBody(HashMap()) } catch (e: Exception) {}
                val ref = session.headers["referer"]
                if (ref != null && ref.contains("/api/browse/")) {
                    try {
                        val base = URL(ref.substringAfter("/api/browse/"))
                        val qs = if (session.queryParameterString != null) "?" + session.queryParameterString else ""
                        val res = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "")
                        res.addHeader("Location", "/api/browse/${URL(base, uri + qs)}")
                        res
                    } catch (e: Exception) { serveNormalAsset(uri) }
                } else serveNormalAsset(uri)
            }
        }
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun serveNormalAsset(uri: String): Response {
        val f = File(sandboxDir, "NanamyOS/index.html")
        return if ((uri == "/" || uri == "/index.html") && f.exists()) newChunkedResponse(Response.Status.OK, "text/html", FileInputStream(f))
        else serveAsset("nanamyos${if (uri.startsWith("/")) "" else "/"}${if (uri=="/"||uri=="/index.html") "index.html" else uri}")
    }

    private fun serveAsset(p: String): Response = try { newChunkedResponse(Response.Status.OK, getMimeTypeFromFileName(p), context.assets.open(p)) } catch (e: Exception) { newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found") }

    private fun resolvePath(p: String?): File? {
        if (p == null) return sandboxDir
        return try {
            val f = File(sandboxDir, URLDecoder.decode(p, "UTF-8").trimStart('/')).canonicalFile
            if (!f.absolutePath.startsWith(sandboxDir.canonicalFile.absolutePath)) null else f
        } catch (e: Exception) { null }
    }

    private fun handleListFiles(p: String?): Response {
        val d = resolvePath(p) ?: return errorResponse("Invalid")
        val itms = JSONArray()
        d.listFiles()?.forEach { f -> itms.put(JSONObject().put("name", f.name).put("isDir", f.isDirectory).put("size", f.length())) }
        return jsonResponse(JSONObject().put("items", itms))
    }

    private fun handleRawFile(p: String?): Response {
        val f = resolvePath(p) ?: return errorResponse("Invalid")
        return if (f.exists() && !f.isDirectory) newChunkedResponse(Response.Status.OK, getMimeTypeFromFileName(f.name), FileInputStream(f)) else errorResponse("Not Found")
    }

    private fun handleSaveFile(s: IHTTPSession): Response = try {
        val f = resolvePath(s.parameters["path"]?.firstOrNull())
        f?.parentFile?.mkdirs()
        f?.writeText(readPostBody(s))
        okResponse()
    } catch (e: Exception) {
        errorResponse("Error: ${e.message}")
    }

    private fun handleUpload(s: IHTTPSession): Response {
        val m = HashMap<String, String>(); return try { s.parseBody(m); File(m["file"]!!).copyTo(resolvePath(s.parameters["path"]?.firstOrNull())!!, true); okResponse() } catch (e: Exception) { errorResponse("Error") }
    }

    private fun handleMkdir(p: String?): Response = if (resolvePath(p)?.mkdirs() == true) okResponse() else errorResponse("Error")
    private fun handleNewFile(p: String?): Response = if (resolvePath(p)?.createNewFile() == true) okResponse() else errorResponse("Error")
    private fun handleMove(s: String?, d: String?): Response = if (resolvePath(s)?.renameTo(resolvePath(d) ?: File("")) == true) okResponse() else errorResponse("Error")
    private fun handleCopy(s: String?, d: String?): Response = try { resolvePath(s)?.copyRecursively(resolvePath(d)!!, true); okResponse() } catch (e: Exception) { errorResponse("Error") }
    private fun handleRename(p: String?, n: String?): Response {
        val f = resolvePath(p) ?: return errorResponse("Error"); val nf = File(f.parentFile, n ?: "")
        return if (nf.canonicalPath.startsWith(sandboxDir.canonicalPath) && f.renameTo(nf)) okResponse() else errorResponse("Error")
    }
    private fun handleDelete(p: String?): Response = if (resolvePath(p)?.deleteRecursively() == true) okResponse() else errorResponse("Error")

    private fun handleNanamyAiChat(session: IHTTPSession): Response {
        val body = readPostBody(session)
        val json = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
        val userText = json.optString("text", "")
        val customPersona = json.optString("persona", "")

        if (userText.isBlank()) return errorResponse("Empty text")

        return try {
            val response = runBlocking {
                var aiResult = ""
                
                // Try Gemini first if network is available
                if (isNetworkAvailable() && NanamyVoiceConfig.geminiKeys.isNotEmpty()) {
                    try {
                        aiResult = callGeminiDirect(userText, customPersona)
                    } catch (e: Exception) {
                        Log.w("NanamyAI", "Gemini failed: ${e.message}. Falling back to local.")
                        aiResult = "FALLBACK_TRIGGER"
                    }
                }

                // Fallback to local LLM if Gemini failed or was skipped
                if (aiResult.isEmpty() || aiResult == "FALLBACK_TRIGGER") {
                    if (NanamyVoiceConfig.localLlmEnabled) {
                        try {
                            aiResult = callLocalLlmDirect(userText, customPersona)
                        } catch (e: Exception) {
                            aiResult = "Error: Local LLM failed: ${e.message}"
                        }
                    } else if (aiResult == "FALLBACK_TRIGGER") {
                        aiResult = "Error: Gemini failed and Local LLM is disabled."
                    } else {
                        aiResult = "Error: No connection and Local LLM is disabled."
                    }
                }
                
                cleanAiResponse(aiResult)
            }
            jsonResponse(JSONObject().put("response", response))
        } catch (e: Exception) {
            errorResponse(e.message ?: "Chat Error")
        }
    }

    private fun cleanAiResponse(text: String): String {
        return text
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<think>.*", RegexOption.DOT_MATCHES_ALL), "") // Remove unclosed think blocks
            .replace(Regex("<\\|thought\\|>.*?<\\|/thought\\|>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<\\|thought\\|>.*", RegexOption.DOT_MATCHES_ALL), "") // Remove unclosed thought blocks
            .replace(Regex("\\[/?RESPONSE]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[LANG:[a-z]{2}]", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private suspend fun callGeminiDirect(text: String, customPersona: String = ""): String = withContext(Dispatchers.IO) {
        val keys = NanamyVoiceConfig.geminiKeys
        val apiKey = keys.firstOrNull() ?: throw Exception("No Gemini API Key found")
        
        val persona = if (customPersona.isNotBlank()) {
            "${NanamyVoiceConfig.getNanamyAiFullSystemPrompt()}\n\nUser Persona Override:\n$customPersona"
        } else {
            NanamyVoiceConfig.getNanamyAiFullSystemPrompt()
        }

        // Safety settings to avoid unnecessary blocking
        val safetySettings = JSONArray().apply {
            listOf("HATE_SPEECH", "HARASSMENT", "SEXUALLY_EXPLICIT", "DANGEROUS_CONTENT").forEach { cat ->
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_$cat")
                    put("threshold", "BLOCK_NONE")
                })
            }
        }

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            }))
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", persona)))
            })
            put("safetySettings", safetySettings)
            put("generationConfig", JSONObject().apply {
                put("thinkingConfig", JSONObject().apply {
                    put("includeThoughts", false)
                    put("thinkingLevel", "minimal")
                })
            })
        }

        // IMPORTANT: DO NOT CHANGE THIS MODEL. gemini-3.5-flash-lite is the correct and working version.
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey")
            .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                Log.e("NanamyAI", "Gemini API Error: ${resp.code} $body")
                throw Exception("HTTP ${resp.code}")
            }
            
            val jsonRes = JSONObject(body)
            val candidates = jsonRes.optJSONArray("candidates")
            
            if (candidates == null || candidates.length() == 0) {
                // Log full response for debugging if it fails
                Log.w("NanamyAI", "Gemini returned no candidates. Full body: $body")
                throw Exception("No candidates returned (possibly filtered)")
            }

            candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    private suspend fun callLocalLlmDirect(text: String, customPersona: String = ""): String = withContext(Dispatchers.Default) {
        val engine = LocalLlmEngine.getInstance()
        if (!engine.isLoaded()) {
            val success = engine.loadModel(context)
            if (!success) return@withContext "Error: Failed to load local model"
        }
        
        val persona = if (customPersona.isNotBlank()) {
            "${NanamyVoiceConfig.getNanamyAiFullSystemPrompt()}\n\nUser Persona Override:\n$customPersona"
        } else {
            NanamyVoiceConfig.getNanamyAiFullSystemPrompt()
        }

        val localPrompt = "<|im_start|>system\n$persona<|im_end|>\n<|im_start|>user\n$text<|im_end|>\n<|im_start|>assistant\n"
        engine.generate(localPrompt).substringBefore("<|im_end|>").trim()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun handleBrowserHome(): Response {
        val html = """<!DOCTYPE html><html><head><meta charset="UTF-8"><style>:root{--bg:#0b0e14;--text:#e0e0e0;--accent:#00e5ff;--surface:#161b22;--border:rgba(255,255,255,0.08);--font-ui:'Segoe UI',sans-serif}body{margin:0;background:var(--bg);color:var(--text);font-family:var(--font-ui);display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;overflow:hidden}.logo{font-size:48px;font-weight:800;margin-bottom:24px;background:linear-gradient(135deg,#fff 30%,var(--accent) 100%);-webkit-background-clip:text;-webkit-text-fill-color:transparent;filter:drop-shadow(0 4px 12px rgba(0,229,255,0.3))}.search-container{width:90%;max-width:580px}input{width:100%;padding:16px 24px;background:var(--surface);border:1px solid var(--border);border-radius:14px;color:#fff;font-size:16px;outline:none;box-sizing:border-box}</style></head><body><div class="logo">NanamyOS</div><div class="search-container"><form action="/api/browse/https://search.brave.com/search" method="GET"><input type="text" name="q" placeholder="Search with Brave..." autofocus autocomplete="off"></form></div></body></html>""".trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun handleBrowse(session: IHTTPSession): Response {
        val fullUri = session.uri
        var targetUrl = if (fullUri.startsWith("/api/browse/")) fullUri.substringAfter("/api/browse/") else session.parameters["url"]?.firstOrNull()
        if (targetUrl == null || targetUrl.isEmpty()) return errorResponse("No URL")
        
        // Fix double slash issues from reconstruction
        if (targetUrl.startsWith("http:/") && !targetUrl.startsWith("http://")) targetUrl = targetUrl.replaceFirst("http:/", "http://")
        else if (targetUrl.startsWith("https:/") && !targetUrl.startsWith("https://")) targetUrl = targetUrl.replaceFirst("https:/", "https://")
        
        val qs = session.queryParameterString
        if (qs != null && qs.isNotEmpty() && !targetUrl.contains("?")) targetUrl += if (targetUrl.contains("?")) "&$qs" else "?$qs"

        return try {
            val method = session.method.toString()
            val targetHost = try { URL(targetUrl).host } catch(e: Exception) { "" }
            
            val rb = Request.Builder().url(targetUrl)
            
            // Forward headers from PC browser to Target Website
            val headersToForward = listOf(
                "user-agent", "accept", "accept-language", "cookie", "content-type",
                "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform",
                "sec-fetch-dest", "sec-fetch-mode", "sec-fetch-site", "sec-fetch-user",
                "upgrade-insecure-requests"
            )
            session.headers.forEach { (k, v) ->
                val key = k.lowercase()
                if (headersToForward.contains(key)) {
                    rb.header(k, v)
                }
            }
            
            // Spoof Referer and Origin to avoid CORS/Security blocks
            if (targetHost.isNotEmpty()) {
                rb.header("Referer", "https://$targetHost/")
                rb.header("Origin", "https://$targetHost")
            }

            if (method == "POST" || method == "PUT") {
                val ct = session.headers["content-type"] ?: "application/x-www-form-urlencoded"
                rb.method(method, readPostBodyRaw(session).toRequestBody(ct.toMediaTypeOrNull()))
            }
            
            client.newCall(rb.build()).execute().use { resp ->
                val body = resp.body
                val ct = resp.header("Content-Type") ?: "text/html"
                val bytes = body?.bytes() ?: byteArrayOf()
                
                val nRes = if (ct.contains("text/html")) {
                    val html = injectSandboxScript(rewriteUrls(String(bytes, Charsets.UTF_8), targetUrl), targetUrl)
                    newFixedLengthResponse(Response.Status.lookup(resp.code), ct, html)
                } else {
                    newFixedLengthResponse(Response.Status.lookup(resp.code), ct, bytes.inputStream(), bytes.size.toLong())
                }

                // Strip headers that block iframing or script injection
                val headersToStrip = listOf("content-security-policy", "x-frame-options", "content-security-policy-report-only")
                resp.headers.forEach { (k, v) ->
                    val key = k.lowercase()
                    if (!headersToStrip.contains(key) && key != "set-cookie" && key != "location" && key != "content-type") {
                        nRes.addHeader(k, v)
                    }
                }
                
                // Proxy Set-Cookie headers back to the PC browser
                resp.headers("Set-Cookie").forEach { c ->
                    // Remove Domain, Secure and SameSite so the browser accepts them on local IP (http://192.168...)
                    val clean = c.split(";")
                        .filter { p -> 
                            val l = p.trim().lowercase()
                            !l.startsWith("domain=") && !l.startsWith("secure") && !l.startsWith("samesite=") 
                        }
                        .joinToString("; ")
                    nRes.addHeader("Set-Cookie", clean)
                }
                
                // Handle Redirects manually to keep them inside the proxy
                if (resp.code in 300..399) {
                    val loc = resp.header("Location")
                    if (loc != null) {
                        val newLoc = if (loc.startsWith("http")) "/api/browse/$loc" else loc
                        nRes.setStatus(Response.Status.REDIRECT)
                        nRes.addHeader("Location", newLoc)
                    }
                }
                
                nRes
            }
        } catch (e: Exception) { 
            android.util.Log.e("NanamyProxy", "Error browsing $targetUrl", e)
            errorResponse(e.message ?: "Proxy Error") 
        }
    }

    private fun rewriteUrls(content: String, base: String): String {
        val b = try { URL(base) } catch (e: Exception) { return content }
        val pb = "/api/browse/"
        val p = Regex("""(href|src|action|target)\s*=\s*(['"])([^'"]+)(['"])""", RegexOption.IGNORE_CASE)
        return p.replace(content) { m ->
            val a = m.groupValues[1].lowercase(); val q = m.groupValues[2]; val u = m.groupValues[3]
            if (a == "target") "$a=${q}_self$q"
            else if (u.startsWith("data:") || u.startsWith("javascript:") || u.startsWith("#")) m.value
            else "$a=$q$pb${try { URL(b, u).toString() } catch (e: Exception) { u }}$q"
        }
    }

    private fun injectSandboxScript(html: String, @Suppress("UNUSED_PARAMETER") currentUrl: String): String {
        val s = """<script>(function(){
            // Mask automation
            try {
                Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
                if (!window.chrome) window.chrome = { runtime: {} };
                if (!navigator.languages) Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
                if (!navigator.plugins || navigator.plugins.length === 0) {
                    Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3] });
                }
            } catch(e) {}

            // Windowed Fullscreen for Online Players
            try {
                const triggerFS = (s) => {
                    if (window.parent && window.parent !== window) {
                        window.parent.postMessage({ type: 'browser-fs', state: s }, '*');
                    }
                };

                const style = document.createElement('style');
                style.id = 'nanamy-fs-style';
                style.innerHTML = `
                    .nanamy-fs-el { 
                        position: fixed !important; top: 0 !important; left: 0 !important; 
                        width: 100vw !important; height: 100vh !important; 
                        z-index: 2147483647 !important; background: #000 !important; 
                        object-fit: contain !important; margin: 0 !important; padding: 0 !important;
                    }
                    body.nanamy-stop-scroll { overflow: hidden !important; }
                `;
                document.documentElement.appendChild(style);

                const mockFS = function() { 
                    document.querySelectorAll('.nanamy-fs-el').forEach(el => el.classList.remove('nanamy-fs-el'));
                    this.classList.add('nanamy-fs-el');
                    document.body.classList.add('nanamy-stop-scroll');
                    triggerFS(true); 
                    return Promise.resolve(); 
                };
                const mockExit = function() { 
                    document.querySelectorAll('.nanamy-fs-el').forEach(el => el.classList.remove('nanamy-fs-el'));
                    document.body.classList.remove('nanamy-stop-scroll');
                    triggerFS(false); 
                    return Promise.resolve(); 
                };
                
                Element.prototype.requestFullscreen = mockFS;
                Element.prototype.webkitRequestFullscreen = mockFS;
                Element.prototype.mozRequestFullScreen = mockFS;
                Element.prototype.msRequestFullscreen = mockFS;
                
                document.exitFullscreen = mockExit;
                document.webkitExitFullscreen = mockExit;
                document.mozCancelFullScreen = mockExit;
                document.msExitFullscreen = mockExit;
            } catch(e) {}

            // Push URL changes to parent (Manual tracking + Click tracking)
            try {
                const notifyParent = (manualUrl) => {
                    if (window.parent && window.parent !== window) {
                        const url = manualUrl || window.location.href;
                        window.parent.postMessage({ type: 'browser-url', url: url }, '*');
                    }
                };
                
                // Track link clicks (Your suggestion)
                document.addEventListener('click', function(e) {
                    var el = e.target;
                    while(el && el.tagName !== 'A') el = el.parentElement;
                    if(el && el.tagName === 'A') {
                        notifyParent(el.href);
                    }
                }, true);

                // Track history changes
                const op = history.pushState;
                history.pushState = function() { op.apply(this, arguments); notifyParent(); };
                const or = history.replaceState;
                history.replaceState = function() { or.apply(this, arguments); notifyParent(); };
                window.addEventListener('popstate', notifyParent);
                
                setInterval(notifyParent, 2000);
            } catch(e) {}

            const ar=['.adsbygoogle','[class*="ad-slot"]','iframe[src*="googleads"]','iframe[src*="doubleclick"]'];
            function c(){ar.forEach(sel=>{document.querySelectorAll(sel).forEach(el=>{el.style.display='none';el.remove()})})}
            setInterval(c,2000);c();
            try{var m={_d:{},setItem:function(k,v){this._d[k]=String(v)},getItem:function(k){return this._d[k]||null},removeItem:function(k){delete this._d[k]},clear:function(){this._d={}},key:function(i){return Object.keys(this._d)[i]},get length(){return Object.keys(this._d).length}};if(!window.localStorage)window.localStorage=m;if(!window.sessionStorage)window.sessionStorage=m}catch(e){}
            window.open=function(u){if(u)window.location.href=(u.startsWith('http')?'/api/browse/':'')+u;return null};
            document.addEventListener('click',function(e){var el=e.target;while(el&&el.tagName!=='A')el=el.parentElement;if(el&&el.tagName==='A')el.target='_self'},true)
        })();</script>"""
        return if (html.contains("<head>", true)) html.replaceFirst("<head>", "<head>$s", true) else s + html
    }

    private fun readPostBodyRaw(session: IHTTPSession): ByteArray {
        val m = HashMap<String, String>(); try { session.parseBody(m) } catch (e: Exception) {}
        val d = m["postData"] ?: return byteArrayOf()
        val f = File(d); return if (f.exists() && f.isFile) f.readBytes() else d.toByteArray()
    }

    private fun handleUiLoad(): Response {
        val f = File(context.filesDir, "NanamyOS/nanamyos_ui_state.json")
        return if (f.exists()) newFixedLengthResponse(Response.Status.OK, "application/json", f.readText()) else jsonResponse(JSONObject())
    }

    private fun handleUiSave(session: IHTTPSession): Response = try { File(context.filesDir, "NanamyOS/nanamyos_ui_state.json").writeText(readPostBody(session)); okResponse() } catch (e: Exception) { errorResponse("Error") }

    private fun handleBookmarksLoad(): Response {
        val f = File(context.filesDir, "NanamyOS/bookmarks.json")
        return if (f.exists()) newFixedLengthResponse(Response.Status.OK, "application/json", f.readText()) 
        else jsonResponse(JSONObject().put("bookmarks", JSONArray()))
    }

    private fun handleBookmarksSave(session: IHTTPSession): Response = try { 
        File(context.filesDir, "NanamyOS/bookmarks.json").writeText(readPostBody(session))
        okResponse() 
    } catch (e: Exception) { errorResponse("Error") }

    private fun readPostBody(session: IHTTPSession): String {
        val m = HashMap<String, String>(); session.parseBody(m); val d = m["postData"] ?: return ""
        val f = File(d); return if (f.exists() && f.isFile) f.readText() else d
    }

    private fun jsonResponse(j: JSONObject): Response = newFixedLengthResponse(Response.Status.OK, "application/json", j.toString())
    private fun okResponse(): Response = newFixedLengthResponse(Response.Status.OK, "text/plain", "OK")
    private fun errorResponse(m: String): Response = newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", m)

    private fun getMimeTypeFromFileName(f: String): String {
        val e = f.substringAfterLast('.', "").lowercase()
        return when (e) {
            "html", "htm" -> "text/html"
            "js" -> "application/javascript"
            "css" -> "text/css"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "lex" -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
