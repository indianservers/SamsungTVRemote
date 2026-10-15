package com.indianservers.samsungsmarttvremote

import com.indianservers.samsungsmarttvremote.data.network.*
import com.indianservers.samsungsmarttvremote.data.storage.PairingStore
import com.indianservers.samsungsmarttvremote.domain.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.cert.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.Assert.*
import org.junit.Test

class TvCertificateTrustTest {
    private val tv=Tv("tv-a","Samsung Test","192.168.1.20",port=8002)
    private fun cert()=HeldCertificate.Builder().commonName("Samsung local TV").build().certificate
    private fun pin(c:X509Certificate)=TvCertificatePin(tv.id,tv.ip,tv.port,TvCertificateTrust.fingerprint(c))
    private val rejecting=object:X509TrustManager {
        override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
        override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String) { throw CertificateException() }
        override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String) { throw CertificateException("untrusted fixture") }
    }
    @Test fun normalTrustedChainRemainsAcceptedWithoutReview() {
        val c=cert();val system=HandshakeCertificates.Builder().addTrustedCertificate(c).build().trustManager
        TvCertificateTrust(tv,null,system).checkServerTrusted(arrayOf(c),"RSA")
    }
    @Test fun unknownSelfSignedIsObservedButRejected() {
        val c=cert();val trust=TvCertificateTrust(tv,null,rejecting)
        val e=assertThrows(CertificateException::class.java) { trust.checkServerTrusted(arrayOf(c),"RSA") }
        val review=trust.review(SSLHandshakeException("sanitized").apply { initCause(e) })!!
        assertEquals(TvCertificateTrust.fingerprint(c),review.fingerprint)
        assertEquals(TlsFailure.UntrustedChain,review.reason);assertTrue(review.approvable)
    }
    @Test fun explicitExactLeafApprovalWorksAcrossNewClients() {
        val c=cert();repeat(2) { TvCertificateTrust(tv,pin(c),rejecting).checkServerTrusted(arrayOf(c),"RSA") }
    }
    @Test fun changedCertificateIsRejectedEvenIfSystemTrustsReplacement() {
        val a=cert();val b=cert();val trusted=HandshakeCertificates.Builder().addTrustedCertificate(b).build().trustManager
        val trust=TvCertificateTrust(tv,pin(a),trusted)
        val e=assertThrows(PinFailure::class.java) { trust.checkServerTrusted(arrayOf(b),"RSA") }
        assertEquals(TlsFailure.CertificateChanged,e.reason);assertTrue(trust.review(e)!!.changed)
    }
    @Test fun wrongDeviceAndEndpointCannotUsePin() {
        val c=cert()
        listOf(tv.copy(id="other"),tv.copy(ip="192.168.1.21")).forEach { other ->
            val e=assertThrows(PinFailure::class.java) { TvCertificateTrust(other,pin(c),rejecting).checkServerTrusted(arrayOf(c),"RSA") }
            assertEquals(TlsFailure.DeviceMismatch,e.reason)
        }
    }
    @Test fun approvedExpiredCertificateRemainsRejected() {
        val c=HeldCertificate.Builder().commonName("Expired").validityInterval(0,1000).build().certificate
        val trust=TvCertificateTrust(tv,pin(c),rejecting)
        val e=assertThrows(CertificateExpiredException::class.java) { trust.checkServerTrusted(arrayOf(c),"RSA") }
        assertEquals(TlsFailure.Expired,trust.classify(e));assertFalse(trust.review(e)!!.approvable)
    }
    @Test fun notYetValidCertificateAndProtocolFailureHaveDistinctDiagnostics() {
        val future=System.currentTimeMillis()+86400000
        val c=HeldCertificate.Builder().commonName("Future").validityInterval(future,future+86400000).build().certificate
        val trust=TvCertificateTrust(tv,null,rejecting)
        runCatching { trust.checkServerTrusted(arrayOf(c),"RSA") }
        assertEquals(TlsFailure.NotYetValid,trust.classify(CertificateException()))
        val empty=TvCertificateTrust(tv,null,rejecting)
        assertEquals(TlsFailure.Handshake,empty.classify(SSLHandshakeException("protocol_version")))
        assertNull(empty.review(SSLHandshakeException("protocol_version")))
        assertEquals(TlsFailure.WebSocket,empty.classify(java.io.IOException("closed")))
    }
    @Test fun resetRemovesCertificateException() {
        val c=cert();TvCertificateTrust(tv,pin(c),rejecting).checkServerTrusted(arrayOf(c),"RSA")
        assertThrows(CertificateException::class.java) { TvCertificateTrust(tv,null,rejecting).checkServerTrusted(arrayOf(c),"RSA") }
    }
    @Test fun hostnameExceptionRequiresMatchingLeafAndExactEndpoint() {
        val c=cert()
        fun session(leaf:X509Certificate)=java.lang.reflect.Proxy.newProxyInstance(SSLSession::class.java.classLoader,arrayOf(SSLSession::class.java)) { _,method,_ ->
            if(method.name=="getPeerCertificates") arrayOf<java.security.cert.Certificate>(leaf) else null
        } as SSLSession
        val trust=TvCertificateTrust(tv,pin(c),rejecting)
        val verifier=trust.hostnameVerifier(HostnameVerifier { _,_ -> false })
        assertTrue(verifier.verify(tv.ip,session(c)))
        assertFalse(verifier.verify("192.168.1.21",session(c)))
        assertFalse(verifier.verify(tv.ip,session(cert())))
        assertFalse(TvCertificateTrust(tv,null,rejecting).hostnameVerifier(HostnameVerifier { _,_ -> false }).verify(tv.ip,session(c)))
    }
}

private class CertificateMemory:PairingStore {
    val tokens=java.util.concurrent.ConcurrentHashMap<String,String>()
    var pin:TvCertificatePin?=null
    override suspend fun save(tv:Tv) {}
    override suspend fun token(id:String)=tokens[id]
    override suspend fun token(id:String,token:String) { tokens[id]=token }
    override suspend fun clearToken(id:String) { tokens.remove(id) }
    override suspend fun certificate(id:String)=pin?.takeIf { it.deviceId==id }
    override suspend fun certificate(pin:TvCertificatePin) { this.pin=pin }
    override suspend fun clearCertificate(id:String) { pin=null }
}
class TvCertificateConnectionTest {
    private fun localAddress():InetAddress = NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
        .first { SamsungProtocol.localIp(it.hostAddress ?: "") }
    @Test fun tlsApprovalThenSamsungPairingTokenReuseReconnectAndReset()=runBlocking {
        val address=localAddress()
        val held=HeldCertificate.Builder().commonName("Samsung fixture without IP SAN").build()
        val server=MockWebServer()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(held).build().sslSocketFactory(),false)
        server.start(address,8002)
        val tv=Tv("tls-tv","Samsung TLS fixture",address.hostAddress!!,port=8002)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default);val store=CertificateMemory();val manager=ConnectionManager(scope,store)
        val opened=CompletableDeferred<WebSocket>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
            override fun onOpen(ws:WebSocket,response:Response) { opened.complete(ws) }
        }))
        try {
            manager.connect(tv)
            val review=withTimeout(7000) { manager.certificateReview.first { it!=null } }!!
            assertEquals(Status.Failed,manager.state.value.status);assertNull(store.pin);assertNull(store.token(tv.id))
            manager.cancelCertificateReview();manager.approveCertificate(review)
            delay(100);assertNull(store.pin) // A canceled review cannot be approved later.
            manager.connect(tv)
            val current=withTimeout(7000) { manager.certificateReview.first { it!=null } }!!
            manager.approveCertificate(current)
            val socket=withTimeout(7000) { opened.await() }
            withTimeout(3000) { manager.state.first { it.status==Status.AwaitingAuthorization } }
            assertNotEquals(Status.Connected,manager.state.value.status)
            socket.send("""{"event":"ms.channel.connect","data":{"token":"tls-pair-token"}}""")
            withTimeout(5000) { manager.state.first { it.status==Status.Connected } }
            assertEquals("tls-pair-token",store.token(tv.id))
            val first=server.takeRequest(3,TimeUnit.SECONDS)!!
            assertEquals("Smart TV Remote",String(java.util.Base64.getDecoder().decode(first.requestUrl!!.queryParameter("name"))))
            assertNull(first.requestUrl!!.queryParameter("token"))
            server.enqueue(MockResponse().withWebSocketUpgrade(object:WebSocketListener() {
                override fun onOpen(ws:WebSocket,response:Response) { ws.send("""{"event":"ms.channel.unauthorized"}""") }
            }))
            manager.reconnect()
            withTimeout(7000) { manager.state.first { it.status==Status.Failed } }
            val next=server.takeRequest(3,TimeUnit.SECONDS)!!
            assertEquals("tls-pair-token",next.requestUrl!!.queryParameter("token"))
            withTimeout(3000) { while(store.token(tv.id)!=null) delay(10) }
            assertNotNull(store.pin);assertNull(manager.certificateReview.value)
            manager.resetCertificate()
            withTimeout(3000) { while(store.pin!=null) delay(10) }
            assertEquals(Status.Disconnected,manager.state.value.status)
            manager.connect(tv)
            withTimeout(7000) { manager.certificateReview.first { it!=null } }
            assertNotEquals(Status.Connected,manager.state.value.status)
        } finally { manager.disconnect();scope.cancel();server.close() }
    }
}
