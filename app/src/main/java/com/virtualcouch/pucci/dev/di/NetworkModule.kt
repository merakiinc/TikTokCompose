package com.virtualcouch.pucci.dev.di

import com.virtualcouch.pucci.dev.data.api.AuthApi
import com.virtualcouch.pucci.dev.data.api.RedditApi
import com.virtualcouch.pucci.dev.data.api.SocialApi
import com.virtualcouch.pucci.dev.util.AuthInterceptor
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
    @Named("auth_oklight")
    fun provideAuthOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
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
    @Named("authenticated_okhttp")
    fun provideAuthenticatedOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @Named("cloud_okhttp")
    fun provideCloudOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build() // TOTALMENTE LIMPO, sem AuthInterceptor
    }

    @Provides
    @Singleton
    @Named("reddit_retrofit")
    fun provideRedditRetrofit(@Named("authenticated_okhttp") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .baseUrl("https://old.reddit.com/")
            .build()
    }

    @Provides
    @Singleton
    @Named("virtual_couch_retrofit")
    fun provideVirtualCouchRetrofit(@Named("authenticated_okhttp") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .baseUrl("https://api2.pucci.dev/")
            .build()
    }

    @Provides
    @Singleton
    @Named("auth_retrofit")
    fun provideAuthRetrofit(@Named("auth_oklight") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .baseUrl("https://api2.pucci.dev/")
            .build()
    }

    @Provides
    @Singleton
    @Named("cloud_retrofit")
    fun provideCloudRetrofit(@Named("cloud_okhttp") okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl("https://api2.pucci.dev/") // Base URL fake para o @Url funcionar
            .build()
    }

    @Provides
    @Singleton
    fun provideRedditApi(@Named("reddit_retrofit") retrofit: Retrofit): RedditApi {
        return retrofit.create(RedditApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApi(@Named("auth_retrofit") retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCalendarApi(@Named("virtual_couch_retrofit") retrofit: Retrofit): com.virtualcouch.pucci.dev.data.api.CalendarApi {
        return retrofit.create(com.virtualcouch.pucci.dev.data.api.CalendarApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSocialApi(@Named("virtual_couch_retrofit") retrofit: Retrofit): SocialApi {
        return retrofit.create(SocialApi::class.java)
    }

    @Provides
    @Singleton
    @Named("cloud_social_api")
    fun provideSocialApiCloud(@Named("cloud_retrofit") retrofit: Retrofit): SocialApi {
        return retrofit.create(SocialApi::class.java)
    }
}
