package com.trackcats.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.trackcats.app.data.Store
import com.trackcats.app.ui.Art
import com.trackcats.app.work.Alerts

class TrackCatsApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Alerts.createChannel(this)
        // Scheduling can fail in unusual process states (and in tests); the app works without it.
        runCatching { Alerts.schedule(this) }
    }

    override fun newImageLoader(): ImageLoader = Art.newLoader(this)
}
