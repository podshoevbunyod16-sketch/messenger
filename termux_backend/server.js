import express from 'express';
import cors from 'cors';

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json({ limit: '2mb' }));

app.get('/', (_, res) => {
  res.json({ ok: true, name: 'NovaMind Termux Backend' });
});

app.post('/api/provider/test', async (req, res) => {
  try {
    const { provider, model, apiKey } = req.body;
    if (!provider || !model || !apiKey) {
      return res.status(400).json({ ok: false, error: 'provider, model and apiKey are required' });
    }

    const result = await callProvider({
      provider,
      model,
      apiKey,
      messages: [{ role: 'user', content: 'Reply with only: ok' }],
      maxTokens: 24
    });

    if (!result.ok) return res.status(400).json({ ok: false, error: result.error });
    return res.json({ ok: true });
  } catch (error) {
    return res.status(500).json({ ok: false, error: error.message || 'Server error' });
  }
});

app.post('/api/chat', async (req, res) => {
  try {
    const { provider, model, apiKey, message, history = [] } = req.body;
    if (!provider || !model || !apiKey || !message) {
      return res.status(400).json({ error: 'provider, model, apiKey and message are required' });
    }

    const messages = [
      { role: 'system', content: 'You are NovaMind, a helpful mobile AI assistant. Answer clearly and practically.' },
      ...history
        .filter(item => item && item.role && item.content)
        .map(item => ({ role: item.role === 'assistant' ? 'assistant' : 'user', content: String(item.content) })),
      { role: 'user', content: String(message) }
    ];

    const result = await callProvider({ provider, model, apiKey, messages, maxTokens: 1200 });
    if (!result.ok) return res.status(400).json({ error: result.error });
    return res.json({ content: result.content, provider, model });
  } catch (error) {
    return res.status(500).json({ error: error.message || 'Server error' });
  }
});

async function callProvider({ provider, model, apiKey, messages, maxTokens }) {
  switch (provider) {
    case 'openrouter':
      return callOpenAICompatible({
        url: 'https://openrouter.ai/api/v1/chat/completions',
        apiKey,
        model,
        messages,
        maxTokens,
        extraHeaders: {
          'HTTP-Referer': 'https://novamind-mobile.local',
          'X-Title': 'NovaMind Mobile'
        }
      });
    case 'openai':
      return callOpenAICompatible({ url: 'https://api.openai.com/v1/chat/completions', apiKey, model, messages, maxTokens });
    case 'groq':
      return callOpenAICompatible({ url: 'https://api.groq.com/openai/v1/chat/completions', apiKey, model, messages, maxTokens });
    case 'cerebras':
      return callOpenAICompatible({ url: 'https://api.cerebras.ai/v1/chat/completions', apiKey, model, messages, maxTokens });
    case 'together':
      return callOpenAICompatible({ url: 'https://api.together.xyz/v1/chat/completions', apiKey, model, messages, maxTokens });
    case 'deepseek':
      return callOpenAICompatible({ url: 'https://api.deepseek.com/chat/completions', apiKey, model, messages, maxTokens });
    case 'modelscope':
      return callOpenAICompatible({ url: 'https://api-inference.modelscope.cn/v1/chat/completions', apiKey, model, messages, maxTokens });
    case 'gemini':
      return callGemini({ apiKey, model, messages, maxTokens });
    case 'claude':
      return callClaude({ apiKey, model, messages, maxTokens });
    case 'huggingface':
      return callHuggingFace({ apiKey, model, messages, maxTokens });
    default:
      return { ok: false, error: `Unsupported provider: ${provider}` };
  }
}

async function callOpenAICompatible({ url, apiKey, model, messages, maxTokens, extraHeaders = {} }) {
  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json', ...extraHeaders },
      body: JSON.stringify({ model, messages, temperature: 0.7, max_tokens: maxTokens })
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) return { ok: false, error: data?.error?.message || data?.message || `HTTP ${response.status}` };
    const content = data?.choices?.[0]?.message?.content;
    return content ? { ok: true, content } : { ok: false, error: 'Empty provider response' };
  } catch (error) {
    return { ok: false, error: error.message || 'OpenAI-compatible request failed' };
  }
}

async function callGemini({ apiKey, model, messages, maxTokens }) {
  try {
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent?key=${encodeURIComponent(apiKey)}`;
    const contents = messages.filter(m => m.role !== 'system').map(m => ({
      role: m.role === 'assistant' ? 'model' : 'user',
      parts: [{ text: m.content }]
    }));
    const systemText = messages.find(m => m.role === 'system')?.content;
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        systemInstruction: systemText ? { parts: [{ text: systemText }] } : undefined,
        contents,
        generationConfig: { temperature: 0.7, maxOutputTokens: maxTokens }
      })
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) return { ok: false, error: data?.error?.message || `Gemini HTTP ${response.status}` };
    const content = data?.candidates?.[0]?.content?.parts?.map(p => p.text).filter(Boolean).join('\n');
    return content ? { ok: true, content } : { ok: false, error: 'Empty Gemini response' };
  } catch (error) {
    return { ok: false, error: error.message || 'Gemini request failed' };
  }
}

async function callClaude({ apiKey, model, messages, maxTokens }) {
  try {
    const system = messages.find(m => m.role === 'system')?.content;
    const claudeMessages = messages.filter(m => m.role !== 'system').map(m => ({
      role: m.role === 'assistant' ? 'assistant' : 'user',
      content: m.content
    }));
    const response = await fetch('https://api.anthropic.com/v1/messages', {
      method: 'POST',
      headers: { 'x-api-key': apiKey, 'anthropic-version': '2023-06-01', 'Content-Type': 'application/json' },
      body: JSON.stringify({ model, system, messages: claudeMessages, max_tokens: maxTokens, temperature: 0.7 })
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) return { ok: false, error: data?.error?.message || `Claude HTTP ${response.status}` };
    const content = data?.content?.map(b => b.text).filter(Boolean).join('\n');
    return content ? { ok: true, content } : { ok: false, error: 'Empty Claude response' };
  } catch (error) {
    return { ok: false, error: error.message || 'Claude request failed' };
  }
}

async function callHuggingFace({ apiKey, model, messages, maxTokens }) {
  try {
    const prompt = messages.map(m => `${m.role}: ${m.content}`).join('\n\n');
    const response = await fetch(`https://api-inference.huggingface.co/models/${model}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ inputs: prompt, parameters: { max_new_tokens: maxTokens, temperature: 0.7, return_full_text: false } })
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) return { ok: false, error: data?.error || `Hugging Face HTTP ${response.status}` };
    const content = Array.isArray(data) ? data[0]?.generated_text : data?.generated_text;
    return content ? { ok: true, content } : { ok: false, error: 'Empty Hugging Face response' };
  } catch (error) {
    return { ok: false, error: error.message || 'Hugging Face request failed' };
  }
}

app.listen(PORT, '127.0.0.1', () => {
  console.log(`NovaMind backend running on http://127.0.0.1:${PORT}`);
});
