package com.indianservers.samsungsmarttvremote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import android.content.pm.ActivityInfo
import com.indianservers.samsungsmarttvremote.data.storage.LocalStore
import com.indianservers.samsungsmarttvremote.domain.Tv
import kotlinx.coroutines.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.indianservers.samsungsmarttvremote.domain.*
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
    @Test fun encryptedPairingPersistsAndCanBeForgotten()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        val dataStore=PreferenceDataStoreFactory.create(scope=scope) { File(context.cacheDir,"storage-test-${System.nanoTime()}.preferences_pb") }
        val store=LocalStore(context,dataStore); val tv=Tv("instrumented","Test TV","192.168.1.12")
        try {
            store.save(tv); store.token(tv.id,"sensitive-pairing-token"); store.haptics(false)
            val reopened=LocalStore(context,dataStore)
            assertEquals(tv,reopened.load()); assertEquals("sensitive-pairing-token",reopened.token(tv.id)); assertFalse(reopened.haptics())
            store.updatePreferences(tv.id) { it.copy(landing=LandingTab.Apps,favorites=listOf(FavoriteShortcut("key:HOME","Home",key=RemoteKey.HOME))) }
            assertEquals(LandingTab.Apps,reopened.preferences(tv.id).landing)
            assertEquals(RemoteKey.HOME,reopened.preferences(tv.id).favorites.single().key)
            assertTrue(reopened.preferences("another-tv").favorites.isEmpty())
            store.forget(); assertNull(reopened.load()); assertNull(reopened.token(tv.id))
        } finally { store.forget(); scope.cancel() }
    }
}
@RunWith(AndroidJUnit4::class)
class LifecycleInstrumentedTest {
    @Test fun activitySurvivesRotationAndRecreation() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.isAttachedToWindow)
                activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED,activity.lifecycle.currentState)
                assertTrue(activity.window.decorView.isAttachedToWindow)
            }
        }
    }
}
