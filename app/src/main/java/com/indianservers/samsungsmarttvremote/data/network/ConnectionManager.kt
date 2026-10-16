package com.indianservers.samsungsmarttvremote.data.network

import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.storage.*

import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import com.indianservers.samsungsmarttvremote.data.protocol.SamsungAppProtocol
import com.indianservers.samsungsmarttvremote.data.protocol.LocalMediaSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ConnectionManager(private val scope: CoroutineScope, private val store: PairingStore) {
    private val mutable=MutableStateFlow(RemoteState())
    val state=mutable.asStateFlow()
    val diagnostics=MutableStateFlow(ConnectionDiagnostics())
    fun reportError(kind: FailureKind,message: String) { diagnostics.update { it.copy(failure=kind,lastError=message) } }
    val media=MutableStateFlow(MediaSnapshot())
    private var localMedia: LocalMediaSession?=null
    private val mediaLock=Mutex()
    private fun mediaAction(action: suspend (LocalMediaSession)->Unit) {
        val version=generation;val target=localMedia ?: return
        if(state.value.status!=Status.Connected || media.value.busy) return
        media.update { it.copy(busy=true) }
        scope.launch {
            mediaLock.withLock {
                try {
                    if(version!=generation || target!==localMedia) return@withLock
                    action(target)
                    val result=target.refresh()
                    if(version==generation) media.value=result
                } catch(e: CancellationException) { throw e } catch(_:Exception) {
                    if(version==generation) media.update { it.copy(busy=false,message="TV did not confirm the media action. Refresh and try again.") }
                }
            }
        }
    }
    suspend fun castMedia(tvId: String,url: String,mime: String): Submission {
        val version=generation;val target=localMedia ?: return Submission(false,"Check media capabilities first.")
        return mediaLock.withLock {
            if(version!=generation || selected?.id!=tvId || state.value.status!=Status.Connected) return@withLock Submission(false,"TV connection changed. Nothing was cast.")
            try {
                target.loadMedia(url,mime)
                runCatching { target.refresh() }.getOrNull()?.let { if(version==generation) media.value=it }
                Submission(true,"TV accepted the media request. Check its screen; codec/playback is not guaranteed.")
            } catch(e:CancellationException) { throw e } catch(_:Exception) { Submission(false,"TV did not accept the complete playback request. Check its media support and screen.") }
        }
    }
    suspend fun stopCastMedia(tvId: String): Submission {
        val version=generation;val target=localMedia ?: return Submission(false,"No active local-media session.")
        return mediaLock.withLock {
            if(version!=generation || selected?.id!=tvId || state.value.status!=Status.Connected) return@withLock Submission(false,"TV connection changed; stop was not sent.")
            try { target.key(RemoteKey.STOP);Submission(true,"Stop request accepted by the TV. Check its screen.") }
            catch(e:CancellationException) { throw e } catch(_:Exception) { Submission(false,"TV did not accept Stop. Use its own remote if playback continues.") }
        }
    }
    fun setVolume(value: Int) { if(media.value.canVolume) mediaAction { it.volume(value) } }
    fun seekTo(seconds: Long) { val track=media.value.track;if(media.value.canSeek) mediaAction { target -> val current=target.refresh();require(current.track==track) { "Media changed" };target.seek(seconds) } }
    fun refreshMedia() { mediaAction { } }
    private suspend fun observeMedia(tv: Tv,version: Int) {
        val target=LocalMediaSession(tv);localMedia=target
        try {
            mediaLock.withLock { val result=target.inspect();if(version==generation) media.value=result }
            if(tv.platform==TvPlatform.Dlna && version==generation) unsupported.value=RemoteKey.entries.filterNot(target::supports).toSet()
            while(currentCoroutineContext().isActive && version==generation) {
                delay(3000)
                mediaLock.withLock {
                    try { val result=target.refresh();if(version==generation) { media.value=result;if(tv.platform==TvPlatform.Dlna) unsupported.value=RemoteKey.entries.filterNot(target::supports).toSet() } }
                    catch(e:CancellationException) { throw e }
                    catch(_:Exception) { if(version==generation) { media.value=MediaSnapshot(message="Local media unavailable. Refresh or reconnect.");if(tv.platform==TvPlatform.Dlna) unsupported.value=RemoteKey.entries.toSet() } }
                }
            }
        } finally { target.close();if(target===localMedia) localMedia=null }
    }
    private var socket: WebSocket?=null
    private var session: Job?=null
    @Volatile private var generation=0
    private var selected: Tv?=null
    val certificateReview=MutableStateFlow<CertificateReview?>(null)
    val tlsFailure=MutableStateFlow<TlsFailure?>(null)
    fun cancelCertificateReview() { certificateReview.value=null }
    fun approveCertificate(review: CertificateReview) {
        if(certificateReview.value!=review || selected!=review.tv || !review.approvable) return
        val version=generation
        certificateReview.value=null
        session=scope.launch {
            try {
                store.certificate(TvCertificatePin(review.tv.id,review.tv.ip,review.tv.port,review.fingerprint))
                if(version==generation && selected==review.tv) connect(review.tv)
            } catch(e: CancellationException) { throw e } catch(_: Exception) {
                if(version==generation) { reportError(FailureKind.Protocol,"Certificate approval could not be saved"); mutable.value=RemoteState(Status.Failed,review.tv,"Certificate approval could not be saved. Retry review.") }
            }
        }
    }
    fun resetCertificate() {
        val tv=selected ?: return
        disconnect()
        val version=generation
        session=scope.launch {
            try { store.clearCertificate(tv.id); if(version==generation) { tlsFailure.value=null; mutable.value=RemoteState(Status.Disconnected,tv,"TV certificate trust reset. Connect to verify again.") } }
            catch(e: CancellationException) { throw e } catch(_: Exception) { if(version==generation) mutable.value=RemoteState(Status.Failed,tv,"Certificate trust could not be reset. Retry.") }
        }
    }
    private var desired=false
    private var lastKey: RemoteKey?=null
    private var lastFeature: Feature?=null
    private val mutableEvents=MutableSharedFlow<TVEvent>(extraBufferCapacity=64)
    val events=mutableEvents.asSharedFlow()
    val unsupported=MutableStateFlow<Set<RemoteKey>>(emptySet())
    private data class Outbound(val version: Int,val messages: List<String>,val key: RemoteKey?,val feature: Feature?,val guard: ()->Boolean,val result: CompletableDeferred<Submission>)
    private val commands=Channel<Outbound>(8)
    init { scope.launch {
        for (command in commands) {
            if(command.result.isCancelled) continue
            var sent=true
            for(message in command.messages) {
                if(command.result.isCancelled || command.version!=generation || mutable.value.status!=Status.Connected || !command.guard() || command.key in unsupported.value) { sent=false; break }
                lastKey=command.key; lastFeature=command.feature
                if(selected?.platform==TvPlatform.Dlna) {
                    try {
                        val target=localMedia ?: error("No media session")
                        mediaLock.withLock {
                            if(command.version!=generation || !command.guard()) error("Device changed")
                            target.key(command.key ?: error("This feature requires a menu remote"))
                            val result=target.refresh();if(command.version==generation) media.value=result
                        }
                    } catch(e:CancellationException) { throw e } catch(_:Exception) { sent=false }
                    if(sent && command.version==generation) diagnostics.update { it.copy(lastTransmissionMillis=System.currentTimeMillis()) }
                    delay(120);continue
                }
                if(socket?.send(message)!=true) {
                    sent=false
                    reportError(FailureKind.Unreachable,"Command transport unavailable")
                    mutable.value=mutable.value.copy(status=Status.Failed,message="Command could not be transmitted. Reconnect.")
                    socket?.cancel(); break
                }
                if(command.version==generation) diagnostics.update { it.copy(lastTransmissionMillis=System.currentTimeMillis()) }
                delay(120)
            }
            command.result.complete(Submission(sent,if(sent) "Submitted to TV transport; execution is unconfirmed." else "Action not fully sent. Connection, focus, or gesture changed."))
        }
    } }
    private fun enqueue(messages: List<String>,feature: Feature?,key: RemoteKey?,guard: ()->Boolean): CompletableDeferred<Submission> {
        val result=CompletableDeferred<Submission>()
        if(selected?.platform==TvPlatform.Dlna && key==null || key in unsupported.value || state.value.status!=Status.Connected || !guard() || commands.trySend(Outbound(generation,messages,key,feature,guard,result)).isFailure) {
            val message="Command not sent. Check connection or slow down."
            mutable.value=mutable.value.copy(message=message); result.complete(Submission(false,message))
        }
        return result
    }
    fun send(key: RemoteKey,guard: ()->Boolean = { true }) {
        val feature=when(key) {
            RemoteKey.UP,RemoteKey.DOWN,RemoteKey.LEFT,RemoteKey.RIGHT,RemoteKey.ENTER -> Feature.Directional
            RemoteKey.SOURCE -> Feature.SourceMenu
            RemoteKey.PLAY,RemoteKey.PAUSE,RemoteKey.STOP,RemoteKey.REWIND,RemoteKey.FF -> Feature.Media
            else -> null
        }
        enqueue(listOf(SamsungProtocol.command(key)),feature,key,guard)
    }
    suspend fun transmit(messages: List<String>,feature: Feature,guard: ()->Boolean = { true }): Submission {
        require(messages.isNotEmpty() && messages.size<=4)
        val pending=enqueue(messages,feature,null,guard)
        return try { pending.await() } finally { if(!pending.isCompleted) pending.cancel() }
    }
    suspend fun transmitKey(key: RemoteKey): Submission {
        val pending=enqueue(listOf(SamsungProtocol.command(key)),null,key) { true }
        return try { pending.await() } finally { if(!pending.isCompleted) pending.cancel() }
    }
    fun disconnect() {
        localMedia?.close();localMedia=null;media.value=MediaSnapshot();certificateReview.value=null; desired=false; generation++; session?.cancel(); session=null; socket?.cancel(); socket=null
        while(true) { val queued=commands.tryReceive().getOrNull() ?: break; queued.result.complete(Submission(false,"Connection changed before transmission.")) }
        lastKey=null; lastFeature=null; mutable.value=RemoteState(tv=selected,message="Disconnected")
    }
    fun reconnect() { selected?.let { connect(it) } }
    fun forget() { disconnect(); selected=null; mutable.value=RemoteState() }
    fun discovering(active: Boolean) { if(mutable.value.tv==null) mutable.value=RemoteState(if(active) Status.Discovering else Status.Disconnected,message=if(active) "Searching your local network…" else "Select a TV or enter its IP address.") }
    fun unsupportedDevice() { if(mutable.value.tv==null) mutable.value=RemoteState(Status.Unsupported,message="This TV model may not support Wi-Fi remote control.") }
    fun networkLost() { if(desired) { disconnect(); desired=true; mutable.value=RemoteState(Status.Reconnecting,selected,"Wi-Fi connection lost. Waiting for network…") } }
    fun networkAvailable() { if(desired && state.value.status in listOf(Status.Reconnecting,Status.Failed)) reconnect() }
    fun connect(tv: Tv) {
        val changed=selected?.id!=tv.id
        disconnect(); selected=tv; desired=true; tlsFailure.value=null
        if(changed) { unsupported.value=emptySet();diagnostics.value=ConnectionDiagnostics() }
        val version=generation
        if(tv.platform==TvPlatform.Dlna) {
            if(!LocalMediaPolicy.valid(tv)) { mutable.value=RemoteState(Status.Unsupported,tv,"Invalid local media endpoint");return }
            unsupported.value=RemoteKey.entries.toSet()
            session=scope.launch {
                mutable.value=RemoteState(Status.Connecting,tv,"Checking advertised local media services…")
                val probe=LocalMediaSession(tv)
                try {
                    val verified=probe.inspect()
                    if(version!=generation) return@launch
                    store.save(tv);media.value=verified
                    mutable.value=RemoteState(Status.Connected,tv,"Local media ready • menu navigation is unavailable")
                    observeMedia(tv,version)
                } catch(e:CancellationException) { throw e } catch(_:Exception) {
                    if(version==generation) { reportError(FailureKind.Protocol,"Local media query failed");mutable.value=RemoteState(Status.Failed,tv,"TV did not expose usable media services. Check its local-network controls.") }
                } finally { probe.close() }
            }
            return
        }
        session=scope.launch {
            val owner=coroutineContext[Job]!!
            for (attempt in 0..4) {
                if (attempt>0) { mutable.value=RemoteState(Status.Reconnecting,tv,"Connection lost. Reconnecting… ($attempt/4)"); delay(SamsungProtocol.retryDelay(attempt-1)) }
                val ended=CompletableDeferred<Boolean>()
                val authorized=CompletableDeferred<Unit>()
                val alive=java.util.concurrent.atomic.AtomicBoolean(true)
                mutable.value=RemoteState(Status.Connecting,tv,"Connecting to ${tv.name}…")
                val approved=try { if(tv.port==8002) store.certificate(tv.id) else null } catch(e: CancellationException) { throw e } catch(_: Exception) {
                    mutable.value=RemoteState(Status.Failed,tv,"Saved certificate trust unavailable. Reset trust before retrying."); break
                }
                val trust=if(tv.port==8002) TvCertificateTrust(tv,approved) else null
                val builder=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(5,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS)
                val client=(trust?.configure(builder) ?: builder).build()
                val name=Base64.getEncoder().encodeToString("Smart TV Remote".toByteArray())
                val pairingToken=if(tv.port==8002) store.token(tv.id) else null
                val url=HttpUrl.Builder().scheme(if(tv.port==8002) "https" else "http").host(tv.ip).port(tv.port)
                    .addPathSegments("api/v2/channels/samsung.remote.control").addQueryParameter("name",name).apply {
                        pairingToken?.let { addQueryParameter("token",it) }
                    }.build().toString().replaceFirst("https:","wss:").replaceFirst("http:","ws:")
                val activeSocket=client.newWebSocket(Request.Builder().url(url).build(),object: WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket,response: Response) {
                        if(version==generation && alive.get()) {
                            tlsFailure.value=null;reportError(FailureKind.None,"None")
                            mutable.value=RemoteState(Status.AwaitingAuthorization,tv,"Approve Smart TV Remote on your television.")
                        }
                    }
                    override fun onMessage(webSocket: WebSocket,text: String) {
                        if(version!=generation || !alive.get() || ended.isCompleted) return
                        runCatching { SamsungAppProtocol.event(text) }.getOrNull()?.let { mutableEvents.tryEmit(it) }
                        val event=runCatching { SamsungProtocol.event(text) }.getOrNull() ?: return
                        when(event.first) {
                            "ms.channel.connect" -> scope.launch(owner) {
                                if(version!=generation || !alive.get() || ended.isCompleted) return@launch
                                try {
                                    event.second?.let { store.token(tv.id,it) }; store.save(tv)
                                    if(version==generation && alive.get() && !ended.isCompleted) {
                                        mutable.value=RemoteState(Status.Connected,tv,"Connected to ${tv.name}"); authorized.complete(Unit)
                                        if(localMedia==null && (tv.transport!=null || tv.rendering!=null)) launch { try { observeMedia(tv,version) } catch(e:CancellationException) { throw e } catch(_:Exception) { if(version==generation) media.value=MediaSnapshot(message="This TV did not expose usable media sliders") } }
                                    }
                                } catch (e: CancellationException) { throw e } catch (_: Exception) { if(version==generation && alive.get() && !ended.isCompleted) { mutable.value=RemoteState(Status.Connected,tv,"Connected, but pairing could not be saved."); authorized.complete(Unit) } }
                            }
                            "ms.channel.unauthorized", "ms.channel.timeOut" -> {
                                reportError(if(pairingToken!=null) FailureKind.Authentication else FailureKind.Denied,"TV authorization denied or expired")
                                mutable.value=RemoteState(Status.Failed,tv,"TV authorization declined or expired. Reconnect and approve on TV.")
                                scope.launch(owner) { try { store.clearToken(tv.id) } finally { ended.complete(false) } }
                            }
                            "ms.error" -> {
                                reportError(FailureKind.CommandRejected,"TV reported a command rejection; execution unconfirmed")
                                lastKey?.let { unsupported.update { keys -> keys+it } }
                                mutableEvents.tryEmit(TVEvent.Rejected(lastFeature,lastKey))
                                mutable.value=mutable.value.copy(message="TV reported a command error. The most recent key, if any, is disabled for this session.")
                            }
                        }
                    }
                    override fun onFailure(webSocket: WebSocket,t: Throwable,response: Response?) {
                        if(version==generation && alive.get()) {
                            val kind=trust?.classify(t) ?: TlsFailure.WebSocket
                            val tls=kind!=TlsFailure.WebSocket
                            tlsFailure.value=if(tls) kind else null
                            val review=trust?.review(t)
                            reportError(if(tls || response?.code==404) FailureKind.Protocol else if(response?.code in listOf(401,403)) FailureKind.Authentication else FailureKind.Unreachable,
                                if(tls) "TLS: ${kind.name}" else "WebSocket HTTP ${response?.code ?: "unavailable"}")
                            val message=when(kind) {
                                TlsFailure.Expired -> "TV certificate has expired. Check TV date and firmware; expired certificates cannot be approved."
                                TlsFailure.NotYetValid -> "TV certificate is not yet valid. Check phone/TV date and firmware."
                                TlsFailure.CertificateChanged -> "Security warning: this TV's certificate changed. Review the new fingerprint."
                                TlsFailure.DeviceMismatch -> "Saved certificate belongs to a different TV endpoint. Reset TV certificate trust and review again."
                                TlsFailure.UntrustedChain,TlsFailure.HostnameMismatch -> "Verify your Samsung TV certificate before connecting."
                                TlsFailure.Handshake -> "TLS handshake failed before certificate verification completed. Check TV firmware and supported TLS configuration."
                                else -> if(response?.code in listOf(401,403)) "TV authorization rejected. Reauthorize and approve on TV." else if(response?.code==404) "TV remote WebSocket endpoint is unsupported." else "Unable to reach TV. Check Wi-Fi and TV power."
                            }
                            mutable.value=RemoteState(Status.Failed,tv,message)
                            certificateReview.value=review
                            ended.complete(!tls && response?.code !in listOf(401,403,404))
                        }
                    }
                    override fun onClosed(webSocket: WebSocket,code: Int,reason: String) { ended.complete(true) }
                    override fun onClosing(webSocket: WebSocket,code: Int,reason: String) { webSocket.close(code,null) }
                })
                socket=activeSocket
                try {
                    val ready=withTimeoutOrNull(45000) { kotlinx.coroutines.selects.select<Boolean> {
                        authorized.onAwait { true }
                        ended.onAwait { false }
                    } }
                    if(ready==null) { reportError(FailureKind.Denied,"TV authorization timed out"); mutable.value=RemoteState(Status.Failed,tv,"TV authorization timed out. Reconnect and approve on TV."); break }
                    if(!ended.await()) break
                } finally { alive.set(false); activeSocket.cancel(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
            }
            if(version==generation && mutable.value.status==Status.Reconnecting) mutable.value=RemoteState(Status.Failed,tv,"Reconnect limit reached. Check TV power and Wi-Fi.")
        }
    }
}
