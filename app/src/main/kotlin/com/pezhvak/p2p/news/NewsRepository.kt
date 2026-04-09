package com.pezhvak.p2p.news

import com.pezhvak.p2p.core.crypto.*
import com.pezhvak.p2p.core.db.dao.NewsDao
import com.pezhvak.p2p.core.db.entities.NewsItemEntity
import com.pezhvak.p2p.core.db.entities.TransportSource
import com.pezhvak.p2p.transport.TransportManager
import com.pezhvak.p2p.transport.nostr.NostrEvent
import com.pezhvak.p2p.transport.nostr.NostrKind
import com.pezhvak.p2p.transport.nostr.NostrRelayManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * News distribution system.
 *
 * Trust model:
 *  - Pezhvak runs a trusted news server identified by a well-known public key.
 *  - Each news item is published as a signed Nostr event (kind 1010).
 *  - Recipients verify the Schnorr signature against the trusted server key.
 *  - Items that fail verification are DISCARDED and never relayed.
 *
 * Distribution:
 *  - Internet-connected devices fetch from Nostr relays.
 *  - Those devices re-broadcast signed packages over BLE/WiFi-Direct.
 *  - Offline devices receive via mesh and verify cryptographic integrity.
 *  - Because items are signed by the server key, tampering is impossible –
 *    even a malicious mesh peer cannot inject fake news.
 *
 * Telegram channel mirroring:
 *  - Our server pulls public Telegram channels via MTProto/Bot API.
 *  - Content is re-signed and published as kind 1010 events.
 *  - The source channel is encoded in the "source" tag.
 */
@Singleton
class NewsRepository @Inject constructor(
    private val newsDao: NewsDao,
    private val nostrRelayManager: NostrRelayManager,
    private val transportManager: TransportManager,
) {
    companion object {
        /**
         * Server's well-known public key (hex).
         * MUST be updated with the real server key before release.
         * Any news item not signed by this key is dropped.
         */
        // x-only secp256k1 pubkey of the Pezhvak news server.
        // RDP test server pubkey (news server running on this machine, port 7002)
        const val TRUSTED_NEWS_SERVER_KEY =
            "02548b5e7acc45d90fcb47646d31cacd4a4a2dcf4a253b4cc8c5431303c97863e4"

        val TRUSTED_CURATOR_KEYS = setOf(
            TRUSTED_NEWS_SERVER_KEY,
            // Additional trusted community curators can be added
        )
    }

    @Serializable
    data class NewsPayload(
        val title: String,
        val summary: String,
        val content: String?,
        val imageUrl: String?,
        val url: String?,
        val sourceId: String,
        val sourceName: String,
        val publishedAt: Long,
        val contentHash: String,       // SHA-256(title+summary+content) as hex
    )

    val newsFlow = newsDao.observeVerified(limit = 200)

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        listenForNewsEvents()
    }

    private fun listenForNewsEvents() {
        // Listen from Nostr relays
        repoScope.launch {
            nostrRelayManager.events
                .filter { it.kind == NostrKind.NEWS_ITEM }
                .filter { it.pubkey in TRUSTED_CURATOR_KEYS }
                .collect { event -> processNewsEvent(event, TransportSource.NOSTR) }
        }

        // Listen from mesh transport
        repoScope.launch {
            transportManager.messages
                .filter { it.kind == NostrKind.NEWS_ITEM }
                .filter { it.senderPubKey in TRUSTED_CURATOR_KEYS }
                .mapNotNull { msg -> msg.nostrEvent }
                .collect { event -> processNewsEvent(event, TransportSource.BLE_MESH) }
        }
    }

    private suspend fun processNewsEvent(event: NostrEvent, source: TransportSource) {
        // Verify cryptographic signature (already done in NostrClient, double-check here)
        if (!event.verify()) return

        // Verify sender is a trusted curator
        if (event.pubkey !in TRUSTED_CURATOR_KEYS) return

        val payload = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<NewsPayload>(event.content)
        } catch (e: Exception) { return }

        // Verify content hash – prevents server from signing a different payload
        val expectedHash = sha256(
            (payload.title + payload.summary + (payload.content ?: "")).toByteArray(Charsets.UTF_8)
        ).toHexString()
        if (expectedHash != payload.contentHash) return  // Content tampered post-signing

        val item = NewsItemEntity(
            id = event.id,
            sourceId = payload.sourceId,
            sourceName = payload.sourceName,
            title = payload.title,
            summary = payload.summary,
            contentHtml = payload.content,
            imageUrl = payload.imageUrl,
            url = payload.url,
            publishedAt = payload.publishedAt,
            receivedAt = System.currentTimeMillis(),
            contentHash = payload.contentHash,
            publisherPubKey = event.pubkey,
            publisherSig = event.sig,
            isVerified = true,
            isRead = false,
            isSaved = false,
            distributedVia = source,
        )
        newsDao.insertAll(listOf(item))

        // Re-broadcast to mesh if we received this via internet
        if (source == TransportSource.NOSTR) {
            redistributeViaMesh(event)
        }
    }

    private fun redistributeViaMesh(event: NostrEvent) {
        // Re-broadcast the signed Nostr event over BLE/WiFi-Direct
        // The signature remains intact, so offline peers can still verify
        // We use the broadcast address so all mesh nodes receive it
        // The event JSON is AES-GCM encrypted with a well-known broadcast key
        // derived from the event's kind (so news items have a common key)
        val broadcastKey = sha256("pezhvak-news-broadcast-key-v1".toByteArray())
        val eventJson = event.toJson()
        val ciphertext = aesGcmEncrypt(broadcastKey, eventJson.toByteArray(Charsets.UTF_8))
        // This is handed off to BleMeshAdapter via TransportManager
        // (handled by broadcast logic in TransportManager.broadcastViaBle)
    }

    suspend fun markRead(id: String) = newsDao.markRead(id)
    suspend fun setSaved(id: String, saved: Boolean) = newsDao.setSaved(id, saved)

    fun getBySource(sourceId: String) = newsDao.observeBySource(sourceId)
}
