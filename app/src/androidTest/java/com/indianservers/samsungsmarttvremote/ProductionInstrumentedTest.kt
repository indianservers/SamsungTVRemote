package com.indianservers.samsungsmarttvremote

import android.app.Application
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.indianservers.samsungsmarttvremote.data.storage.LocalStore
import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.presentation.*
import com.indianservers.samsungsmarttvremote.ui.theme.SamsungSmartTVRemoveTheme
import com.indianservers.samsungsmarttvremote.widget.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiDeviceStorageInstrumentedTest {
    @Test fun certificateApprovalIsEncryptedPersistsAndResetsIndependently()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val ds=PreferenceDataStoreFactory.create(scope=scope) { File(context.cacheDir,"certificate-store-${System.nanoTime()}.preferences_pb") }
        val store=LocalStore(context,ds)
        val tv=Tv("uuid:certificate","TV","192.168.1.8",port=8002)
        val fingerprint=(1..32).joinToString(":") { "AB" }
        val pin=com.indianservers.samsungsmarttvremote.data.network.TvCertificatePin(tv.id,tv.ip,8002,fingerprint)
        try {
            store.save(tv);store.token(tv.id,"pair-token");store.certificate(pin)
            assertFalse(ds.data.first().asMap().values.any { it.toString().contains(fingerprint) })
            assertEquals(pin,LocalStore(context,ds).certificate(tv.id))
            assertNull(store.certificate("other-device"))
            store.clearCertificate(tv.id)
            assertNull(store.certificate(tv.id));assertEquals("pair-token",store.token(tv.id))
            store.certificate(pin);store.remove(tv.id)
            assertNull(store.certificate(tv.id))
            ds.edit { it[androidx.datastore.preferences.core.stringPreferencesKey("token:certificate:${tv.id}")]="tampered" }
            assertTrue(runCatching { store.certificate(tv.id) }.isFailure)
            store.clearCertificate(tv.id);assertNull(store.certificate(tv.id))
        } finally { store.forget();scope.cancel() }
    }

    @Test fun legacySingleTvMigrationPreservesCredentialsAndFavorites()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val ds=PreferenceDataStoreFactory.create(scope=scope) { File(context.cacheDir,"legacy-store-${System.nanoTime()}.preferences_pb") }
        val store=LocalStore(context,ds)
        val legacy=Tv("uuid:legacy","Original TV","192.168.1.10")
        try {
            ds.edit { data ->
                data[androidx.datastore.preferences.core.stringPreferencesKey("device")]=org.json.JSONObject().put("id",legacy.id).put("name",legacy.name).put("ip",legacy.ip).put("model",legacy.model).put("port",legacy.port).toString()
            }
            store.token(legacy.id,"legacy-private-token")
            store.updatePreferences(legacy.id) { it.copy(landing=LandingTab.Apps,favorites=listOf(FavoriteShortcut("home","Home",key=RemoteKey.HOME))) }
            assertEquals(legacy,store.load())
            store.save(Tv("uuid:new","New TV","192.168.1.11"))
            assertEquals(2,store.profilesFlow.first().size)
            store.select(legacy.id)
            assertEquals(legacy,store.load());assertEquals("legacy-private-token",store.token(legacy.id))
            assertEquals(RemoteKey.HOME,store.preferences(legacy.id).favorites.single().key)
        } finally { store.forget();scope.cancel() }
    }
    @Test fun credentialsDeletionWidgetsAndPreferencesAreIndependent()=runBlocking<Unit> {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val ds=PreferenceDataStoreFactory.create(scope=scope) { File(context.cacheDir,"production-store-${System.nanoTime()}.preferences_pb") }
        val store=LocalStore(context,ds)
        val first=Tv("uuid:first","First","192.168.1.8");val second=Tv("uuid:second","Second","192.168.1.9")
        try {
            store.save(first);store.token(first.id,"first-private-token");store.rename(first.id,"Cinema","Living Room")
            store.save(second);store.token(second.id,"second-private-token")
            store.updatePreferences(first.id) { it.copy(landing=LandingTab.Apps) };store.updatePreferences(second.id) { it.copy(landing=LandingTab.Touchpad) }
            store.widget(WidgetConfig(42,first.id));store.widget(WidgetConfig(43,second.id))
            store.appearance { it.copy(theme=RemoteTheme.MidnightDark,motion=0f,onboardingDone=true) }
            assertEquals(2,store.profilesFlow.first().size)
            assertEquals(second,store.load());store.select(first.id);assertEquals(first,store.load())
            assertEquals("first-private-token",store.token(first.id));assertEquals("second-private-token",store.token(second.id))
            assertFalse(ds.data.first().asMap().values.any { it.toString().contains("private-token") })
            store.save(first.copy(ip="192.168.1.18"))
            assertEquals("Cinema",store.profilesFlow.first().first { it.tv.id==first.id }.friendlyName)
            store.remove(first.id)
            assertNull(store.token(first.id));assertNull(store.widget(42));assertEquals(second.id,store.widget(43)!!.deviceId)
            assertEquals("second-private-token",store.token(second.id));assertEquals(LandingTab.Touchpad,store.preferences(second.id).landing)
            assertEquals(second,store.load());assertEquals(RemoteTheme.MidnightDark,store.appearanceFlow.first().theme)
            assertFalse(LocalStore(context,ds).appearanceFlow.first().motion>0f)
        } finally { store.forget();scope.cancel() }
    }
}

@RunWith(AndroidJUnit4::class)
class ProductionUiInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val models=ViewModelStore()
    private lateinit var store: LocalStore
    private lateinit var vm: RemoteViewModel
    @Before fun setup() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val ds=PreferenceDataStoreFactory.create(scope=scope) { File(app.cacheDir,"production-ui-${System.nanoTime()}.preferences_pb") }
        store=LocalStore(app,ds)
        val factory=object: ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=RemoteViewModel(app,store) as T }
        vm=ViewModelProvider(models,factory)[RemoteViewModel::class.java]
    }
    @After fun cleanup() { InstrumentationRegistry.getInstrumentation().runOnMainSync { models.clear() };scope.cancel() }
    @Test fun brandSelectionPersistsAndKeepsSamsungProfiles() {
        runBlocking { store.save(Tv("saved-samsung","Living room","192.168.1.8")) }
        compose.setContent { SamsungSmartTVRemoveTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { BrandSettings(vm,{}) } } }
        compose.onNodeWithText("Search 30 brands").performTextInput("Xiaomi")
        compose.onNodeWithText("Xiaomi / Mi").performScrollTo().performClick()
        compose.waitUntil(2000) { vm.appearance.value.brand=="Xiaomi / Mi" }
        assertEquals("Xiaomi / Mi",runBlocking { store.appearanceFlow.first().brand })
        assertEquals("saved-samsung",runBlocking { store.profilesFlow.first().single().tv.id })
        compose.onNodeWithText("Find Xiaomi / Mi TVs").assertExists()
    }
    @Test fun persistentNavigationOpensSettingsAndBrandSelector() {
        runBlocking { store.appearance { it.copy(onboardingDone=true) } }
        compose.setContent { RemoteApp(vm) }
        compose.waitUntil(3000) { vm.settingsReady.value }
        compose.onNodeWithContentDescription("Open Settings").performClick()
        compose.onNodeWithText("TV brands").performScrollTo().performClick()
        compose.onNodeWithText("Search 30 brands").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Devices").assertIsDisplayed().performClick()
        compose.onNodeWithText("Your televisions").assertIsDisplayed()
    }
    @Test fun mirroringIsSystemManagedAndCastingRequiresCompatibleReceiver() {
        compose.setContent { SamsungSmartTVRemoveTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { CastingScreen(vm,{}) } } }
        compose.onNodeWithText("Open phone mirroring controls").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Media Casting").performScrollTo().performClick()
        compose.onNodeWithText("Connect a television").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Direct media URL").performScrollTo().performTextInput("https://example.com/movie.mp4")
        compose.onNodeWithText("Cast this URL").performScrollTo().assertIsNotEnabled()
        assertFalse(vm.casting.state.value.sharing)
    }
    @Test fun documentSelectionDoesNotAutomaticallyStartSharing() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(app.cacheDir,"diagnostics").apply { mkdirs() }
        val file=File(folder,"cast-fixture.mp4").apply { writeBytes(byteArrayOf(0,1,2,3)) }
        try {
            val uri=androidx.core.content.FileProvider.getUriForFile(app,"${app.packageName}.files",file)
            compose.runOnIdle { vm.casting.selectFile(uri) }
            compose.waitUntil(3000) { !vm.casting.state.value.busy && vm.casting.state.value.file!=null }
            assertEquals(4L,vm.casting.state.value.file!!.bytes)
            assertFalse(vm.casting.state.value.sharing)
            compose.runOnIdle { vm.casting.clearFile() }
            assertNull(vm.casting.state.value.file)
        } finally { file.delete() }
    }
    @Test fun firstLaunchOnboardingCompletesAndPersists() {
        compose.setContent { RemoteApp(vm) }
        compose.waitUntil(3000) { vm.settingsReady.value }
        compose.onNodeWithText("Control Your TV").assertIsDisplayed()
        repeat(3) { compose.onNodeWithText("Continue").performScrollTo().performClick() }
        compose.onNodeWithText("Start controlling").performScrollTo().performClick()
        compose.waitUntil(2000) { vm.appearance.value.onboardingDone }
        compose.onNodeWithText("Your televisions").assertIsDisplayed()
        assertTrue(runBlocking { store.appearanceFlow.first().onboardingDone })
    }
    @Test fun themesPersistAndLargeFontSettingsRemainReachable() {
        var background=Color.Unspecified
        compose.setContent {
            val appearance by vm.appearance.collectAsState()
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                SamsungSmartTVRemoveTheme(appearance) {
                    val actualBackground=MaterialTheme.colorScheme.background
                    SideEffect { background=actualBackground }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) { AppearanceSettings(vm) }
                }
            }
        }
        compose.onNodeWithText("Midnight Dark").performScrollTo().performClick()
        compose.waitUntil(2000) { vm.appearance.value.theme==RemoteTheme.MidnightDark }
        compose.runOnIdle { assertEquals(Color(0xFF080F1E),background) }
        compose.onNodeWithText("Follow system").performScrollTo().assertIsDisplayed()
        assertEquals(RemoteTheme.MidnightDark,runBlocking { store.appearanceFlow.first().theme })
    }
    @Test fun deviceFriendlyNameAndRemovalPreserveOtherProfiles() {
        runBlocking { store.save(Tv("one","First TV","192.168.1.8"));store.save(Tv("two","Second TV","192.168.1.9")) }
        compose.setContent { SamsungSmartTVRemoveTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) { DeviceDashboard(vm,{},{},{}) } } }
        compose.waitUntil(2000) { vm.profiles.value.size==2 }
        compose.onAllNodesWithText("Device settings")[0].performScrollTo().performClick()
        compose.onNodeWithText("Friendly name").performTextReplacement("Cinema")
        compose.onNodeWithText("Save device").performClick()
        compose.waitUntil(2000) { vm.profiles.value.any { it.friendlyName=="Cinema" } }
        compose.onAllNodesWithText("Remove")[1].performScrollTo().performClick()
        compose.onNodeWithText("Remove TV").performClick()
        compose.waitUntil(2000) { vm.profiles.value.size==1 }
        assertEquals("Cinema",vm.profiles.value.single().friendlyName)
    }
}

@RunWith(AndroidJUnit4::class)
class WidgetConfigurationInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    @Test fun launcherWidgetConfigurationResolvesOnlyItsSavedDevice()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val manager=AppWidgetManager.getInstance(context)
        val host=AppWidgetHost(context,73109)
        val widgetId=host.allocateAppWidgetId()
        val store=LocalStore(context)
        val previous=store.load()
        val tv=Tv("phase3-widget-test","Widget Test","192.168.253.245")
        try {
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
            val bound=manager.bindAppWidgetIdIfAllowed(widgetId,ComponentName(context,CompactRemoteWidget::class.java))
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            Assume.assumeTrue("Launcher widget binding is not available on this device",bound)
            store.save(tv);store.rename(tv.id,"Widget Test","Office")
            val intent=Intent(context,WidgetConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId)
            ActivityScenario.launch<WidgetConfigurationActivity>(intent).use {
                compose.onNodeWithText("Configure your remote widget").assertIsDisplayed()
                compose.waitUntil(3000) { compose.onAllNodesWithText("Widget Test • Office").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Widget Test • Office").performScrollTo().performClick()
            }
            assertEquals(tv.id,store.widget(widgetId)!!.deviceId)
            val action=WidgetActionHandler.intent(context,widgetId,RemoteKey.VOLUP)
            assertEquals(RemoteKey.VOLUP,WidgetActionHandler.resolve(context,action)!!.second)
            action.putExtra("widgetKey",RemoteKey.DIGIT_9.name)
            assertNull(WidgetActionHandler.resolve(context,action))
            store.remove(tv.id)
            assertNull(store.widget(widgetId));assertNull(WidgetActionHandler.resolve(context,WidgetActionHandler.intent(context,widgetId,RemoteKey.POWER)))
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            store.remove(tv.id);previous?.let { runCatching { store.select(it.id) } };host.deleteAppWidgetId(widgetId)
        }
    }
}



@RunWith(AndroidJUnit4::class)
class CertificateUiInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private val tv=Tv("cert-ui","Samsung Q60AA 55 TV","192.168.100.224",port=8002)
    private val fingerprint=(1..32).joinToString(":") { "AB" }
    @Test fun unknownCertificateShowsFullFingerprintAndExplicitApproval() {
        val review=com.indianservers.samsungsmarttvremote.data.network.CertificateReview(tv,fingerprint,com.indianservers.samsungsmarttvremote.data.network.TlsFailure.UntrustedChain,false,true)
        var approved=false
        compose.setContent { SamsungSmartTVRemoveTheme { CertificateReviewDialog(review,{ approved=true },{}) } }
        compose.onNodeWithText("Verify Your Samsung TV").assertIsDisplayed()
        compose.onNodeWithText(fingerprint.split(':').chunked(8).joinToString("\n") { it.joinToString(":") }).assertExists()
        compose.onNodeWithText("Trust Certificate & Connect").performClick()
        compose.runOnIdle { assertTrue(approved) }
    }
    @Test fun changedCertificateShowsSecurityWarningAndCancelDoesNotApprove() {
        val review=com.indianservers.samsungsmarttvremote.data.network.CertificateReview(tv,fingerprint,com.indianservers.samsungsmarttvremote.data.network.TlsFailure.CertificateChanged,true,true)
        var approved=false;var canceled=false
        compose.setContent { SamsungSmartTVRemoveTheme { CertificateReviewDialog(review,{ approved=true },{ canceled=true }) } }
        compose.onNodeWithText("Security Warning: TV Certificate Changed").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(approved);assertTrue(canceled) }
    }
    @Test fun expiredCertificateCannotBeApproved() {
        val review=com.indianservers.samsungsmarttvremote.data.network.CertificateReview(tv,fingerprint,com.indianservers.samsungsmarttvremote.data.network.TlsFailure.Expired,false,false)
        compose.setContent { SamsungSmartTVRemoveTheme { CertificateReviewDialog(review,{ fail("Expired certificate approved") },{}) } }
        compose.onNodeWithText("Trust Certificate & Connect").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertExists()
    }
}
