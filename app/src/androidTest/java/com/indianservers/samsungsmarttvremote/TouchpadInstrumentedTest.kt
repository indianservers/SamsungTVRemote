package com.indianservers.samsungsmarttvremote.presentation

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import com.indianservers.samsungsmarttvremote.ui.theme.SamsungSmartTVRemoveTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.indianservers.samsungsmarttvremote.domain.RemoteKey
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class TouchpadInstrumentedTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var store: ViewModelStore
    private lateinit var vm: RemoteViewModel
    private val sent=CopyOnWriteArrayList<RemoteKey>()
    @Before fun setup() {
        store=ViewModelStore()
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        vm=ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory(app))[RemoteViewModel::class.java]
        compose.setContent { SamsungSmartTVRemoveTheme { Column { TouchpadPanel(vm,true) { sent.add(it) } } } }
    }
    @After fun cleanup() { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }
    @Test fun swipeTapDoubleTapAndHoldProduceBoundedActions() {
        val pad=compose.onNodeWithContentDescription("Directional television touchpad")
        pad.performTouchInput { swipe(centerLeft+Offset(35f,0f),centerRight-Offset(35f,0f),400) }
        compose.runOnIdle { assertEquals(listOf(RemoteKey.RIGHT),sent.toList());sent.clear() }
        pad.performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(400)
        compose.waitUntil(2000) { sent.isNotEmpty() }
        compose.runOnIdle { assertEquals(listOf(RemoteKey.ENTER),sent.toList());sent.clear() }
        pad.performTouchInput { doubleClick(center,delayMillis=100) }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(listOf(RemoteKey.ENTER),sent.toList());sent.clear() }
        pad.performTouchInput { longClick(center,durationMillis=650) }
        compose.runOnIdle { assertEquals(listOf(RemoteKey.RETURN),sent.toList()) }
    }
    @Test fun canceledSwipeDoesNotTransmit() {
        compose.onNodeWithContentDescription("Directional television touchpad").performTouchInput {
            down(centerLeft+Offset(35f,0f));moveBy(Offset(180f,0f));cancel()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertTrue(sent.isEmpty()) }
    }
}

