package com.example.vishnu

import android.app.Application
import com.google.android.gms.maps.MapsInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CrockeryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Google Maps
        MapsInitializer.initialize(this)
    }
}