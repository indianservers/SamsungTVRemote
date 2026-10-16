package com.indianservers.samsungsmarttvremote.presentation

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.storage.LocalStore
import com.indianservers.samsungsmarttvremote.data.discovery.TvDiscovery
import com.indianservers.samsungsmarttvremote.data.network.ConnectionManager
import com.indianservers.samsungsmarttvremote.data.storage.FavoritesManager
import com.indianservers.samsungsmarttvremote.data.protocol.SamsungAppProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RemoteViewModel @JvmOverloads constructor(app: Application,private val store: LocalStore=LocalStore(app)): AndroidViewModel(app) {
    private val discovery=TvDiscovery(app)
    val connection=ConnectionManager(viewModelScope,store)
    val casting=com.indianservers.samsungsmarttvremote.casting.CastingController(app,viewModelScope,connection)
    val advanced=AdvancedController(viewModelScope,connection)
    val preferences=MutableStateFlow(RemotePreferences())
    val devices=MutableStateFlow<List<Tv>>(emptyList())
    val scanning=MutableStateFlow(false)
    val error=MutableStateFlow("")
    val haptics=MutableStateFlow(true)
    val profiles=MutableStateFlow<List<DeviceProfile>>(emptyList())
    val appearance=MutableStateFlow(Appearance())
    val settingsReady=MutableStateFlow(false)
    val wifiAvailable=MutableStateFlow(false)
    private var switchJob: Job?=null
    private var restoreJob: Job?=null
    private var rediscoveredFor: String?=null
    private var scan: Job?=null
    private var initialized=false
    private var suspendedTv: Tv?=null
    private var foreground=true
    private val connectivity=app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback=object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { viewModelScope.launch { wifiAvailable.value=true;rediscoveredFor=null; if(foreground) connection.networkAvailable() } }
        override fun onLost(network: Network) { viewModelScope.launch { wifiAvailable.value=isWifiAvailable(); if(!wifiAvailable.value) connection.networkLost() } }
    }
    init {
        connectivity.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),networkCallback)
        wifiAvailable.value=isWifiAvailable()
        viewModelScope.launch { try { store.profilesFlow.collect { profiles.value=it } } catch(e: CancellationException) { throw e } catch(_: Exception) { error.value="Saved device profiles could not be read." } }
        viewModelScope.launch { try { store.appearanceFlow.collect { appearance.value=it; settingsReady.value=true } } catch(e: CancellationException) { throw e } catch(_: Exception) { settingsReady.value=true;error.value="Appearance preferences could not be read." } }
        viewModelScope.launch {
            connection.state.map { it.tv?.id }.distinctUntilChanged().collectLatest { id ->
                preferences.value=RemotePreferences()
                if(id!=null) try {
                    val loaded=store.preferences(id)
                    preferences.value=loaded; advanced.apps.value=loaded.apps
                } catch(e: CancellationException) { throw e } catch(_: Exception) { error.value="Unable to read local customization." }
            }
        }
        viewModelScope.launch {
            advanced.listedApps.collect { (id,apps) ->
                updatePreferencesFor(id) { it.copy(apps=apps) }
            }
        }
        viewModelScope.launch {
            connection.state.map { it.status to it.tv?.id }.distinctUntilChanged().collect { (status,id) ->
                val app=getApplication<Application>()
                val permitted=!LocalNetworkPermission.required(android.os.Build.VERSION.SDK_INT,app.applicationInfo.targetSdkVersion) || app.checkSelfPermission(LocalNetworkPermission.NAME)==android.content.pm.PackageManager.PERMISSION_GRANTED
                if(status==Status.Failed && id!=null && id!=rediscoveredFor && foreground && permitted && !scanning.value && profiles.value.any { it.tv.id==id } && connection.diagnostics.value.failure==FailureKind.Unreachable) {
                    rediscoveredFor=id;refresh() // One bounded rediscovery per selected device/network epoch, never a polling loop.
                }
            }
        }
    }
    private fun isWifiAvailable()=connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true
    fun appearance(change: (Appearance)->Appearance) { viewModelScope.launch { try { store.appearance(change) } catch(e: CancellationException) { throw e } catch(_: Exception) { error.value="Unable to save appearance preferences." } } }
    fun completeOnboarding() { appearance { it.copy(onboardingDone=true) } }
    fun connectProfile(profile: DeviceProfile) {
        scan?.cancel();restoreJob?.cancel();switchJob?.cancel();rediscoveredFor=null;suspendedTv=null;advanced.cancelSensitiveAction();connection.disconnect()
        switchJob=viewModelScope.launch { try { store.select(profile.tv.id);connection.connect(profile.tv) } catch(e: CancellationException) { throw e } catch(_: Exception) { error.value="Device could not be selected. Rescan and retry." } }
    }
    fun brand(name: String) { scan?.cancel();devices.value=emptyList();appearance { it.copy(brand=TvBrands.get(name).name) } }
    fun checkMedia() {
        val tv=connection.state.value.tv ?: return
        scan?.cancel();restoreJob?.cancel();switchJob?.cancel()
        scan=viewModelScope.launch {
            error.value="Checking advertised media services…"
            try {
                val found=discovery.media(tv.ip)
                if(!foreground || connection.state.value.tv!=tv) return@launch
                if(found!=null) { error.value="";connection.connect(tv.copy(transport=found.transport,rendering=found.rendering)) }
                else error.value="No compatible local media services found. Relative Samsung controls remain available."
            } catch(e:CancellationException) { throw e } catch(_:Exception) { error.value="Media service discovery failed. Check Wi-Fi and local-network permission." }
        }
    }
    fun connectDiscovered(tv: Tv) { scan?.cancel();restoreJob?.cancel();switchJob?.cancel();rediscoveredFor=null;suspendedTv=null;advanced.cancelSensitiveAction();connection.connect(tv) }
    fun rename(id: String,name: String,room: String) { viewModelScope.launch { try { store.rename(id,name,room);com.indianservers.samsungsmarttvremote.widget.WidgetActionHandler.updateAll(getApplication()) } catch(e: CancellationException) { throw e } catch(_: Exception) { error.value="Enter a name (up to 60 characters) and room (up to 40)." } } }
    fun removeProfile(id: String) {
        restoreJob?.cancel();switchJob?.cancel()
        if(connection.state.value.tv?.id==id) { connection.forget();suspendedTv=null }
        viewModelScope.launch { store.remove(id);com.indianservers.samsungsmarttvremote.widget.WidgetActionHandler.updateAll(getApplication()) }
    }
    fun reauthorize() { val tv=connection.state.value.tv ?: return;restoreJob?.cancel();switchJob?.cancel();connection.disconnect();switchJob=viewModelScope.launch { store.clearToken(tv.id);connection.connect(tv) } }
    fun resetDevices() { restoreJob?.cancel();connection.forget();suspendedTv=null;switchJob?.cancel();viewModelScope.launch { store.forget();initialized=false;com.indianservers.samsungsmarttvremote.widget.WidgetActionHandler.updateAll(getApplication()) } }
    fun permissionLost() { restoreJob?.cancel();switchJob?.cancel();suspendedTv=connection.state.value.tv;connection.disconnect();scan?.cancel();connection.reportError(FailureKind.Permission,"Local network permission is required") }
    fun updatePreferences(change: (RemotePreferences)->RemotePreferences) {
        val id=connection.state.value.tv?.id ?: return
        updatePreferencesFor(id,change)
    }
    private fun updatePreferencesFor(id: String,change: (RemotePreferences)->RemotePreferences) {
        viewModelScope.launch {
            try { val updated=store.updatePreferences(id,change); if(connection.state.value.tv?.id==id) preferences.value=updated }
            catch(e: CancellationException) { throw e } catch(e: Exception) { advanced.notice.value=e.message ?: "Unable to save customization." }
        }
    }
    fun favorite(app: TVApplication) { updatePreferences { it.copy(favorites=FavoritesManager.toggle(it.favorites,FavoriteShortcut("app:${app.id}",app.name,appId=app.id))) } }
    fun pin(key: RemoteKey) { updatePreferences { it.copy(favorites=FavoritesManager.toggle(it.favorites,FavoriteShortcut("key:${key.name}",key.code.removePrefix("KEY_").replace('_',' '),key=key))) } }
    fun moveFavorite(id: String,delta: Int) { updatePreferences { it.copy(favorites=FavoritesManager.move(it.favorites,id,delta)) } }
    fun removeFavorite(id: String) { updatePreferences { it.copy(favorites=it.favorites.filterNot { f -> f.id==id }) } }
    fun customApp(name: String,id: String,action: String) {
        val app=TVApplication(id.trim(),name.trim(),action,AppOrigin.Custom)
        try {
            require(app.name.isNotBlank() && app.name.length<=120) { "Enter an app name (up to 120 characters)." }
            SamsungAppProtocol.launch(app)
            advanced.apps.update { it.filterNot { old -> old.id==app.id }+app }
            updatePreferences { it.copy(apps=advanced.apps.value) }
            advanced.notice.value="Custom shortcut saved. Installation and launch support are unverified."
        } catch(e: IllegalArgumentException) { advanced.notice.value=e.message ?: "Invalid app identifier." }
    }
    fun removeCustom(id: String) { advanced.apps.update { it.filterNot { a -> a.id==id && a.origin==AppOrigin.Custom } }; updatePreferences { it.copy(apps=advanced.apps.value) } }
    fun start() { if(initialized) return; initialized=true; restoreJob=viewModelScope.launch { try { haptics.value=store.haptics(); val saved=store.load(); if(foreground) { saved?.let { connection.connect(it) }; refresh() } else suspendedTv=saved } catch(e: CancellationException) { throw e } catch(_: Exception) { error.value="Unable to restore the saved television." } } }
    fun resume() { foreground=true; suspendedTv?.let { suspendedTv=null; if(connection.state.value.tv==null) connection.connect(it) else connection.reconnect() } }
    fun pause() { casting.stopSharing();foreground=false; scan?.cancel(); advanced.cancelSensitiveAction(); if(connection.state.value.status in listOf(Status.Connected,Status.Connecting,Status.Reconnecting,Status.AwaitingAuthorization)) { suspendedTv=connection.state.value.tv; connection.disconnect() } }
    fun refresh() { scan?.cancel(); scan=viewModelScope.launch { scanning.value=true; connection.discovering(true); error.value=""; devices.value=emptyList()
        try { discovery.scan(appearance.value.brand) { tv ->
            devices.update { SamsungProtocol.merge(it,tv) }
            viewModelScope.launch {
                val old=connection.state.value.tv
                if(foreground && old?.id==tv.id && old.ip!=tv.ip && old.port==tv.port) connection.connect(tv)
            }
        } }
        catch(e: CancellationException) { throw e } catch(_: Exception) { connection.reportError(FailureKind.Discovery,"Local discovery failed");error.value="Discovery failed. Check local network permission and Wi-Fi." }
        finally { scanning.value=false; connection.discovering(false) }
    } }
    fun manual(ip: String) { viewModelScope.launch {
        error.value="Checking television…"
        try { val tv=if(appearance.value.brand=="Samsung") discovery.validate(ip.trim()) else discovery.media(ip.trim())?.takeIf { it.brand==appearance.value.brand }; if(tv==null) { connection.unsupportedDevice(); error.value="No compatible services found for this brand and IP. Check the TV model and local-network settings." } else { devices.update { SamsungProtocol.merge(it,tv) }; error.value="" } }
        catch(e: CancellationException) { throw e } catch(_:Exception) { error.value="Unable to validate TV. Enter its private IPv4 address and check Wi-Fi." }
    } }
    fun forget() { connection.state.value.tv?.id?.let(::removeProfile) }
    fun haptics(value:Boolean) { haptics.value=value; viewModelScope.launch { store.haptics(value) } }
    override fun onCleared() { casting.stopSharing(); connectivity.unregisterNetworkCallback(networkCallback); connection.disconnect() }
}


