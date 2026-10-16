package com.indianservers.samsungsmarttvremote.domain

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class TvPlatform { Samsung, Dlna }
data class MediaService(val type: String,val controlUrl: String,val descriptionUrl: String)
data class MediaSnapshot(val volume: Int?=null,val minimum: Int=0,val maximum: Int=100,val step: Int=1,
    val position: Long?=null,val duration: Long?=null,val canSeek: Boolean=false,val canVolume: Boolean=false,
    val actions: Set<String> = emptySet(),val busy: Boolean=false,val message: String="Check media capabilities to enable sliders",val track: String?=null)
data class TvBrand(val name: String,val aliases: List<String>,val note: String) {
    fun matches(manufacturer: String)=aliases.any { manufacturer.contains(it,true) }
}
object TvBrands {
    private const val media="Local media controls only when this model advertises compatible UPnP/DLNA services. Menu, apps and keyboard adapters are not implemented."
    val all=listOf(
        TvBrand("Samsung",listOf("samsung"),"Samsung WebSocket remote; local media sliders require advertised services."),
        TvBrand("LG",listOf("lg electronics","lge","lg"),"webOS/NetCast varies by model. $media"),
        TvBrand("Sony",listOf("sony"),"BRAVIA platform varies by model. $media"),
        TvBrand("Xiaomi / Mi",listOf("xiaomi","mi tv"),"Android/Google TV and regional platforms vary. $media"),
        TvBrand("TCL",listOf("tcl"),"Google TV, Roku and other systems vary. $media"),
        TvBrand("Hisense",listOf("hisense"),"VIDAA, Google TV and Roku vary by model. $media"),
        TvBrand("Panasonic",listOf("panasonic"),media),TvBrand("Philips",listOf("philips","tp vision"),media),
        TvBrand("Sharp",listOf("sharp"),media),TvBrand("Toshiba",listOf("toshiba"),media),
        TvBrand("Vizio",listOf("vizio"),media),TvBrand("Insignia",listOf("insignia"),media),
        TvBrand("Onn",listOf("onn","onn."),media),TvBrand("JVC",listOf("jvc"),media),
        TvBrand("Sanyo",listOf("sanyo"),media),TvBrand("Hitachi",listOf("hitachi"),media),
        TvBrand("Haier",listOf("haier"),media),TvBrand("Skyworth",listOf("skyworth"),media),
        TvBrand("Realme",listOf("realme"),media),TvBrand("OnePlus",listOf("oneplus"),media),
        TvBrand("Vu",listOf("vu televisions","vu technologies"),media),TvBrand("Thomson",listOf("thomson"),media),
        TvBrand("Blaupunkt",listOf("blaupunkt"),media),TvBrand("Nokia",listOf("nokia"),media),
        TvBrand("Motorola",listOf("motorola"),media),TvBrand("BPL",listOf("bpl"),media),
        TvBrand("Kodak",listOf("kodak"),media),TvBrand("Sansui",listOf("sansui"),media),
        TvBrand("Sceptre",listOf("sceptre"),media),TvBrand("Vestel",listOf("vestel"),media))
    fun get(name: String)=all.firstOrNull { it.name==name } ?: all.first()
}
object LocalMediaPolicy {
    fun url(value: String,ip: String): Boolean {
        val url=value.toHttpUrlOrNull() ?: return false
        return SamsungProtocol.localIp(ip) && url.host==ip && url.scheme=="http" && url.username.isEmpty() && url.password.isEmpty() && url.fragment==null
    }
    fun valid(tv: Tv)=ProfileCodec.validId(tv.id) && SamsungProtocol.localIp(tv.ip) && (tv.platform==TvPlatform.Samsung && tv.port in setOf(8001,8002) || tv.platform==TvPlatform.Dlna && tv.port in 1..65535 && (tv.transport!=null || tv.rendering!=null)) &&
        listOfNotNull(tv.transport,tv.rendering).all { url(it.controlUrl,tv.ip) && url(it.descriptionUrl,tv.ip) && it.type.matches(Regex("urn:schemas-upnp-org:service:(AVTransport|RenderingControl):[1-3]")) }
    fun seconds(value: String): Long? {
        val parts=value.substringBefore('.').split(':');if(parts.size!=3) return null
        val h=parts[0].toLongOrNull() ?: return null;val m=parts[1].toLongOrNull() ?: return null;val s=parts[2].toLongOrNull() ?: return null
        return (h*3600+m*60+s).takeIf { h in 0..9999 && m in 0..59 && s in 0..59 }
    }
    fun time(seconds: Long): String { val n=seconds.coerceAtLeast(0);return "%02d:%02d:%02d".format(java.util.Locale.US,n/3600,n/60%60,n%60) }
}
fun Status.label()=when(this) { Status.AwaitingAuthorization->"Waiting for TV approval";Status.Connecting->"Connecting";Status.Connected->"Ready";Status.Reconnecting->"Reconnecting";Status.Discovering->"Searching";Status.Failed->"Needs attention";Status.Unsupported->"Not supported";else->"Disconnected" }
