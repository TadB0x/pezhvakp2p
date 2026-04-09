package com.pezhvak.p2p.transport.nostr

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.relayDataStore by preferencesDataStore(name = "relay_prefs")
private val RELAY_URLS_KEY = stringSetPreferencesKey("relay_urls")

/**
 * Manages a pool of Nostr relay connections with DataStore persistence.
 * Broadcasts events to all connected relays simultaneously (redundancy).
 * Deduplicates incoming events by event ID.
 */
@Singleton
class NostrRelayManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = mutableMapOf<String, NostrClient>()
    private val seenEventIds = LruSet<String>(maxSize = 10_000)

    private val _events = MutableSharedFlow<NostrEvent>(extraBufferCapacity = 1024)
    val events: SharedFlow<NostrEvent> = _events.asSharedFlow()

    private val _relayStatuses = MutableStateFlow<Map<String, NostrClient.ConnectionState>>(emptyMap())
    val relayStatuses: StateFlow<Map<String, NostrClient.ConnectionState>> = _relayStatuses.asStateFlow()

    // Exposed for UI — persisted relay URL list
    val relayUrls: StateFlow<List<String>> = context.relayDataStore.data
        .map { prefs -> prefs[RELAY_URLS_KEY]?.toList()?.sorted() ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    companion object {
        // No default public relays — user adds their own
        val DEFAULT_RELAYS: List<String> = emptyList()
    }

    fun start() {
        // Load persisted relays and connect
        scope.launch {
            relayUrls.first().forEach { url -> connectRelay(url) }
        }
    }

    fun addRelay(url: String) {
        scope.launch {
            context.relayDataStore.edit { prefs ->
                val current = prefs[RELAY_URLS_KEY] ?: emptySet()
                prefs[RELAY_URLS_KEY] = current + url
            }
            connectRelay(url)
        }
    }

    fun removeRelay(url: String) {
        scope.launch {
            context.relayDataStore.edit { prefs ->
                val current = prefs[RELAY_URLS_KEY] ?: emptySet()
                prefs[RELAY_URLS_KEY] = current - url
            }
            clients.remove(url)?.disconnect()
            _relayStatuses.value = _relayStatuses.value - url
        }
    }

    private fun connectRelay(url: String) {
        if (clients.containsKey(url)) return
        val client = NostrClient(url, scope)
        clients[url] = client
        client.connect()

        scope.launch {
            client.incomingEvents.collect { event ->
                if (seenEventIds.add(event.id)) {
                    _events.emit(event)
                }
            }
        }
        scope.launch {
            client.connectionState.collect {
                _relayStatuses.value = clients.mapValues { it.value.connectionState.value }
            }
        }
    }

    /**
     * Publish to all connected relays.
     */
    suspend fun publish(event: NostrEvent): Map<String, NostrClient.PublishResult> {
        return clients.values
            .filter { it.isConnected() }
            .map { client ->
                scope.async { client.relayUrl to client.publish(event) }
            }
            .awaitAll()
            .toMap()
    }

    /**
     * Subscribe across all relays with the same filter.
     * Returns a merged, deduplicated flow.
     */
    fun subscribe(subscriptionId: String, filter: JsonObject): Flow<NostrEvent> {
        val seen = mutableSetOf<String>()
        return clients.values.map { client ->
            client.subscribe("${subscriptionId}_${client.relayUrl.hashCode()}", filter)
        }.merge().filter { event ->
            synchronized(seen) { seen.add(event.id) }
        }
    }

    fun stop() {
        clients.values.forEach { it.disconnect() }
        clients.clear()
        scope.cancel()
    }
}

/** Thread-safe LRU set for deduplication */
private class LruSet<T>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<T, Unit>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<T, Unit>) = size > maxSize
    }
    @Synchronized fun add(item: T): Boolean = map.put(item, Unit) == null
    @Synchronized fun contains(item: T): Boolean = map.containsKey(item)
}
