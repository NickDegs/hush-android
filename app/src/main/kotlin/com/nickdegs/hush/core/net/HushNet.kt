package com.nickdegs.hush.core.net

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Hush istemci imzası — web/non-app erişimini engelleme katmanı.
 *
 * Uygulama her HTTP isteğine gizli `X-Hush-Client` başlığını ekler. Sunucu (nginx)
 * bu başlığı olmayan istekleri reddeder → tarayıcı / Element / curl gibi non-app
 * istemciler Matrix API'ye erişemez. APK decompile edilerek çıkarılabilir (tek başına
 * mutlak değil), ama Play Integrity ile birlikte kurcalanmamış app şartı eklenince
 * pratik web erişimi tamamen kapanır.
 *
 * NOT: Sunucu enforcement'ı paylaşılan Matrix altyapısını (Element/B2B/federation)
 * etkiler; matrix oturumuyla koordine edilerek kademeli açılacak. App tarafı (başlığı
 * göndermek) zararsız ve geriye dönük uyumlu — hiçbir şeyi kırmaz.
 */
object HushNet {
    // Sunucudaki nginx kuralıyla BİREBİR aynı olmalı.
    const val CLIENT_SIGNATURE = "99a12fabf9ee0c2c7d8cce3ad3182be6a4490dbc17ce894b"

    val signatureInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("X-Hush-Client", CLIENT_SIGNATURE)
            .header("X-Hush-Platform", "android")
            .build()
        chain.proceed(req)
    }
}
