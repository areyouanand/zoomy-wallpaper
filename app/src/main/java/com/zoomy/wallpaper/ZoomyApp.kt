package com.zoomy.wallpaper

import android.app.Application
import com.zoomy.wallpaper.util.AppUtils

class ZoomyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppUtils.init(this)
    }
}
