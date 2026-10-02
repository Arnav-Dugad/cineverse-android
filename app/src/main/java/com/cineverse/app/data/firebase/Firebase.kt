package com.cineverse.app.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

/**
 * The SAME Firebase project as the website — the whole point of the app. A
 * title ticked on the phone is ticked on the laptop before the thumb leaves
 * the screen.
 *
 * There is deliberately no `google-services.json` in the repository. The
 * project is configured here from the same values the website carries in
 * `js/config.js`, which are public by design (a Firebase web API key identifies
 * a project; it does not authorise anything — the security rules do). That
 * means the repo can be cloned and built by anyone without a secrets dance, and
 * it means the app cannot drift out of step with the site's configuration.
 *
 * The one thing this cannot do is Google Sign-In, which needs an Android app
 * registered in the Firebase console with this APK's signing certificate. The
 * app detects that ([googleSignInAvailable]) and offers email sign-in instead
 * rather than showing a button that fails.
 */
object Firebase {

    /** The website's `firebaseConfig`, field for field. */
    private const val API_KEY = "AIzaSyDtcGPY2iCh4SsjFIid_H0lwMfIj9ocN8I"
    private const val PROJECT_ID = "movies-2b6dd"
    private const val APP_ID = "1:229804615049:web:5d438b81c71137d0c3ad58"
    private const val SENDER_ID = "229804615049"
    private const val STORAGE_BUCKET = "movies-2b6dd.firebasestorage.app"

    /**
     * Paste the Web client ID from Firebase console → Authentication → Google →
     * Web SDK configuration to turn on one-tap Google sign-in. Left blank, the
     * app simply does not offer it.
     */
    const val GOOGLE_WEB_CLIENT_ID = ""

    val googleSignInAvailable: Boolean get() = GOOGLE_WEB_CLIENT_ID.isNotBlank()

    @Volatile private var app: FirebaseApp? = null

    fun init(context: Context): FirebaseApp = app ?: synchronized(this) {
        app ?: run {
            // A google-services.json, if someone adds one, wins: it will carry a
            // real Android app id and therefore working Google sign-in.
            val existing = runCatching { FirebaseApp.getInstance() }.getOrNull()
            val created = existing ?: FirebaseApp.initializeApp(
                context.applicationContext,
                FirebaseOptions.Builder()
                    .setApiKey(API_KEY)
                    .setApplicationId(APP_ID)
                    .setProjectId(PROJECT_ID)
                    .setGcmSenderId(SENDER_ID)
                    .setStorageBucket(STORAGE_BUCKET)
                    .build(),
            )
            configureFirestore(created)
            app = created
            created
        }
    }

    /**
     * Firestore keeps the whole library on the device and syncs it. This is why
     * the app has no database of its own: the user's data is already offline,
     * already merged, already live across devices, and writing a second copy of
     * it into Room would only create a third thing to disagree.
     */
    private fun configureFirestore(app: FirebaseApp) {
        runCatching {
            val store = FirebaseFirestore.getInstance(app)
            store.firestoreSettings = firestoreSettings {
                setLocalCacheSettings(
                    persistentCacheSettings {
                        // Unbounded: a library is kilobytes, and a user who has
                        // been offline for a month should still see all of it.
                        setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    }
                )
            }
        }
    }

    fun auth(context: Context): FirebaseAuth = FirebaseAuth.getInstance(init(context))

    fun firestore(context: Context): FirebaseFirestore = FirebaseFirestore.getInstance(init(context))
}
