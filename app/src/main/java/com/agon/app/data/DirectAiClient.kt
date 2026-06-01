package com.agon.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<DirectMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 1400,
)

@Serializable
private data class DirectMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
    val error: ProviderError? = null,
)

@Serializable
private data class Choice(val message: DirectMessage? = null)

@Serializable
private data class ProviderError(val message: String? = null)

@Serializable
private data class OpenAiImageContent(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrl? = null,
)

@Serializable
private data class ImageUrl(val url: String)

@Serializable
private data class VisionMessage(
    val role: String,
    val content: List<OpenAiImageContent>,
)

@Serializable
private data class VisionRequest(
    val model: String,
    val messages: List<VisionMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 1400,
)

@Serializable
private data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val size: String = "1024x1024",
    val n: Int = 1,
)

@Serializable
private data class ImageGenerationResponse(
    val data: List<GeneratedImage> = emptyList(),
    val error: ProviderError? = null,
)

@Serializable
private data class GeneratedImage(
    val url: String? = null,
    @SerialName("b64_json") val base64Json: String? = null,
)

class DirectAiClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun fetchModels(provider: String, apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = getJson(modelsUrl(provider), authHeaders(provider, apiKey))
            val root = json.parseToJsonElement(raw).jsonObject
            root["data"]?.jsonArray?.mapNotNull { item ->
                item.jsonObject["id"]?.jsonPrimitive?.content
            }?.filter { it.isNotBlank() }?.sorted() ?: emptyList()
        }
    }

    suspend fun testConnection(provider: String, model: String, apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val text = chat(provider, model, apiKey, "Reply only: ok", emptyList(), null).getOrThrow()
            text.isNotBlank()
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
            if (attachment?.isImage == true) {
                return@runCatching vision(provider, model, apiKey, message, attachment)
            }
            val attachmentText = attachment?.let { file ->
                "\n\nAttached file: ${file.name} (${file.mimeType}, ${file.sizeLabel}). Extracted preview:\n${file.bytes.toString(Charsets.UTF_8).take(12_000)}"
            }.orEmpty()
            val messages = buildList {
                add(DirectMessage("system", buildSystemPrompt()))
                history.takeLast(12).forEach {
                    add(DirectMessage(if (it.fromUser) "user" else "assistant", it.text))
                }
                add(DirectMessage("user", message + attachmentText))
            }
            val request = ChatCompletionRequest(model = model, messages = messages)
            val raw = postJson(chatUrl(provider), json.encodeToString(request), authHeaders(provider, apiKey))
            val response = json.decodeFromString<ChatCompletionResponse>(raw)
            response.error?.message?.let { error(it) }
            response.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() } ?: error("Provider returned empty response")
        }
    }

    suspend fun generateImage(provider: String, apiKey: String, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (provider != "openai") {
                error("Image generation in this standalone build uses OpenAI Images API. Switch active provider to OpenAI.")
            }
            val body = ImageGenerationRequest(model = "gpt-image-1", prompt = prompt)
            val raw = postJson("https://api.openai.com/v1/images/generations", json.encodeToString(body), authHeaders("openai", apiKey), readTimeout = 120_000)
            val response = json.decodeFromString<ImageGenerationResponse>(raw)
            response.error?.message?.let { error(it) }
            val image = response.data.firstOrNull() ?: error("No image returned")
            image.url ?: image.base64Json?.let { "data:image/png;base64,$it" } ?: error("No image URL/base64 returned")
        }
    }

    private fun vision(provider: String, model: String, apiKey: String, message: String, image: AttachmentData): String {
        if (provider !in setOf("openai", "openrouter")) {
            error("Image analysis is supported for OpenAI/OpenRouter compatible vision models. Choose OpenAI or OpenRouter and a vision model.")
        }
        val dataUrl = "data:${image.mimeType};base64,${Base64.encodeToString(image.bytes, Base64.NO_WRAP)}"
        val body = VisionRequest(
            model = model,
            messages = listOf(
                VisionMessage(
                    role = "user",
                    content = listOf(
                        OpenAiImageContent(type = "text", text = message.ifBlank { "Analyze this image in detail." }),
                        OpenAiImageContent(type = "image_url", imageUrl = ImageUrl(dataUrl)),
                    ),
                )
            ),
        )
        val raw = postJson(chatUrl(provider), json.encodeToString(body), authHeaders(provider, apiKey), readTimeout = 120_000)
        val response = json.decodeFromString<ChatCompletionResponse>(raw)
        response.error?.message?.let { error(it) }
        return response.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() } ?: error("Vision provider returned empty response")
    }

    private fun buildSystemPrompt(): String = """
        You are NovaMind, a helpful mobile AI assistant.
        If the user asks for code, detect the requested programming language even from aliases, typos, transliteration and mixed casing.
        Examples: питон, пайтон, python, Python, py -> Python; джаваскрипт, js -> JavaScript; си шарп, csharp, c# -> C#.
        You can write code in 50+ languages including Python, JavaScript, TypeScript, Java, Kotlin, Swift, C, C++, C#, Go, Rust, PHP, Ruby, Dart, R, Julia, Lua, Perl, Bash, PowerShell, SQL, HTML, CSS, SCSS, XML, JSON, YAML, Markdown, Scala, Groovy, Haskell, Elixir, Erlang, Clojure, F#, VB.NET, Objective-C, MATLAB, Octave, Assembly, Zig, Nim, Crystal, D, Fortran, COBOL, Pascal, Delphi, Prolog, Lisp, Scheme, Solidity, Vyper, Move, Apex, Visual Basic, Scratch pseudocode and more.
        For code requests, return: 1) detected language, 2) ready-to-run code in a fenced code block, 3) filename/extension, 4) short run instructions.
        If user says something like "give code in python that prints 'hello everyone'", produce the exact minimal program for that language.
        Answer clearly and practically.
    """.trimIndent()

    private fun chatUrl(provider: String): String = when (provider) {
        "openrouter" -> "https://openrouter.ai/api/v1/chat/completions"
        "openai" -> "https://api.openai.com/v1/chat/completions"
        "groq" -> "https://api.groq.com/openai/v1/chat/completions"
        "cerebras" -> "https://api.cerebras.ai/v1/chat/completions"
        "together" -> "https://api.together.xyz/v1/chat/completions"
        "deepseek" -> "https://api.deepseek.com/chat/completions"
        "modelscope" -> "https://api-inference.modelscope.cn/v1/chat/completions"
        else -> error("Direct standalone mode for $provider is not implemented yet. Use OpenRouter/OpenAI/Groq/Cerebras/Together/DeepSeek/ModelScope, or add provider endpoint here.")
    }

    private fun modelsUrl(provider: String): String = when (provider) {
        "openrouter" -> "https://openrouter.ai/api/v1/models"
        "openai" -> "https://api.openai.com/v1/models"
        "groq" -> "https://api.groq.com/openai/v1/models"
        "cerebras" -> "https://api.cerebras.ai/v1/models"
        "together" -> "https://api.together.xyz/v1/models"
        "deepseek" -> "https://api.deepseek.com/models"
        "modelscope" -> "https://api-inference.modelscope.cn/v1/models"
        else -> error("Model discovery is not available for $provider in direct mode yet.")
    }

    private fun authHeaders(provider: String, apiKey: String): Map<String, String> {
        val base = mutableMapOf("Authorization" to "Bearer $apiKey")
        if (provider == "openrouter") {
            base["HTTP-Referer"] = "https://novamind-mobile.local"
            base["X-Title"] = "NovaMind Mobile"
        }
        return base
    }

    private fun getJson(url: String, headers: Map<String, String>, readTimeout: Int = 45_000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 25_000
            this.readTimeout = readTimeout
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
        connection.disconnect()
        if (status !in 200..299) error("HTTP $status: $response")
        return response
    }

    private fun postJson(url: String, body: String, headers: Map<String, String>, readTimeout: Int = 90_000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 25_000
            this.readTimeout = readTimeout
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
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
