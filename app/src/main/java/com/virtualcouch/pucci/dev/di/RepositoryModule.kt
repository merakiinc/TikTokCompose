package com.virtualcouch.pucci.dev.di

import com.virtualcouch.pucci.dev.data.api.SocialApi
import com.virtualcouch.pucci.dev.data.repository.VirtualCouchFeedRepository
import com.virtualcouch.pucci.dev.data.repository.SampleVideoDataRepository
import com.virtualcouch.pucci.dev.domain.repository.VideoDataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RepositoryModule {
    @Provides
    @Singleton
    @Named("reddit_data")
    fun provideVideoDataRepository(
        api: SocialApi
    ): VideoDataRepository {
        return VirtualCouchFeedRepository(api)
    }

    @Provides
    @Singleton
    @Named("sample_data")
    fun provideSampleVideoDataRepository(): VideoDataRepository {
        return SampleVideoDataRepository()
    }
}
