package com.example.vishnu.di

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class) // Available to any ViewModel
object VideoModule {

    @Provides
    @ViewModelScoped // Created once per ViewModel, reused until screen closes
    fun provideExoPlayer(app: Application): ExoPlayer {
        return ExoPlayer.Builder(app).build()
    }
}