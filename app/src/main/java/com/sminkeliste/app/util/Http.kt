package com.sminkeliste.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpException(val code: Int, val body: String) : Exception("HTTP $code")

/** Tiny HTTP helper (no extra libraries). Runs on the IO dispatcher. */
object Http {
    private const val UA = "Sminkeliste/1.0 (Android; github.com/ShinobiSondre/sminkeliste)"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 15000): String =
        request("GET", url, headers, null, null, timeoutMs)

    suspend fun post(
        url: String, body: String, contentType: String,
        headers: Map<String, String> = emptyMap(), timeoutMs: Int = 30000
    ): String = request("POST", url, headers, body, contentType, timeoutMs)

    private suspend fun request(
        method: String, url: String, headers: Map<String, String>,
        body: String?, contentType: String?, timeoutMs: Int
    ): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs.coerceAtMost(10000)
            c.readTimeout = timeoutMs
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, text.take(500))
            text
        } finally {
            c.disconnect()
        }
    }
}
