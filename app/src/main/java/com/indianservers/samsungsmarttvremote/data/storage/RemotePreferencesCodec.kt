package com.indianservers.samsungsmarttvremote.data.storage

import com.indianservers.samsungsmarttvremote.domain.*
import org.json.JSONArray
import org.json.JSONObject

object RemotePreferencesCodec {
    fun encode(p: RemotePreferences): String {
        require(p.favorites.size<=30 && p.favorites.all { it.valid() } && p.favorites.distinctBy { it.id }.size==p.favorites.size)
        return JSONObject().put("version",1).put("landing",p.landing.name).put("sensitivity",p.sensitivity.toDouble())
            .put("hidden",JSONArray(p.hiddenGroups.toList()))
            .put("favorites",JSONArray().apply { p.favorites.forEach { f -> put(JSONObject().put("id",f.id).put("title",f.title).put("app",f.appId).put("key",f.key?.name)) } })
            .put("apps",JSONArray().apply { p.apps.take(300).forEach { app -> put(JSONObject().put("id",app.id).put("name",app.name).put("action",app.actionType).put("custom",app.origin==AppOrigin.Custom)) } }).toString()
    }
    fun decode(text: String?): RemotePreferences {
        if(text==null) return RemotePreferences()
        val root=JSONObject(text); require(root.getInt("version")==1)
        val favorites=root.optJSONArray("favorites") ?: JSONArray()
        val apps=root.optJSONArray("apps") ?: JSONArray()
        val hidden=root.optJSONArray("hidden") ?: JSONArray()
        return RemotePreferences(
            landing=LandingTab.valueOf(root.optString("landing","Remote")),
            sensitivity=root.optDouble("sensitivity",1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(.5f,2f) ?: 1f,
            hiddenGroups=(0 until hidden.length()).map { hidden.getString(it) }.filter { it in setOf("numeric","media","channels") }.toSet(),
            favorites=(0 until minOf(favorites.length(),30)).mapNotNull { i ->
                val f=favorites.getJSONObject(i)
                val shortcut=FavoriteShortcut(f.getString("id"),f.getString("title"),f.optString("app").takeIf { it.isNotBlank() && it!="null" },
                    f.optString("key").takeIf { it.isNotBlank() && it!="null" }?.let { runCatching { RemoteKey.valueOf(it) }.getOrNull() })
                shortcut.takeIf { it.valid() }
            }.distinctBy { it.id },
            apps=(0 until minOf(apps.length(),300)).mapNotNull { i ->
                val a=apps.getJSONObject(i); val id=a.getString("id"); val name=a.getString("name"); val action=a.getString("action")
                if(!id.matches(Regex("[A-Za-z0-9._-]{1,128}")) || name.isBlank() || name.length>120 || action !in setOf("DEEP_LINK","NATIVE_LAUNCH","UNKNOWN")) null
                else TVApplication(id,name,action,if(a.optBoolean("custom")) AppOrigin.Custom else AppOrigin.Cached)
            }.distinctBy { it.id }
        )
    }
}

object FavoritesManager {
    fun toggle(current: List<FavoriteShortcut>, favorite: FavoriteShortcut): List<FavoriteShortcut> {
        require(favorite.valid())
        if(current.any { it.id==favorite.id }) return current.filterNot { it.id==favorite.id }
        require(current.size<30) { "You can pin up to 30 shortcuts." }
        return current+favorite
    }
    fun move(current: List<FavoriteShortcut>,id: String,delta: Int): List<FavoriteShortcut> {
        val from=current.indexOfFirst { it.id==id }; if(from<0) return current
        val to=(from+delta).coerceIn(0,current.lastIndex)
        return current.toMutableList().apply { add(to,removeAt(from)) }
    }
}
