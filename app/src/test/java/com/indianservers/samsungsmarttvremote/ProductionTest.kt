package com.indianservers.samsungsmarttvremote

import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.network.ConnectionManager
import com.indianservers.samsungsmarttvremote.data.storage.PairingStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class ProductionModelsTest {
    @Test fun independentProfilesRoundTripAndKeepFriendlyNames() {
        val a=DeviceProfile(Tv("uuid:one","Samsung One","192.168.1.2"),"Cinema","Living Room")
        val b=DeviceProfile(Tv("uuid:two","Samsung Two","192.168.1.3"),"Desk TV","Office")
        assertEquals(listOf(a,b),ProfileCodec.decode(ProfileCodec.encode(listOf(a,b))))
        assertThrows(IllegalArgumentException::class.java) { ProfileCodec.encode(listOf(a,a)) }
    }
    @Test fun invalidCachedEndpointsAndIdentifiersAreRejected() {
        val candidate=DeviceProfile(Tv("../../token","Bad","8.8.8.8"))
        assertTrue(ProfileCodec.decode(ProfileCodec.encode(listOf(candidate))).isEmpty())
        listOf("+10.0.0.1","010.0.0.1","10.0.0.1/path","127.0.0.1").forEach { assertFalse(SamsungProtocol.localIp(it)) }
    }
    @Test fun permissionOnlyAppliesToAndroid17AndTarget37() {
        assertTrue(LocalNetworkPermission.required(37,37));assertTrue(LocalNetworkPermission.required(38,37))
        assertFalse(LocalNetworkPermission.required(36,37));assertFalse(LocalNetworkPermission.required(37,36))
    }
    @Test fun diagnosticExportCannotSerializeSecretsOrPersonalIdentity() {
        val state=RemoteState(Status.Failed,Tv("secret-identity","Personal living room","192.168.1.44"),"token=secret-token keyboard=password123")
        val result=DiagnosticReport.create(state,ConnectionDiagnostics(FailureKind.Authentication,"secret-token password123",123),true,false)
        listOf("secret-token","password123","secret-identity","Personal living room","192.168.1.44").forEach { assertFalse(result.contains(it)) }
        assertTrue(result.contains("Authentication"));assertTrue(result.contains("123"))
    }
    @Test fun widgetsCannotTargetAnUnassignedOrRemovedDevice() {
        val profile=DeviceProfile(Tv("one","TV","192.168.1.3"))
        val config=WidgetConfig(123,"one")
        assertTrue(WidgetPolicy.valid(config,profile,RemoteKey.VOLUP))
        assertFalse(WidgetPolicy.valid(config,null,RemoteKey.VOLUP))
        assertFalse(WidgetPolicy.valid(config,profile.copy(tv=profile.tv.copy(id="two")),RemoteKey.VOLUP))
        assertFalse(WidgetPolicy.valid(config,profile,RemoteKey.DIGIT_9))
        assertFalse(WidgetPolicy.valid(null,profile,RemoteKey.POWER))
    }
}
private class ProfileMemory: PairingStore {
    val tokens=java.util.concurrent.ConcurrentHashMap<String,String>()
    override suspend fun save(tv:Tv) {}
    override suspend fun token(id:String)=tokens[id]
    override suspend fun token(id:String,token:String) { tokens[id]=token }
    override suspend fun clearToken(id:String) { tokens.remove(id) }
}
class ProductionConnectionTest {
    @Test fun switchingTvCancelsQueuedKeysAndKeepsOldTokensOutOfNewSocket()=runBlocking<Unit> {
        val queues=listOf(LinkedBlockingQueue<String>(),LinkedBlockingQueue())
        val servers=queues.mapIndexed { index,queue -> MockWebServer().apply {
            enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) { webSocket.send("""{"event":"ms.channel.connect","data":{"token":"token-$index"}}""") }
                override fun onMessage(webSocket:WebSocket,text:String) { queue.add(JSONObject(text).getJSONObject("params").getString("DataOfCmd")) }
            }));start()
        } }
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val store=ProfileMemory();val manager=ConnectionManager(scope,store)
        try {
            manager.connect(Tv("one","First","127.0.0.1",port=servers[0].port));withTimeout(3000) { manager.state.first { it.status==Status.Connected } }
            manager.send(RemoteKey.UP);assertEquals("KEY_UP",queues[0].poll(2,TimeUnit.SECONDS))
            repeat(5) { manager.send(RemoteKey.VOLUP) }
            manager.connect(Tv("two","Second","127.0.0.1",port=servers[1].port));withTimeout(3000) { manager.state.first { it.status==Status.Connected && it.tv?.id=="two" } }
            manager.send(RemoteKey.RIGHT);assertEquals("KEY_RIGHT",queues[1].poll(2,TimeUnit.SECONDS));assertNull(queues[1].poll(200,TimeUnit.MILLISECONDS))
            assertEquals("token-0",store.token("one"));assertEquals("token-1",store.token("two"))
            assertNull(servers[1].takeRequest().requestUrl!!.queryParameter("token"))
        } finally { manager.disconnect();scope.cancel();servers.forEach { it.close() } }
    }
    @Test fun networkLossStopsCommandsUntilAvailableAndAuthorizedAgain()=runBlocking<Unit> {
        val server=MockWebServer()
        repeat(2) { server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
            override fun onOpen(webSocket:WebSocket,response:Response) { webSocket.send("""{"event":"ms.channel.connect"}""") }
        })) };server.start()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val manager=ConnectionManager(scope,ProfileMemory())
        try {
            manager.connect(Tv("one","TV","127.0.0.1",port=server.port));withTimeout(3000) { manager.state.first { it.status==Status.Connected } }
            manager.networkLost();assertEquals(Status.Reconnecting,manager.state.value.status)
            assertFalse(manager.transmitKey(RemoteKey.POWER).sent)
            manager.networkAvailable();withTimeout(3000) { manager.state.first { it.status==Status.Connected } }
            assertEquals(2,server.requestCount)
        } finally { manager.disconnect();scope.cancel();server.close() }
    }
    @Test fun httpAuthenticationDenialDoesNotSpinReconnects()=runBlocking<Unit> {
        val server=MockWebServer();server.enqueue(MockResponse().setResponseCode(401));server.start()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val manager=ConnectionManager(scope,ProfileMemory())
        try {
            manager.connect(Tv("one","TV","127.0.0.1",port=server.port))
            withTimeout(3000) { manager.diagnostics.first { it.failure==FailureKind.Authentication } }
            delay(1200);assertEquals(1,server.requestCount)
            assertEquals(Status.Failed,manager.state.value.status)
        } finally { manager.disconnect();scope.cancel();server.close() }
    }
}
