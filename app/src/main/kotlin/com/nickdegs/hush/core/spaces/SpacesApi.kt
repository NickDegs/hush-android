package com.nickdegs.hush.core.spaces

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class SpaceDto(
    val room_id: String,
    val name: String,
    val tier: String? = null,
    val frozen: Boolean = false,
    val legal_hold: Boolean = false,
)

@Serializable data class SpacesResp(val ok: Boolean = false, val spaces: List<SpaceDto> = emptyList(), val error: String? = null)
@Serializable data class CreateResp(val ok: Boolean = false, val room_id: String? = null, val error: String? = null, val limit: Int? = null, val owned: Int? = null)
@Serializable data class SimpleResp(val ok: Boolean = false, val error: String? = null)

@Serializable data class ListReq(val user_token: String)
@Serializable data class CreateReq(
    val user_token: String, val name: String,
    val platform: String = "android", val purchase_token: String? = null, val product_id: String? = null,
)
@Serializable data class SyncReq(
    val user_token: String,
    val platform: String = "android", val purchase_token: String? = null, val product_id: String? = null,
)
@Serializable data class InviteReq(val user_token: String, val room_id: String, val phone: String)
@Serializable data class MemberReq(val user_token: String, val room_id: String, val target_uid: String, val level: Int? = null)

interface SpacesApi {
    @POST("api/spaces/list") suspend fun list(@Body b: ListReq): SpacesResp
    @POST("api/spaces/create") suspend fun create(@Body b: CreateReq): CreateResp
    @POST("api/spaces/sync-entitlement") suspend fun sync(@Body b: SyncReq): SimpleResp
    @POST("api/spaces/invite") suspend fun invite(@Body b: InviteReq): SimpleResp
    @POST("api/spaces/kick") suspend fun kick(@Body b: MemberReq): SimpleResp
    @POST("api/spaces/role") suspend fun role(@Body b: MemberReq): SimpleResp
}

/** Telefon → Matrix kullanıcı kimliği (@p<rakamlar>:chat.nickdegs.com). */
fun phoneToUid(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return "@p$digits:chat.nickdegs.com"
}
