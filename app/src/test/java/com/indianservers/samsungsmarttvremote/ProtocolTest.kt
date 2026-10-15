package com.indianservers.samsungsmarttvremote

import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.storage.*
import com.indianservers.samsungsmarttvremote.data.discovery.*
import com.indianservers.samsungsmarttvremote.data.network.*

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import okhttp3.*
import okhttp3.mockwebserver.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ProtocolTest {
    @Test fun commandsUseSamsungWireTypes() {
        RemoteKey.entries.forEach { key ->
            val j=JSONObject(SamsungProtocol.command(key)); assertEquals("ms.remote.control",j.getString("method"))
            val p=j.getJSONObject("params"); assertEquals(key.code,p.getString("DataOfCmd")); assertEquals("Click",p.getString("Cmd"))
            assertEquals("false",p.get("Option")); assertEquals("SendRemoteKey",p.getString("TypeOfRemote"))
        }
    }
    @Test fun pairingTokenIsOptionalAndNotConnectionProof() {
        assertEquals("1234",SamsungProtocol.event("""{"event":"ms.channel.connect","data":{"token":"1234"}}""").second)
        assertNull(SamsungProtocol.event("""{"event":"ms.channel.connect","data":{}}""").second)
        assertEquals("ms.channel.unauthorized",SamsungProtocol.event("""{"event":"ms.channel.unauthorized"}""").first)
    }
    @Test fun privateAddressesOnly() {
        listOf("192.168.1.2","10.0.0.2","172.31.2.3").forEach { assertTrue(SamsungProtocol.localIp(it)) }
        listOf("8.8.8.8","127.0.0.1","192.168.1.256","localhost","172.32.1.1","192.168.1.1/path").forEach { assertFalse(SamsungProtocol.localIp(it)) }
    }
    @Test fun candidatesMustAdvertiseSamsungApi() {
        assertNull(SamsungProtocol.device("192.168.1.2","""{"device":{"type":"Other"}}"""))
        assertNull(SamsungProtocol.device("192.168.1.2","{}"))
        val tv=SamsungProtocol.device("192.168.1.2","""{"device":{"type":"Samsung SmartTV","id":"uuid:tv","name":"Living room","modelName":"Q60"}}""")!!
        assertEquals("uuid:tv",tv.id); assertEquals(8002,tv.port); assertEquals("Q60",tv.model)
    }
    @Test fun discoveryHeadersAreCaseInsensitive() {
        assertEquals("http://192.168.1.2:9197/description.xml",SamsungProtocol.location("HTTP/1.1 200 OK\r\nLOCATION: http://192.168.1.2:9197/description.xml\r\n"))
        assertNull(SamsungProtocol.location("garbage"))
    }
    @Test fun reconnectBackoffIsBounded() { assertEquals(listOf(1000L,2000L,4000L,8000L,16000L,16000L),(0..5).map(SamsungProtocol::retryDelay)) }
    @Test fun rediscoveryReplacesStaleAddressAndDeduplicates() {
        val original=Tv("uuid:one","Living room","192.168.1.2")
        val updated=original.copy(ip="192.168.1.3")
        val devices=SamsungProtocol.merge(SamsungProtocol.merge(emptyList(),original),updated)
        assertEquals(listOf(updated),devices)
        assertEquals(1, SamsungProtocol.merge(devices, updated.copy(id="new-id")).size)
    }
    @Test fun clientAttributePairingTokenIsParsed() {
        assertEquals("nested",SamsungProtocol.event("""{"event":"ms.channel.connect","data":{"id":"this-client","clients":[{"id":"other","attributes":{"token":"wrong"}},{"id":"this-client","attributes":{"token":"nested"}}]}}""").second)
    }
    @Test fun malformedProtocolIsRejected() {
        assertThrows(org.json.JSONException::class.java) { SamsungProtocol.event("not json") }
        assertThrows(org.json.JSONException::class.java) { SamsungProtocol.device("192.168.1.2","<html>no api</html>") }
    }
}

private class MemoryPairing: PairingStore {
    var saved: Tv?=null
    private val tokens=java.util.concurrent.ConcurrentHashMap<String,String>()
    override suspend fun save(tv:Tv) { saved=tv }
    override suspend fun token(id:String)=tokens[id]
    override suspend fun token(id:String,token:String) { tokens[id]=token }
    override suspend fun clearToken(id:String) { tokens.remove(id) }
}
class ConnectionTest {
    @Test fun disconnectedCommandsFailExplicitly()=runBlocking {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val manager=ConnectionManager(scope,MemoryPairing()); manager.send(RemoteKey.UP)
            assertEquals(Status.Disconnected,manager.state.value.status)
            assertTrue(manager.state.value.message.contains("not sent"))
        } finally { scope.cancel() }
    }
    @Test fun lostSocketReconnectsWithoutExposingTokenOnNonTls()=runBlocking {
        val server=MockWebServer()
        val firstSocket=CompletableDeferred<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) { firstSocket.complete(webSocket); webSocket.send("""{"event":"ms.channel.connect","data":{"token":"saved"}}""") }
        }))
        server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) { webSocket.send("""{"event":"ms.channel.connect"}""") }
        }))
        server.start(); val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default); val store=MemoryPairing(); val manager=ConnectionManager(scope,store)
        try {
            manager.connect(Tv("test","Fake Samsung","127.0.0.1",port=server.port))
            withTimeout(5000) { manager.state.first { it.status==Status.Connected } }
            firstSocket.await().close(1000,"simulate TV disconnect")
            withTimeout(5000) { manager.state.first { it.status==Status.Reconnecting } }
            withTimeout(5000) { manager.state.first { it.status==Status.Connected } }
            server.takeRequest(2,TimeUnit.SECONDS)
            assertNull(server.takeRequest(2,TimeUnit.SECONDS)!!.requestUrl!!.queryParameter("token"))
            manager.forget(); assertNull(manager.state.value.tv)
        } finally { manager.disconnect(); scope.cancel(); server.close() }
    }
    @Test fun fakeTvAuthorizesStoresTokenAndReceivesOrderedThrottledKeys()=runBlocking {
        val received=LinkedBlockingQueue<Pair<String,Long>>()
        val server=MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) { webSocket.send("""{"event":"ms.channel.connect","data":{"token":"secret"}}""") }
            override fun onMessage(webSocket:WebSocket,text:String) { received.add(text to System.nanoTime()) }
        }))
        server.start()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val store=MemoryPairing(); val manager=ConnectionManager(scope,store)
        val tv=Tv("test","Fake Samsung","127.0.0.1",port=server.port)
        try {
            manager.connect(tv)
            withTimeout(5000) { manager.state.first { it.status==Status.Connected } }
            assertEquals(tv,store.saved); assertEquals("secret",store.token(tv.id))
            manager.send(RemoteKey.UP); manager.send(RemoteKey.ENTER); manager.send(RemoteKey.VOLUP)
            val messages=(1..3).map { received.poll(3,TimeUnit.SECONDS) ?: error("Command missing") }
            assertEquals(listOf("KEY_UP","KEY_ENTER","KEY_VOLUP"),messages.map { JSONObject(it.first).getJSONObject("params").getString("DataOfCmd") })
            assertTrue(messages[2].second-messages[0].second>=200_000_000L)
            manager.disconnect(); manager.send(RemoteKey.HOME)
            assertEquals(Status.Disconnected,manager.state.value.status)
            assertNull(received.poll(200,TimeUnit.MILLISECONDS))
        } finally { manager.disconnect(); scope.cancel(); server.close() }
    }
    @Test fun deniedAuthorizationNeverEnablesControlsAndClearsToken()=runBlocking {
        val server=MockWebServer(); server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) { webSocket.send("""{"event":"ms.channel.unauthorized"}""") }
        })); server.start()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default); val store=MemoryPairing(); store.token("test","stale")
        val manager=ConnectionManager(scope,store)
        try {
            manager.connect(Tv("test","Fake Samsung","127.0.0.1",port=server.port))
            withTimeout(5000) { manager.state.first { it.status==Status.Failed } }
            withTimeout(5000) { while(store.token("test")!=null) delay(10) }
            assertNotEquals(Status.Connected,manager.state.value.status)
        } finally { manager.disconnect(); scope.cancel(); server.close() }
    }
}



