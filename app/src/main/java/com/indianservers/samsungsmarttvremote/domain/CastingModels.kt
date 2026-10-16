package com.indianservers.samsungsmarttvremote.domain

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CastMediaPolicy {
    val types=linkedMapOf("Video (MP4)" to "video/mp4","Video (WebM)" to "video/webm","Audio (MP3)" to "audio/mpeg","Audio (M4A)" to "audio/mp4","Photo (JPEG)" to "image/jpeg","Photo (PNG)" to "image/png")
    fun validUrl(value: String): Boolean {
        if(value.length>4096 || value.any { it.isISOControl() }) return false
        val url=value.toHttpUrlOrNull() ?: return false
        return url.scheme in setOf("http","https") && url.username.isEmpty() && url.password.isEmpty() && url.fragment==null
    }
    fun validType(value: String)=value in types.values
}
data class ByteWindow(val start: Long,val end: Long) { val length get()=end-start+1 }
object HttpByteRanges {
    fun parse(value: String?,length: Long): ByteWindow? {
        if(length<=0) return null
        if(value==null) return ByteWindow(0,length-1)
        if(!value.startsWith("bytes=") || ',' in value) return null
        val parts=value.removePrefix("bytes=").split('-');if(parts.size!=2) return null
        if(parts[0].isEmpty()) {
            val suffix=parts[1].toLongOrNull()?.takeIf { it>0 } ?: return null
            return ByteWindow((length-suffix.coerceAtMost(length)).coerceAtLeast(0),length-1)
        }
        val start=parts[0].toLongOrNull()?.takeIf { it in 0 until length } ?: return null
        val end=if(parts[1].isEmpty()) length-1 else parts[1].toLongOrNull()?.coerceAtMost(length-1) ?: return null
        return ByteWindow(start,end).takeIf { end>=start }
    }
}
