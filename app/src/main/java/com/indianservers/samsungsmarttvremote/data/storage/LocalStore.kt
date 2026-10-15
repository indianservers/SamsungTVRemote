package com.indianservers.samsungsmarttvremote.data.storage

import com.indianservers.samsungsmarttvremote.domain.*
import com.indianservers.samsungsmarttvremote.data.storage.*

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore("remote")
interface PairingStore {
    suspend fun save(tv: Tv)
    suspend fun token(id: String): String?
    suspend fun token(id: String, token: String)
    suspend fun clearToken(id: String)
    suspend fun certificate(id: String): com.indianservers.samsungsmarttvremote.data.network.TvCertificatePin? = null
    suspend fun certificate(pin: com.indianservers.samsungsmarttvremote.data.network.TvCertificatePin) { throw UnsupportedOperationException("Certificate storage unavailable") }
    suspend fun clearCertificate(id: String) {}
}
class LocalStore(private val context: Context, testStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null): PairingStore {
    private val preferences=testStore ?: context.preferences
    private val device = stringPreferencesKey("device")
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey("tv-pairing", null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("tv-pairing", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes("GCM").setEncryptionPaddings("NoPadding").build())
        }.generateKey()
    }
    private val profilesKey=stringPreferencesKey("profiles-v3")
    private val selectedKey=stringPreferencesKey("selected-tv")
    private fun profiles(data: Preferences): List<DeviceProfile> {
        data[profilesKey]?.let { return ProfileCodec.decode(it) }
        return data[device]?.let { val j=JSONObject(it); val tv=Tv(j.getString("id"),j.getString("name"),j.getString("ip"),j.getString("model"),j.getInt("port"));
            if(ProfileCodec.validId(tv.id) && SamsungProtocol.localIp(tv.ip) && tv.port in setOf(8001,8002)) listOf(DeviceProfile(tv)) else emptyList() } ?: emptyList()
    }
    val profilesFlow=preferences.data.map { profiles(it) }
    private fun decodeAppearance(data:Preferences): Appearance {
        val j=JSONObject(data[stringPreferencesKey("appearance-v3")] ?: "{}")
        return Appearance(runCatching { RemoteTheme.valueOf(j.optString("theme","SnowWhite")) }.getOrDefault(RemoteTheme.SnowWhite),j.optDouble("size",1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(1f,1.2f) ?: 1f,j.optDouble("motion",1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0f,1f) ?: 1f,j.optBoolean("onboarded"),j.optBoolean("mediaFirst"))
    }
    val appearanceFlow=preferences.data.map { decodeAppearance(it) }
    suspend fun appearance(change: (Appearance)->Appearance) {
        preferences.edit {
            val updated=change(decodeAppearance(it))
            it[stringPreferencesKey("appearance-v3")]=JSONObject().put("theme",updated.theme.name).put("size",updated.buttonSize.toDouble()).put("motion",updated.motion.toDouble()).put("onboarded",updated.onboardingDone).put("mediaFirst",updated.mediaFirst).toString() }
    }
    override suspend fun save(tv: Tv) { preferences.edit { data ->
        val old=profiles(data); val profile=old.firstOrNull { it.tv.id==tv.id }?.copy(tv=tv) ?: DeviceProfile(tv)
        data[profilesKey]=ProfileCodec.encode(old.filterNot { it.tv.id==tv.id }+profile)
        data[selectedKey]=tv.id
        data[device]=JSONObject().put("id",tv.id).put("name",tv.name).put("ip",tv.ip).put("model",tv.model).put("port",tv.port).toString()
    } }
    suspend fun load(): Tv? = try { val data=preferences.data.first(); val profiles=profiles(data); (profiles.firstOrNull { it.tv.id==data[selectedKey] } ?: profiles.lastOrNull())?.tv } catch(e: CancellationException) { throw e } catch(_: Exception) { null }
    suspend fun rename(id: String,name: String,room: String) {
        require(name.isNotBlank() && name.length<=60 && room.isNotBlank() && room.length<=40)
        preferences.edit { it[profilesKey]=ProfileCodec.encode(profiles(it).map { p -> if(p.tv.id==id) p.copy(friendlyName=name.trim(),room=room.trim()) else p }) }
    }
    suspend fun select(id: String) { preferences.edit { require(profiles(it).any { p -> p.tv.id==id });it[selectedKey]=id } }
    suspend fun remove(id: String) { preferences.edit { data ->
        val remaining=profiles(data).filterNot { it.tv.id==id };data[profilesKey]=ProfileCodec.encode(remaining)
        data.remove(stringPreferencesKey("token:certificate:$id"));data.remove(stringPreferencesKey("token:$id"));data.remove(stringPreferencesKey("phase2:$id"))
        if(data[selectedKey]==id || data[selectedKey]==null) { data.remove(selectedKey);data.remove(device) }
        data.asMap().keys.filter { it.name.startsWith("widget:") }.forEach { key ->
            val config=data[stringPreferencesKey(key.name)]?.let { runCatching { JSONObject(it) }.getOrNull() }
            if(config?.optString("device")==id) data.remove(stringPreferencesKey(key.name))
        }
    } }
    override suspend fun token(id: String): String? = withContext(Dispatchers.IO) { try {
        preferences.data.first()[stringPreferencesKey("token:$id")]?.let {
            val parts=it.split(':'); val c=Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],0)))
            String(c.doFinal(Base64.decode(parts[1],0)), Charsets.UTF_8)
        }
    } catch(e: CancellationException) { throw e } catch(_: Exception) { null } }
    override suspend fun token(id: String, token: String) = withContext(Dispatchers.IO) {
        val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=Base64.encodeToString(c.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(token.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        preferences.edit { it[stringPreferencesKey("token:$id")]=encrypted }
        Unit
    }
    override suspend fun certificate(id: String): com.indianservers.samsungsmarttvremote.data.network.TvCertificatePin? = withContext(Dispatchers.IO) {
        // Missing approval is different from corrupt/unreadable approval: corruption must fail closed.
        val encrypted=preferences.data.first()[stringPreferencesKey("token:certificate:$id")] ?: return@withContext null
        val parts=encrypted.split(':');require(parts.size==2)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],0)))
        val j=JSONObject(String(cipher.doFinal(Base64.decode(parts[1],0)),Charsets.UTF_8))
        require(j.getString("id")==id)
        com.indianservers.samsungsmarttvremote.data.network.TvCertificatePin(id,j.getString("ip"),j.getInt("port"),j.getString("fingerprint"))
    }
    override suspend fun certificate(pin: com.indianservers.samsungsmarttvremote.data.network.TvCertificatePin) {
        require(ProfileCodec.validId(pin.deviceId) && SamsungProtocol.localIp(pin.ip) && pin.port==8002)
        require(Regex("[0-9A-F]{2}(:[0-9A-F]{2}){31}").matches(pin.fingerprint))
        token("certificate:${pin.deviceId}",JSONObject().put("id",pin.deviceId).put("ip",pin.ip).put("port",pin.port).put("fingerprint",pin.fingerprint).toString())
    }
    override suspend fun clearCertificate(id: String) { clearToken("certificate:$id") }
    suspend fun forget() { preferences.edit { it.clear() } }
    override suspend fun clearToken(id: String) { preferences.edit { it.remove(stringPreferencesKey("token:$id")) } }
    suspend fun haptics() = preferences.data.first()[booleanPreferencesKey("haptics")] ?: true
    suspend fun haptics(value: Boolean) { preferences.edit { it[booleanPreferencesKey("haptics")]=value } }
    suspend fun widget(id: Int): WidgetConfig? = preferences.data.first()[stringPreferencesKey("widget:$id")]?.let { val j=JSONObject(it); WidgetConfig(id,j.getString("device"),j.optString("status","Open app to connect").take(120)) }
    suspend fun widget(config: WidgetConfig) { require(config.widgetId>=0 && ProfileCodec.validId(config.deviceId));preferences.edit { it[stringPreferencesKey("widget:${config.widgetId}")]=JSONObject().put("device",config.deviceId).put("status",config.status.take(120)).toString() } }
    suspend fun deleteWidget(id: Int) { preferences.edit { it.remove(stringPreferencesKey("widget:$id")) } }
    suspend fun preferences(id: String): RemotePreferences = RemotePreferencesCodec.decode(preferences.data.first()[stringPreferencesKey("phase2:$id")])
    suspend fun updatePreferences(id: String, change: (RemotePreferences)->RemotePreferences): RemotePreferences {
        var updated=RemotePreferences()
        preferences.edit {
            val key=stringPreferencesKey("phase2:$id")
            updated=change(RemotePreferencesCodec.decode(it[key]))
            it[key]=RemotePreferencesCodec.encode(updated)
        }
        return updated
    }
}




