package com.cineverse.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.compose.runtime.Immutable
import com.cineverse.app.BuildConfig
import com.cineverse.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * The app updates itself from GitHub Releases, because there is no Play Store
 * listing and friends install it by hand.
 *
 * What that has to get right, in order of how annoyed someone is when it does
 * not:
 *
 *  1. **Never download without asking.** 40 MB on mobile data without consent is
 *     a hostile act. The sheet asks first, every time.
 *  2. **Show real progress.** Bytes, percentage, and whether it has stalled —
 *     not a spinner.
 *  3. **Verify before installing.** The downloaded file's size and SHA-256 are
 *     checked against the release asset before it is handed to the installer.
 *  4. **Be quiet when there is nothing to say.** One check every six hours, and
 *     a version the user skipped is never mentioned again.
 */
@Immutable
data class Release(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val publishedAt: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(
        val release: Release,
        val bytes: Long,
        val total: Long,
    ) : UpdateState {
        val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data class Verifying(val release: Release) : UpdateState
    data class ReadyToInstall(val release: Release, val file: File) : UpdateState
    data class Failed(val reason: String) : UpdateState
    data object UpToDate : UpdateState
}

class UpdateRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME
    val currentCode: Int get() = BuildConfig.VERSION_CODE

    /**
     * Ask GitHub what the newest release is.
     * @param force true when the user tapped "Check for updates" — that ignores
     *   both the six-hour gate and a previously skipped version, because asking
     *   explicitly is a different act from launching the app.
     */
    /**
     * A debug build must never offer an update.
     *
     * Its package is `com.cineverse.app.debug`, so a release APK installed over
     * it is a DIFFERENT app — Android would put a second CineVerse on the home
     * screen rather than updating this one. Offering it is worse than not having
     * an updater: it is an action that cannot do what it says.
     */
    val canUpdate: Boolean get() = context.packageName == "com.cineverse.app"

    private val _history = MutableStateFlow<List<Release>>(emptyList())

    /** Every release GitHub will tell us about, newest first. */
    val history: StateFlow<List<Release>> = _history.asStateFlow()

    private val _whatsNew = MutableStateFlow<Release?>(null)

    /**
     * The notes for the version now running, when it has just changed.
     *
     * Set once per upgrade. An app that silently replaces itself and says
     * nothing is the worst part of sideloading: the user accepted an install
     * prompt and has no idea what they accepted.
     */
    val whatsNew: StateFlow<Release?> = _whatsNew.asStateFlow()

    /**
     * Did this launch follow an update?
     *
     * Deliberately NOT on the critical path: it asks GitHub for the notes, which
     * means the answer arrives a moment after the app is already usable. The
     * version is recorded as seen either way, so a failed fetch costs the user a
     * changelog and never a repeated prompt.
     */
    suspend fun checkWhatsNew() {
        val seen = settings.seenVersion.value
        if (seen >= currentCode) return
        settings.markVersionSeen(currentCode)
        if (seen == 0) return
        val releases = loadHistory()
        _whatsNew.value = releases.firstOrNull { it.versionCode == currentCode }
            ?: releases.firstOrNull { it.versionName.trimStart('v') == currentVersion }
    }

    fun clearWhatsNew() { _whatsNew.value = null }

    /**
     * The release list, cached for the session.
     *
     * One request serves both the history sheet and the what-is-new lookup, so
     * opening the changelog right after an update does not ask GitHub twice.
     */
    suspend fun loadHistory(force: Boolean = false): List<Release> {
        if (!force && _history.value.isNotEmpty()) return _history.value
        val releases = withContext(io) {
            runCatching { fetchHistory() }.getOrNull().orEmpty()
        }
        if (releases.isNotEmpty()) _history.value = releases
        return releases
    }

    private fun fetchHistory(): List<Release> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string()
        } ?: return emptyList()
        return json.parseToJsonElement(body).jsonArray
            .mapNotNull { runCatching { parseRelease(it.jsonObject) }.getOrNull() }
            .sortedByDescending { it.versionCode }
    }

    suspend fun check(force: Boolean = false): UpdateState {
        if (!canUpdate) {
            _state.value = UpdateState.UpToDate
            return _state.value
        }
        val sinceLast = System.currentTimeMillis() - settings.lastUpdateCheck.value
        if (!force && sinceLast < CHECK_INTERVAL_MS) return _state.value
        _state.value = UpdateState.Checking
        val release = withContext(io) { runCatching { latest() }.getOrNull() }
        settings.markUpdateChecked()
        val next = when {
            release == null -> UpdateState.Failed("Could not reach GitHub.")
            release.versionCode <= currentCode -> UpdateState.UpToDate
            !force && release.versionCode == settings.skippedVersion.value -> UpdateState.Idle
            else -> UpdateState.Available(release)
        }
        _state.value = next
        return next
    }

    private fun latest(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            // GitHub's unauthenticated rate limit is per IP and generous enough
            // for one call every six hours, so the app ships no token.
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        } ?: return null
        return parseRelease(json.parseToJsonElement(body).jsonObject)
    }

    /** Download it, reporting progress as it goes. */
    suspend fun download(release: Release): UpdateState = withContext(io) {
        _state.value = UpdateState.Downloading(release, 0, release.sizeBytes)
        val target = File(context.cacheDir, "updates").apply { mkdirs() }
            .resolve("cineverse-${release.versionCode}.apk")
        runCatching {
            if (target.exists()) target.delete()
            val request = Request.Builder().url(release.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: error("empty response")
                val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var read: Int
                        var done = 0L
                        var lastEmit = 0L
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            done += read
                            // Emitting every chunk would repaint the bar 300
                            // times a second for no visible gain; 16 ms is one
                            // frame, which is as often as it can possibly matter.
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 16) {
                                lastEmit = now
                                _state.value = UpdateState.Downloading(release, done, total)
                            }
                        }
                    }
                }
            }
            _state.value = UpdateState.Verifying(release)
            verify(target, release)
            _state.value = UpdateState.ReadyToInstall(release, target)
        }.getOrElse { error ->
            target.delete()
            _state.value = UpdateState.Failed(
                error.message?.takeIf { it.isNotBlank() } ?: "The download did not finish."
            )
        }
        _state.value
    }

    /**
     * The file has to be the file the release says it is before it is handed to
     * the package installer. A size that does not match means a truncated
     * download; a digest that does not match means something else entirely.
     */
    private fun verify(file: File, release: Release) {
        if (release.sizeBytes > 0 && file.length() != release.sizeBytes) {
            error("The download was incomplete — try again.")
        }
        if (release.sha256.isBlank()) return
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(128 * 1024)
            var read: Int
            while (stream.read(buffer).also { read = it } >= 0) digest.update(buffer, 0, read)
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (!hex.equals(release.sha256, ignoreCase = true)) {
            error("The download did not match its checksum and was discarded.")
        }
    }

    /**
     * Hand the APK to the system installer. Android shows its own confirmation —
     * the app cannot and should not install silently.
     */
    fun install(file: File) {
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(context.packageName)
                runCatching { setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED) }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("cineverse", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, InstallReceiver::class.java)
                val pending = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                )
                session.commit(pending.intentSender)
            }
        }.onFailure {
            _state.value = UpdateState.Failed(
                "Android would not start the install. Allow CineVerse to install apps in Settings."
            )
        }
    }

    suspend fun skip(release: Release) {
        settings.skipVersion(release.versionCode)
        _state.value = UpdateState.Idle
    }

    fun dismiss() { _state.value = UpdateState.Idle }

    companion object {
        const val REPO = "Arnav-Dugad/cineverse-android"
        private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

        /**
         * A release's tag is `v1.4.2`; its version CODE is what decides whether
         * this is an update, and it is published in the release body as a line
         * `versionCode: 142` written by CI. Parsing the tag would mean guessing
         * at an ordering that semver does not actually give us across a rename.
         */
        fun parseRelease(json: JsonObject): Release? {
            fun text(field: String) = json[field]?.jsonPrimitive?.contentOrNull.orEmpty()
            val tag = text("tag_name").ifBlank { return null }
            val notes = text("body")
            val code = Regex("versionCode:\\s*(\\d+)").find(notes)?.groupValues?.get(1)?.toIntOrNull()
                ?: tag.removePrefix("v").split('.').let { parts ->
                    // Fall back to the tag read as major*10000 + minor*100 + patch,
                    // which orders correctly for anything this project will ship.
                    val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
                    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    major * 10_000 + minor * 100 + patch
                }
            val asset = json["assets"]?.jsonArray?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
                ?: return null
            val sha = Regex("sha256:\\s*([0-9a-fA-F]{64})").find(notes)?.groupValues?.get(1).orEmpty()
            return Release(
                versionName = tag.removePrefix("v"),
                versionCode = code,
                notes = cleanNotes(notes),
                downloadUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                sizeBytes = (asset["size"]?.jsonPrimitive?.contentOrNull)?.toLongOrNull() ?: 0L,
                sha256 = sha,
                publishedAt = text("published_at").take(10),
            ).takeIf { it.downloadUrl.isNotBlank() }
        }

        /**
         * What the reader should actually see.
         *
         * Three things had to come out of a release body, and every one of them
         * was found by reading the sheet on a real phone rather than by reading
         * the code:
         *
         *  - the two machine-readable lines and the HTML comment explaining
         *    them, which are addressed to this class and looked like a bug;
         *  - Markdown, because a GitHub release body is Markdown and this sheet
         *    is not a renderer — a note arrived reading
         *    `**[Download the APK](https://…)**`;
         *  - runs of blank lines, so what is left reads as prose.
         *
         * Flattening the four constructs that actually turn up is a few lines.
         * A Markdown library for one paragraph of release notes would not be.
         */
        fun cleanNotes(body: String): String = body
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .lineSequence()
            .filterNot { it.trimStart().startsWith("versionCode:") }
            .filterNot { it.trimStart().startsWith("sha256:") }
            .toList()
            .joinToString(separator = System.lineSeparator())
            // [text](url) keeps the text and drops the address.
            .replace(Regex("""\[([^\]]+)]\((?:[^)]*)\)"""), "$1")
            // **bold**, *italic*, `code`
            .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
            .replace(Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)"""), "$1")
            .replace(Regex("""`([^`\n]+)`"""), "$1")
            // Headings lose their hashes but keep their line.
            .replace(Regex("""(?m)^\s{0,3}#{1,6}\s*"""), "")
            // A list is a list; a hyphen at the start of a line is not a word.
            .replace(Regex("""(?m)^\s{0,3}[-*+]\s+"""), "• ")
            .replace(Regex("""(\s*\R){3,}"""), System.lineSeparator() + System.lineSeparator())
            .trim()

        private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
            get() = runCatching { content }.getOrNull()
    }
}
