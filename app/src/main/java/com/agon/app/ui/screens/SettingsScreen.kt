package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.AiProvider
import com.agon.app.data.STATUS_CONNECTED
import com.agon.app.data.STATUS_EMPTY
import com.agon.app.data.STATUS_ERROR
import com.agon.app.data.novaProviders
import com.agon.app.viewmodel.NovaMindViewModel

@Composable
fun SettingsScreen(viewModel: NovaMindViewModel) {
    val settings by viewModel.settings.collectAsState()
    val activeProvider = novaProviders.firstOrNull { it.id == settings.activeProviderId } ?: novaProviders.first()
    val connected = settings.statuses.values.count { it == STATUS_CONNECTED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070A12), MaterialTheme.colorScheme.background))),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsHero(activeProvider.name, settings.selectedModels[activeProvider.id].orEmpty(), connected, viewModel::clearKeys)
        }
        item { SectionTitle("Settings / API Keys", "One clean card per provider with status, model and local key storage") }
        items(novaProviders) { provider ->
            ProviderKeyCard(
                provider = provider,
                savedKey = settings.apiKeys[provider.id].orEmpty(),
                status = settings.statuses[provider.id] ?: STATUS_EMPTY,
                active = settings.activeProviderId == provider.id,
                selectedModel = settings.selectedModels[provider.id] ?: provider.models.first(),
                availableModels = settings.fetchedModels[provider.id].orEmpty().ifEmpty { provider.models },
                onSave = { viewModel.saveApiKey(provider.id, it) },
                onTest = { viewModel.testProvider(provider.id) },
                onFetchModels = { viewModel.fetchModels(provider.id) },
                onActivate = { viewModel.setActiveProvider(provider.id) },
                onModel = { viewModel.setModel(provider.id, it) },
            )
        }
        item { Spacer(Modifier.height(84.dp)) }
    }
}

@Composable
private fun SettingsHero(activeProvider: String, model: String, connected: Int, onClear: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xE6101828)), shape = RoundedCornerShape(32.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Key, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Provider vault", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Active: $activeProvider · ${model.ifBlank { "select model" }}", color = Color(0xFFB7C2DD), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = {}, label = { Text("$connected connected") }, leadingIcon = { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) })
                OutlinedButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun BackendUrlCard(backendUrl: String, onSave: (String) -> Unit) {
    var draft by remember(backendUrl) { mutableStateOf(backendUrl) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Backend URL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("Termux on the same phone: http://127.0.0.1:3000. If backend runs elsewhere, use its HTTPS URL.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Server base URL") },
                placeholder = { Text("https://your-backend.example.com") },
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                ),
            )
            Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, null, Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save backend URL")
            }
        }
    }
}

@Composable
private fun ProviderKeyCard(
    provider: AiProvider,
    savedKey: String,
    status: String,
    active: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    onSave: (String) -> Unit,
    onTest: () -> Unit,
    onFetchModels: () -> Unit,
    onActivate: () -> Unit,
    onModel: (String) -> Unit,
) {
    var draft by remember(provider.id, savedKey) { mutableStateOf(savedKey) }
    var visible by remember(provider.id) { mutableStateOf(false) }
    val statusColor = when (status) {
        STATUS_CONNECTED -> Color(0xFF86EFAC)
        STATUS_ERROR -> Color(0xFFFF8A8A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (status) {
        STATUS_CONNECTED -> "connected"
        STATUS_ERROR -> "error"
        else -> "empty"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = if (active) Color(provider.accent).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier.border(1.dp, if (active) Color(provider.accent) else Color(0x14FFFFFF), RoundedCornerShape(30.dp)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderAvatar(provider.shortName, Color(provider.accent), 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text(provider.description, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                StatusPill(statusText, statusColor)
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${provider.name} API key") },
                placeholder = { Text("Paste key or token") },
                leadingIcon = { Icon(Icons.Default.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password,
                ),
                shape = RoundedCornerShape(20.dp),
                supportingText = { Text(provider.freeTier) },
                isError = status == STATUS_ERROR,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Model", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableModels.take(36).forEach { model ->
                        FilterChip(
                            selected = model == selectedModel,
                            onClick = { onModel(model) },
                            label = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onSave(draft) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Save")
                }
                OutlinedButton(onClick = onTest, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Sync, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Test")
                }
                IconButton(onClick = onFetchModels) {
                    Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onActivate) {
                    Icon(Icons.Default.RadioButtonChecked, null, tint = if (active) Color(provider.accent) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.45f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(targetState = label, label = "status") { text ->
                Icon(if (text == "error") Icons.Default.Error else Icons.Default.Check, null, Modifier.size(14.dp), tint = color)
            }
            Spacer(Modifier.width(5.dp))
            Text(label, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
