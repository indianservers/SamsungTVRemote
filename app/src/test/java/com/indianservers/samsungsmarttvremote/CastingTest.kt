package com.indianservers.samsungsmarttvremote

import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.network.LocalCastServer
import com.indianservers.samsungsmarttvremote.data.protocol.*
import java.io.ByteArrayInputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class CastingPolicyTest {
    @Test fun linksAndFormatsMustBeExplicitSupportedMedia() {
        assertTrue(CastMediaPolicy.validUrl("https://example.com/movie.mp4?x=1"))
        listOf("file:///secret","content://files/one","javascript:alert(1)","https://user:password@example.com/movie","https://example.com/movie#secret").forEach { assertFalse(CastMediaPolicy.validUrl(it)) }
        assertTrue(CastMediaPolicy.validType("video/mp4"));assertFalse(CastMediaPolicy.validType("text/html"));assertFalse(CastMediaPolicy.validType("video/mp4\r\nInjected: true"))
    }
    @Test fun byteRangesAreBoundedAndRejectMultipleOrUnsatisfiableRequests() {
        assertEquals(ByteWindow(0,99),HttpByteRanges.parse(null,100))
        assertEquals(ByteWindow(10,19),HttpByteRanges.parse("bytes=10-19",100))
        assertEquals(ByteWindow(90,99),HttpByteRanges.parse("bytes=90-",100))
        assertEquals(ByteWindow(95,99),HttpByteRanges.parse("bytes=-5",100))
        assertEquals(ByteWindow(0,99),HttpByteRanges.parse("bytes=-999",100))
        listOf("bytes=100-","bytes=9-2","bytes=1-2,4-5","items=1-2","bytes=-0").forEach { assertNull(HttpByteRanges.parse(it,100)) }
        assertNull(HttpByteRanges.parse(null,0))
    }
}
class LocalCastServerTest {
    @Test fun onlySelectedFileCanBeReadAndHeadRangeAndRevocationWork() {
        val bytes=ByteArray(256) { it.toByte() };var opens=0
        val server=LocalCastServer(InetAddress.getByName("127.0.0.1"),"127.0.0.1",bytes.size.toLong(),"video/mp4") { opens++;ByteArrayInputStream(bytes) }
        val client=OkHttpClient.Builder().connectTimeout(1,TimeUnit.SECONDS).readTimeout(2,TimeUnit.SECONDS).build()
        try {
            client.newCall(Request.Builder().url(server.url).head().build()).execute().use { assertEquals(200,it.code);assertEquals("256",it.header("Content-Length"));assertEquals(0,opens) }
            client.newCall(Request.Builder().url(server.url).header("Range","bytes=10-19").build()).execute().use { assertEquals(206,it.code);assertEquals("bytes 10-19/256",it.header("Content-Range"));assertArrayEquals(bytes.copyOfRange(10,20),it.body!!.bytes()) }
            client.newCall(Request.Builder().url(server.url).header("Range","bytes=999-").build()).execute().use { assertEquals(416,it.code) }
            client.newCall(Request.Builder().url(server.url.substringBeforeLast('/')+"/other-file").build()).execute().use { assertEquals(404,it.code) }
            assertEquals(1,opens)
            server.close()
            assertThrows(java.io.IOException::class.java) { client.newCall(Request.Builder().url(server.url).build()).execute().close() }
        } finally { server.close();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown() }
    }
    @Test fun anotherReceiverCannotReadTheDocument() {
        var opened=false
        val server=LocalCastServer(InetAddress.getByName("127.0.0.1"),"127.0.0.2",3,"image/png") { opened=true;ByteArrayInputStream(byteArrayOf(1,2,3)) }
        val client=OkHttpClient()
        try { client.newCall(Request.Builder().url(server.url).build()).execute().use { assertEquals(403,it.code) };assertFalse(opened) }
        finally { server.close();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown() }
    }
}
class CastingProtocolTest {
    @Test fun loadPlayAndStopUseAdvertisedTransportWithoutSamsungKeys()=runBlocking<Unit> {
        val address=NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.first { SamsungProtocol.localIp(it.hostAddress ?: "") }
        val operations=java.util.concurrent.LinkedBlockingQueue<Pair<String,String>>()
        val server=MockWebServer()
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                if(request.path=="/schema") return MockResponse().setBody("<scpd><actionList>"+listOf("GetTransportInfo","SetAVTransportURI","Play","Stop").joinToString("") { "<action><name>$it</name></action>" }+"</actionList></scpd>")
                val action=request.getHeader("SOAPAction")!!.substringAfter('#').trim('"')
                operations.add(action to request.body.readUtf8())
                return MockResponse().setBody("<Envelope><Body><Response><CurrentTransportState>STOPPED</CurrentTransportState></Response></Body></Envelope>")
            }
        }
        server.start(address,0)
        val base="http://${address.hostAddress}:${server.port}"
        val tv=Tv("cast-test","Receiver",address.hostAddress!!,"Fixture",server.port,TvPlatform.Dlna,"LG",MediaService("urn:schemas-upnp-org:service:AVTransport:1","$base/control","$base/schema"))
        val session=LocalMediaSession(tv)
        try {
            session.inspect();operations.clear()
            session.loadMedia("https://example.com/a.mp4?x=1&y=2","video/mp4")
            session.key(RemoteKey.STOP)
            val load=operations.poll(2,TimeUnit.SECONDS)!!;assertEquals("SetAVTransportURI",load.first)
            val xml=MediaXml.document(load.second)
            assertEquals("https://example.com/a.mp4?x=1&y=2",MediaXml.value(xml,"CurrentURI"))
            val metadata=MediaXml.document(MediaXml.value(xml,"CurrentURIMetaData"))
            assertEquals("http-get:*:video/mp4:*",MediaXml.elements(metadata,"res").single().getAttribute("protocolInfo"))
            assertEquals("Play",operations.poll(2,TimeUnit.SECONDS)!!.first)
            assertEquals("Stop",operations.poll(2,TimeUnit.SECONDS)!!.first)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { session.loadMedia("file:///private","video/mp4") } }
        } finally { session.close();server.close() }
    }
}
