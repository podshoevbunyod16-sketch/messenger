package com.agon.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.novaDataStore by preferencesDataStore(name = "novamind_secure_local_settings")

data class ProviderSettings(
    val activeProviderId: String = "openrouter",
    val selectedModels: Map<String, String> = novaProviders.associate { it.id to it.models.first() },
    val apiKeys: Map<String, String> = emptyMap(),
    val statuses: Map<String, String> = novaProviders.associate { it.id to STATUS_EMPTY },
    val fetchedModels: Map<String, List<String>> = emptyMap(),
    val backendUrl: String = "http://127.0.0.1:3000",
)

class NovaMindRepository(private val context: Context) {
    private val activeProviderKey = stringPreferencesKey("active_provider")
    private val backendUrlKey = stringPreferencesKey("backend_url")
    private fun apiKey(providerId: String) = stringPreferencesKey("api_key_$providerId")
    private fun statusKey(providerId: String) = stringPreferencesKey("status_$providerId")
    private fun modelKey(providerId: String) = stringPreferencesKey("model_$providerId")
    private fun fetchedModelsKey(providerId: String) = stringPreferencesKey("fetched_models_$providerId")

    val settings: Flow<ProviderSettings> = context.novaDataStore.data.map { preferences ->
        val selectedModels = novaProviders.associate { provider ->
            provider.id to (preferences[modelKey(provider.id)] ?: provider.models.first())
        }
        val keys = novaProviders.associate { provider ->
            provider.id to (preferences[apiKey(provider.id)] ?: "")
        }
        val statuses = novaProviders.associate { provider ->
            provider.id to (preferences[statusKey(provider.id)] ?: if (keys[provider.id].isNullOrBlank()) STATUS_EMPTY else STATUS_CONNECTED)
        }
        val fetchedModels = novaProviders.associate { provider ->
            provider.id to preferences[fetchedModelsKey(provider.id)].orEmpty().split("\n").filter { it.isNotBlank() }
        }.filterValues { it.isNotEmpty() }
        ProviderSettings(
            activeProviderId = preferences[activeProviderKey] ?: "openrouter",
            selectedModels = selectedModels,
            apiKeys = keys,
            statuses = statuses,
            fetchedModels = fetchedModels,
            backendUrl = preferences[backendUrlKey] ?: "http://127.0.0.1:3000",
        )
    }

    suspend fun setBackendUrl(url: String) {
        context.novaDataStore.edit { it[backendUrlKey] = url.trim() }
    }

    suspend fun setActiveProvider(providerId: String) {
        context.novaDataStore.edit { it[activeProviderKey] = providerId }
    }

    suspend fun saveApiKey(providerId: String, value: String) {
        context.novaDataStore.edit { preferences ->
            preferences[apiKey(providerId)] = value.trim()
            preferences[statusKey(providerId)] = when {
                value.isBlank() -> STATUS_EMPTY
                value.trim().length < 12 -> STATUS_ERROR
                else -> STATUS_CONNECTED
            }
        }
    }

    suspend fun setModel(providerId: String, model: String) {
        context.novaDataStore.edit { it[modelKey(providerId)] = model }
    }

    suspend fun setStatus(providerId: String, status: String) {
        context.novaDataStore.edit { it[statusKey(providerId)] = status }
    }

    suspend fun saveFetchedModels(providerId: String, models: List<String>) {
        context.novaDataStore.edit { preferences ->
            preferences[fetchedModelsKey(providerId)] = models.distinct().take(300).joinToString("\n")
            if (models.isNotEmpty()) preferences[modelKey(providerId)] = models.first()
        }
    }

    suspend fun clearAll() {
        context.novaDataStore.edit { it.clear() }
    }
}
