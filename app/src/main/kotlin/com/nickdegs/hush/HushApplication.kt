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

    /** Coil mxc medya isteklerine X-Hush-Client (web kilidi) + Authorization Bearer
     *  (authenticated media v1) ekler — yoksa foto/avatar 404, açılmaz. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor(HushNet.signatureInterceptor)
                    .addInterceptor(HushNet.mediaAuthInterceptor)
                    .build()
            )
            .build()
}
