package com.nickdegs.hush.core.auth

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * nickdegs.com/api/phone/* — Twilio Verify ile telefon kayıt/giriş.
 * iOS PhoneAuthClient'in birebir karşılığı.
 */
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
)

@Serializable
data class VerifyResp(
    val user_id: String,
    val access_token: String,
    val homeserver: String,
    val display_name: String? = null,
)
