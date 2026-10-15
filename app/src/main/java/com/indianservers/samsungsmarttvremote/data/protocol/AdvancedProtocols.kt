package com.indianservers.samsungsmarttvremote.data.protocol

import com.indianservers.samsungsmarttvremote.domain.*
import org.json.JSONObject
import java.util.Base64

object SamsungTextProtocol {
    const val MAX_UTF8_BYTES=1024
    fun messages(text: String): List<String> {
        require(text.isNotBlank()) { "Enter text first." }
        require(text.toByteArray(Charsets.UTF_8).size<=MAX_UTF8_BYTES) { "Text is limited to 1,024 UTF-8 bytes." }
        require(Charsets.UTF_8.newEncoder().canEncode(text)) { "Text contains an incomplete Unicode character." }
        val begin=JSONObject().put("method","ms.channel.emit").put("params",JSONObject().put("event","custom.remote.textReceived").put("to","broadcast"))
        val input=JSONObject().put("method","ms.remote.control").put("params",JSONObject().put("Cmd",Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))).put("DataOfCmd","base64").put("TypeOfRemote","SendInputString"))
        val end=JSONObject().put("method","ms.remote.control").put("params",JSONObject().put("TypeOfRemote","SendInputEnd"))
        return listOf(begin.toString(),input.toString(),end.toString())
    }
}
object SamsungAppProtocol {
    fun list()=emit("ed.installedApp.get").toString()
    fun launch(app: TVApplication): String {
        require(app.id.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Invalid application identifier" }
        require(app.actionType in setOf("DEEP_LINK","NATIVE_LAUNCH"))
        return emit("ed.apps.launch").apply { getJSONObject("params").put("data",JSONObject().put("appId",app.id).put("action_type",app.actionType).put("metaTag","")) }.toString()
    }
    private fun emit(event: String)=JSONObject().put("method","ms.channel.emit").put("params",JSONObject().put("event",event).put("to","host"))
    fun parseApps(root: JSONObject): List<TVApplication> {
        val array=root.getJSONObject("data").getJSONArray("data")
        require(array.length()<=300) { "App listing exceeds limit" }
        return (0 until array.length()).map { index ->
            val app=array.getJSONObject(index)
            val id=app.getString("appId"); val name=app.getString("name")
            require(id.matches(Regex("[A-Za-z0-9._-]{1,128}")) && name.isNotBlank() && name.length<=120)
            val type=app.getInt("app_type")
            TVApplication(id,name,when(type) { 2 -> "DEEP_LINK";1,4 -> "NATIVE_LAUNCH";else -> "UNKNOWN" })
        }.distinctBy { it.id }
    }
    fun event(text: String): TVEvent? {
        val root=JSONObject(text)
        return when(root.optString("event")) {
            "ed.installedApp.get" -> try { TVEvent.Apps(parseApps(root)) } catch (_: Exception) { TVEvent.InvalidApps }
            "ms.remote.imeStart" -> TVEvent.Ime(true)
            "ms.remote.imeEnd" -> TVEvent.Ime(false)
            "ed.apps.launch" -> TVEvent.LaunchReply(root.optInt("data",-1)==200)
            else -> null
        }
    }
}
/** No verified model-specific pointer interface is available; unknown must fail closed. */
object SamsungPointerProtocol {
    fun enabled(capabilities: TVCapabilities)=capabilities[Feature.Pointer].support==Support.Supported
}
