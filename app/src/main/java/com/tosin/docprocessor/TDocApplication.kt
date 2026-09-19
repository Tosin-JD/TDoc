package com.tosin.docprocessor

import android.app.Application
import com.tosin.docprocessor.util.ImageCacheCleaner
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TDocApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ImageCacheCleaner.prune(this)
    }
}