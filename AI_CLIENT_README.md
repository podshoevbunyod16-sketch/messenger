# NovaMind Universal Python AI Client

The Android app now calls API providers through `app/src/main/python/ai_client.py` using Chaquopy. The client is intentionally stdlib-only (`urllib`) to avoid CI/Android build failures caused by pip/native wheels.

## Why the old API code broke

1. **Different API dialects were treated as one format**
   - OpenAI/OpenRouter/Groq/Cerebras/Together/DeepSeek/ModelScope use OpenAI-like `/chat/completions`.
   - Claude/Anthropic uses `/v1/messages`, `x-api-key`, `anthropic-version`, `system` as a top-level field and a different image format.
   - Gemini uses `:generateContent?key=...`, `contents`, `parts`, and `inline_data`.
   - Hugging Face serverless inference often uses `inputs`/`parameters`, not chat messages.
   - Ollama local uses `/api/chat` and usually no API key.

2. **Wrong base_url assumptions**
   Some providers are OpenAI-compatible but not at the same base path:
   - Groq: `https://api.groq.com/openai/v1/chat/completions`
   - DeepSeek: `https://api.deepseek.com/chat/completions`
   - Cerebras: `https://api.cerebras.ai/v1/chat/completions`

3. **Headers were not provider-specific**
   - OpenAI-compatible APIs use `Authorization: Bearer ...`.
   - Anthropic uses `x-api-key` and `anthropic-version`.
   - Gemini uses a query key.
   - OpenRouter works better with `HTTP-Referer` and `X-Title`.

4. **No resilient HTTP layer**
   The fixed client adds:
   - timeout handling
   - retries for 429/5xx/timeout-like statuses
   - normalized errors
   - lightweight logs without leaking keys

5. **Model discovery is not universal**
   OpenAI-like providers expose `/models`. Anthropic does not expose the same public list in the same way, so the app uses a curated list. Gemini has `models` with supported generation methods. Ollama has `/api/tags`.

## Android usage

The Kotlin wrapper is:

```text
app/src/main/java/com/agon/app/data/PythonAiClient.kt
```

It exposes:

```kotlin
fetchModels(provider, apiKey)
testConnection(provider, model, apiKey)
chat(provider, model, apiKey, message, history, attachment)
generateImage(provider, apiKey, prompt)
```

## Provider IDs

Use these IDs from the app:

```text
openai
openrouter
claude / anthropic
gemini
groq
cerebras
together
deepseek
modelscope
huggingface
ollama
```

## .env example for server/desktop reuse

Android does not read `.env`; keys are stored in the app UI. If you reuse the same Python logic on a PC/server, use:

```env
OPENAI_API_KEY=sk-...
OPENROUTER_API_KEY=sk-or-...
ANTHROPIC_API_KEY=sk-ant-...
GROQ_API_KEY=gsk_...
CEREBRAS_API_KEY=...
TOGETHER_API_KEY=...
DEEPSEEK_API_KEY=...
MODELSCOPE_API_KEY=...
HUGGINGFACE_API_KEY=hf_...
GEMINI_API_KEY=...
OLLAMA_BASE_URL=http://127.0.0.1:11434
```

## Ollama on Android

For Ollama, the API key field is used as the base URL, for example:

```text
http://192.168.1.10:11434
```

If empty, it defaults to:

```text
http://127.0.0.1:11434
```

## Example Python calls

```python
print(fetch_models("openrouter", "sk-or-..."))
print(chat("openai", "gpt-4o-mini", "sk-...", "Hello", "[]"))
print(chat("claude", "claude-3-5-haiku-latest", "sk-ant-...", "Hello", "[]"))
print(chat("gemini", "gemini-1.5-flash", "AIza...", "Hello", "[]"))
```
