package com.agon.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class BackendMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class BackendChatRequest(
    val provider: String,
    val model: String,
    val apiKey: String,
    val message: String,
    val history: List<BackendMessageDto>,
)

@Serializable
data class BackendChatResponse(
    val content: String? = null,
    val error: String? = null,
    val provider: String? = null,
    val model: String? = null,
)

@Serializable
data class BackendTestRequest(
    val provider: String,
    val model: String,
    val apiKey: String,
)

@Serializable
data class BackendTestResponse(
    val ok: Boolean = false,
    val error: String? = null,
)

class BackendClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun chat(
        baseUrl: String,
        provider: String,
        model: String,
        apiKey: String,
        message: String,
        history: List<ChatMessage>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = BackendChatRequest(
                provider = provider,
                model = model,
                apiKey = apiKey,
                message = message,
                history = history.takeLast(12).map {
                    BackendMessageDto(
                        role = if (it.fromUser) "user" else "assistant",
                        content = it.text,
                    )
                },
            )
            val raw = postJson(baseUrl, "/api/chat", json.encodeToString(request))
            val response = json.decodeFromString<BackendChatResponse>(raw)
            if (!response.error.isNullOrBlank()) error(response.error)
            response.content?.takeIf { it.isNotBlank() } ?: error("Backend returned empty response")
        }
    }

    suspend fun testConnection(
        baseUrl: String,
        provider: String,
        model: String,
        apiKey: String,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val request = BackendTestRequest(provider, model, apiKey)
            val raw = postJson(baseUrl, "/api/provider/test", json.encodeToString(request))
            val response = json.decodeFromString<BackendTestResponse>(raw)
            if (!response.ok) error(response.error ?: "Provider test failed")
            true
        }
    }

    private fun postJson(baseUrl: String, path: String, body: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://") || normalized.startsWith("http://")) {
            "Backend URL must start with https:// or http://"
        }
        val connection = (URL(normalized + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
        connection.disconnect()
        if (status !in 200..299) error("HTTP $status: $response")
        return response
    }
}
