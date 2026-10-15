package com.indianservers.samsungsmarttvremote

import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.protocol.*
import com.indianservers.samsungsmarttvremote.data.storage.*
import com.indianservers.samsungsmarttvremote.data.network.ConnectionManager
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.mockwebserver.*

class AdvancedProtocolTest {
    private val gestures=GestureInterpreter()
    @Test fun deliberateSwipeDirectionsAndSensitivity() {
        assertEquals(RemoteKey.RIGHT,gestures.swipe(80f,5f,1f));assertEquals(RemoteKey.LEFT,gestures.swipe(-80f,5f,1f))
        assertEquals(RemoteKey.UP,gestures.swipe(5f,-80f,1f));assertEquals(RemoteKey.DOWN,gestures.swipe(5f,80f,1f))
        assertNull(gestures.swipe(10f,5f,1f));assertNull(gestures.swipe(80f,80f,1f))
        assertNull(gestures.swipe(40f,0f,.5f));assertEquals(RemoteKey.RIGHT,gestures.swipe(40f,0f,2f))
    }
    @Test fun tapDoubleTapHoldAndCancelHaveExplicitMappings() {
        assertEquals(RemoteKey.ENTER,gestures.action(TouchAction.Tap));assertEquals(RemoteKey.ENTER,gestures.action(TouchAction.DoubleTap))
        assertEquals(RemoteKey.RETURN,gestures.action(TouchAction.LongPress));assertNull(gestures.action(TouchAction.Cancel))
    }
    @Test fun textUsesUtf8Base64AndCommitSequence() {
        val text="తెలుగు हिंदी 日本語 👨‍👩‍👧‍👦 é"
        val messages=SamsungTextProtocol.messages(text).map(::JSONObject)
        assertEquals("custom.remote.textReceived",messages[0].getJSONObject("params").getString("event"))
        val input=messages[1].getJSONObject("params")
        assertEquals("SendInputString",input.getString("TypeOfRemote"));assertEquals("base64",input.getString("DataOfCmd"))
        assertEquals(text,String(Base64.getDecoder().decode(input.getString("Cmd")),Charsets.UTF_8))
        assertEquals("SendInputEnd",messages[2].getJSONObject("params").getString("TypeOfRemote"))
    }
    @Test fun textLengthIsBytesAndMalformedUnicodeRejected() {
        SamsungTextProtocol.messages("a".repeat(1024))
        assertThrows(IllegalArgumentException::class.java) { SamsungTextProtocol.messages("界".repeat(342)) }
        assertThrows(IllegalArgumentException::class.java) { SamsungTextProtocol.messages("\uD800") }
        assertThrows(IllegalArgumentException::class.java) { SamsungTextProtocol.messages("") }
    }
    @Test fun installedAppTypesDetermineLaunchAction() {
        val apps=SamsungAppProtocol.parseApps(JSONObject("""{"data":{"data":[{"appId":"111299001912","name":"YouTube","app_type":2},{"appId":"org.tizen.browser","name":"Browser","app_type":4}]}}"""))
        assertEquals(listOf("DEEP_LINK","NATIVE_LAUNCH"),apps.map { it.actionType })
        assertTrue(apps.all { it.origin==AppOrigin.Installed })
        val launch=JSONObject(SamsungAppProtocol.launch(apps.last())).getJSONObject("params")
        assertEquals("ed.apps.launch",launch.getString("event"));assertEquals("host",launch.getString("to"))
        assertEquals("org.tizen.browser",launch.getJSONObject("data").getString("appId"))
        assertEquals("NATIVE_LAUNCH",launch.getJSONObject("data").getString("action_type"))
    }
    @Test fun malformedAppListingDoesNotBecomeInstalledApps() {
        assertEquals(TVEvent.InvalidApps,SamsungAppProtocol.event("""{"event":"ed.installedApp.get","data":{"data":[{"name":"Missing ID"}]}}"""))
        assertEquals(TVEvent.Apps(emptyList()),SamsungAppProtocol.event("""{"event":"ed.installedApp.get","data":{"data":[]}}"""))
        assertThrows(IllegalArgumentException::class.java) { SamsungAppProtocol.launch(TVApplication("../invalid","App","DEEP_LINK")) }
    }
    @Test fun unknownAppTypeCannotGenerateALaunchRequest() {
        val app=SamsungAppProtocol.parseApps(JSONObject("""{"data":{"data":[{"appId":"unknown","name":"Unknown type","app_type":999}]}}""")).single()
        assertEquals("UNKNOWN",app.actionType)
        assertThrows(IllegalArgumentException::class.java) { SamsungAppProtocol.launch(app) }
    }
    @Test fun capabilitiesStartUnknownAndResetPerSession() {
        val manager=TVCapabilityManager();assertTrue(manager.state.value.features.values.all { it.support==Support.Unknown })
        assertFalse(SamsungPointerProtocol.enabled(manager.state.value))
        manager.record(Feature.TextInput,Support.Supported,"remote IME event")
        assertEquals(Support.Supported,manager.state.value[Feature.TextInput].support)
        manager.record(Feature.TextInput,Support.Unsupported,"verified rejection")
        assertEquals(Support.Unsupported,manager.state.value[Feature.TextInput].support)
        manager.reset();assertEquals(Support.Unknown,manager.state.value[Feature.TextInput].support)
    }
    @Test fun sourceMenuIsNotAHardcodedHdmiSequence() {
        InputSources.candidates.forEach { assertNull(InputSources.command(it,TVCapabilities())) }
        assertEquals("KEY_SOURCE",JSONObject(SamsungProtocol.command(RemoteKey.SOURCE)).getJSONObject("params").getString("DataOfCmd"))
    }
    @Test fun numericAndMediaKeysMatchActualWireCodes() {
        (0..9).forEach { digit -> assertEquals("KEY_$digit",JSONObject(SamsungProtocol.command(RemoteKey.valueOf("DIGIT_$digit"))).getJSONObject("params").getString("DataOfCmd")) }
        listOf(RemoteKey.STOP,RemoteKey.REWIND,RemoteKey.FF,RemoteKey.PRECH,RemoteKey.CH_LIST).forEach { assertEquals("KEY_${it.name}",it.code) }
    }
    @Test fun favoritesAndPreferencesRoundTripWithCachedAppEvidence() {
        val p=RemotePreferences(LandingTab.Keyboard,1.5f,setOf("channels"),listOf(FavoriteShortcut("app:yt","YouTube",appId="yt"),FavoriteShortcut("key:HOME","Home",key=RemoteKey.HOME)),listOf(TVApplication("yt","YouTube","DEEP_LINK"),TVApplication("custom","Custom app","NATIVE_LAUNCH",AppOrigin.Custom)))
        val restored=RemotePreferencesCodec.decode(RemotePreferencesCodec.encode(p))
        assertEquals(p.copy(apps=p.apps.map { if(it.origin==AppOrigin.Installed) it.copy(origin=AppOrigin.Cached) else it }),restored)
        val moved=FavoritesManager.move(restored.favorites,"key:HOME",-1)
        assertEquals("key:HOME",moved.first().id)
        assertEquals(1,FavoritesManager.toggle(moved,moved.first()).size)
        assertThrows(IllegalArgumentException::class.java) { FavoritesManager.toggle(emptyList(),FavoriteShortcut("invalid","Invalid")) }
    }
    @Test fun launchReplyIsOnlyAnAcknowledgment() {
        assertEquals(TVEvent.LaunchReply(true),SamsungAppProtocol.event("""{"event":"ed.apps.launch","data":200}"""))
        assertEquals(TVEvent.LaunchReply(false),SamsungAppProtocol.event("""{"event":"ed.apps.launch","data":404}"""))
    }
}

private class TestStore: PairingStore {
    override suspend fun save(tv: Tv) {}
    override suspend fun token(id: String): String?=null
    override suspend fun token(id: String,token: String) {}
    override suspend fun clearToken(id: String) {}
}
private class FakeTV(private val reply: (WebSocket,JSONObject)->Unit = {_,_->}): AutoCloseable {
    val messages=LinkedBlockingQueue<JSONObject>()
    private val socket=CompletableDeferred<WebSocket>()
    val server=MockWebServer().apply {
        enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket,response: Response) { socket.complete(webSocket);webSocket.send("""{"event":"ms.channel.connect"}""") }
            override fun onMessage(webSocket: WebSocket,text: String) { val json=JSONObject(text); messages.add(json);reply(webSocket,json) }
        }));start()
    }
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    val connection=ConnectionManager(scope,TestStore())
    val advanced=AdvancedController(scope,connection,500)
    suspend fun connect() {
        connection.connect(Tv("fake-tv","Fake TV","127.0.0.1",port=server.port))
        withTimeout(3000) { connection.state.first { it.status==Status.Connected } }
        delay(40)
    }
    suspend fun emit(text: String) { assertTrue(socket.await().send(text)) }
    fun take()=messages.poll(3,TimeUnit.SECONDS) ?: error("No TV command received")
    override fun close() { connection.disconnect();scope.cancel();server.close() }
}
class AdvancedConnectionTest {
    @Test fun textGatedUntilImeEventThenUsesSameSocket()=runBlocking<Unit> {
        FakeTV().use { tv ->
            tv.connect(); tv.advanced.sendText("Must not send")
            withTimeout(1000) { tv.advanced.notice.first { it.contains("unavailable") } }
            assertNull(tv.messages.poll(150,TimeUnit.MILLISECONDS))
            tv.emit("""{"event":"ms.remote.imeStart"}""")
            withTimeout(1000) { tv.advanced.focused.first { it } }
            tv.advanced.sendText("తెలుగు 😊")
            val messages=(1..3).map { tv.take() }
            assertEquals("SendInputString",messages[1].getJSONObject("params").getString("TypeOfRemote"))
            withTimeout(1500) { tv.advanced.notice.first { it.contains("Text submitted") } }
            assertEquals(1,tv.server.requestCount)
            tv.emit("""{"event":"ms.remote.imeEnd"}""")
            withTimeout(1000) { tv.advanced.focused.first { !it } }
        }
    }
    @Test fun tvListingAndLaunchAcknowledgmentUpdateEvidence()=runBlocking<Unit> {
        FakeTV { socket,json ->
            when(json.optJSONObject("params")?.optString("event")) {
                "ed.installedApp.get" -> socket.send("""{"event":"ed.installedApp.get","data":{"data":[{"appId":"yt","name":"YouTube","app_type":2}]}}""")
                "ed.apps.launch" -> socket.send("""{"event":"ed.apps.launch","data":200}""")
            }
        }.use { tv ->
            tv.connect();tv.advanced.refreshApps()
            val apps=withTimeout(2000) { tv.advanced.apps.first { it.isNotEmpty() } }
            withTimeout(2000) { tv.advanced.busy.first { !it } }
            assertEquals(Support.Supported,tv.advanced.capabilities.state.value[Feature.AppListing].support)
            tv.advanced.launch(apps.single())
            withTimeout(2000) { tv.advanced.capabilities.state.first { it[Feature.AppLaunching].support==Support.Supported } }
            assertTrue(tv.advanced.notice.value.contains("acknowledged"))
            assertEquals(1,tv.server.requestCount)
        }
    }
    @Test fun listingTimeoutRetainsUnknownAndNoFakeCatalog()=runBlocking<Unit> {
        FakeTV().use { tv ->
            tv.connect();tv.advanced.refreshApps()
            withTimeout(2000) { tv.advanced.notice.first { it.contains("listing unavailable") } }
            assertEquals(Support.Unknown,tv.advanced.capabilities.state.value[Feature.AppListing].support)
            assertTrue(tv.advanced.apps.value.isEmpty())
        }
    }
    @Test fun malformedListingIsHandledWithoutCapabilityPromotion()=runBlocking<Unit> {
        FakeTV { socket,_ -> socket.send("""{"event":"ed.installedApp.get","data":"malformed"}""") }.use { tv ->
            tv.connect();tv.advanced.refreshApps()
            withTimeout(2000) { tv.advanced.notice.first { it.contains("listing unavailable") } }
            assertEquals(Support.Unknown,tv.advanced.capabilities.state.value[Feature.AppListing].support)
        }
    }
    @Test fun textRejectionClearsFocusWithoutLeakingInputToStatus()=runBlocking<Unit> {
        FakeTV { socket,json -> if(json.optJSONObject("params")?.optString("TypeOfRemote")=="SendInputString") socket.send("""{"event":"ms.error","data":{"message":"unsupported text"}}""") }.use { tv ->
            tv.connect();tv.emit("""{"event":"ms.remote.imeStart"}""")
            withTimeout(1000) { tv.advanced.focused.first { it } }
            tv.advanced.sendText("sensitive-secret")
            withTimeout(2000) { tv.advanced.focused.first { !it } }
            assertFalse(tv.advanced.notice.value.contains("sensitive-secret"))
            assertEquals(Support.Unknown,tv.advanced.capabilities.state.value[Feature.TextInput].support)
        }
    }
    @Test fun releaseGuardCancelsQueuedRepeatCommands()=runBlocking<Unit> {
        FakeTV().use { tv ->
            tv.connect()
            val held=java.util.concurrent.atomic.AtomicBoolean(true)
            tv.connection.send(RemoteKey.VOLUP)
            repeat(5) { tv.connection.send(RemoteKey.VOLUP) { held.get() } }
            tv.take();held.set(false)
            assertNull(tv.messages.poll(350,TimeUnit.MILLISECONDS))
        }
    }
    @Test fun connectionLossDuringTextNeverReportsFullSubmission()=runBlocking<Unit> {
        FakeTV().use { tv ->
            tv.connect();tv.emit("""{"event":"ms.remote.imeStart"}""")
            withTimeout(1000) { tv.advanced.focused.first { it } }
            tv.advanced.sendText("private")
            tv.take();tv.connection.disconnect()
            withTimeout(1000) { tv.advanced.focused.first { !it } }
            delay(200)
            assertFalse(tv.advanced.notice.value.contains("Text submitted"))
            assertNull(tv.messages.poll(200,TimeUnit.MILLISECONDS))
        }
    }
    @Test fun negativeLaunchReplyDoesNotClaimAnOpenedApplication()=runBlocking<Unit> {
        FakeTV { socket,_ -> socket.send("""{"event":"ed.apps.launch","data":404}""") }.use { tv ->
            tv.connect();tv.advanced.launch(TVApplication("missing","Missing","DEEP_LINK",AppOrigin.Custom))
            withTimeout(2000) { tv.advanced.notice.first { it.contains("did not accept") } }
            assertEquals(Support.Unknown,tv.advanced.capabilities.state.value[Feature.AppLaunching].support)
        }
    }
}

