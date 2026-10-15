package com.indianservers.samsungsmarttvremote.data.network

import com.indianservers.samsungsmarttvremote.domain.*
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.*
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.*
import okhttp3.OkHttpClient

/** Exact DER leaf pin; endpoint binding is deliberate: an IP change requires a fresh review. */
data class TvCertificatePin(val deviceId: String,val ip: String,val port: Int,val fingerprint: String) {
    fun matches(tv: Tv,leaf: X509Certificate)=deviceId==tv.id && ip==tv.ip && port==tv.port && fingerprint==TvCertificateTrust.fingerprint(leaf)
}
enum class TlsFailure { UntrustedChain, HostnameMismatch, Expired, NotYetValid, CertificateChanged, DeviceMismatch, Handshake, WebSocket }
data class CertificateReview(val tv: Tv,val fingerprint: String,val reason: TlsFailure,val changed: Boolean,val approvable: Boolean)
class PinFailure(val reason: TlsFailure): CertificateException(reason.name)

/** Observes a failed standard verification without ever accepting an unapproved certificate. */
class TvCertificateTrust(private val tv: Tv,private val approved: TvCertificatePin?,
    private val standard: X509TrustManager=systemTrust()) : X509TrustManager {
    val observed=AtomicReference<X509Certificate?>(null)
    override fun getAcceptedIssuers(): Array<X509Certificate> = standard.acceptedIssuers
    override fun checkClientTrusted(chain: Array<X509Certificate>,authType: String) = standard.checkClientTrusted(chain,authType)
    override fun checkServerTrusted(chain: Array<X509Certificate>,authType: String) {
        val leaf=chain.firstOrNull() ?: throw CertificateException("Missing certificate")
        observed.set(leaf)
        // Always attempt Android's verification first; approved trust is an explicit narrow fallback.
        val failure=try { standard.checkServerTrusted(chain,authType); null } catch(e: CertificateException) { e }
        if(approved!=null) {
            if(approved.deviceId!=tv.id || approved.ip!=tv.ip || approved.port!=tv.port) throw PinFailure(TlsFailure.DeviceMismatch)
            if(!approved.matches(tv,leaf)) throw PinFailure(TlsFailure.CertificateChanged)
            leaf.checkValidity()
            return
        }
        if(failure!=null) throw failure
    }
    fun hostnameVerifier(default: HostnameVerifier)=HostnameVerifier { host,session ->
        if(host!=tv.ip) false else {
            val leaf=runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
            if(leaf!=null) observed.set(leaf)
            if(approved!=null) leaf!=null && approved.matches(tv,leaf) && runCatching { leaf.checkValidity() }.isSuccess
            else default.verify(host,session)
        }
    }
    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        require(tv.port==8002 && SamsungProtocol.localIp(tv.ip))
        val defaultVerifier=OkHttpClient().hostnameVerifier
        val ssl=SSLContext.getInstance("TLS").apply { init(null,arrayOf(this@TvCertificateTrust),SecureRandom()) }
        return builder.sslSocketFactory(ssl.socketFactory,this).hostnameVerifier(hostnameVerifier(defaultVerifier))
    }
    fun review(error: Throwable): CertificateReview? {
        val leaf=observed.get() ?: return null
        val reason=classify(error)
        if(reason==TlsFailure.Handshake || reason==TlsFailure.WebSocket) return null
        val valid=runCatching { leaf.checkValidity() }.isSuccess
        return CertificateReview(tv,fingerprint(leaf),reason,approved!=null && !approved.matches(tv,leaf),valid && reason!=TlsFailure.DeviceMismatch)
    }
    fun classify(error: Throwable): TlsFailure {
        val causes=generateSequence(error) { it.cause }.take(12).toList()
        causes.filterIsInstance<PinFailure>().firstOrNull()?.let { return it.reason }
        val leaf=observed.get()
        if(causes.any { it is CertificateExpiredException } || (leaf!=null && leaf.notAfter.time<System.currentTimeMillis())) return TlsFailure.Expired
        if(causes.any { it is CertificateNotYetValidException } || (leaf!=null && leaf.notBefore.time>System.currentTimeMillis())) return TlsFailure.NotYetValid
        if(causes.any { it is SSLPeerUnverifiedException }) return TlsFailure.HostnameMismatch
        if(causes.any { it is CertificateException }) return TlsFailure.UntrustedChain
        if(causes.any { it is SSLException }) return TlsFailure.Handshake
        return TlsFailure.WebSocket
    }
    companion object {
        fun fingerprint(leaf: X509Certificate)=MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString(":") { "%02X".format(it.toInt() and 255) }
        fun systemTrust(): X509TrustManager {
            val factory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }
}
