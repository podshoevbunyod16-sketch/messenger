package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.agon.app.data.ChatMessage
import com.agon.app.data.STATUS_CONNECTED
import com.agon.app.data.novaProviders
import com.agon.app.viewmodel.NovaMindViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: NovaMindViewModel, onOpenKeys: () -> Unit) {
    val messages by viewModel.messages.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentLabel by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        attachmentUri = uri
        attachmentLabel = uri?.lastPathSegment ?: "Selected file"
    }
    val context = LocalContext.current
    var pendingDownloadCode by remember { mutableStateOf("") }
    val saveCodeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(pendingDownloadCode.toByteArray()) }
            Toast.makeText(context, "Code file saved", Toast.LENGTH_SHORT).show()
        }
    }
    val activeProvider = novaProviders.firstOrNull { it.id == settings.activeProviderId } ?: novaProviders.first()
    val activeModel = settings.selectedModels[activeProvider.id] ?: activeProvider.models.first()
    val connected = settings.statuses[activeProvider.id] == STATUS_CONNECTED

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070A12), MaterialTheme.colorScheme.background)))
            .imePadding(),
    ) {
        ChatHeader(activeProvider.name, activeProvider.shortName, Color(activeProvider.accent), activeModel, connected, onOpenKeys)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(messages) { message ->
                ChatBubble(
                    message = message,
                    onCopyCode = { code -> copyToClipboard(context, code) },
                    onDownloadCode = { code, filename ->
                        pendingDownloadCode = code
                        saveCodeLauncher.launch(filename)
                    },
                )
            }
            item {
                AnimatedVisibility(isThinking) { TypingBubble(activeProvider.shortName, Color(activeProvider.accent)) }
                Spacer(Modifier.height(8.dp))
            }
        }
        Surface(tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (attachmentUri != null) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(attachmentLabel ?: "Attachment selected", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { attachmentUri = null; attachmentLabel = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (connected) "Ask, analyze file/image..." else "Connect API key first...") },
                        shape = RoundedCornerShape(22.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            viewModel.sendMessage(input, attachmentUri)
                            input = ""
                            attachmentUri = null
                            attachmentLabel = null
                        }),
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, null) },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.generateImage(input); input = "" }, enabled = input.isNotBlank() && !isThinking) {
                                Icon(Icons.Default.Brush, contentDescription = "Generate image")
                            }
                        },
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendMessage(input, attachmentUri)
                            input = ""
                            attachmentUri = null
                            attachmentLabel = null
                        },
                        enabled = (input.isNotBlank() || attachmentUri != null) && !isThinking,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (input.isNotBlank() || attachmentUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Icon(Icons.Default.Send, null, tint = if (input.isNotBlank() || attachmentUri != null) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(provider: String, shortName: String, accent: Color, model: String, connected: Boolean, onOpenKeys: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xE60D1324)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProviderAvatar(shortName, accent, 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Chat", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("$provider · $model", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB7C2DD), maxLines = 1)
            }
            AssistChip(onClick = onOpenKeys, label = { Text(if (connected) "Connected" else "Setup") }, leadingIcon = { Icon(Icons.Default.SmartToy, null, Modifier.size(16.dp)) })
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCopyCode: (String) -> Unit,
    onDownloadCode: (String, String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomStart = if (message.fromUser) 22.dp else 6.dp,
                bottomEnd = if (message.fromUser) 6.dp else 22.dp,
            ),
            color = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(if (message.fromUser) 0.82f else 0.90f),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (message.fromUser) "You" else "${message.providerName} · ${message.modelName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                message.imageUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = message.attachmentName ?: "image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                message.attachmentName?.let { name ->
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = if (message.fromUser) 0.18f else 0.08f)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp), tint = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(name, style = MaterialTheme.typography.labelMedium, color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(message.text, color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                val codeBlock = remember(message.text) { extractFirstCodeBlock(message.text) }
                if (!message.fromUser && codeBlock != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { onCopyCode(codeBlock.code) },
                            label = { Text("Copy code") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) },
                        )
                        AssistChip(
                            onClick = { onDownloadCode(codeBlock.code, codeBlock.fileName) },
                            label = { Text("Download") },
                            leadingIcon = { Icon(Icons.Default.Download, null, Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }
    }
}

private data class CodeBlock(val language: String, val code: String, val fileName: String)

private fun extractFirstCodeBlock(text: String): CodeBlock? {
    val regex = Regex("""```([A-Za-z0-9_+#.\-]*)\s*\n([\s\S]*?)```""", RegexOption.MULTILINE)
    val match = regex.find(text) ?: return null
    val language = match.groupValues[1].ifBlank { detectLanguageFromText(text) }.lowercase()
    val code = match.groupValues[2].trim()
    if (code.isBlank()) return null
    return CodeBlock(language, code, buildFileName(language))
}

private fun detectLanguageFromText(text: String): String {
    val lower = text.lowercase()
    return when {
        "python" in lower || "питон" in lower || "пайтон" in lower -> "python"
        "kotlin" in lower || "котлин" in lower -> "kotlin"
        "javascript" in lower || "js" in lower || "джаваскрипт" in lower -> "javascript"
        "typescript" in lower || "ts" in lower -> "typescript"
        "java" in lower || "джава" in lower -> "java"
        "c++" in lower || "cpp" in lower -> "cpp"
        "c#" in lower || "csharp" in lower || "си шарп" in lower -> "csharp"
        "rust" in lower || "раст" in lower -> "rust"
        "go" in lower || "golang" in lower -> "go"
        "html" in lower -> "html"
        "css" in lower -> "css"
        "php" in lower -> "php"
        "ruby" in lower -> "ruby"
        "swift" in lower -> "swift"
        "dart" in lower -> "dart"
        "bash" in lower || "shell" in lower -> "bash"
        else -> "txt"
    }
}

private fun buildFileName(language: String): String {
    val extension = when (language.lowercase()) {
        "python", "py" -> "py"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "java" -> "java"
        "kotlin", "kt" -> "kt"
        "swift" -> "swift"
        "c" -> "c"
        "cpp", "c++" -> "cpp"
        "csharp", "c#", "cs" -> "cs"
        "go", "golang" -> "go"
        "rust", "rs" -> "rs"
        "php" -> "php"
        "ruby", "rb" -> "rb"
        "dart" -> "dart"
        "r" -> "r"
        "julia", "jl" -> "jl"
        "lua" -> "lua"
        "perl", "pl" -> "pl"
        "bash", "sh", "shell" -> "sh"
        "powershell", "ps1" -> "ps1"
        "sql" -> "sql"
        "html" -> "html"
        "css" -> "css"
        "scss" -> "scss"
        "xml" -> "xml"
        "json" -> "json"
        "yaml", "yml" -> "yml"
        "markdown", "md" -> "md"
        "scala" -> "scala"
        "groovy" -> "groovy"
        "haskell", "hs" -> "hs"
        "elixir", "ex" -> "ex"
        "erlang", "erl" -> "erl"
        "clojure", "clj" -> "clj"
        "fsharp", "f#", "fs" -> "fs"
        "vb", "vb.net", "visualbasic" -> "vb"
        "objective-c", "objc" -> "m"
        "matlab", "octave" -> "m"
        "assembly", "asm" -> "asm"
        "zig" -> "zig"
        "nim" -> "nim"
        "crystal", "cr" -> "cr"
        "d" -> "d"
        "fortran" -> "f90"
        "cobol" -> "cob"
        "pascal" -> "pas"
        "delphi" -> "pas"
        "prolog" -> "pl"
        "lisp", "scheme" -> "scm"
        "solidity", "sol" -> "sol"
        "vyper", "vy" -> "vy"
        "move" -> "move"
        "apex" -> "cls"
        else -> "txt"
    }
    return "novamind_code.$extension"
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NovaMind code", text))
    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
}

@Composable
private fun TypingBubble(shortName: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProviderAvatar(shortName, accent, 34)
        Spacer(Modifier.width(10.dp))
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("Thinking…", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
