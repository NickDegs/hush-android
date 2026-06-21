package com.nickdegs.hush

import android.app.Application

class HushApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase otomatik init (google-services plugin tarafından)
    }
}
