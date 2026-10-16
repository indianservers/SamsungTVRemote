package com.indianservers.samsungsmarttvremote.data.discovery

import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.storage.*

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import com.indianservers.samsungsmarttvremote.data.protocol.MediaXml
import java.net.*
import java.util.concurrent.TimeUnit

class TvDiscovery(context: Context) {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val http = OkHttpClient.Builder().connectTimeout(2,TimeUnit.SECONDS).readTimeout(2,TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun validate(ip: String): Tv? = withContext(Dispatchers.IO) {
        require(SamsungProtocol.localIp(ip)) { "Enter a private local IPv4 address." }
        http.newCall(Request.Builder().url("http://$ip:8001/api/v2/").build()).execute().use {
            if (it.isSuccessful) {
                val source=it.body?.source() ?: return@use null
                source.request(65537)
                require(source.buffer.size<=65536) { "TV response is too large" }
                SamsungProtocol.device(ip,source.readUtf8())
            } else null
        }
    }
    suspend fun media(ip: String): Tv? {
        var result: Tv?=null
        scan("",ip) { if(it.transport!=null || it.rendering!=null) result=it }
        return result
    }
    suspend fun scan(brand: String="Samsung",onlyIp: String?=null,found: (Tv)->Unit) = withContext(Dispatchers.IO) {
        val lock=wifi.createMulticastLock("tv-discovery").apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout=400
                for (service in listOf("ssdp:all", "urn:schemas-upnp-org:device:MediaRenderer:1")) {
                    val bytes=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $service\r\n\r\n").toByteArray()
                    socket.send(DatagramPacket(bytes,bytes.size,InetAddress.getByName("239.255.255.250"),1900))
                }
                val checked=mutableSetOf<String>();val locations=mutableSetOf<String>();val known=mutableMapOf<String,Tv>()
                val end=System.currentTimeMillis()+6500
                while (System.currentTimeMillis()<end && locations.size<64) {
                    ensureActive()
                    val packet=DatagramPacket(ByteArray(8192),8192)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val ip=packet.address.hostAddress ?: continue
                    if (!SamsungProtocol.localIp(ip) || onlyIp!=null && onlyIp!=ip) continue
                    if((brand=="Samsung" || brand.isBlank()) && checked.add(ip)) {
                        try { validate(ip)?.let { known[ip]=it;found(it) } } catch(e: CancellationException) { throw e } catch(_:Exception) { }
                    }
                    val location=SamsungProtocol.location(String(packet.data,packet.offset,packet.length)) ?: continue
                    if(!LocalMediaPolicy.url(location,ip) || !locations.add(location)) continue
                    try {
                        http.newCall(Request.Builder().url(location).build()).execute().use { response ->
                            if(!response.isSuccessful) return@use
                            val source=response.body?.source() ?: return@use
                            source.request(65537);if(source.buffer.size>65536) return@use
                            val media=MediaXml.device(ip,location,source.readUtf8()) ?: return@use
                            val tv=known[ip]?.copy(transport=media.transport,rendering=media.rendering) ?: media
                            if(brand.isBlank() || tv.brand==brand) found(tv)
                        }
                    } catch(e: CancellationException) { throw e } catch(_:Exception) { }
                }
            }
        } finally { lock.release() }
    }
}
