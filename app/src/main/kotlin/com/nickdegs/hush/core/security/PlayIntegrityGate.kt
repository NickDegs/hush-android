package com.nickdegs.hush.core.security

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * Play Integrity API kapısı — anti-piracy / anti-tamper.
 *
 * Token üretmek için cihazın GERÇEK olması, uygulamanın Google Play'den kurulu ve
 * KURCALANMAMIŞ olması şart. Korsan/sideload/emülatör/modlu APK geçerli token üretemez.
 * Token sunucuya gönderilir; sunucu Google ile doğrular ve verdict kötüyse Matrix
 * access_token VERMEZ (hush_chat_api.py /api/phone/verify).
 */
object PlayIntegrityGate {
    // film-ozet (NickDegs Play) Google Cloud proje numarası.
    // Play Console > App integrity bu projeye bağlı olmalı, Play Integrity API açık olmalı.
    private const val CLOUD_PROJECT_NUMBER = 909630333798L

    /**
     * Telefona bağlı nonce (base64url). Sunucu aynı nonce'u üretip decoded token'daki
     * requestDetails.nonce ile karşılaştırır → token başka akıştan replay edilemez.
     */
    fun nonceFor(phone: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest("hush:$phone".toByteArray())
        return Base64.encodeToString(d, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Play Integrity token üretir. Başarısız olursa (Play yok / ağ yok / hata) null döner.
     * Sunucu null/eksik token'da fail-open davranır (API yapılandırılana kadar app kırılmaz),
     * ama GEÇERSIZ verdict gelirse fail-closed (engeller).
     */
    suspend fun token(context: Context, phone: String): String? = try {
        val manager = IntegrityManagerFactory.create(context.applicationContext)
        val response = manager.requestIntegrityToken(
            IntegrityTokenRequest.builder()
                .setNonce(nonceFor(phone))
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                .build()
        ).await()
        response.token()
    } catch (e: Exception) {
        null
    }
}
