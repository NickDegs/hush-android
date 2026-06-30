package com.nickdegs.hush.core.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Sohbet odası (iOS MatrixRoom karşılığı). */
data class MatrixRoom(
    val id: String,
    val name: String,
    val lastMessage: String? = null,
    val lastTs: Long = 0,
    val unread: Int = 0,
    val avatarMxc: String? = null,
    val isDirect: Boolean = false,
)

/** Tek mesaj (iOS ChatMessage karşılığı). */
data class ChatMessage(
    val id: String,
    val sender: String,
    val body: String,
    val ts: Long,
    val mine: Boolean,
    val type: String = "text",   // text | image | video | audio | file
    val mediaMxc: String? = null,
)

/**
 * Matrix Client-Server API istemcisi — iOS MatrixClient'in Kotlin karşılığı.
 * OkHttp + manuel JSON (sync yapısı derin ve dinamik oda ID'li, esnek parse şart).
 */
class MatrixClient(
    homeserver: String,
    private val token: String,
    val userId: String,
) {
    private val base = homeserver.trimEnd('/').let {
        if (it.startsWith("http")) it else "https://$it"
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .addInterceptor(com.nickdegs.hush.core.net.HushNet.signatureInterceptor)
        .build()

    var syncToken: String? = null
        private set

    /** Token doğrulama sonucu (kimlik kilidi + offline engeli için). */
    enum class TokenStatus { VALID, INVALID, NO_NETWORK }

    /**
     * Token'ı sunucuda doğrular (whoami). Geçerli kimlik + internet ŞART.
     * Korsan APK token'sız/geçersiz token'la içeri giremez, internetsiz çalışamaz.
     */
    suspend fun validateToken(): TokenStatus = withContext(Dispatchers.IO) {
        try {
            http.newCall(
                Request.Builder().url("$base/_matrix/client/v3/account/whoami")
                    .header("Authorization", "Bearer $token").get().build()
            ).execute().use { resp ->
                when {
                    resp.isSuccessful -> {
                        val who = resp.body?.string()?.let {
                            json.parseToJsonElement(it).jsonObject["user_id"]?.jsonPrimitive?.contentOrNull
                        }
                        if (who == userId) TokenStatus.VALID else TokenStatus.INVALID
                    }
                    resp.code == 401 || resp.code == 403 -> TokenStatus.INVALID
                    else -> TokenStatus.NO_NETWORK
                }
            }
        } catch (e: Exception) {
            TokenStatus.NO_NETWORK   // ağ yok → offline = giriş yok
        }
    }

    private fun get(path: String): String? = run(Request.Builder().url(base + path)
        .header("Authorization", "Bearer $token").get().build())

    private fun put(path: String, body: String): String? = run(Request.Builder().url(base + path)
        .header("Authorization", "Bearer $token")
        .put(body.toRequestBody("application/json".toMediaType())).build())

    private fun post(path: String, body: String, mime: String = "application/json"): String? =
        run(Request.Builder().url(base + path).header("Authorization", "Bearer $token")
            .post(body.toRequestBody(mime.toMediaType())).build())

    private fun run(req: Request): String? = try {
        http.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (e: Exception) { null }

    /** mxc:// → indirilebilir HTTP URL (Coil için).
     *  Matrix 1.11+ authenticated media: v1 endpoint + Authorization Bearer ŞART
     *  (eski /_matrix/media/v3/* artık 404). Bearer'ı Coil interceptor ekler. */
    fun mxcToHttp(mxc: String?, thumb: Boolean = false, size: Int = 800): String? {
        if (mxc == null || !mxc.startsWith("mxc://")) return null
        val parts = mxc.removePrefix("mxc://").split("/", limit = 2)
        if (parts.size != 2) return null
        return if (thumb)
            "$base/_matrix/client/v1/media/thumbnail/${parts[0]}/${parts[1]}?width=$size&height=$size&method=scale"
        else "$base/_matrix/client/v1/media/download/${parts[0]}/${parts[1]}"
    }

    /** Sync — ilk çağrı timeout=0 (anında), sonrası long-poll. Oda listesini döner. */
    suspend fun sync(timeout: Int = 0): List<MatrixRoom> = withContext(Dispatchers.IO) {
        val q = StringBuilder("/_matrix/client/v3/sync?timeout=$timeout")
        val since = syncToken
        if (since == null) {
            q.append("&full_state=true&filter=")
                .append("%7B%22room%22%3A%7B%22timeline%22%3A%7B%22limit%22%3A20%7D%7D%7D")
        } else q.append("&since=$since")
        val raw = get(q.toString()) ?: return@withContext emptyList()
        val root = json.parseToJsonElement(raw).jsonObject
        syncToken = root["next_batch"]?.jsonPrimitive?.contentOrNull
        parseRooms(root)
    }

    private fun parseRooms(root: JsonObject): List<MatrixRoom> {
        val join = root["rooms"]?.jsonObject?.get("join")?.jsonObject ?: return emptyList()
        val out = ArrayList<MatrixRoom>()
        for ((roomId, v) in join) {
            val room = v.jsonObject
            var name = roomId
            var avatar: String? = null
            var direct = true
            var members = 0
            // State: m.room.name / m.room.avatar
            (room["state"]?.jsonObject?.get("events")?.jsonArray
                ?: room["timeline"]?.jsonObject?.get("events")?.jsonArray)?.forEach { ev ->
                val e = ev.jsonObject
                when (e["type"]?.jsonPrimitive?.contentOrNull) {
                    "m.room.name" -> e["content"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.let { name = it; direct = false }
                    "m.room.avatar" -> avatar = e["content"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                    "m.room.member" -> members++
                }
            }
            // Son mesaj (timeline son event)
            var lastMsg: String? = null; var lastTs = 0L
            room["timeline"]?.jsonObject?.get("events")?.jsonArray?.forEach { ev ->
                val e = ev.jsonObject
                if (e["type"]?.jsonPrimitive?.contentOrNull == "m.room.message") {
                    val c = e["content"]?.jsonObject
                    lastMsg = c?.get("body")?.jsonPrimitive?.contentOrNull
                    lastTs = e["origin_server_ts"]?.jsonPrimitive?.longOrNull ?: lastTs
                }
            }
            val unread = room["unread_notifications"]?.jsonObject?.get("notification_count")?.jsonPrimitive?.intOrNull ?: 0
            out.add(MatrixRoom(roomId, name, lastMsg, lastTs, unread, avatar, direct))
        }
        return out.sortedByDescending { it.lastTs }
    }

    /** Oda mesajları (geçmiş, en yeni altta). */
    suspend fun messages(roomId: String, limit: Int = 60): List<ChatMessage> = withContext(Dispatchers.IO) {
        val raw = get("/_matrix/client/v3/rooms/${enc(roomId)}/messages?dir=b&limit=$limit") ?: return@withContext emptyList()
        val chunk = json.parseToJsonElement(raw).jsonObject["chunk"]?.jsonArray ?: return@withContext emptyList()
        chunk.mapNotNull { ev ->
            val e = ev.jsonObject
            if (e["type"]?.jsonPrimitive?.contentOrNull != "m.room.message") return@mapNotNull null
            val c = e["content"]?.jsonObject ?: return@mapNotNull null
            val sender = e["sender"]?.jsonPrimitive?.contentOrNull ?: ""
            val msgtype = c["msgtype"]?.jsonPrimitive?.contentOrNull ?: "m.text"
            val type = when (msgtype) {
                "m.image" -> "image"; "m.video" -> "video"; "m.audio" -> "audio"; "m.file" -> "file"; else -> "text"
            }
            ChatMessage(
                id = e["event_id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString(),
                sender = sender,
                body = c["body"]?.jsonPrimitive?.contentOrNull ?: "",
                ts = e["origin_server_ts"]?.jsonPrimitive?.longOrNull ?: 0,
                mine = sender == userId,
                type = type,
                mediaMxc = c["url"]?.jsonPrimitive?.contentOrNull,
            )
        }.reversed()
    }

    /** Metin mesajı gönder. */
    suspend fun sendText(roomId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val txn = "m${System.nanoTime()}"
        val body = """{"msgtype":"m.text","body":${json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(text))}}"""
        put("/_matrix/client/v3/rooms/${enc(roomId)}/send/m.room.message/$txn", body) != null
    }

    /** DM oluştur (kullanıcı adıyla). */
    suspend fun createDirect(userId: String): String? = withContext(Dispatchers.IO) {
        val body = """{"is_direct":true,"preset":"trusted_private_chat","invite":["$userId"]}"""
        val raw = post("/_matrix/client/v3/createRoom", body) ?: return@withContext null
        json.parseToJsonElement(raw).jsonObject["room_id"]?.jsonPrimitive?.contentOrNull
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
