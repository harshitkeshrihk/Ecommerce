package com.example.vishnu.di

import android.net.http.HttpResponseCache.install
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Singleton because we only need ONE connection
object SupabaseModule {

    @OptIn(SupabaseInternal::class)
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://wciyfeqbczhrjifxlszz.supabase.co",
            supabaseKey = "sb_publishable_KaxV45NXWYXd12ClY2lUPQ_FbpiTWY4"
        ) {
            install(Postgrest) // This plugin handles the Database
            install(Auth) // <--- Add this line
            install(Storage)
            defaultSerializer = KotlinXSerializer(
                Json {
                    ignoreUnknownKeys = true // <--- THIS FIXES THE CRASH
                    isLenient = true
                }
            )
            httpConfig {
                install(io.ktor.client.plugins.HttpTimeout) {
                    requestTimeoutMillis = 20_000 // 60 seconds
                    connectTimeoutMillis = 20_000
                    socketTimeoutMillis = 20_000
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideSupabaseAuth(client: SupabaseClient): Auth {
        return client.auth
    }

    // Helper to inject the Postgrest plugin directly
    @Provides
    @Singleton
    fun providePostgrest(client: SupabaseClient): Postgrest {
        return client.postgrest
    }

    @Provides
    @Singleton
    fun provideStorage(client: SupabaseClient): Storage {
        return client.storage // Extracts the Storage plugin from the client
    }
}