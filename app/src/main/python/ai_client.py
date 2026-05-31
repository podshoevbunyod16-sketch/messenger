import base64
import json
import urllib.request
import urllib.error

TIMEOUT = 90


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


def _chat_url(provider):
    urls = {
        "openrouter": "https://openrouter.ai/api/v1/chat/completions",
        "openai": "https://api.openai.com/v1/chat/completions",
        "groq": "https://api.groq.com/openai/v1/chat/completions",
        "cerebras": "https://api.cerebras.ai/v1/chat/completions",
        "together": "https://api.together.xyz/v1/chat/completions",
        "deepseek": "https://api.deepseek.com/chat/completions",
        "modelscope": "https://api-inference.modelscope.cn/v1/chat/completions",
    }
    if provider not in urls:
        raise Exception(f"Python API mode for {provider} is not implemented yet. Use OpenRouter/OpenAI/Groq/Cerebras/Together/DeepSeek/ModelScope.")
    return urls[provider]


def _models_url(provider):
    urls = {
        "openrouter": "https://openrouter.ai/api/v1/models",
        "openai": "https://api.openai.com/v1/models",
        "groq": "https://api.groq.com/openai/v1/models",
        "cerebras": "https://api.cerebras.ai/v1/models",
        "together": "https://api.together.xyz/v1/models",
        "deepseek": "https://api.deepseek.com/models",
        "modelscope": "https://api-inference.modelscope.cn/v1/models",
    }
    if provider not in urls:
        raise Exception(f"Model discovery is not available for {provider} in Python mode yet.")
    return urls[provider]


def _request_json(method, url, headers, payload=None, timeout=45):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        raise Exception(f"HTTP {e.code}: {body}")
    except urllib.error.URLError as e:
        raise Exception(f"Network error: {e.reason}")


def _headers(provider, api_key):
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    if provider == "openrouter":
        headers["HTTP-Referer"] = "https://novamind-mobile.local"
        headers["X-Title"] = "NovaMind Mobile"
    return headers


def _parse_chat_response(data):
    if isinstance(data, dict) and data.get("error"):
        err = data["error"]
        if isinstance(err, dict):
            raise Exception(err.get("message") or json.dumps(err, ensure_ascii=False))
        raise Exception(str(err))
    try:
        content = data["choices"][0]["message"]["content"]
    except Exception:
        content = None
    if not content:
        raise Exception("Provider returned empty response")
    return content


def fetch_models(provider, api_key):
    try:
        data = _request_json("GET", _models_url(provider), _headers(provider, api_key))
        models = []
        for item in data.get("data", []):
            model_id = item.get("id") if isinstance(item, dict) else None
            if model_id:
                models.append(model_id)
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
        history = json.loads(history_json or "[]")
        messages = [{"role": "system", "content": _system_prompt()}]
        for item in history[-12:]:
            role = "assistant" if item.get("role") == "assistant" else "user"
            messages.append({"role": role, "content": str(item.get("content", ""))})

        final_message = message or ""
        if attachment_b64 and attachment_mime and attachment_mime.startswith("image/"):
            if provider not in ("openai", "openrouter"):
                raise Exception("Image analysis supports OpenAI/OpenRouter vision-compatible models only.")
            data_url = f"data:{attachment_mime};base64,{attachment_b64}"
            payload = {
                "model": model,
                "messages": [{
                    "role": "user",
                    "content": [
                        {"type": "text", "text": final_message or "Analyze this image in detail."},
                        {"type": "image_url", "image_url": {"url": data_url}},
                    ],
                }],
                "max_tokens": 1400,
            }
        else:
            if attachment_b64:
                raw = base64.b64decode(attachment_b64)
                try:
                    preview = raw.decode("utf-8", errors="replace")[:12000]
                except Exception:
                    preview = "Binary file. No text preview available."
                final_message += f"\n\nAttached file: {attachment_name or 'attachment'} ({attachment_mime or 'application/octet-stream'}). Extracted preview:\n{preview}"
            messages.append({"role": "user", "content": final_message})
            payload = {
                "model": model,
                "messages": messages,
                "temperature": 0.7,
                "max_tokens": 1400,
            }

        data = _request_json("POST", _chat_url(provider), _headers(provider, api_key), payload, TIMEOUT)
        content = _parse_chat_response(data)
        return json.dumps({"ok": True, "content": content}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)


def generate_image(provider, api_key, prompt):
    try:
        if provider != "openai":
            raise Exception("Image generation in Python standalone mode uses OpenAI Images API. Switch active provider to OpenAI.")
        payload = {
            "model": "gpt-image-1",
            "prompt": prompt,
            "size": "1024x1024",
            "n": 1,
        }
        data = _request_json("POST", "https://api.openai.com/v1/images/generations", _headers("openai", api_key), payload, 120)
        if data.get("error"):
            raise Exception(data["error"].get("message") if isinstance(data["error"], dict) else str(data["error"]))
        first = (data.get("data") or [{}])[0]
        image = first.get("url") or ("data:image/png;base64," + first.get("b64_json") if first.get("b64_json") else None)
        if not image:
            raise Exception("No image returned")
        return json.dumps({"ok": True, "image": image}, ensure_ascii=False)
    except Exception as e:
        return json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False)
