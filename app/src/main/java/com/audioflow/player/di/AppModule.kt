package com.audioflow.player.di

import android.content.Context
import com.audioflow.player.data.local.LocalMusicScanner
import com.audioflow.player.data.remote.YouTubeMetadataFetcher
import com.audioflow.player.data.repository.MediaRepository
import com.audioflow.player.service.PlayerController
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
    fun provideLocalMusicScanner(
        @ApplicationContext context: Context
    ): LocalMusicScanner {
        return LocalMusicScanner(context)
    }
    
    @Provides
    @Singleton
    fun provideYouTubeMetadataFetcher(): YouTubeMetadataFetcher {
        return YouTubeMetadataFetcher()
    }
    
    @Provides
    @Singleton
    fun provideMediaRepository(
        localMusicScanner: LocalMusicScanner,
        youTubeMetadataFetcher: YouTubeMetadataFetcher
    ): MediaRepository {
        return MediaRepository(localMusicScanner, youTubeMetadataFetcher)
    }
    
    @Provides
    @Singleton
    fun providePlayerController(
        @ApplicationContext context: Context
    ): PlayerController {
        return PlayerController(context)
    }
}
