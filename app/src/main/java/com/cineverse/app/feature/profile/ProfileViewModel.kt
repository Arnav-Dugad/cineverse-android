package com.cineverse.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cineverse.app.AppContainer
import com.cineverse.app.core.design.MotionChoice
import com.cineverse.app.core.design.ThemeChoice
import com.cineverse.app.data.firebase.CvUser
import com.cineverse.app.data.firebase.Library
import com.cineverse.app.data.prefs.Settings
import com.cineverse.app.update.UpdateState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val app: AppContainer) : ViewModel() {

    val user: StateFlow<CvUser?> = app.auth.user
    val settings: StateFlow<Settings> = app.settings.settings
    val library: StateFlow<Library> = app.library.library
    val update: StateFlow<UpdateState> = app.updates.state

    /** Pass a line to the one snackbar the app has. */
    fun say(message: String) = app.say(message)

    /** What is in the crash folder, read once when Settings opens. */
    val crashes: List<com.cineverse.app.core.crash.CrashReport> get() = app.crashes.reports()

    fun crashIssueUrl(report: com.cineverse.app.core.crash.CrashReport): String =
        app.crashes.issueUrl(report)

    fun clearCrashes() = app.crashes.clear()

    val currentVersion: String get() = app.updates.currentVersion

    fun setTheme(value: ThemeChoice) = viewModelScope.launch { app.settings.setTheme(value) }
    fun setDynamicColor(value: Boolean) = viewModelScope.launch { app.settings.setDynamicColor(value) }
    fun setMotion(value: MotionChoice) = viewModelScope.launch { app.settings.setMotion(value) }
    fun setHaptics(value: Boolean) = viewModelScope.launch { app.settings.setHaptics(value) }
    fun setSpoilerShield(value: Boolean) = viewModelScope.launch { app.settings.setSpoilerShield(value) }
    fun setAutoplay(value: Boolean) = viewModelScope.launch { app.settings.setAutoplay(value) }
    fun setAutoplayWifi(value: Boolean) = viewModelScope.launch { app.settings.setAutoplayWifiOnly(value) }
    fun setShowRatings(value: Boolean) = viewModelScope.launch { app.settings.setShowRatings(value) }
    fun setShowWatched(value: Boolean) = viewModelScope.launch { app.settings.setShowWatched(value) }
    fun setPosterCaptions(value: Boolean) = viewModelScope.launch { app.settings.setPosterCaptions(value) }
    fun setMature(value: Boolean) = viewModelScope.launch { app.settings.setMature(value) }
    fun setMatureBlur(value: Boolean) = viewModelScope.launch { app.settings.setMatureBlur(value) }
    fun setRegion(value: String) = viewModelScope.launch { app.settings.setRegion(value) }
    fun setHeroAutoAdvance(value: Boolean) = viewModelScope.launch { app.settings.setHeroAutoAdvance(value) }
    fun setHeroSeconds(value: Int) = viewModelScope.launch { app.settings.setHeroSeconds(value) }
    fun setGridDensity(value: com.cineverse.app.data.prefs.GridDensity) =
        viewModelScope.launch { app.settings.setGridDensity(value) }
    fun setStartTab(value: String) = viewModelScope.launch { app.settings.setStartTab(value) }
    fun setConfetti(value: Boolean) = viewModelScope.launch { app.settings.setConfetti(value) }
    fun setShakeToPick(value: Boolean) = viewModelScope.launch { app.settings.setShakeToPick(value) }
    fun setEpisodeSwipe(value: Boolean) = viewModelScope.launch { app.settings.setEpisodeSwipe(value) }
    fun setPosterMeta(value: Boolean) = viewModelScope.launch { app.settings.setPosterMeta(value) }
    fun setPosterCorner(value: String) = viewModelScope.launch { app.settings.setPosterCorner(value) }
    fun setPosterMatch(value: Boolean) = viewModelScope.launch { app.settings.setPosterMatch(value) }
    fun setTitleLogos(value: Boolean) = viewModelScope.launch { app.settings.setTitleLogos(value) }
    fun setCountdowns(value: Boolean) = viewModelScope.launch { app.settings.setCountdowns(value) }

    fun setNotifyEpisodes(value: Boolean) = viewModelScope.launch { app.settings.setNotifyEpisodes(value) }
    fun setNotifyReleases(value: Boolean) = viewModelScope.launch { app.settings.setNotifyReleases(value) }

    fun signOut() = app.auth.signOut()
}
