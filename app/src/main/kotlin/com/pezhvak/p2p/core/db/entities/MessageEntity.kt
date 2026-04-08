package com.pezhvak.p2p.core.db.entities

import androidx.room.*

/**
 * Persisted message – content is always stored encrypted.
 * The [contentEncrypted] field contains NIP-44 ciphertext or null for
 * channel/forum messages which use group symmetric keys.
 *
 * [contentHash] is SHA-256(canonical_plaintext) and is ALWAYS stored so
 * we can verify integrity even after decryption.
 *
 * [senderSig] is the 64-byte Schnorr signature over the event ID.
 * Any message whose sig fails verification is discarded.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("senderPubKey"),
        Index("createdAt"),
        Index("eventId", unique = true)
    ]
)
data class MessageEntity(
    @PrimaryKey val eventId: String,           // SHA-256 of canonical event JSON
    val conversationId: String,                // DM pubkey pair | group ID | channel ID
    val conversationType: ConversationType,
    val senderPubKey: String,                  // hex pubkey (32 bytes)
    val senderSig: String,                     // hex Schnorr sig (64 bytes)
    val contentEncrypted: String?,             // NIP-44 ciphertext (DMs/private groups)
    val contentPlain: String?,                 // decrypted cache (not persisted to disk in prod)
    val contentHash: String,                   // SHA-256 of plaintext for integrity
    val replyToEventId: String?,
    val mediaAttachments: String?,             // JSON array of MediaAttachment
    val reactions: String?,                    // JSON map of emoji→count
    val createdAt: Long,                       // Unix timestamp ms
    val receivedAt: Long,
    val deliveryStatus: DeliveryStatus,
    val transportSource: TransportSource,      // which transport delivered this
    val seenByPeerAt: Long?,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val threadId: String? = null,              // for forum threading
    val tags: String? = null,                  // JSON array of Nostr tags
)

enum class ConversationType { DM, GROUP, CHANNEL, FORUM }
enum class DeliveryStatus { SENDING, SENT, DELIVERED, SEEN, FAILED }
enum class TransportSource { NOSTR, BLE_MESH, BLE_P2P, WIFI_DIRECT, INTERNET, UNKNOWN }

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val type: ConversationType,
    val name: String,
    val description: String?,
    val avatarUrl: String?,
    val participantPubKeys: String,            // JSON array
    val encryptedGroupKey: String?,            // sealed for each participant
    val creatorPubKey: String,
    val createdAt: Long,
    val lastMessageAt: Long,
    val lastMessagePreview: String?,
    val unreadCount: Int,
    val isMuted: Boolean,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val nostrRelayHints: String?,              // JSON array of relay URLs
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val pubKeyHex: String,
    val displayName: String,
    val about: String?,
    val avatarUrl: String?,
    val nip05: String?,
    val nip05Verified: Boolean,
    val petname: String?,                      // local nickname
    val addedAt: Long,
    val lastSeenAt: Long,
    val lastSeenTransport: TransportSource,
    val isTrusted: Boolean,
    val isBlocked: Boolean,
    val nostrRelays: String?,                  // JSON array
)

@Entity(tableName = "news_items")
data class NewsItemEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val sourceName: String,
    val title: String,
    val summary: String,
    val contentHtml: String?,
    val imageUrl: String?,
    val url: String?,
    val publishedAt: Long,
    val receivedAt: Long,
    val contentHash: String,                   // SHA-256 for tamper detection
    val publisherPubKey: String,               // server's signing key
    val publisherSig: String,                  // signature over contentHash
    val isVerified: Boolean,
    val isRead: Boolean,
    val isSaved: Boolean,
    val distributedVia: TransportSource,       // how this item arrived
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val about: String?,
    val pictureUrl: String?,
    val creatorPubKey: String,
    val isPublic: Boolean,
    val nostrEventId: String?,
    val subscriberCount: Int,
    val createdAt: Long,
    val isSubscribed: Boolean,
    val isOwned: Boolean,
)

@Entity(tableName = "forum_threads")
data class ForumThreadEntity(
    @PrimaryKey val id: String,
    val forumId: String,
    val title: String,
    val authorPubKey: String,
    val rootMessageId: String,
    val replyCount: Int,
    val lastReplyAt: Long,
    val createdAt: Long,
    val tags: String?,
    val isLocked: Boolean,
    val isPinned: Boolean,
)
