package com.pezhvak.p2p.transport.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a pool of Nostr relay connections.
 * Broadcasts events to all connected relays simultaneously (redundancy).
 * Deduplicates incoming events by event ID.
 *
 * Default relays can be overridden by user preferences.
 */
@Singleton
class NostrRelayManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clients = mutableMapOf<String, NostrClient>()
    private val seenEventIds = LruSet<String>(maxSize = 10_000)

    private val _events = MutableSharedFlow<NostrEvent>(extraBufferCapacity = 1024)
    val events: SharedFlow<NostrEvent> = _events.asSharedFlow()

    private val _relayStatuses = MutableStateFlow<Map<String, NostrClient.ConnectionState>>(emptyMap())
    val relayStatuses: StateFlow<Map<String, NostrClient.ConnectionState>> = _relayStatuses.asStateFlow()

    companion object {
        val DEFAULT_RELAYS = listOf(
            "wss://relay.damus.io",
            "wss://relay.nostr.band",
            "wss://nos.lol",
            "wss://relay.snort.social",
            "wss://nostr.wine",
            "wss://relay.primal.net",
        )
    }

    fun start(relayUrls: List<String> = DEFAULT_RELAYS) {
        relayUrls.forEach { url -> addRelay(url) }
    }

    fun addRelay(url: String) {
        if (clients.containsKey(url)) return
        val client = NostrClient(url, scope)
        clients[url] = client
        client.connect()

        // Forward events, deduplicating across relays
        scope.launch {
            client.incomingEvents.collect { event ->
                if (seenEventIds.add(event.id)) {
                    _events.emit(event)
                }
            }
        }
        // Track connection statuses
        scope.launch {
            client.connectionState.collect { state ->
                _relayStatuses.value = clients.mapValues { it.value.connectionState.value }
            }
        }
    }

    fun removeRelay(url: String) {
        clients.remove(url)?.disconnect()
    }

    /**
     * Publish to all connected relays. Returns map of relay→result.
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
