# CineVerse for Android

The phone half of [CineVerse](https://github.com/Arnav-Dugad/movies) — the same
Firebase project, the same documents, the same account. Tick an episode here and
it is ticked on the laptop before your thumb leaves the screen.

**[Install it →](https://arnav-dugad.github.io/cineverse-android/)**

---

## What it is

CineVerse is not a streaming service. It does not play films. It is a tracker
that knows what you have watched and what you should watch next, with a
reference library attached, and everything in the app follows from that:

| | |
|---|---|
| **Home** | A hero that rotates through what is trending, Continue Watching, and a dozen rails including "because you're watching" |
| **Discover** | Films, series, in cinemas, coming soon, top rated, six moods, and Surprise me |
| **My List** | Watchlist, what you are in the middle of, and what you have finished — sortable by IMDb |
| **Stats** | Hours, streaks, twelve weeks of viewing, what you actually watch |
| **You** | Account, settings, and the updater |
| **A title** | Collapsing artwork, every score, and **Episodes first** on a series |

The reasoning behind all of that — why nine nav items became five tabs, why the
episode tick is where it is, what each animation is for — is written down in
**[DESIGN.md](DESIGN.md)**.

## Tracking an episode

The most-used interaction in the app, so it is the most designed:

- **Tap the tick** — 48dp at the right edge, where a thumb falls. The circle
  fills with a spring and the check draws itself along its path.
- **Swipe right** — mark everything up to and including that episode, with the
  count following your thumb so you can see what you are about to do. Let go
  early to cancel.
- **Swipe left** — un-tick.
- **Long-press a season chip** — mark or clear the whole season.
- **Or never open the app at all** — the home-screen widget's tick writes
  straight to Firestore.

Every one of those goes through one repository, so the merge rules, the log
encoding and the offline queue cannot disagree with each other.

## What the phone does that the website cannot

- **A Glance widget** whose tick marks an episode watched without opening the app
- **Launcher shortcuts** — Up next, Search, My List
- **Rich haptics** composed from the hardware's own primitives, not `vibrate(ms)`
- **Material You** — the whole app can take its accent from your wallpaper
- **Predictive back**, edge-to-edge, per-app language, a themed monochrome icon
- **Offline** — Firestore's own persistence plus a 192 MB HTTP cache and 320 MB
  of artwork, so your library and everything you have opened work on a plane
- **Deep links** — a cineverse.pages.dev or `cineverse://` link opens in the app,
  and text shared from any app becomes a search

## Building it

Nothing to configure. Clone and run:

```bash
./gradlew assembleDebug
```

Requirements: JDK 21, and the Android SDK with platform 37. The TMDB key and the
Firebase project are in the source on purpose — a TMDB v3 key identifies an
application rather than authorising anything, and a Firebase web API key is
public by design (the [security rules](https://github.com/Arnav-Dugad/movies/blob/main/firestore.rules)
are what protect the data). That means this repository can be cloned and built
by anyone with no secrets dance.

For a **signed release** build, copy `keystore.properties.example` to
`keystore.properties` and fill it in. Without it, a release build falls back to
the debug key and simply will not install as an update over a properly signed
copy — which is the honest behaviour rather than a confusing failure.

### Google sign-in

Email and password work out of the box. Google sign-in needs an Android app
registered in the Firebase console with this APK's signing certificate; paste the
Web client ID into `Firebase.GOOGLE_WEB_CLIENT_ID` and the button appears. Until
then the app does not offer it, because an offer that cannot succeed is worse
than no offer.

## Releasing

```bash
git tag v1.1.0
git push --tags
```

GitHub Actions builds it, signs it, verifies the signature, takes its SHA-256 and
publishes the release. The app notices within six hours.

The repository needs four secrets for that, set once:

| Secret | What it is |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 cineverse-release.jks` |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `cineverse` |
| `KEY_PASSWORD` | the key password |

## How updating works

There is no Play Store listing, so the app updates itself:

1. On launch, at most once every six hours, it asks the GitHub Releases API for
   the newest tag and compares `versionCode`.
2. A sheet shows the release notes and asks. It never downloads on its own —
   40 MB on mobile data without consent is a hostile act.
3. The download reports real progress: megabytes, a percentage, a bar.
4. The file's size and SHA-256 are checked against the release before it is
   handed to Android's package installer, which shows its own confirmation.
5. A version you skip is never mentioned again; the next one still is.

## Architecture

One module, manual dependency injection, no annotation processors.

```
core/design     colour, type, shape, motion, haptics, theme
core/ui         the poster, the rail, score badges, images
core/net        OkHttp with per-endpoint cache rules and an offline mode
data/tmdb       DTOs, the API, one mapper, one repository
data/firebase   auth, library, episodes — the website's documents, field for field
data/scores     IMDb, Rotten Tomatoes, Metacritic, OMDb
data/prefs      DataStore, mirrored to users/{uid}.experiencePrefs
feature/*       one package per screen: a view model and its composables
nav             type-safe routes, the shell, deep links
update          the GitHub Releases updater
widget          the Glance widget
```

**There is no local database.** Firestore's own persistence already keeps the
user's library on the device, merged and live across devices; a second copy in
Room would only create a third thing to disagree. TMDB responses are cached by
OkHttp with a TTL per endpoint — ten minutes for a catalogue page, a day for a
title, a week for a person.

### Two rules that are not negotiable

Both were learned the hard way on the website, and the app re-implements them
rather than inheriting them:

1. **The episode log is a list of strings**, `"season.episode.stamp.bulk"`, never
   a nested array. Firestore rejects an array of arrays, and when the website
   stored tuples every show with history silently stopped syncing.
2. **Every episode write is a transaction that merges with the server copy.** A
   side with no opinion never overrules one that has one; a single tick always
   outranks a bulk sweep, so "Mark season" can never rewrite the day you actually
   watched something; and an exact tie prefers the removal, which is what makes
   the merge symmetric.

## Credits

Film and television data from [TMDB](https://www.themoviedb.org/). Ratings from
Cinemeta, Wikidata and optionally OMDb. This product uses the TMDB API but is not
endorsed or certified by TMDB.

Arnav Dugad
