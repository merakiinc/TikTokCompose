package com.virtualcouch.pucci.dev.di

import com.virtualcouch.pucci.dev.data.api.AuthApi
import com.virtualcouch.pucci.dev.data.api.RedditApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "VirtualCouchMobile/1.0")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    @Provides
    @Singleton
    @Named("reddit_retrofit")
    fun provideRedditRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .baseUrl("https://old.reddit.com/")
            .build()
    }

    @Provides
    @Singleton
    @Named("virtual_couch_retrofit")
    fun provideVirtualCouchRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .baseUrl("https://api2.pucci.dev/")
            .build()
    }

    @Provides
    @Singleton
    fun provideRedditApi(@Named("reddit_retrofit") retrofit: Retrofit): RedditApi {
        return retrofit.create(RedditApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApi(@Named("virtual_couch_retrofit") retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }
}
