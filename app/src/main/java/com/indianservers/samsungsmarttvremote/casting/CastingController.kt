package com.indianservers.samsungsmarttvremote.casting

import android.app.Application
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.OpenableColumns
import com.indianservers.samsungsmarttvremote.data.network.*
import com.indianservers.samsungsmarttvremote.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetAddress

data class SelectedCastFile(val uri: Uri,val name: String,val mime: String,val bytes: Long)
data class CastingState(val file: SelectedCastFile?=null,val busy: Boolean=false,val sharing: Boolean=false,
    val notice: String="Select a compatible TV, then explicitly choose what to cast.")
class CastingController(private val app: Application,private val scope: CoroutineScope,private val connection: ConnectionManager) {
    val state=MutableStateFlow(CastingState())
    private var server: LocalCastServer?=null
    private var job: Job?=null
    private var receiver: String?=null
    private var epoch=0
    init {
        scope.launch { connection.state.map { it.tv?.id to it.status }.distinctUntilChanged().collect { (id,status) ->
            if(receiver!=null && (receiver!=id || status!=Status.Connected)) stopSharing()
        } }
    }
    fun selectFile(uri: Uri) {
        stopSharing()
        val version=epoch
        job=scope.launch {
            state.update { it.copy(busy=true) }
            try {
                val file=withContext(Dispatchers.IO) {
                    require(uri.scheme=="content")
                    val resolver=app.contentResolver
                    val mime=resolver.getType(uri)?.substringBefore(';')?.lowercase().orEmpty()
                    require(CastMediaPolicy.validType(mime))
                    var name="Selected media";var length=-1L
                    resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use { cursor ->
                        if(cursor.moveToFirst()) {
                            val n=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0) name=cursor.getString(n).orEmpty().take(120)
                            val size=cursor.getColumnIndex(OpenableColumns.SIZE);if(size>=0 && !cursor.isNull(size)) length=cursor.getLong(size)
                        }
                    }
                    resolver.openAssetFileDescriptor(uri,"r")?.use { asset -> if(length<0) length=asset.length }
                    require(length>0)
                    SelectedCastFile(uri,name.ifBlank { "Selected media" },mime,length)
                }
                if(version==epoch) state.value=CastingState(file=file,notice="File selected. Tap Cast selected file to share it with the selected TV.")
            } catch(e:CancellationException) { throw e } catch(_:Exception) {
                if(version==epoch) state.value=CastingState(notice="Choose a readable MP4/WebM, MP3/M4A or JPEG/PNG file with a known size. Codec support depends on the TV.")
            }
        }
    }
    fun castUrl(url: String,mime: String) {
        if(!CastMediaPolicy.validUrl(url) || !CastMediaPolicy.validType(mime)) { state.update { it.copy(notice="Use a direct HTTP/HTTPS media URL without embedded credentials and select its actual format.") };return }
        begin { _,_ -> url to mime }
    }
    fun castFile() {
        val file=state.value.file ?: return
        begin { tv,version ->
            val connectivity=app.getSystemService(ConnectivityManager::class.java)
            val network=connectivity.activeNetwork ?: error("No local network")
            val address=connectivity.getLinkProperties(network)?.linkAddresses?.map { it.address }?.firstOrNull { SamsungProtocol.localIp(it.hostAddress.orEmpty()) } ?: error("No reachable local IPv4")
            val sharing=withContext(Dispatchers.IO) {
                LocalCastServer(address,tv.ip,file.bytes,file.mime) { app.contentResolver.openInputStream(file.uri) ?: error("File permission unavailable") }
            }
            if(version!=epoch) { sharing.close();error("Sharing canceled") }
            server=sharing
            state.update { it.copy(sharing=true) }
            sharing.url to file.mime
        }
    }
    private fun begin(source: suspend (Tv,Int)->Pair<String,String>) {
        val tv=connection.state.value.tv ?: return
        if(connection.state.value.status!=Status.Connected || !connection.media.value.actions.containsAll(setOf("SetAVTransportURI","Play"))) {
            state.update { it.copy(notice="This TV has not advertised media loading. Check media capabilities first.") };return
        }
        val file=state.value.file
        stopSharing();state.update { it.copy(file=file,busy=true,notice="Sending media request to ${tv.name}…") }
        val version=epoch;receiver=tv.id
        job=scope.launch {
            try {
                val (url,mime)=source(tv,version)
                if(version!=epoch || connection.state.value.tv!=tv) return@launch
                val result=connection.castMedia(tv.id,url,mime)
                if(version==epoch) {
                    if(!result.sent) { server?.close();server=null }
                    state.update { it.copy(busy=false,sharing=server!=null,notice=result.message) }
                }
            } catch(e:CancellationException) { throw e } catch(_:Exception) {
                if(version==epoch) { server?.close();server=null;state.update { it.copy(busy=false,sharing=false,notice="Unable to share this media on the local network. Check the TV, file access and Wi-Fi.") } }
            }
        }
    }
    fun stopPlayback() {
        val id=connection.state.value.tv?.id
        stopSharing()
        val version=epoch
        if(id!=null) job=scope.launch {
            val result=connection.stopCastMedia(id)
            if(version==epoch) state.update { it.copy(notice="Local sharing is off. ${result.message}") }
        }
    }
    fun stopSharing() {
        epoch++;job?.cancel();job=null;receiver=null;server?.close();server=null
        state.update { it.copy(busy=false,sharing=false,notice=if(it.sharing) "Local file sharing stopped." else it.notice) }
    }
    fun clearFile() { stopSharing();state.update { it.copy(file=null,notice="Selected file cleared.") } }
}
