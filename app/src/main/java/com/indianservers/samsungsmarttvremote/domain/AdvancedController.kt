package com.indianservers.samsungsmarttvremote.domain

import com.indianservers.samsungsmarttvremote.data.network.ConnectionManager
import com.indianservers.samsungsmarttvremote.data.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Uses the authorized Phase 1 transport. No feature creates another socket. */
class AdvancedController(private val scope: CoroutineScope,private val connection: ConnectionManager,private val timeoutMillis: Long=4000) {
    val capabilities=TVCapabilityManager()
    val focused=MutableStateFlow(false)
    val apps=MutableStateFlow<List<TVApplication>>(emptyList())
    val listedApps=MutableSharedFlow<Pair<String,List<TVApplication>>>(extraBufferCapacity=1)
    val notice=MutableStateFlow("")
    val busy=MutableStateFlow(false)
    private val operation=Mutex()
    private var current: Job?=null
    private var bound: String?=null
    init {
        scope.launch {
            connection.state.map { it.status to it.tv?.id }.distinctUntilChanged().collect { (status,id) ->
                if(status!=Status.Connected || bound!=id) {
                    current?.cancel(); focused.value=false; capabilities.reset()
                    if(bound!=id) apps.value=emptyList() else apps.update { list -> list.map { if(it.origin==AppOrigin.Installed) it.copy(origin=AppOrigin.Cached) else it } }
                    bound=id
                }
            }
        }
        scope.launch {
            connection.events.collect { event ->
                if(connection.state.value.status!=Status.Connected) return@collect
                when(event) {
                    is TVEvent.Ime -> {
                        if(event.focused) capabilities.record(Feature.TextInput,Support.Supported,"TV advertised a focused remote IME; insertion still depends on the current TV app.")
                        focused.value=event.focused
                    }
                    is TVEvent.Rejected -> {
                        if(event.feature==Feature.TextInput) { capabilities.record(Feature.TextInput,Support.Unknown,"TV reported an error near text transmission. Focus a TV text field again."); focused.value=false }
                        notice.value="TV reported a command error; execution was not confirmed."
                    }
                    else -> Unit
                }
            }
        }
    }
    private fun runAction(block: suspend ()->Unit) {
        if(current?.isActive==true) { notice.value="Wait for the current TV request."; return }
        current=scope.launch {
            operation.withLock {
                busy.value=true
                try { block() }
                catch(e: CancellationException) { notice.value="Action canceled because the connection or screen changed."; throw e }
                catch(e: IllegalArgumentException) { notice.value=e.message ?: "Invalid action." }
                catch(_: Exception) { notice.value="Unable to complete this TV request." }
                finally { busy.value=false }
            }
        }
    }
    fun cancelSensitiveAction() { current?.cancel() }
    fun sendText(text: String) = runAction {
        if(!focused.value || capabilities.state.value[Feature.TextInput].support!=Support.Supported) {
            notice.value="Text send is unavailable. Focus a text field on the TV and wait for its keyboard event."; return@runAction
        }
        val submission=connection.transmit(SamsungTextProtocol.messages(text),Feature.TextInput) { focused.value && capabilities.state.value[Feature.TextInput].support==Support.Supported }
        notice.value=if(submission.sent) "Text submitted; TV insertion is unconfirmed. Check the television." else submission.message
    }
    fun refreshApps() = runAction {
        val deviceId=connection.state.value.tv?.id ?: return@runAction
        coroutineScope {
            val reply=async(start=CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(timeoutMillis) {
                connection.events.first { it is TVEvent.Apps || it is TVEvent.InvalidApps || it is TVEvent.Rejected && it.feature==Feature.AppListing }
            } }
            val submission=connection.transmit(listOf(SamsungAppProtocol.list()),Feature.AppListing)
            if(!submission.sent) { reply.cancel(); notice.value=submission.message; return@coroutineScope }
            when(val event=reply.await()) {
                is TVEvent.Apps -> {
                    apps.value=(event.apps+apps.value.filter { it.origin==AppOrigin.Custom && event.apps.none { installed -> installed.id==it.id } }).distinctBy { it.id }
                    capabilities.record(Feature.AppListing,Support.Supported,"Installed-app listing received from this TV connection.")
                    listedApps.emit(deviceId to apps.value)
                    notice.value="${event.apps.size} applications reported by the TV."
                }
                else -> {
                    capabilities.record(Feature.AppListing,Support.Unknown,"Listing was rejected, malformed, or timed out. Cached and custom shortcuts are unverified.")
                    notice.value="TV app listing unavailable. Add a custom app identifier or use TV Home."
                }
            }
        }
    }
    val launcher=TVAppLauncher(connection)
    fun launch(app: TVApplication) = runAction {
        if(capabilities.state.value[Feature.AppLaunching].support==Support.Unsupported) { notice.value="App launching is unsupported on this TV."; return@runAction }
        coroutineScope {
            val reply=async(start=CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(timeoutMillis) {
                connection.events.first { it is TVEvent.LaunchReply || it is TVEvent.Rejected && it.feature==Feature.AppLaunching }
            } }
            val result=launcher.launch(app)
            if(!result.sent) { reply.cancel(); notice.value=result.message; return@coroutineScope }
            notice.value="Launch request sent. Waiting for a TV acknowledgment…"
            when(val event=reply.await()) {
                is TVEvent.LaunchReply -> if(event.accepted) {
                    capabilities.record(Feature.AppLaunching,Support.Supported,"TV acknowledged ed.apps.launch (200); visible app opening is not confirmed.")
                    notice.value="TV acknowledged the launch request. Check the TV screen."
                } else { capabilities.record(Feature.AppLaunching,Support.Unknown,"TV rejected the launch request; this app or action type may be unsupported."); notice.value="TV did not accept the launch request." }
                else -> { capabilities.record(Feature.AppLaunching,Support.Unknown,"No reliable launch acknowledgment received."); notice.value="Launch request submitted; app opening is unconfirmed." }
            }
        }
    }
}
class TVAppLauncher(private val connection: ConnectionManager) {
    suspend fun launch(app: TVApplication): Submission = connection.transmit(listOf(SamsungAppProtocol.launch(app)),Feature.AppLaunching)
}
