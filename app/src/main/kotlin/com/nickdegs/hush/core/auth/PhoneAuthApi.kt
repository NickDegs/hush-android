package com.nickdegs.hush.core.auth

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

// nickdegs.com/api/phone/{start,verify} — Twilio Verify ile telefon kayıt/giriş.
// iOS PhoneAuthClient'in birebir karşılığı.
interface PhoneAuthApi {
    @POST("api/phone/start")
    suspend fun start(@Body req: StartReq): StartResp

    @POST("api/phone/verify")
    suspend fun verify(@Body req: VerifyReq): VerifyResp
}

@Serializable
data class StartReq(val phone: String)

@Serializable
data class StartResp(val status: String? = null, val expires_in_seconds: Int? = null, val demo: Boolean? = null)

@Serializable
data class VerifyReq(
    val phone: String,
    val code: String,
    val display_name: String? = null,
    // Anti-piracy: Android istemci kimliği + Play Integrity token'ı.
    // Sunucu bunu Google ile doğrular; geçersizse access_token vermez.
    val platform: String = "android",
    val integrity_token: String? = null,
)

@Serializable
data class VerifyResp(
    val user_id: String,
    val access_token: String,
    val homeserver: String,
    val display_name: String? = null,
)
