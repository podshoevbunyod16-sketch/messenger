package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.STATUS_CONNECTED
import com.agon.app.data.novaProviders
import com.agon.app.viewmodel.NovaMindViewModel

@Composable
fun ModelsScreen(viewModel: NovaMindViewModel) {
    val settings by viewModel.settings.collectAsState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070A12), MaterialTheme.colorScheme.background))),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionTitle("Model router", "Select a default model for every provider")
            Spacer(Modifier.height(6.dp))
        }
        items(novaProviders) { provider ->
            val availableModels = settings.fetchedModels[provider.id].orEmpty().ifEmpty { provider.models }
            val selected = settings.selectedModels[provider.id] ?: availableModels.first()
            val active = settings.activeProviderId == provider.id
            Card(
                colors = CardDefaults.cardColors(containerColor = if (active) Color(provider.accent).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.border(1.dp, if (active) Color(provider.accent) else Color.Transparent, RoundedCornerShape(28.dp)),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderAvatar(provider.shortName, Color(provider.accent), 42)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                            Text(provider.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (settings.statuses[provider.id] == STATUS_CONNECTED) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF86EFAC))
                    }
                    Button(onClick = { viewModel.fetchModels(provider.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Fetch live models (${availableModels.size})")
                    }
                    availableModels.forEach { model ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setModel(provider.id, model) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (model == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Memory, null, Modifier.size(18.dp), tint = if (model == selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Text(model, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (model == selected) FontWeight.Bold else FontWeight.Normal)
                                if (model == selected) Text("Active", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(84.dp)) }
    }
}
