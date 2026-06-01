"""
NovaMind universal AI client for Android/Chaquopy.

Design goals:
- Single sync interface for chat/completions, model discovery, key validation and image generation.
- No third-party pip dependencies: uses Python stdlib urllib so Android/GitHub builds are stable.
- Provider-specific request adapters: OpenAI-compatible APIs are NOT the same as Anthropic/Gemini/HuggingFace/Ollama.
- Production hardening: validation, timeouts, retry with exponential backoff, useful errors and lightweight logs.

Optional desktop/server .env example for the same code style:
OPENAI_API_KEY=sk-...
OPENROUTER_API_KEY=sk-or-...
ANTHROPIC_API_KEY=sk-ant-...
GROQ_API_KEY=gsk_...
OLLAMA_BASE_URL=http://127.0.0.1:11434

In this Android app keys are passed from DataStore/Kotlin, not read from .env.
"""

import base64
import json
import os
import random
import time
import urllib.error
import urllib.parse
import urllib.request

TIMEOUT = 90
CONNECT_TIMEOUT = 25
MAX_RETRIES = 2
RETRY_STATUSES = {408, 409, 425, 429, 500, 502, 503, 504}
APP_REFERER = "https://novamind-mobile.local"
APP_TITLE = "NovaMind Mobile"


class AiClientError(Exception):
    """User-readable AI client exception."""


class ProviderConfig:
    def __init__(self, provider, chat_url=None, models_url=None, api_style="openai", auth_style="bearer", default_model=None):
        self.provider = provider
        self.chat_url = chat_url
        self.models_url = models_url
        self.api_style = api_style
        self.auth_style = auth_style
        self.default_model = default_model


PROVIDERS = {
    # OpenAI-compatible chat/completions.
    "openai": ProviderConfig("openai", "https://api.openai.com/v1/chat/completions", "https://api.openai.com/v1/models", "openai", "bearer", "gpt-4o-mini"),
    "openrouter": ProviderConfig("openrouter", "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/api/v1/models", "openai", "bearer", "openrouter/auto"),
    "groq": ProviderConfig("groq", "https://api.groq.com/openai/v1/chat/completions", "https://api.groq.com/openai/v1/models", "openai", "bearer", "llama-3.1-8b-instant"),
    "cerebras": ProviderConfig("cerebras", "https://api.cerebras.ai/v1/chat/completions", "https://api.cerebras.ai/v1/models", "openai", "bearer", "llama3.1-8b"),
    "together": ProviderConfig("together", "https://api.together.xyz/v1/chat/completions", "https://api.together.xyz/v1/models", "openai", "bearer", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
    "deepseek": ProviderConfig("deepseek", "https://api.deepseek.com/chat/completions", "https://api.deepseek.com/models", "openai", "bearer", "deepseek-chat"),
    "modelscope": ProviderConfig("modelscope", "https://api-inference.modelscope.cn/v1/chat/completions", "https://api-inference.modelscope.cn/v1/models", "openai", "bearer", "qwen2.5-72b-instruct"),
    # Different API formats.
    "claude": ProviderConfig("claude", "https://api.anthropic.com/v1/messages", None, "anthropic", "anthropic", "claude-3-5-haiku-latest"),
    "anthropic": ProviderConfig("anthropic", "https://api.anthropic.com/v1/messages", None, "anthropic", "anthropic", "claude-3-5-haiku-latest"),
    "gemini": ProviderConfig("gemini", None, "https://generativelanguage.googleapis.com/v1beta/models", "gemini", "query_key", "gemini-1.5-flash"),
    "huggingface": ProviderConfig("huggingface", None, None, "huggingface", "bearer", "mistralai/Mistral-7B-Instruct-v0.3"),
    "ollama": ProviderConfig("ollama", None, None, "ollama", "none", "llama3.1"),
}


def _system_prompt():
    return """
You are NovaMind, a helpful mobile AI assistant.
If the user asks for code, detect the requested programming language even from aliases, typos, transliteration and mixed casing.
Examples: питон, пайтон, python, Python, py -> Python; джаваскрипт, js -> JavaScript; си шарп, csharp, c# -> C#.
You can write code in 50+ languages including Python, JavaScript, TypeScript, Java, Kotlin, Swift, C, C++, C#, Go, Rust, PHP, Ruby, Dart, R, Julia, Lua, Perl, Bash, PowerShell, SQL, HTML, CSS, SCSS, XML, JSON, YAML, Markdown, Scala, Groovy, Haskell, Elixir, Erlang, Clojure, F#, VB.NET, Objective-C, MATLAB, Octave, Assembly, Zig, Nim, Crystal, D, Fortran, COBOL, Pascal, Delphi, Prolog, Lisp, Scheme, Solidity, Vyper, Move, Apex, Visual Basic, Scratch pseudocode and more.
For code requests, return: 1) detected language, 2) ready-to-run code in a fenced code block, 3) filename/extension, 4) short run instructions.
If user says something like "give code in python that prints 'hello everyone'", produce the exact minimal program for that language.
Answer clearly and practically.
""".strip()


def _log(event, **fields):
    safe = {k: ("***" if "key" in k.lower() else v) for k, v in fields.items()}
    print("[NovaMindAI] " + json.dumps({"event": event, **safe}, ensure_ascii=False)[:3000])


def _provider(provider):
    normalized = (provider or "").strip().lower()
    if normalized not in PROVIDERS:
        raise AiClientError(f"Unsupported provider '{provider}'. Supported: {', '.join(sorted(PROVIDERS))}")
    return PROVIDERS[normalized]


def _validate(provider, model, api_key, allow_empty_key=False):
    cfg = _provider(provider)
    if not model:
        model = cfg.default_model
    if cfg.auth_style != "none" and not allow_empty_key and not (api_key or "").strip():
        raise AiClientError(f"API key is empty for {provider}")
    return cfg, model, (api_key or "").strip()


def _headers(cfg, api_key):
    headers = {"Accept": "application/json", "Content-Type": "application/json; charset=utf-8", "User-Agent": "NovaMindMobile/1.0"}
    if cfg.auth_style == "bearer":
        headers["Authorization"] = f"Bearer {api_key}"
    elif cfg.auth_style == "anthropic":
        headers["x-api-key"] = api_key
        headers["anthropic-version"] = "2023-06-01"
    if cfg.provider == "openrouter":
        headers["HTTP-Referer"] = APP_REFERER
        headers["X-Title"] = APP_TITLE
    return headers


def _json_loads(raw):
    try:
        return json.loads(raw) if raw else {}
    except Exception:
        return {"raw": raw}


def _extract_error(data):
    if not isinstance(data, dict):
        return str(data)
    err = data.get("error") or data.get("detail") or data.get("message")
    if isinstance(err, dict):
        return err.get("message") or err.get("error") or json.dumps(err, ensure_ascii=False)
    if isinstance(err, list):
        return json.dumps(err, ensure_ascii=False)
    return str(err) if err else json.dumps(data, ensure_ascii=False)[:1200]


def _request_json(method, url, headers=None, payload=None, timeout=TIMEOUT, retries=MAX_RETRIES):
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    last_error = None
    for attempt in range(retries + 1):
        req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
        try:
            started = time.time()
            with urllib.request.urlopen(req, timeout=timeout) as response:
                raw = response.read().decode("utf-8", errors="replace")
                _log("http_ok", method=method, url=url, status=response.status, ms=int((time.time() - started) * 1000))
                return _json_loads(raw)
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            parsed = _json_loads(body)
            message = _extract_error(parsed)
            last_error = AiClientError(f"HTTP {e.code}: {message}")
            _log("http_error", method=method, url=url, status=e.code, attempt=attempt, error=str(last_error))
            if e.code not in RETRY_STATUSES or attempt >= retries:
                raise last_error
        except urllib.error.URLError as e:
            last_error = AiClientError(f"Network error: {getattr(e, 'reason', e)}")
            _log("network_error", method=method, url=url, attempt=attempt, error=str(last_error))
            if attempt >= retries:
                raise last_error
        except TimeoutError as e:
            last_error = AiClientError(f"Timeout: {e}")
            if attempt >= retries:
                raise last_error
        time.sleep(min(3.0, 0.6 * (2 ** attempt)) + random.random() * 0.25)
    raise last_error or AiClientError("Unknown HTTP error")


def _history_messages(history_json):
    try:
        history = json.loads(history_json or "[]")
    except Exception:
        history = []
    messages = []
    for item in history[-12:]:
        if not isinstance(item, dict):
            continue
        content = str(item.get("content", "")).strip()
        if not content:
            continue
        role = "assistant" if item.get("role") == "assistant" else "user"
        messages.append({"role": role, "content": content})
    return messages


def _text_from_openai(data):
    if isinstance(data, dict) and data.get("error"):
        raise AiClientError(_extract_error(data))
    choices = data.get("choices") or []
    if not choices:
        raise AiClientError("Provider returned no choices")
    msg = choices[0].get("message") or {}
    content = msg.get("content")
    if isinstance(content, list):
        content = "\n".join(part.get("text", "") for part in content if isinstance(part, dict))
    if not content:
        content = choices[0].get("text")
    if not content:
        raise AiClientError("Provider returned empty response")
    return content


def _chat_openai(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64):
    messages = [{"role": "system", "content": _system_prompt()}] + _history_messages(history_json)
    final_message = message or ""

    if attachment_b64 and attachment_mime and attachment_mime.startswith("image/"):
        if cfg.provider not in ("openai", "openrouter"):
            raise AiClientError("Image analysis supports OpenAI/OpenRouter vision-compatible models only.")
        data_url = f"data:{attachment_mime};base64,{attachment_b64}"
        messages.append({
            "role": "user",
            "content": [
                {"type": "text", "text": final_message or "Analyze this image in detail."},
                {"type": "image_url", "image_url": {"url": data_url}},
            ],
        })
    else:
        if attachment_b64:
            raw = base64.b64decode(attachment_b64)
            preview = raw.decode("utf-8", errors="replace")[:12000]
            final_message += f"\n\nAttached file: {attachment_name or 'attachment'} ({attachment_mime or 'application/octet-stream'}). Extracted preview:\n{preview}"
        messages.append({"role": "user", "content": final_message})

    payload = {"model": model, "messages": messages, "temperature": 0.7, "max_tokens": 1400}
    data = _request_json("POST", cfg.chat_url, _headers(cfg, api_key), payload, TIMEOUT)
    return _text_from_openai(data)


def _chat_anthropic(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64):
    messages = _history_messages(history_json)
    final_message = message or ""
    if attachment_b64:
        if attachment_mime and attachment_mime.startswith("image/"):
            content = [
                {"type": "text", "text": final_message or "Analyze this image in detail."},
                {"type": "image", "source": {"type": "base64", "media_type": attachment_mime, "data": attachment_b64}},
            ]
            messages.append({"role": "user", "content": content})
        else:
            raw = base64.b64decode(attachment_b64)
            final_message += f"\n\nAttached file: {attachment_name or 'attachment'} preview:\n{raw.decode('utf-8', errors='replace')[:12000]}"
            messages.append({"role": "user", "content": final_message})
    else:
        messages.append({"role": "user", "content": final_message})
    payload = {"model": model, "system": _system_prompt(), "messages": messages, "max_tokens": 1400, "temperature": 0.7}
    data = _request_json("POST", cfg.chat_url, _headers(cfg, api_key), payload, TIMEOUT)
    if data.get("error"):
        raise AiClientError(_extract_error(data))
    blocks = data.get("content") or []
    text = "\n".join(block.get("text", "") for block in blocks if isinstance(block, dict) and block.get("type") == "text")
    if not text:
        raise AiClientError("Anthropic returned empty response")
    return text


def _chat_gemini(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{urllib.parse.quote(model)}:generateContent?key={urllib.parse.quote(api_key)}"
    contents = []
    for msg in _history_messages(history_json):
        contents.append({"role": "model" if msg["role"] == "assistant" else "user", "parts": [{"text": msg["content"]}]})
    parts = [{"text": message or "Analyze this input."}]
    if attachment_b64 and attachment_mime and attachment_mime.startswith("image/"):
        parts.append({"inline_data": {"mime_type": attachment_mime, "data": attachment_b64}})
    elif attachment_b64:
        raw = base64.b64decode(attachment_b64)
        parts[0]["text"] += f"\n\nAttached file: {attachment_name or 'attachment'} preview:\n{raw.decode('utf-8', errors='replace')[:12000]}"
    contents.append({"role": "user", "parts": parts})
    payload = {"systemInstruction": {"parts": [{"text": _system_prompt()}]}, "contents": contents, "generationConfig": {"temperature": 0.7, "maxOutputTokens": 1400}}
    data = _request_json("POST", url, {"Content-Type": "application/json", "Accept": "application/json"}, payload, TIMEOUT)
    if data.get("error"):
        raise AiClientError(_extract_error(data))
    text = "\n".join(part.get("text", "") for part in (((data.get("candidates") or [{}])[0].get("content") or {}).get("parts") or []))
    if not text:
        raise AiClientError("Gemini returned empty response")
    return text


def _chat_huggingface(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64):
    prompt = _system_prompt() + "\n\n" + "\n\n".join(f"{m['role']}: {m['content']}" for m in _history_messages(history_json)) + f"\n\nuser: {message or ''}\nassistant:"
    url = f"https://api-inference.huggingface.co/models/{model}"
    payload = {"inputs": prompt, "parameters": {"max_new_tokens": 900, "temperature": 0.7, "return_full_text": False}}
    data = _request_json("POST", url, _headers(cfg, api_key), payload, TIMEOUT)
    if isinstance(data, list):
        text = (data[0] or {}).get("generated_text") if data else None
    else:
        if data.get("error"):
            raise AiClientError(_extract_error(data))
        text = data.get("generated_text")
    if not text:
        raise AiClientError("Hugging Face returned empty response")
    return text


def _chat_ollama(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64):
    base_url = (api_key or os.environ.get("OLLAMA_BASE_URL") or "http://127.0.0.1:11434").rstrip("/")
    messages = [{"role": "system", "content": _system_prompt()}] + _history_messages(history_json) + [{"role": "user", "content": message or ""}]
    payload = {"model": model, "messages": messages, "stream": False}
    data = _request_json("POST", f"{base_url}/api/chat", {"Content-Type": "application/json", "Accept": "application/json"}, payload, TIMEOUT)
    content = ((data.get("message") or {}).get("content"))
    if not content:
        raise AiClientError("Ollama returned empty response")
    return content


def fetch_models(provider, api_key):
    try:
        cfg, _, api_key = _validate(provider, None, api_key, allow_empty_key=(provider == "ollama"))
        if cfg.api_style == "anthropic":
            models = ["claude-3-5-haiku-latest", "claude-3-5-sonnet-latest", "claude-3-7-sonnet-latest", "claude-opus-4-1"]
        elif cfg.api_style == "gemini":
            data = _request_json("GET", f"{cfg.models_url}?key={urllib.parse.quote(api_key)}", {"Accept": "application/json"}, None, 45)
            models = [m.get("name", "").replace("models/", "") for m in data.get("models", []) if "generateContent" in m.get("supportedGenerationMethods", [])]
        elif cfg.api_style == "huggingface":
            models = [cfg.default_model, "meta-llama/Llama-3.1-8B-Instruct", "Qwen/Qwen2.5-7B-Instruct", "google/gemma-2-9b-it"]
        elif cfg.api_style == "ollama":
            base_url = (api_key or os.environ.get("OLLAMA_BASE_URL") or "http://127.0.0.1:11434").rstrip("/")
            data = _request_json("GET", f"{base_url}/api/tags", {"Accept": "application/json"}, None, 20, retries=0)
            models = [m.get("name") for m in data.get("models", []) if m.get("name")]
        else:
            data = _request_json("GET", cfg.models_url, _headers(cfg, api_key), None, 45)
            models = [item.get("id") for item in data.get("data", []) if isinstance(item, dict) and item.get("id")]
        return json.dumps({"ok": True, "models": sorted(set(models))}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)


def test_connection(provider, model, api_key):
    try:
        result = chat(provider, model, api_key, "Reply only: ok", "[]", None, None, None)
        data = json.loads(result)
        return json.dumps({"ok": bool(data.get("ok")), "error": data.get("error")}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)


def chat(provider, model, api_key, message, history_json="[]", attachment_name=None, attachment_mime=None, attachment_b64=None):
    try:
        cfg, model, api_key = _validate(provider, model, api_key, allow_empty_key=(provider == "ollama"))
        _log("chat_start", provider=cfg.provider, model=model, has_attachment=bool(attachment_b64))
        if cfg.api_style == "anthropic":
            content = _chat_anthropic(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64)
        elif cfg.api_style == "gemini":
            content = _chat_gemini(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64)
        elif cfg.api_style == "huggingface":
            content = _chat_huggingface(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64)
        elif cfg.api_style == "ollama":
            content = _chat_ollama(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64)
        else:
            content = _chat_openai(cfg, model, api_key, message, history_json, attachment_name, attachment_mime, attachment_b64)
        return json.dumps({"ok": True, "content": content}, ensure_ascii=False)
    except Exception as e:
        _log("chat_failed", provider=provider, model=model, error=str(e))
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)


def generate_image(provider, api_key, prompt):
    try:
        cfg, _, api_key = _validate(provider, "image", api_key)
        if cfg.provider != "openai":
            raise AiClientError("Image generation uses OpenAI Images API. Switch active provider to OpenAI.")
        payload = {"model": "gpt-image-1", "prompt": prompt, "size": "1024x1024", "n": 1}
        data = _request_json("POST", "https://api.openai.com/v1/images/generations", _headers(cfg, api_key), payload, 120)
        if data.get("error"):
            raise AiClientError(_extract_error(data))
        first = (data.get("data") or [{}])[0]
        image = first.get("url") or ("data:image/png;base64," + first.get("b64_json") if first.get("b64_json") else None)
        if not image:
            raise AiClientError("No image returned")
        return json.dumps({"ok": True, "image": image}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)
