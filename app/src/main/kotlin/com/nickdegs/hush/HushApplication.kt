package com.nickdegs.hush

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.nickdegs.hush.core.net.HushNet
import okhttp3.OkHttpClient

class HushApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // Firebase otomatik init (google-services plugin tarafından)
    }

    /** Coil de mxc medya isteklerine X-Hush-Client başlığını eklesin (web erişim kilidi). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor(HushNet.signatureInterceptor)
                    .build()
            )
            .build()
}
