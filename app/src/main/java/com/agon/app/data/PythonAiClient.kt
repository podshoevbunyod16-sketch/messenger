package com.agon.app.data

import android.util.Base64
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class PythonHistoryMessage(val role: String, val content: String)

@Serializable
private data class PythonModelsResponse(
    val ok: Boolean = false,
    val models: List<String> = emptyList(),
    val error: String? = null,
)

@Serializable
private data class PythonChatResponse(
    val ok: Boolean = false,
    val content: String? = null,
    val image: String? = null,
    val error: String? = null,
)

class PythonAiClient(private val context: android.content.Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }
    }

    private fun module() = Python.getInstance().getModule("ai_client")

    suspend fun fetchModels(provider: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = module().callAttr("fetch_models", provider, apiKey).toString()
            val response = json.decodeFromString<PythonModelsResponse>(raw)
            if (!response.ok) error(response.error ?: "Python model discovery failed")
            response.models
        }
    }

    suspend fun testConnection(provider: String, model: String, apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = module().callAttr("test_connection", provider, model, apiKey).toString()
            val response = json.decodeFromString<PythonChatResponse>(raw)
            if (!response.ok) error(response.error ?: "Python provider test failed")
            true
        }
    }

    suspend fun chat(
        provider: String,
        model: String,
        apiKey: String,
        message: String,
        history: List<ChatMessage>,
        attachment: AttachmentData?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val historyJson = json.encodeToString(
                history.takeLast(12).map {
                    PythonHistoryMessage(if (it.fromUser) "user" else "assistant", it.text)
                }
            )
            val attachmentBase64 = attachment?.let { Base64.encodeToString(it.bytes, Base64.NO_WRAP) }
            val raw = module().callAttr(
                "chat",
                provider,
                model,
                apiKey,
                message,
                historyJson,
                attachment?.name,
                attachment?.mimeType,
                attachmentBase64,
            ).toString()
            val response = json.decodeFromString<PythonChatResponse>(raw)
            if (!response.ok) error(response.error ?: "Python chat failed")
            response.content?.takeIf { it.isNotBlank() } ?: error("Python returned empty response")
        }
    }

    suspend fun generateImage(provider: String, apiKey: String, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = module().callAttr("generate_image", provider, apiKey, prompt).toString()
            val response = json.decodeFromString<PythonChatResponse>(raw)
            if (!response.ok) error(response.error ?: "Python image generation failed")
            response.image?.takeIf { it.isNotBlank() } ?: error("Python returned no image")
        }
    }
}
