package com.agon.app.data

import androidx.annotation.DrawableRes

const val STATUS_EMPTY = "empty"
const val STATUS_CONNECTED = "connected"
const val STATUS_ERROR = "error"

data class AiProvider(
    val id: String,
    val name: String,
    val shortName: String,
    val description: String,
    val accent: Long,
    val models: List<String>,
    val freeTier: String,
)

data class ChatMessage(
    val id: Long,
    val text: String,
    val fromUser: Boolean,
    val providerName: String,
    val modelName: String,
    val imageUri: String? = null,
    val attachmentName: String? = null,
)

data class AttachmentData(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val sizeLabel: String get() = when {
        bytes.size >= 1024 * 1024 -> "${bytes.size / (1024 * 1024)} MB"
        bytes.size >= 1024 -> "${bytes.size / 1024} KB"
        else -> "${bytes.size} B"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttachmentData) return false
        return name == other.name && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

val novaProviders = listOf(
    AiProvider(
        id = "openrouter",
        name = "OpenRouter",
        shortName = "OR",
        description = "Unified router for dozens of frontier and community models.",
        accent = 0xFF7C5CFF,
        models = listOf("openrouter/auto", "meta-llama/llama-3.3-70b", "google/gemini-flash-1.5", "mistralai/mixtral-8x7b"),
        freeTier = "Many free/community routes",
    ),
    AiProvider(
        id = "openai",
        name = "OpenAI",
        shortName = "AI",
        description = "Fast general intelligence, coding, writing and reasoning models.",
        accent = 0xFF12B981,
        models = listOf("gpt-4.1", "gpt-4o", "gpt-4o-mini", "o4-mini"),
        freeTier = "Paid API / credits",
    ),
    AiProvider(
        id = "claude",
        name = "Claude",
        shortName = "CL",
        description = "Long-context assistant with polished analysis and writing.",
        accent = 0xFFFF8A4C,
        models = listOf("claude-3.7-sonnet", "claude-3.5-sonnet", "claude-3-haiku", "claude-opus-4"),
        freeTier = "Console credits where available",
    ),
    AiProvider(
        id = "groq",
        name = "Groq",
        shortName = "GQ",
        description = "Ultra-low latency inference for open models.",
        accent = 0xFFFF4D6D,
        models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it"),
        freeTier = "Generous free tier",
    ),
    AiProvider(
        id = "cerebras",
        name = "Cerebras",
        shortName = "CB",
        description = "High-throughput Llama inference for rapid iteration.",
        accent = 0xFF00D1FF,
        models = listOf("llama3.1-8b", "llama3.1-70b", "qwen-3-32b", "deepseek-r1-distill-llama-70b"),
        freeTier = "Free developer tier",
    ),
    AiProvider(
        id = "gemini",
        name = "Gemini",
        shortName = "GE",
        description = "Google models for multimodal reasoning and fast chat.",
        accent = 0xFF5EA2FF,
        models = listOf("gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash", "gemma-3-27b"),
        freeTier = "AI Studio free quota",
    ),
    AiProvider(
        id = "huggingface",
        name = "Hugging Face",
        shortName = "HF",
        description = "Open-source model hub with serverless inference options.",
        accent = 0xFFFFC857,
        models = listOf("meta-llama/Llama-3.1-8B-Instruct", "mistralai/Mistral-7B-Instruct", "Qwen/Qwen2.5-7B-Instruct", "google/gemma-2-9b-it"),
        freeTier = "Free inference options",
    ),
    AiProvider(
        id = "modelscope",
        name = "ModelScope",
        shortName = "MS",
        description = "Model hub and hosted inference ecosystem for open models.",
        accent = 0xFF9B8CFF,
        models = listOf("qwen2.5-72b-instruct", "qwen2.5-coder-32b", "deepseek-v3", "yi-large"),
        freeTier = "Community/free endpoints",
    ),
    AiProvider(
        id = "together",
        name = "Together AI",
        shortName = "TG",
        description = "Production APIs for open-weight chat and image models.",
        accent = 0xFF35E0A1,
        models = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-72B-Instruct-Turbo", "mistralai/Mixtral-8x7B-Instruct-v0.1"),
        freeTier = "Starter credits",
    ),
    AiProvider(
        id = "deepseek",
        name = "DeepSeek",
        shortName = "DS",
        description = "Efficient reasoning and coding models with accessible pricing.",
        accent = 0xFF4A7DFF,
        models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-coder", "deepseek-v3"),
        freeTier = "Promotional/free balances",
    ),
    AiProvider(
        id = "ollama",
        name = "Ollama",
        shortName = "OL",
        description = "Local models through an Ollama server URL used as the key field.",
        accent = 0xFF94A3B8,
        models = listOf("llama3.1", "qwen2.5", "mistral", "gemma2"),
        freeTier = "Local / self-hosted",
    ),
)
