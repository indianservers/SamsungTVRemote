package com.indianservers.samsungsmarttvremote.domain

import org.json.JSONArray
import org.json.JSONObject

data class DeviceProfile(val tv: Tv,val friendlyName: String=tv.name,val room: String="Living Room")
enum class RemoteTheme { SnowWhite, MidnightDark, RoyalBlue, Minimal, System }
data class Appearance(val theme: RemoteTheme=RemoteTheme.SnowWhite,val buttonSize: Float=1f,val motion: Float=1f,val onboardingDone: Boolean=false,val mediaFirst: Boolean=false)
object ProfileCodec {
    fun validId(id: String)=id.matches(Regex("[A-Za-z0-9:._-]{1,160}"))
    fun encode(profiles: List<DeviceProfile>): String {
        require(profiles.size<=30 && profiles.distinctBy { it.tv.id }.size==profiles.size)
        return JSONArray().apply { profiles.forEach { p -> put(JSONObject().put("id",p.tv.id).put("ip",p.tv.ip).put("name",p.tv.name).put("model",p.tv.model).put("port",p.tv.port).put("friendly",p.friendlyName.take(60)).put("room",p.room.take(40))) } }.toString()
    }
    fun decode(text: String?): List<DeviceProfile> {
        if(text==null) return emptyList()
        val array=JSONArray(text); require(array.length()<=30)
        return (0 until array.length()).mapNotNull { i ->
            val p=array.getJSONObject(i);val id=p.getString("id");val ip=p.getString("ip");val port=p.getInt("port")
            if(!validId(id) || !SamsungProtocol.localIp(ip) || port !in setOf(8001,8002)) null else
                DeviceProfile(Tv(id,p.getString("name").take(128),ip,p.optString("model","Samsung TV").take(128),port),p.optString("friendly",p.getString("name")).take(60),p.optString("room","Living Room").take(40))
        }.distinctBy { it.tv.id }
    }
}
enum class FailureKind { None, Discovery, Unreachable, Denied, Authentication, Protocol, CommandRejected, Unsupported, Permission }
data class ConnectionDiagnostics(val failure: FailureKind=FailureKind.None,val lastError: String="None",val lastTransmissionMillis: Long?=null)
object DiagnosticReport {
    // Deliberate allowlist: never serialize raw events, credentials, names, IPs, text, or arbitrary exception messages.
    fun create(state: RemoteState,diagnostics: ConnectionDiagnostics,network: Boolean,permission: Boolean): String = buildString {
        appendLine("Smart TV Remote diagnostics v3")
        appendLine("Network available: $network")
        appendLine("Local network permission: $permission")
        appendLine("TV selected: ${state.tv!=null}")
        appendLine("Protocol: ${if(state.tv?.port==8002) "TLS WebSocket / 8002" else if(state.tv?.port==8001) "Unencrypted WebSocket / 8001" else "None"}")
        appendLine("Socket state: ${state.status}")
        appendLine("Failure category: ${diagnostics.failure}")
        appendLine("Last transport submission (epoch ms, not execution proof): ${diagnostics.lastTransmissionMillis ?: "None"}")
        appendLine("Pairing tokens, device identity/address, keyboard text and raw logs excluded.")
    }
}
object LocalNetworkPermission {
    const val NAME="android.permission.ACCESS_LOCAL_NETWORK"
    fun required(sdk: Int,target: Int)=sdk>=37 && target>=37
}
data class WidgetConfig(val widgetId: Int,val deviceId: String,val status: String="Open app to connect")
object WidgetPolicy {
    val allowed=setOf(RemoteKey.POWER,RemoteKey.VOLUP,RemoteKey.VOLDOWN,RemoteKey.MUTE,RemoteKey.UP,RemoteKey.DOWN,RemoteKey.LEFT,RemoteKey.RIGHT,RemoteKey.ENTER,RemoteKey.HOME,RemoteKey.RETURN)
    fun valid(config: WidgetConfig?,profile: DeviceProfile?,key: RemoteKey?)=config!=null && profile!=null && config.deviceId==profile.tv.id && key in allowed
}
