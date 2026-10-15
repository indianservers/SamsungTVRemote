package com.indianservers.samsungsmarttvremote.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.indianservers.samsungsmarttvremote.MainActivity
import com.indianservers.samsungsmarttvremote.R
import com.indianservers.samsungsmarttvremote.data.storage.LocalStore
import com.indianservers.samsungsmarttvremote.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

open class RemoteWidgetProvider(private val advanced: Boolean): AppWidgetProvider() {
    override fun onUpdate(context: Context,manager: AppWidgetManager,ids: IntArray) {
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { withTimeout(8000) { ids.forEach { WidgetActionHandler.render(context,it,advanced) } } } catch(_:Exception) { /* No command is attempted by receivers. Open app remains the recovery path. */ } finally { pending.finish() } }
    }
    override fun onAppWidgetOptionsChanged(context:Context,manager:AppWidgetManager,id:Int,options:android.os.Bundle) { onUpdate(context,manager,intArrayOf(id)) }
    override fun onDeleted(context:Context,ids:IntArray) {
        val pending=goAsync()
        CoroutineScope(Dispatchers.IO).launch { try { withTimeout(5000) { ids.forEach { LocalStore(context).deleteWidget(it) } } } catch(_:Exception) { /* No command is attempted by receivers. Open app remains the recovery path. */ } finally { pending.finish() } }
    }
}
class CompactRemoteWidget: RemoteWidgetProvider(false)
class AdvancedRemoteWidget: RemoteWidgetProvider(true)

object WidgetActionHandler {
    private val controls=mapOf(
        R.id.widget_power to RemoteKey.POWER,R.id.widget_volup to RemoteKey.VOLUP,R.id.widget_voldown to RemoteKey.VOLDOWN,
        R.id.widget_mute to RemoteKey.MUTE,R.id.widget_up to RemoteKey.UP,R.id.widget_down to RemoteKey.DOWN,
        R.id.widget_left to RemoteKey.LEFT,R.id.widget_right to RemoteKey.RIGHT,R.id.widget_ok to RemoteKey.ENTER,
        R.id.widget_home to RemoteKey.HOME,R.id.widget_back to RemoteKey.RETURN)
    fun belongs(context:Context,id:Int): Boolean {
        val name=AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ?: return false
        return name.packageName==context.packageName && name.className in setOf(CompactRemoteWidget::class.java.name,AdvancedRemoteWidget::class.java.name)
    }
    fun intent(context:Context,id:Int,key:RemoteKey?):Intent=Intent(context,MainActivity::class.java)
        .setAction("com.indianservers.samsungsmarttvremote.WIDGET")
        .setData(Uri.parse("smartremote://widget/$id/${key?.name ?: "open"}"))
        .putExtra("widgetId",id).putExtra("widgetKey",key?.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    suspend fun resolve(context:Context,incoming:Intent):Pair<WidgetConfig,RemoteKey>? {
        if(incoming.action!="com.indianservers.samsungsmarttvremote.WIDGET") return null
        val id=incoming.getIntExtra("widgetId",-1)
        if(!belongs(context,id)) return null
        val key=runCatching { RemoteKey.valueOf(incoming.getStringExtra("widgetKey").orEmpty()) }.getOrNull() ?: return null
        if(incoming.data!=intent(context,id,key).data) return null
        val store=LocalStore(context);val config=store.widget(id) ?: return null
        val profile=store.profilesFlow.first().firstOrNull { it.tv.id==config.deviceId }
        return if(WidgetPolicy.valid(config,profile,key)) config to key else null
    }
    suspend fun render(context:Context,id:Int,advanced:Boolean) {
        val manager=AppWidgetManager.getInstance(context)
        if(!belongs(context,id)) return
        val store=LocalStore(context)
        val config=store.widget(id)
        val profile=store.profilesFlow.first().firstOrNull { it.tv.id==config?.deviceId }
        val view=RemoteViews(context.packageName,if(advanced) R.layout.widget_advanced else R.layout.widget_compact)
        view.setTextViewText(R.id.widget_name,profile?.friendlyName?.take(28) ?: "Choose a saved TV")
        view.setTextViewText(R.id.widget_status,if(profile==null) "Configure widget in the app" else config?.status ?: "Open app to connect")
        val widgetControls=if(advanced) controls else controls.filterKeys { it in setOf(R.id.widget_power,R.id.widget_volup,R.id.widget_voldown,R.id.widget_mute) }
        widgetControls.forEach { (button,key) ->
            view.setBoolean(button,"setEnabled",profile!=null)
            view.setOnClickPendingIntent(button,PendingIntent.getActivity(context,id,intent(context,id,key),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        view.setOnClickPendingIntent(R.id.widget_open,PendingIntent.getActivity(context,id,intent(context,id,null),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val configure=Intent(context,WidgetConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).setData(Uri.parse("smartremote://configure/$id"))
        view.setOnClickPendingIntent(R.id.widget_name,PendingIntent.getActivity(context,id,configure,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        manager.updateAppWidget(id,view)
    }
    suspend fun updateAll(context:Context)=withContext(Dispatchers.IO) {
        val manager=AppWidgetManager.getInstance(context)
        listOf(CompactRemoteWidget::class.java to false,AdvancedRemoteWidget::class.java to true).forEach { (provider,advanced) ->
            manager.getAppWidgetIds(ComponentName(context,provider)).forEach { render(context,it,advanced) }
        }
    }
}

