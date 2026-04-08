package com.pezhvak.p2p.transport.nostr

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket client for a single Nostr relay.
 * Handles:
 *  - Connection lifecycle with exponential backoff reconnection
 *  - REQ subscriptions with filter support
 *  - EVENT publishing with OK acknowledgement tracking
 *  - NOTICE and AUTH handling
 */
class NostrClient(
    val relayUrl: String,
    private val scope: CoroutineScope,
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // WebSocket – no read timeout
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<NostrEvent>(extraBufferCapacity = 256)
    val incomingEvents: SharedFlow<NostrEvent> = _incomingEvents.asSharedFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val pendingPublishes = ConcurrentHashMap<String, CompletableDeferred<PublishResult>>()
    private val activeSubscriptions = ConcurrentHashMap<String, JsonObject>()
    private val shouldReconnect = AtomicBoolean(true)
    private var reconnectJob: Job? = null
    private var reconnectDelay = 1_000L

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }
    data class PublishResult(val eventId: String, val accepted: Boolean, val message: String?)

    fun connect() {
        shouldReconnect.set(true)
        doConnect()
    }

    fun disconnect() {
        shouldReconnect.set(false)
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun doConnect() {
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(relayUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectDelay = 1_000L
                _connectionState.value = ConnectionState.CONNECTED
                // Resubscribe all active subs after reconnect
                activeSubscriptions.forEach { (subId, filter) ->
                    ws.send(NostrWire.reqMessage(subId, filter))
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                scope.launch { handleMessage(text) }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = ConnectionState.RECONNECTING
                scheduleReconnect()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (shouldReconnect.get()) {
                    _connectionState.value = ConnectionState.RECONNECTING
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect.get()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(reconnectDelay)
            reconnectDelay = minOf(reconnectDelay * 2, 60_000L)
            doConnect()
        }
    }

    private suspend fun handleMessage(text: String) {
        val arr = try { Json.parseToJsonElement(text).jsonArray } catch (e: Exception) { return }
        when (arr[0].jsonPrimitive.content) {
            "EVENT" -> {
                val event = try {
                    Json { ignoreUnknownKeys = true }.decodeFromJsonElement<NostrEvent>(arr[2])
                } catch (e: Exception) { return }
                if (event.verify()) {
                    _incomingEvents.emit(event)
                }
                // Silently drop events that fail verification
            }
            "EOSE" -> { /* end of stored events – subscription can now be considered live */ }
            "OK" -> {
                val eventId = arr[1].jsonPrimitive.content
                val accepted = arr[2].jsonPrimitive.boolean
                val msg = arr.getOrNull(3)?.jsonPrimitive?.contentOrNull
                pendingPublishes.remove(eventId)?.complete(PublishResult(eventId, accepted, msg))
            }
            "NOTICE" -> _notices.emit(arr[1].jsonPrimitive.content)
            "AUTH" -> { /* NIP-42 – future */ }
        }
    }

    /**
     * Publish an event. Returns the relay's OK response or times out after 10s.
     */
    suspend fun publish(event: NostrEvent): PublishResult {
        val deferred = CompletableDeferred<PublishResult>()
        pendingPublishes[event.id] = deferred
        val sent = webSocket?.send(event.toRelayMessage()) ?: false
        if (!sent) {
            pendingPublishes.remove(event.id)
            return PublishResult(event.id, false, "Not connected")
        }
        return withTimeoutOrNull(10_000) { deferred.await() }
            ?: PublishResult(event.id, false, "Timeout").also { pendingPublishes.remove(event.id) }
    }

    /**
     * Subscribe to events matching filters. Returns a flow of matching events.
     */
    fun subscribe(subscriptionId: String, filter: JsonObject): Flow<NostrEvent> {
        activeSubscriptions[subscriptionId] = filter
        webSocket?.send(NostrWire.reqMessage(subscriptionId, filter))
        return incomingEvents.filter { event ->
            matchesFilter(event, filter)
        }.onCompletion {
            activeSubscriptions.remove(subscriptionId)
            webSocket?.send(NostrWire.closeMessage(subscriptionId))
        }
    }

    private fun matchesFilter(event: NostrEvent, filter: JsonObject): Boolean {
        filter["kinds"]?.jsonArray?.let { kinds ->
            if (kinds.none { it.jsonPrimitive.int == event.kind }) return false
        }
        filter["authors"]?.jsonArray?.let { authors ->
            if (authors.none { it.jsonPrimitive.content == event.pubkey }) return false
        }
        filter["since"]?.jsonPrimitive?.long?.let { since ->
            if (event.created_at < since) return false
        }
        filter["until"]?.jsonPrimitive?.long?.let { until ->
            if (event.created_at > until) return false
        }
        return true
    }

    fun isConnected() = _connectionState.value == ConnectionState.CONNECTED
}
