package com.greenguardians.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** `code` is the server's `error` field (e.g. "already_done") or "network". */
class ApiException(val code: String, val status: Int, val detail: String? = null) : Exception(code)

private fun networkError(e: IOException): ApiException {
    android.util.Log.w("ApiClient", "network error", e)
    return ApiException("network", 0, "${e.javaClass.simpleName}: ${e.message}")
}

/** Minimal JSON-over-HTTPS client for the EcoQuest API (see server/README). */
class ApiClient(private val baseUrl: String, private val token: () -> String?) {

    suspend fun call(method: String, path: String, body: JSONObject? = null, timeoutMs: Int = 15_000): JSONObject =
        raw(method, path, body, timeoutMs).let { if (it.isBlank()) JSONObject() else JSONObject(it) }

    suspend fun getArray(path: String): JSONArray = JSONArray(raw("GET", path, null, 15_000))

    suspend fun get(path: String) = call("GET", path)
    suspend fun uploadReceipt(image: ByteArray, count: Int?): JSONObject = withContext(Dispatchers.IO) {
        val path = "/api/receipts" + if (count == null) "" else "?count=$count"
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 100_000
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(image.size)
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("Accept", "application/json")
            token()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.outputStream.use { it.write(image) }
            val status = conn.responseCode
            if (status >= 400) throw httpError(conn, status)
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: IOException) {
            throw networkError(e)
        } finally {
            conn.disconnect()
        }
    }
    suspend fun post(path: String, body: JSONObject = JSONObject(), timeoutMs: Int = 15_000) = call("POST", path, body, timeoutMs)

    /** Server-Sent Events: emits each `data: {...}` JSON as soon as its line arrives. */
    fun stream(path: String, body: JSONObject, timeoutMs: Int = 120_000): Flow<JSONObject> = flow {
        val conn = open("POST", path, body, timeoutMs, "text/event-stream")
        try {
            val status = conn.responseCode
            if (status >= 400) throw httpError(conn, status)
            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("data:")) emit(JSONObject(line.substring(5).trim()))
                }
            }
        } catch (e: IOException) {
            throw networkError(e)
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun raw(method: String, path: String, body: JSONObject?, timeoutMs: Int): String =
        withContext(Dispatchers.IO) {
            val conn = open(method, path, body, timeoutMs, "application/json")
            try {
                val status = conn.responseCode
                if (status >= 400) throw httpError(conn, status)
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                throw networkError(e)
            } finally {
                conn.disconnect()
            }
        }

    /** Opens the connection and sends the body (blocking; call on Dispatchers.IO). */
    private fun open(method: String, path: String, body: JSONObject?, timeoutMs: Int, accept: String): HttpURLConnection {
        try {
            val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("User-Agent", "GreenGuardians-Android/1.0")
            token()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            return conn
        } catch (e: IOException) {
            throw networkError(e)
        }
    }

    private fun httpError(conn: HttpURLConnection, status: Int): ApiException {
        val text = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val code = runCatching { JSONObject(text).getString("error") }.getOrDefault("http_$status")
        android.util.Log.w("ApiClient", "HTTP $status ${conn.requestMethod} ${conn.url.path}: $code")
        return ApiException(code, status)
    }
}
