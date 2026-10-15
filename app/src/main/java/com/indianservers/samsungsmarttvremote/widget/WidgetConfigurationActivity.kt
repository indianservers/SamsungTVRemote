package com.indianservers.samsungsmarttvremote.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.indianservers.samsungsmarttvremote.data.storage.LocalStore
import com.indianservers.samsungsmarttvremote.domain.WidgetConfig
import com.indianservers.samsungsmarttvremote.ui.theme.SamsungSmartTVRemoveTheme
import kotlinx.coroutines.launch

class WidgetConfigurationActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1)
        if(!WidgetActionHandler.belongs(this,id)) { finish();return }
        val store=LocalStore(applicationContext)
        setContent {
            val profiles by produceState<List<com.indianservers.samsungsmarttvremote.domain.DeviceProfile>?>(initialValue=null) { store.profilesFlow.collect { value=it } }
            val scope=rememberCoroutineScope()
            var error by remember { mutableStateOf("") }
            SamsungSmartTVRemoveTheme {
                Scaffold { padding -> Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text("Configure your remote widget",style=MaterialTheme.typography.headlineSmall)
                    Text("Choose a paired TV. Widget actions open the app for a live connection and explicit send confirmation. No background service stays running.")
                    if(profiles==null) CircularProgressIndicator() else if(profiles!!.isEmpty()) Text("Pair a TV in Smart TV Remote, then add this widget again.")
                    profiles.orEmpty().forEach { profile -> Button(onClick={scope.launch {
                        try {
                            store.widget(WidgetConfig(id,profile.tv.id));WidgetActionHandler.updateAll(applicationContext)
                            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish()
                        } catch(_:Exception) { error="Widget configuration could not be saved." }
                    }},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("${profile.friendlyName} • ${profile.room}") } }
                    if(error.isNotEmpty()) Text(error)
                    TextButton(onClick={finish()}) { Text("Cancel") }
                } }
            }
        }
    }
}
