package com.indianservers.samsungsmarttvremote.domain

import org.json.JSONObject

enum class RemoteKey(val wire: String? = null) {
    POWER, VOLUP, VOLDOWN, MUTE, CHUP, CHDOWN, UP, DOWN, LEFT, RIGHT, ENTER, RETURN, HOME, MENU, SOURCE, PLAY, PAUSE,
    STOP, REWIND, FF, PRECH, CH_LIST, BACKSPACE("KEY_BACKSPACE"),
    DIGIT_0("KEY_0"), DIGIT_1("KEY_1"), DIGIT_2("KEY_2"), DIGIT_3("KEY_3"), DIGIT_4("KEY_4"),
    DIGIT_5("KEY_5"), DIGIT_6("KEY_6"), DIGIT_7("KEY_7"), DIGIT_8("KEY_8"), DIGIT_9("KEY_9");
    val code get() = wire ?: "KEY_$name"
}
data class Tv(val id: String, val name: String, val ip: String, val model: String = "Samsung TV", val port: Int = 8002,val platform: TvPlatform=TvPlatform.Samsung,val brand: String="Samsung",val transport: MediaService?=null,val rendering: MediaService?=null)
enum class Status { Disconnected, Discovering, Connecting, AwaitingAuthorization, Connected, Reconnecting, Failed, Unsupported }
data class RemoteState(val status: Status = Status.Disconnected, val tv: Tv? = null, val message: String = "Connect your television")
interface RemoteProtocol { fun command(key: RemoteKey): String }
object SamsungProtocol : RemoteProtocol {
    override fun command(key: RemoteKey) = JSONObject().put("method", "ms.remote.control").put("params", JSONObject()
        .put("Cmd", "Click").put("DataOfCmd", key.code).put("Option", "false").put("TypeOfRemote", "SendRemoteKey")).toString()
    fun event(text: String): Pair<String, String?> {
        val json = JSONObject(text)
        val data=json.optJSONObject("data")
        var token=data?.optString("token")?.takeIf { it.isNotBlank() }
        if(token==null) {
            val clients=data?.optJSONArray("clients")
            for(i in 0 until (clients?.length() ?: 0)) {
                val client=clients?.optJSONObject(i) ?: continue
                if(client.optString("id")==data?.optString("id")) token=client.optJSONObject("attributes")?.optString("token")?.takeIf { it.isNotBlank() }
            }
        }
        return json.optString("event") to token
    }
    fun localIp(ip: String): Boolean {
        if(!ip.matches(Regex("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}"))) return false
        val p = ip.split('.').map { it.toIntOrNull() ?: return false }
        return p.size == 4 && p.all { it in 0..255 } && (p[0] == 10 || p[0] == 192 && p[1] == 168 || p[0] == 172 && p[1] in 16..31)
    }
    fun device(ip: String, body: String): Tv? {
        if (!localIp(ip)) return null
        val root = JSONObject(body)
        val d = root.optJSONObject("device") ?: return null
        if (!d.optString("type").contains("Samsung", true) && !d.optString("manufacturer").contains("Samsung", true)) return null
        val id = d.optString("id").ifBlank { root.optString("id").ifBlank { ip } }
        return Tv(id.takeIf(ProfileCodec::validId) ?: ip, d.optString("name").ifBlank { root.optString("name", "Samsung TV") }.take(128), ip, d.optString("modelName", "Samsung TV").take(128),
            if (d.optString("TokenAuthSupport") == "false") 8001 else 8002)
    }
    fun location(response: String): String? = response.lineSequence().firstOrNull { it.startsWith("location:", true) }?.substringAfter(':')?.trim()
    fun retryDelay(attempt: Int) = (1000L shl attempt.coerceIn(0, 4))
    fun merge(devices: List<Tv>, tv: Tv) = (devices.filterNot { it.id==tv.id || it.ip==tv.ip }+tv).sortedBy { it.name }
}

