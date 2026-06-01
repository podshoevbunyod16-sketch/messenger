package com.agon.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agon.app.data.STATUS_CONNECTED
import com.agon.app.data.novaProviders
import com.agon.app.viewmodel.NovaMindViewModel

@Composable
fun HomeScreen(
    viewModel: NovaMindViewModel,
    onOpenChat: () -> Unit,
    onOpenKeys: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val activeProvider = novaProviders.firstOrNull { it.id == settings.activeProviderId } ?: novaProviders.first()
    val connectedCount = settings.statuses.values.count { it == STATUS_CONNECTED }
    val progress by animateFloatAsState(
        targetValue = connectedCount / novaProviders.size.toFloat(),
        animationSpec = tween(700),
        label = "providerProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF070A12),
                        Color(0xFF0B1020),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
    ) {
        AmbientOrbs()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { NovaHero(activeProvider.name, settings.selectedModels[activeProvider.id].orEmpty(), onOpenChat, onOpenKeys) }
            item { ConnectionOverview(connectedCount, progress, onOpenKeys) }
            item { ProviderRail(settings.activeProviderId, settings.statuses, viewModel::setActiveProvider) }
            item { FeatureGrid() }
            item { WorkflowPanel() }
            item { Spacer(Modifier.height(84.dp)) }
        }
    }
}

@Composable
private fun AmbientOrbs() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x55645CFF), Color.Transparent)),
            radius = size.minDimension * 0.48f,
            center = Offset(size.width * 0.08f, size.height * 0.03f),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x4430F0D0), Color.Transparent)),
            radius = size.minDimension * 0.36f,
            center = Offset(size.width * 1.02f, size.height * 0.27f),
        )
    }
}

@Composable
private fun NovaHero(activeProvider: String, activeModel: String, onOpenChat: () -> Unit, onOpenKeys: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xCC101828)),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x334E6BFF), RoundedCornerShape(32.dp)),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF172554),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF67E8F9))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("NovaMind", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("multi-provider AI workspace", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFA7B0C8))
                }
                AssistChip(onClick = onOpenKeys, label = { Text(activeProvider) }, leadingIcon = { Icon(Icons.Default.RadioButtonChecked, null, Modifier.size(16.dp)) })
            }
            Text(
                "A native mobile clone of the original web flow: hero dashboard, chat console, model switching, API key vault and minimal dark visual language.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD5DCF0),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Dark glass UI", "Bottom navigation", "Local key storage", "Provider status", activeModel.ifBlank { "model selector" }).forEach {
                    Surface(shape = RoundedCornerShape(50), color = Color(0x1F67E8F9)) {
                        Text(it, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color(0xFFCFFAFE), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onOpenChat, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Bolt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start chat")
                }
                OutlinedButton(onClick = onOpenKeys, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Key, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("API Keys")
                }
            }
        }
    }
}

@Composable
private fun ConnectionOverview(connectedCount: Int, progress: Float, onOpenKeys: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(28.dp),
        onClick = onOpenKeys,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("API vault health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$connectedCount of ${novaProviders.size} providers connected", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), strokeCap = StrokeCap.Round)
        }
    }
}

@Composable
private fun ProviderRail(activeId: String, statuses: Map<String, String>, setActive: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Providers", "Tap to route the chat through a platform")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(novaProviders) { provider ->
                val selected = provider.id == activeId
                val connected = statuses[provider.id] == STATUS_CONNECTED
                Surface(
                    modifier = Modifier
                        .width(156.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { setActive(provider.id) }
                        .border(1.dp, if (selected) Color(provider.accent) else Color(0x1FFFFFFF), RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = if (selected) Color(provider.accent).copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProviderAvatar(provider.shortName, Color(provider.accent), 40)
                        Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(provider.freeTier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        AnimatedVisibility(connected) {
                            Text("Connected", color = Color(0xFF86EFAC), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderAvatar(shortName: String, color: Color, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 2).dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.45f))))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape((size / 2).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(shortName, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FeatureGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Mobile experience", "Key website blocks adapted to native UX")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard("Chat console", "Message bubbles, provider badges, typing state", Icons.Default.AutoAwesome, Modifier.weight(1f))
                FeatureCard("Model lab", "Quick chips and routing hierarchy", Icons.Default.Tune, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard("Secure local", "DataStore persistence on device", Icons.Default.Security, Modifier.weight(1f))
                FeatureCard("Dark native", "Material 3 surfaces and motion", Icons.Default.Bolt, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f)), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkflowPanel() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1324)), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Flow", "Connect → choose model → chat → compare")
            listOf("1" to "Save provider key", "2" to "Mark active platform", "3" to "Select model", "4" to "Run chat from the console").forEach { (index, text) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(30.dp)) { Box(contentAlignment = Alignment.Center) { Text(index, fontWeight = FontWeight.Bold) } }
                    Spacer(Modifier.width(12.dp))
                    Text(text, color = Color(0xFFE6ECFF))
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
