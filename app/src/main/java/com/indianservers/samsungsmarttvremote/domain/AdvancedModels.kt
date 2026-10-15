package com.indianservers.samsungsmarttvremote.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Support { Unknown, Supported, Unsupported }
enum class Feature { Directional, Pointer, TextInput, AppListing, AppLaunching, SourceMenu, DirectInput, Media }
data class Capability(val support: Support = Support.Unknown, val evidence: String = "Not verified on this television")
data class TVCapabilities(val features: Map<Feature,Capability> = Feature.entries.associateWith { Capability() }) {
    operator fun get(feature: Feature) = features.getValue(feature)
}
class TVCapabilityManager {
    private val mutable=MutableStateFlow(TVCapabilities())
    val state=mutable.asStateFlow()
    fun reset() { mutable.value=TVCapabilities() }
    fun record(feature: Feature, support: Support, evidence: String) { mutable.update { it.copy(features=it.features+(feature to Capability(support,evidence))) } }
}
enum class AppOrigin { Installed, Cached, Custom }
data class TVApplication(val id: String, val name: String, val actionType: String, val origin: AppOrigin=AppOrigin.Installed)
data class TVInputSource(val name: String, val directKey: RemoteKey?=null)
object InputSources {
    val candidates=listOf("TV","HDMI 1","HDMI 2","HDMI 3","HDMI 4").map { TVInputSource(it) }
    // There is no verified direct-port interface in the current model registry.
    fun command(source: TVInputSource, capabilities: TVCapabilities): RemoteKey? =
        source.directKey?.takeIf { capabilities[Feature.DirectInput].support==Support.Supported }
}
enum class LandingTab { Remote, Touchpad, Keyboard, Apps }
data class FavoriteShortcut(val id: String, val title: String, val appId: String?=null, val key: RemoteKey?=null) {
    fun valid() = id.isNotBlank() && title.isNotBlank() && ((appId!=null) xor (key!=null))
}
data class RemotePreferences(
    val landing: LandingTab=LandingTab.Remote,
    val sensitivity: Float=1f,
    val hiddenGroups: Set<String> = emptySet(),
    val favorites: List<FavoriteShortcut> = emptyList(),
    val apps: List<TVApplication> = emptyList()
)
data class Submission(val sent: Boolean, val message: String)
sealed interface TVEvent {
    data class Apps(val apps: List<TVApplication>): TVEvent
    data object InvalidApps: TVEvent
    data class Ime(val focused: Boolean): TVEvent
    data class LaunchReply(val accepted: Boolean): TVEvent
    data class Rejected(val feature: Feature?, val key: RemoteKey?): TVEvent
}
