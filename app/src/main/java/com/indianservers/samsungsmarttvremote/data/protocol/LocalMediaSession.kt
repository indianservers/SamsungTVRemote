package com.indianservers.samsungsmarttvremote.data.protocol

import com.indianservers.samsungsmarttvremote.domain.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** UPnP AV, not a menu remote. Uses only same-device, advertised services and SCPD actions. */
object MediaXml {
    fun document(text: String): Element {
        require(text.toByteArray().size<=65536 && !text.contains("<!DOCTYPE",true) && !text.contains("<!ENTITY",true))
        val factory=DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware=true;isExpandEntityReferences=false
            setFeature("http://xml.org/sax/features/external-general-entities",false)
            setFeature("http://xml.org/sax/features/external-parameter-entities",false)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray())).documentElement
    }
    fun elements(root: Element,name: String): List<Element> {
        val nodes=root.getElementsByTagNameNS("*",name)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    fun value(root: Element,name: String)=elements(root,name).firstOrNull()?.textContent?.trim().orEmpty()
    fun device(ip: String,location: String,text: String): Tv? {
        require(LocalMediaPolicy.url(location,ip))
        val root=document(text)
        val device=elements(root,"device").firstOrNull { value(it,"deviceType").contains(":MediaRenderer:") } ?: return null
        fun service(name: String): MediaService? {
            val s=elements(device,"service").firstOrNull { value(it,"serviceType").matches(Regex("urn:schemas-upnp-org:service:$name:[1-3]")) } ?: return null
            val declared=value(root,"URLBase").ifBlank { location }
            if(!LocalMediaPolicy.url(declared,ip)) return null
            val base=declared.toHttpUrlOrNull()!!
            val control=base.resolve(value(s,"controlURL"))?.toString() ?: return null
            val description=base.resolve(value(s,"SCPDURL"))?.toString() ?: return null
            if(!LocalMediaPolicy.url(control,ip) || !LocalMediaPolicy.url(description,ip)) return null
            return MediaService(value(s,"serviceType"),control,description)
        }
        val transport=service("AVTransport");val rendering=service("RenderingControl")
        if(transport==null && rendering==null) return null
        val manufacturer=value(device,"manufacturer").take(128)
        val brand=TvBrands.all.firstOrNull { it.matches(manufacturer) }?.name ?: manufacturer.ifBlank { "Other" }
        val id=value(device,"UDN").takeIf(ProfileCodec::validId) ?: "renderer:$ip"
        return Tv(id,value(device,"friendlyName").take(128).ifBlank { "$brand media renderer" },ip,value(device,"modelName").take(128),location.toHttpUrlOrNull()!!.port,TvPlatform.Dlna,brand,transport,rendering)
    }
    fun escape(text: String)=text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
}
class LocalMediaSession(val tv: Tv) {
    private val http=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(3,TimeUnit.SECONDS).readTimeout(3,TimeUnit.SECONDS).build()
    private var renderingActions=emptySet<String>()
    private var transportActions=emptySet<String>()
    private var range: IntRange?=null
    private var volumeStep=1
    private var relativeSeek=false
    var snapshot=MediaSnapshot();private set
    suspend fun get(url: String): String=withContext(Dispatchers.IO) {
        require(LocalMediaPolicy.url(url,tv.ip))
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            require(response.isSuccessful);read(response)
        }
    }
    private fun read(response: Response): String {
        val source=response.body?.source() ?: error("Empty local media response")
        source.request(65537);require(source.buffer.size<=65536)
        return source.readUtf8()
    }
    private suspend fun call(service: MediaService,action: String,args: Map<String,String>): Element=withContext(Dispatchers.IO) {
        require(LocalMediaPolicy.url(service.controlUrl,tv.ip))
        val content="""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="${service.type}">${args.entries.joinToString("") { "<${it.key}>${MediaXml.escape(it.value)}</${it.key}>" }}</u:$action></s:Body></s:Envelope>"""
        val request=Request.Builder().url(service.controlUrl).header("SOAPAction","\"${service.type}#$action\"").post(content.toRequestBody("text/xml; charset=utf-8".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "TV rejected local media action" }
            MediaXml.document(read(response)).also { require(MediaXml.elements(it,"Fault").isEmpty()) }
        }
    }
    private fun arguments(vararg pairs: Pair<String,String>)=mapOf("InstanceID" to "0")+pairs
    suspend fun inspect(): MediaSnapshot {
        require(LocalMediaPolicy.valid(tv))
        tv.rendering?.let { service ->
            try {
                val xml=MediaXml.document(get(service.descriptionUrl))
                renderingActions=MediaXml.elements(xml,"action").map { MediaXml.value(it,"name") }.toSet()
                val volume=MediaXml.elements(xml,"stateVariable").firstOrNull { MediaXml.value(it,"name")=="Volume" }
                if(volume!=null) {
                    val min=MediaXml.value(volume,"minimum").toIntOrNull();val max=MediaXml.value(volume,"maximum").toIntOrNull()
                    if(min!=null && max!=null && min>=0 && max>min && max<=10000) {
                        range=min..max;volumeStep=MediaXml.value(volume,"step").toIntOrNull()?.coerceIn(1,max-min) ?: 1
                    }
                }
            } catch(e: CancellationException) { throw e } catch(_:Exception) { renderingActions=emptySet() }
        }
        tv.transport?.let { service ->
            try {
                val xml=MediaXml.document(get(service.descriptionUrl))
                transportActions=MediaXml.elements(xml,"action").map { MediaXml.value(it,"name") }.toSet()
                relativeSeek=MediaXml.elements(xml,"stateVariable").any { MediaXml.value(it,"name")=="A_ARG_TYPE_SeekMode" && MediaXml.elements(it,"allowedValue").any { value -> value.textContent.trim()=="REL_TIME" } }
            } catch(e: CancellationException) { throw e } catch(_:Exception) { transportActions=emptySet() }
        }
        return refresh()
    }
    suspend fun refresh(): MediaSnapshot {
        var volume: Int?=null;var position: Long?=null;var duration: Long?=null;var track: String?=null;var reachable=false;var seek=false
        tv.rendering?.takeIf { "GetVolume" in renderingActions }?.let {
            try {
                val raw=MediaXml.value(call(it,"GetVolume",arguments("Channel" to "Master")),"CurrentVolume").toIntOrNull()
                if(raw!=null) { reachable=true;volume=raw.takeIf { n -> range?.contains(n)==true } }
            } catch(e: CancellationException) { throw e } catch(_:Exception) { }
        }
        tv.transport?.let { service ->
            try {
                if("GetTransportInfo" in transportActions) { call(service,"GetTransportInfo",arguments());reachable=true }
                if("GetPositionInfo" in transportActions) {
                    val xml=call(service,"GetPositionInfo",arguments());reachable=true
                    position=LocalMediaPolicy.seconds(MediaXml.value(xml,"RelTime"));duration=LocalMediaPolicy.seconds(MediaXml.value(xml,"TrackDuration"));track=MediaXml.value(xml,"TrackURI").takeIf { it.isNotBlank() }
                }
                if(relativeSeek && "Seek" in transportActions && "GetCurrentTransportActions" in transportActions) {
                    val current=MediaXml.value(call(service,"GetCurrentTransportActions",arguments()),"Actions").split(',').map(String::trim)
                    seek="Seek" in current && duration!=null && duration!!>0 && position!=null && position!!<=duration!!
                }
            } catch(e: CancellationException) { throw e } catch(_:Exception) { }
        }
        require(reachable) { "No supported local media query responded" }
        snapshot=MediaSnapshot(volume,range?.first ?: 0,range?.last ?: 100,volumeStep,position,duration,seek,volume!=null && "SetVolume" in renderingActions,
            transportActions+(if("SetMute" in renderingActions && "GetMute" in renderingActions) setOf("Mute") else emptySet()),message="TV-reported local media • streaming apps may expose no timeline",track=track)
        return snapshot
    }
    suspend fun loadMedia(url: String,mime: String) {
        require(CastMediaPolicy.validUrl(url) && CastMediaPolicy.validType(mime))
        require("SetAVTransportURI" in transportActions && "Play" in transportActions)
        val kind=when { mime.startsWith("video/")->"videoItem";mime.startsWith("audio/")->"audioItem.musicTrack";else->"imageItem.photo" }
        val metadata="""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>Selected media</dc:title><upnp:class>object.item.$kind</upnp:class><res protocolInfo="http-get:*:$mime:*">${MediaXml.escape(url)}</res></item></DIDL-Lite>"""
        call(tv.transport!!,"SetAVTransportURI",arguments("CurrentURI" to url,"CurrentURIMetaData" to metadata))
        call(tv.transport,"Play",arguments("Speed" to "1"))
    }
    suspend fun volume(value: Int) {
        require(snapshot.canVolume);val bounds=range ?: error("Unknown range")
        val quantized=bounds.first+((value.coerceIn(bounds)-bounds.first)/volumeStep)*volumeStep
        call(tv.rendering!!,"SetVolume",arguments("Channel" to "Master","DesiredVolume" to quantized.toString()))
    }
    suspend fun seek(seconds: Long) {
        require(snapshot.canSeek);val duration=snapshot.duration ?: error("Unknown duration")
        call(tv.transport!!,"Seek",arguments("Unit" to "REL_TIME","Target" to LocalMediaPolicy.time(seconds.coerceIn(0,duration))))
    }
    fun supports(key: RemoteKey)=when(key) {
        RemoteKey.VOLUP,RemoteKey.VOLDOWN -> snapshot.canVolume
        RemoteKey.MUTE -> "Mute" in snapshot.actions
        RemoteKey.PLAY -> "Play" in transportActions
        RemoteKey.PAUSE -> "Pause" in transportActions
        RemoteKey.STOP -> "Stop" in transportActions
        RemoteKey.FF,RemoteKey.REWIND -> snapshot.canSeek
        else -> false
    }
    suspend fun key(key: RemoteKey) {
        require(supports(key))
        when(key) {
            RemoteKey.VOLUP,RemoteKey.VOLDOWN -> { refresh();volume((snapshot.volume ?: error("Unknown volume"))+(if(key==RemoteKey.VOLUP) volumeStep else -volumeStep)) }
            RemoteKey.MUTE -> { val value=MediaXml.value(call(tv.rendering!!,"GetMute",arguments("Channel" to "Master")),"CurrentMute");require(value in setOf("0","1","true","false"));call(tv.rendering,"SetMute",arguments("Channel" to "Master","DesiredMute" to if(value=="1" || value=="true") "0" else "1")) }
            RemoteKey.FF,RemoteKey.REWIND -> seek((snapshot.position ?: error("Unknown position"))+(if(key==RemoteKey.FF) 10 else -10))
            else -> { val action=when(key) { RemoteKey.PLAY->"Play";RemoteKey.PAUSE->"Pause";else->"Stop" };call(tv.transport!!,action,if(action=="Play") arguments("Speed" to "1") else arguments()) }
        }
    }
    fun close() { http.dispatcher.cancelAll();http.connectionPool.evictAll();http.dispatcher.executorService.shutdown() }
}
