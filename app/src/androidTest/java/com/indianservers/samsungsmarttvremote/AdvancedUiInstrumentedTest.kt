package com.indianservers.samsungsmarttvremote.presentation

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.indianservers.samsungsmarttvremote.ui.theme.SamsungSmartTVRemoveTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.samsungsmarttvremote.data.storage.LocalStore
import com.indianservers.samsungsmarttvremote.domain.*
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvancedUiInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val messages=LinkedBlockingQueue<JSONObject>()
    private val socket=CompletableDeferred<WebSocket>()
    private val viewModels=ViewModelStore()
    private lateinit var vm: RemoteViewModel
    private lateinit var server: MockWebServer
    @Before fun setup() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val persistence=PreferenceDataStoreFactory.create(scope=scope) { File(app.cacheDir,"ui-test-${System.nanoTime()}.preferences_pb") }
        val factory=object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T = RemoteViewModel(app,LocalStore(app,persistence)) as T
        }
        vm=ViewModelProvider(viewModels,factory)[RemoteViewModel::class.java]
        server=MockWebServer().apply {
            enqueue(MockResponse().withWebSocketUpgrade(object: WebSocketListener() {
                override fun onOpen(webSocket:WebSocket,response:Response) { socket.complete(webSocket);webSocket.send("""{"event":"ms.channel.connect"}""") }
                override fun onMessage(webSocket:WebSocket,text:String) {
                    val json=JSONObject(text);messages.add(json)
                    when(json.optJSONObject("params")?.optString("event")) {
                        "ed.installedApp.get" -> webSocket.send("""{"event":"ed.installedApp.get","data":{"data":[{"appId":"test.app","name":"Test App","app_type":2}]}}""")
                        "ed.apps.launch" -> webSocket.send("""{"event":"ed.apps.launch","data":200}""")
                    }
                }
            }));start()
        }
        compose.setContent { SamsungSmartTVRemoveTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            ConnectedRemote(vm,{vm.connection.send(it)},onChangeTv={},onSettings={})
        } } }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.connection.connect(Tv("isolated-ui-tv","Samsung · Test server","127.0.0.1",model="Automated test endpoint",port=server.port)) }
        runBlocking { withTimeout(5000) { vm.connection.state.first { it.status==Status.Connected } } }
        compose.waitForIdle()
    }
    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModels.clear() }
        scope.cancel();server.close()
    }
    private fun screenshot(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"qa").apply { mkdirs() }
        val image=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
    private fun key()=messages.poll(3,TimeUnit.SECONDS)?.getJSONObject("params")?.getString("DataOfCmd")
    @Test fun tabsUseOneAuthorizedSocketAndGateAdvancedActions() {
        screenshot("phase2-remote")
        compose.onNodeWithText("Touchpad").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Directional television touchpad").performScrollTo().performTouchInput { swipe(centerLeft+androidx.compose.ui.geometry.Offset(35f,0f),centerRight-androidx.compose.ui.geometry.Offset(35f,0f),350) }
        assertEquals("KEY_RIGHT",key());screenshot("phase2-touchpad")
        compose.onNodeWithText("Keyboard").performScrollTo().performClick()
        compose.onNodeWithText("Text for your television").performScrollTo().performTextInput("Hello తెలుగు")
        compose.onNodeWithText("Send to TV").performScrollTo().assertIsNotEnabled()
        runBlocking { socket.await().send("""{"event":"ms.remote.imeStart"}""") }
        compose.waitUntil(2000) { vm.advanced.focused.value }
        screenshot("phase2-keyboard")
        compose.onNodeWithText("Send to TV").performScrollTo().performClick()
        val batch=(1..3).map { messages.poll(3,TimeUnit.SECONDS) ?: error("Text frame missing") }
        assertEquals("SendInputString",batch[1].getJSONObject("params").getString("TypeOfRemote"))
        compose.waitUntil(3000) { !vm.advanced.busy.value }
        compose.onNodeWithText("Apps").performScrollTo().performClick()
        compose.onNodeWithText("Refresh apps").performScrollTo().performClick()
        compose.waitUntil(3000) { vm.advanced.apps.value.isNotEmpty() && !vm.advanced.busy.value }
        messages.clear();screenshot("phase2-apps")
        compose.onNodeWithContentDescription("Favorite Test App").performScrollTo().performClick()
        compose.waitUntil(2000) { vm.preferences.value.favorites.any { it.appId=="test.app" } }
        compose.onNodeWithText("Try launch").performScrollTo().performClick()
        assertEquals("ed.apps.launch",messages.poll(3,TimeUnit.SECONDS)!!.getJSONObject("params").getString("event"))
        compose.waitUntil(3000) { !vm.advanced.busy.value }
        compose.onNodeWithText("Sources").performScrollTo().performClick()
        compose.onNodeWithText("HDMI 1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Open source menu").performScrollTo().performClick()
        assertEquals("KEY_SOURCE",key());screenshot("phase2-sources")
        compose.onNodeWithText("More").performScrollTo().performClick()
        compose.onNodeWithText("7").performScrollTo().performClick()
        assertEquals("KEY_7",key())
        compose.onNodeWithText("Favorites").performScrollTo().performClick()
        compose.onNodeWithText("Try Test App").performScrollTo().assertIsDisplayed();screenshot("phase2-favorites")
        compose.runOnIdle { vm.pin(RemoteKey.HOME) }
        compose.waitUntil(2000) { vm.preferences.value.favorites.size==2 }
        val drag=compose.onNodeWithContentDescription("Reorder Test App",useUnmergedTree=true)
        drag.performScrollTo().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        drag.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f,height*1.3f));up() }
        compose.waitUntil(2500) { vm.preferences.value.favorites.firstOrNull()?.key==RemoteKey.HOME }
        assertEquals(1,server.requestCount)
    }
}

