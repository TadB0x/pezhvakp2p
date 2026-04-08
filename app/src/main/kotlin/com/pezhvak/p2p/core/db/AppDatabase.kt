package com.pezhvak.p2p.core.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pezhvak.p2p.core.db.dao.*
import com.pezhvak.p2p.core.db.entities.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * Room database with SQLCipher full-disk encryption.
 * The encryption key is derived from the user's identity key via HKDF,
 * so the database is only readable with knowledge of the user's private key.
 */
@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ContactEntity::class,
        NewsItemEntity::class,
        ChannelEntity::class,
        ForumThreadEntity::class,
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao
    abstract fun newsDao(): NewsDao
    abstract fun channelDao(): ChannelDao
    abstract fun forumDao(): ForumDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, dbKey: ByteArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = SQLiteDatabase.getBytes(
                    android.util.Base64.encodeToString(dbKey, android.util.Base64.NO_WRAP).toCharArray()
                )
                val factory = SupportFactory(passphrase)
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pezhvak.db"
                )
                .openHelperFactory(factory)
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { INSTANCE = it }
            }
        }

        private val MIGRATIONS = arrayOf<Migration>(
            // Future migrations go here
        )
    }
}

class Converters {
    @TypeConverter fun fromConversationType(v: ConversationType): String = v.name
    @TypeConverter fun toConversationType(v: String): ConversationType = ConversationType.valueOf(v)
    @TypeConverter fun fromDeliveryStatus(v: DeliveryStatus): String = v.name
    @TypeConverter fun toDeliveryStatus(v: String): DeliveryStatus = DeliveryStatus.valueOf(v)
    @TypeConverter fun fromTransportSource(v: TransportSource): String = v.name
    @TypeConverter fun toTransportSource(v: String): TransportSource = TransportSource.valueOf(v)
}
