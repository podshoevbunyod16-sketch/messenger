package com.agon.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.AttachmentData
import com.agon.app.data.ChatMessage
import com.agon.app.data.NovaMindRepository
import com.agon.app.data.PythonAiClient
import com.agon.app.data.ProviderSettings
import com.agon.app.data.STATUS_CONNECTED
import com.agon.app.data.STATUS_EMPTY
import com.agon.app.data.STATUS_ERROR
import com.agon.app.data.novaProviders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NovaMindViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = NovaMindRepository(appContext)
    private val pythonAiClient = PythonAiClient(appContext)

    private val _settings = MutableStateFlow(ProviderSettings())
    val settings: StateFlow<ProviderSettings> = _settings.asStateFlow()

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = 1L,
                text = "Welcome to NovaMind mobile. Connect a provider in API Keys, pick a model, then use this native chat workspace to compare responses.",
                fromUser = false,
                providerName = "NovaMind",
                modelName = "orchestrator",
            ),
            ChatMessage(
                id = 2L,
                text = "The interface mirrors the web product: dark glass cards, neon accents, provider switching, model chips and a smooth bottom-tab flow.",
                fromUser = false,
                providerName = "NovaMind",
                modelName = "mobile shell",
            ),
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { _settings.value = it }
        }
    }

    fun setBackendUrl(url: String) {
        viewModelScope.launch { repository.setBackendUrl(url) }
    }

    fun setActiveProvider(providerId: String) {
        viewModelScope.launch { repository.setActiveProvider(providerId) }
    }

    fun saveApiKey(providerId: String, key: String) {
        viewModelScope.launch { repository.saveApiKey(providerId, key) }
    }

    fun setModel(providerId: String, model: String) {
        viewModelScope.launch { repository.setModel(providerId, model) }
    }

    fun testProvider(providerId: String) {
        viewModelScope.launch {
            val settingsSnapshot = _settings.value
            val key = settingsSnapshot.apiKeys[providerId].orEmpty()
            val provider = novaProviders.firstOrNull { it.id == providerId } ?: return@launch
            val model = settingsSnapshot.selectedModels[providerId] ?: provider.models.first()
            if (key.isBlank() && providerId != "ollama") {
                repository.setStatus(providerId, STATUS_EMPTY)
                return@launch
            }
            repository.setStatus(providerId, STATUS_EMPTY)
            val modelsResult = pythonAiClient.fetchModels(providerId, key)
            modelsResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { repository.saveFetchedModels(providerId, it) }
            val result = pythonAiClient.testConnection(
                provider = providerId,
                model = modelsResult.getOrNull()?.firstOrNull() ?: model,
                apiKey = key,
            )
            repository.setStatus(providerId, if (result.isSuccess || modelsResult.isSuccess) STATUS_CONNECTED else STATUS_ERROR)
        }
    }

    fun fetchModels(providerId: String) {
        viewModelScope.launch {
            val key = _settings.value.apiKeys[providerId].orEmpty()
            if (key.isBlank()) {
                repository.setStatus(providerId, STATUS_EMPTY)
                return@launch
            }
            val result = pythonAiClient.fetchModels(providerId, key)
            result.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                repository.saveFetchedModels(providerId, it)
                repository.setStatus(providerId, STATUS_CONNECTED)
            } ?: repository.setStatus(providerId, STATUS_ERROR)
        }
    }

    fun clearKeys() {
        viewModelScope.launch { repository.clearAll() }
    }

    fun sendMessage(text: String, attachmentUri: Uri? = null) {
        val clean = text.trim()
        if ((clean.isEmpty() && attachmentUri == null) || _isThinking.value) return
        val settingsSnapshot = _settings.value
        val provider = novaProviders.firstOrNull { it.id == settingsSnapshot.activeProviderId } ?: novaProviders.first()
        val model = settingsSnapshot.selectedModels[provider.id] ?: provider.models.first()
        val apiKey = settingsSnapshot.apiKeys[provider.id].orEmpty()
        val attachment = attachmentUri?.let { readAttachment(appContext, it) }
        val prompt = clean.ifBlank { if (attachment?.isImage == true) "Analyze this image" else "Analyze this file" }
        val nextId = System.currentTimeMillis()
        val historyBeforeUserMessage = _messages.value
        _messages.value = historyBeforeUserMessage + ChatMessage(
            id = nextId,
            text = prompt,
            fromUser = true,
            providerName = provider.name,
            modelName = model,
            imageUri = attachmentUri?.toString()?.takeIf { attachment?.isImage == true },
            attachmentName = attachment?.name,
        )
        _isThinking.value = true
        viewModelScope.launch {
            val connected = settingsSnapshot.statuses[provider.id] == STATUS_CONNECTED
            val response = if (!connected || apiKey.isBlank()) {
                "${provider.name} is not connected yet. Open Settings → API Keys, paste a key, save it and run Test connection."
            } else {
                pythonAiClient.chat(
                    provider = provider.id,
                    model = model,
                    apiKey = apiKey,
                    message = prompt,
                    history = historyBeforeUserMessage,
                    attachment = attachment,
                ).getOrElse { error ->
                    repository.setStatus(provider.id, STATUS_ERROR)
                    "API error: ${error.message ?: "Unknown error"}\n\nThe app now works standalone without Termux/backend. Check provider, model and key."
                }
            }
            _messages.value = _messages.value + ChatMessage(nextId + 1, response, false, provider.name, model)
            _isThinking.value = false
        }
    }

    fun generateImage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _isThinking.value) return
        val settingsSnapshot = _settings.value
        val provider = novaProviders.firstOrNull { it.id == settingsSnapshot.activeProviderId } ?: novaProviders.first()
        val apiKey = settingsSnapshot.apiKeys[provider.id].orEmpty()
        val nextId = System.currentTimeMillis()
        _messages.value = _messages.value + ChatMessage(nextId, "Generate image: $clean", true, provider.name, "image")
        _isThinking.value = true
        viewModelScope.launch {
            val response = if (apiKey.isBlank()) {
                "OpenAI API key is required for image generation. Select OpenAI in Keys, save key, then try again."
            } else {
                pythonAiClient.generateImage(provider.id, apiKey, clean).getOrElse { error ->
                    "Image generation error: ${error.message ?: "Unknown error"}"
                }
            }
            val imageUri = response.takeIf { it.startsWith("http") || it.startsWith("data:image") }
            _messages.value = _messages.value + ChatMessage(nextId + 1, if (imageUri != null) "Generated image" else response, false, provider.name, "image", imageUri = imageUri)
            _isThinking.value = false
        }
    }

    private fun readAttachment(context: Context, uri: Uri): AttachmentData? {
        return runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            } ?: "attachment"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            AttachmentData(name, mime, bytes)
        }.getOrNull()
    }
}
