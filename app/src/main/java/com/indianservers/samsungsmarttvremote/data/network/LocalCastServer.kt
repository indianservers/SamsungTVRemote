package com.indianservers.samsungsmarttvremote.data.network

import com.indianservers.samsungsmarttvremote.domain.*
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/** Ephemeral foreground sharing: one selected document, one selected receiver, no directory access. */
class LocalCastServer(private val address: InetAddress,private val receiver: String,private val length: Long,
    private val mime: String,private val source: ()->InputStream) {
    private val token=UUID.randomUUID().toString().replace("-","")
    private val path="/$token/media"
    private val socket=run { require(length>0 && CastMediaPolicy.validType(mime));ServerSocket(0,8,address) }
    private val clients=ConcurrentHashMap.newKeySet<Socket>()
    private val streams=ConcurrentHashMap.newKeySet<InputStream>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val slots=Semaphore(3)
    val url="http://${address.hostAddress}:${socket.localPort}$path"
    init {
        require(length>0 && CastMediaPolicy.validType(mime))
        scope.launch {
            while(isActive) {
                val client=try { socket.accept() } catch(_:Exception) { break }
                if(!slots.tryAcquire()) { client.close();continue }
                clients.add(client)
                launch { try { client.use(::serve) } catch(_:Exception) { } finally { clients.remove(client);slots.release() } }
            }
        }
    }
    private fun serve(client: Socket) {
        client.soTimeout=5000
        fun response(code: String,headers: String="") {
            client.getOutputStream().write(("HTTP/1.1 $code\r\nConnection: close\r\n$headers\r\n").toByteArray(Charsets.US_ASCII))
        }
        if(client.inetAddress.hostAddress!=receiver) { response("403 Forbidden","Content-Length: 0\r\n");return }
        val input=client.getInputStream().buffered()
        fun line():String {
            val bytes=ArrayList<Byte>()
            while(bytes.size<4096) { val next=input.read();if(next<0) break;if(next==10) return bytes.toByteArray().toString(Charsets.US_ASCII).trimEnd('\r');bytes.add(next.toByte()) }
            error("Invalid HTTP header")
        }
        val request=line().split(' ')
        if(request.size!=3 || request[1]!=path) { response("404 Not Found","Content-Length: 0\r\n");return }
        if(request[0] !in setOf("GET","HEAD")) { response("405 Method Not Allowed","Allow: GET, HEAD\r\nContent-Length: 0\r\n");return }
        var range: String?=null;var count=0
        while(true) {
            val header=line();count+=header.length
            if(count>16384) { response("431 Request Header Fields Too Large","Content-Length: 0\r\n");return }
            if(header.isEmpty()) break
            if(header.substringBefore(':').equals("Range",true)) { if(range!=null) { response("400 Bad Request","Content-Length: 0\r\n");return };range=header.substringAfter(':').trim() }
        }
        val window=HttpByteRanges.parse(range,length)
        if(window==null) { response("416 Range Not Satisfiable","Content-Range: bytes */$length\r\nContent-Length: 0\r\n");return }
        val headers="Content-Type: $mime\r\nAccept-Ranges: bytes\r\nContent-Length: ${window.length}\r\nCache-Control: no-store\r\ntransferMode.dlna.org: ${if(mime.startsWith("image/")) "Interactive" else "Streaming"}\r\n"+
            (if(range!=null) "Content-Range: bytes ${window.start}-${window.end}/$length\r\n" else "")
        if(request[0]=="HEAD") { response(if(range==null) "200 OK" else "206 Partial Content",headers);return }
        source().use { stream ->
            streams.add(stream)
            try {
            var skip=window.start
            while(skip>0) { val n=stream.skip(skip);if(n>0) skip-=n else { require(stream.read()!=-1);skip-- } }
            response(if(range==null) "200 OK" else "206 Partial Content",headers)
            var remaining=window.length;val buffer=ByteArray(65536);val output=client.getOutputStream()
            while(remaining>0 && scope.isActive) {
                val read=stream.read(buffer,0,minOf(remaining,buffer.size.toLong()).toInt());if(read<0) break
                output.write(buffer,0,read);remaining-=read
            }
            } finally { streams.remove(stream) }
        }
    }
    fun close() { scope.cancel();runCatching { socket.close() };clients.forEach { runCatching { it.close() } };clients.clear();streams.forEach { runCatching { it.close() } };streams.clear() }
}
