package com.pezhvak.p2p.di

import android.content.Context
import com.pezhvak.p2p.core.crypto.hkdf
import com.pezhvak.p2p.core.db.AppDatabase
import com.pezhvak.p2p.core.db.dao.*
import com.pezhvak.p2p.core.identity.KeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: KeyManager,
    ): AppDatabase {
        // Derive DB encryption key from user's identity key
        // This ties the DB to the identity – if the private key is wiped, DB is unrecoverable
        val dbKey = hkdf(
            inputKeyMaterial = keyManager.getOrCreateIdentity().pubKeyBytes,
            salt = "pezhvak-db-salt-v1".toByteArray(),
            info = "database-encryption-key".toByteArray(),
            length = 32
        )
        return AppDatabase.getInstance(context, dbKey)
    }

    @Provides fun provideMessageDao(db: AppDatabase) = db.messageDao()
    @Provides fun provideConversationDao(db: AppDatabase) = db.conversationDao()
    @Provides fun provideContactDao(db: AppDatabase) = db.contactDao()
    @Provides fun provideNewsDao(db: AppDatabase) = db.newsDao()
    @Provides fun provideChannelDao(db: AppDatabase) = db.channelDao()
    @Provides fun provideForumDao(db: AppDatabase) = db.forumDao()
}
