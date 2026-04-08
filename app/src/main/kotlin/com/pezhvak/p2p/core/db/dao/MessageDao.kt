package com.pezhvak.p2p.core.db.dao

import androidx.room.*
import com.pezhvak.p2p.core.db.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC")
    fun observeMessages(id: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun getMessages(id: String, limit: Int = 50, offset: Int = 0): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE eventId = :eventId")
    suspend fun updateStatus(eventId: String, status: DeliveryStatus)

    @Query("UPDATE messages SET seenByPeerAt = :ts WHERE eventId = :eventId")
    suspend fun markSeen(eventId: String, ts: Long)

    @Query("UPDATE messages SET isDeleted = 1, deletedAt = :ts WHERE eventId = :eventId")
    suspend fun softDelete(eventId: String, ts: Long)

    @Query("SELECT * FROM messages WHERE eventId = :id")
    suspend fun getById(id: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :id AND deliveryStatus != 'SEEN'")
    suspend fun unreadCount(id: String): Int

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeThread(threadId: String): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteConversation(id: String)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("UPDATE conversations SET lastMessageAt = :ts, lastMessagePreview = :preview, unreadCount = unreadCount + 1 WHERE id = :id")
    suspend fun updateLastMessage(id: String, ts: Long, preview: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE conversations SET isMuted = :muted WHERE id = :id")
    suspend fun setMuted(id: String, muted: Boolean)

    @Query("UPDATE conversations SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE conversations SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts WHERE isBlocked = 0 ORDER BY displayName ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE pubKeyHex = :pubKey")
    suspend fun get(pubKey: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE pubKeyHex = :pubKey")
    suspend fun setBlocked(pubKey: String, blocked: Boolean)

    @Query("UPDATE contacts SET isTrusted = :trusted WHERE pubKeyHex = :pubKey")
    suspend fun setTrusted(pubKey: String, trusted: Boolean)

    @Query("UPDATE contacts SET lastSeenAt = :ts, lastSeenTransport = :transport WHERE pubKeyHex = :pubKey")
    suspend fun updateLastSeen(pubKey: String, ts: Long, transport: TransportSource)

    @Query("SELECT * FROM contacts WHERE pubKeyHex LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<ContactEntity>
}

@Dao
interface NewsDao {
    @Query("SELECT * FROM news_items WHERE isVerified = 1 ORDER BY publishedAt DESC LIMIT :limit")
    fun observeVerified(limit: Int = 100): Flow<List<NewsItemEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<NewsItemEntity>)

    @Query("UPDATE news_items SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE news_items SET isSaved = :saved WHERE id = :id")
    suspend fun setSaved(id: String, saved: Boolean)

    @Query("SELECT * FROM news_items WHERE sourceId = :sourceId ORDER BY publishedAt DESC LIMIT :limit")
    fun observeBySource(sourceId: String, limit: Int = 50): Flow<List<NewsItemEntity>>
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE isSubscribed = 1 ORDER BY name ASC")
    fun observeSubscribed(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY subscriberCount DESC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: ChannelEntity)

    @Query("UPDATE channels SET isSubscribed = :subscribed WHERE id = :id")
    suspend fun setSubscribed(id: String, subscribed: Boolean)
}

@Dao
interface ForumDao {
    @Query("SELECT * FROM forum_threads WHERE forumId = :forumId ORDER BY isPinned DESC, lastReplyAt DESC")
    fun observeThreads(forumId: String): Flow<List<ForumThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(thread: ForumThreadEntity)

    @Query("UPDATE forum_threads SET replyCount = replyCount + 1, lastReplyAt = :ts WHERE id = :id")
    suspend fun incrementReply(id: String, ts: Long)
}
