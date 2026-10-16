package com.indianservers.samsungsmarttvremote

import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.protocol.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.net.NetworkInterface

class UniversalModelsTest {
    @Test fun thirtyBrandsAreUniqueAndSamsungRemainsDefault() {
        assertTrue(TvBrands.all.size>=25);assertEquals(TvBrands.all.size,TvBrands.all.map { it.name }.toSet().size)
        assertEquals("Samsung",Appearance().brand);assertEquals(TvPlatform.Samsung,Tv("one","TV","192.168.1.2").platform)
        assertTrue(TvBrands.get("Xiaomi / Mi").matches("Xiaomi Communications"));assertEquals("LG",TvBrands.get("LG").name)
    }
    @Test fun localServiceUrlsCannotLeaveTheSelectedDevice() {
        val ip="192.168.1.2"
        assertTrue(LocalMediaPolicy.url("http://$ip:1400/control",ip))
        listOf("http://8.8.8.8/control","https://$ip/control","http://example.com/control","http://user:pass@$ip/control","http://$ip/control#x").forEach { assertFalse(LocalMediaPolicy.url(it,ip)) }
    }
    @Test fun serviceProfilesRoundTripAndLegacySamsungIsRetained() {
        val ip="192.168.1.2";val service=MediaService("urn:schemas-upnp-org:service:AVTransport:1","http://$ip:1400/control","http://$ip:1400/schema")
        val tv=Tv("uuid:lg","LG TV",ip,"Reported model",1400,TvPlatform.Dlna,"LG",transport=service)
        assertEquals(tv,ProfileCodec.decode(ProfileCodec.encode(listOf(DeviceProfile(tv)))).single().tv)
        val old="""[{"id":"legacy","name":"TV","ip":"192.168.1.3","port":8002}]"""
        assertEquals(TvPlatform.Samsung,ProfileCodec.decode(old).single().tv.platform)
        assertFalse(LocalMediaPolicy.valid(tv.copy(transport=service.copy(controlUrl="http://8.8.8.8/control"))))
    }
    @Test fun invalidTimelineValuesAreNotFabricated() {
        assertEquals(3723L,LocalMediaPolicy.seconds("01:02:03.500"))
        listOf("NOT_IMPLEMENTED","00:99:00","-1:00:00","123").forEach { assertNull(LocalMediaPolicy.seconds(it)) }
        assertEquals("01:02:03",LocalMediaPolicy.time(3723))
    }
    @Test fun hostileXmlAndCrossDeviceDescriptionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MediaXml.document("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///secret'>]><a>&x;</a>") }
        assertThrows(IllegalArgumentException::class.java) { MediaXml.document("x".repeat(65537)) }
        assertThrows(IllegalArgumentException::class.java) { MediaXml.device("192.168.1.2","http://8.8.8.8/device.xml","<root/>") }
        assertEquals("&lt;&amp;&quot;&apos;&gt;",MediaXml.escape("<&\"'>"))
    }
    @Test fun deviceDescriptionsRequireRendererAndAdvertisedLocalServices() {
        val body="""<root xmlns="urn:schemas-upnp-org:device-1-0"><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType><friendlyName>Living room</friendlyName><manufacturer>LG Electronics</manufacturer><modelName>Fixture model</modelName><UDN>uuid:renderer</UDN><serviceList><service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/control</controlURL><SCPDURL>/schema</SCPDURL></service></serviceList></device></root>"""
        val tv=MediaXml.device("192.168.1.2","http://192.168.1.2:1400/device.xml",body)!!
        assertEquals("LG",tv.brand);assertEquals("Fixture model",tv.model);assertEquals("http://192.168.1.2:1400/control",tv.rendering!!.controlUrl)
        assertNull(MediaXml.device(tv.ip,"http://${tv.ip}:1400/device.xml",body.replace(">/control<",">http://8.8.8.8/control<")))
        assertNull(MediaXml.device(tv.ip,"http://${tv.ip}:1400/device.xml",body.replace("MediaRenderer","MediaServer")))
    }
}
class LocalMediaSessionTest {
    @Test fun advertisedRangesAndActionsDriveActualVolumeAndSeekSoap()=runBlocking<Unit> {
        val address=NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.first { SamsungProtocol.localIp(it.hostAddress ?: "") }
        var volume=21;var position="00:00:10";var mutations=0
        val server=MockWebServer()
        server.dispatcher=object: Dispatcher() {
            override fun dispatch(request:RecordedRequest):MockResponse {
                val action=request.getHeader("SOAPAction")?.substringAfter('#')?.trim('"')
                if(request.path=="/render.xml") return MockResponse().setBody("""<scpd><actionList><action><name>GetVolume</name></action><action><name>SetVolume</name></action></actionList><serviceStateTable><stateVariable><name>Volume</name><allowedValueRange><minimum>1</minimum><maximum>65</maximum><step>2</step></allowedValueRange></stateVariable></serviceStateTable></scpd>""")
                if(request.path=="/transport.xml") return MockResponse().setBody("""<scpd><actionList>${listOf("GetPositionInfo","GetTransportInfo","GetCurrentTransportActions","Seek","Play","Pause","Stop").joinToString("") { "<action><name>$it</name></action>" }}</actionList><serviceStateTable><stateVariable><name>A_ARG_TYPE_SeekMode</name><allowedValueList><allowedValue>REL_TIME</allowedValue></allowedValueList></stateVariable></serviceStateTable></scpd>""")
                val content=when(action) {
                    "GetVolume"->"<CurrentVolume>$volume</CurrentVolume>"
                    "GetTransportInfo"->"<CurrentTransportState>PLAYING</CurrentTransportState>"
                    "GetPositionInfo"->"<RelTime>$position</RelTime><TrackDuration>00:10:00</TrackDuration>"
                    "GetCurrentTransportActions"->"<Actions>Play,Pause,Seek,Stop</Actions>"
                    "SetVolume"->{ val body=request.body.readUtf8();assertTrue(body.contains("<DesiredVolume>11</DesiredVolume>"));volume=11;mutations++;"" }
                    "Seek"->{ val body=request.body.readUtf8();assertTrue(body.contains("<Unit>REL_TIME</Unit>"));assertTrue(body.contains("<Target>00:05:00</Target>"));position="00:05:00";mutations++;"" }
                    else->return MockResponse().setResponseCode(500)
                }
                return MockResponse().setBody("<Envelope><Body><Response>$content</Response></Body></Envelope>")
            }
        }
        server.start(address,0)
        val url="http://${address.hostAddress}:${server.port}"
        val tv=Tv("uuid:fixture","Local renderer",address.hostAddress!!,"Model fixture",server.port,TvPlatform.Dlna,"LG",
            MediaService("urn:schemas-upnp-org:service:AVTransport:1","$url/control","$url/transport.xml"),MediaService("urn:schemas-upnp-org:service:RenderingControl:1","$url/render","$url/render.xml"))
        val session=LocalMediaSession(tv)
        try {
            val state=session.inspect();assertEquals(21,state.volume);assertEquals(65,state.maximum);assertEquals(1,state.minimum);assertEquals(2,state.step)
            assertTrue(state.canVolume);assertTrue(state.canSeek);assertEquals(600L,state.duration);assertEquals(0,mutations)
            session.volume(12);session.seek(300)
            val actual=session.refresh();assertEquals(11,actual.volume);assertEquals(300L,actual.position);assertEquals(2,mutations)
            assertFalse(session.supports(RemoteKey.HOME));assertFalse(session.supports(RemoteKey.POWER))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { session.key(RemoteKey.HOME) } }
        } finally { session.close();server.close() }
    }
}
