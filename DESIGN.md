# CineVerse for Android — the design

This is the reasoning behind the app, written before the code. The website is the
source of truth for *what* CineVerse knows; this document is about what a phone
should do with it, which is not the same thing.

## 1. What the app is for

CineVerse is not a streaming service. It does not play films. It is a **tracker
that knows what you have watched and what you should watch next**, with a
reference library attached. Every design decision below follows from that, and it
is the reason the app does not simply copy Netflix's shape: Netflix's job is to
start playback in as few taps as possible, and ours is to answer *"what's next,
and where did I get to?"*

The two things a returning user does, in order of frequency:

1. **Tick an episode they just finished.** Many times a week, often in bed, often
   one-handed, often with the TV still on. This must be reachable from the lock
   screen outward in as few taps as physically possible.
2. **Decide what to watch.** Less often, but it is the emotional centre: the hero,
   the rails, Discover, Surprise me.

Everything else — stats, lists, franchises, box office — is the reward layer that
makes the first two worth doing. So the app is built around a **thumb-reachable
tick** and a **cinematic browse**, and the reward layer is rich but never in the
way.

## 2. Navigation: nine nav items become five tabs

The website's top bar carries Home, Movies, TV Shows, Discover, Releases, My List,
Franchises, Box Office and Stats. Nine is fine on a 1440px bar and wrong on a
phone — a bottom bar holds five before the labels start lying.

| Tab | Holds | Why |
|---|---|---|
| **Home** | Hero, Continue Watching, every recommendation rail, Top 10, Because you're watching | The default answer to "what now" |
| **Discover** | Movies, TV Shows, Franchises, Box Office, Releases, moods, Surprise me, studios | Everything *browsable* in one hub, sectioned — five website pages that are all the same verb |
| **My List** | Watchlist, custom lists, Watched, Continue Watching in full | Everything *you own*. One tab, three segments |
| **Stats** | The whole stats page, re-cut as a vertical story | CineVerse's differentiator; it earns a tab |
| **Profile** | Account, friends, settings, backup, the updater | The drawer that isn't a drawer |

**Search is not a tab.** It is a persistent action in the top bar on every tab and
a dedicated full-screen surface, because search is a *mode*, not a place — the
pattern IMDb, YouTube and Prime all settled on. It opens with the keyboard up, the
history below, and voice on the right.

The bottom bar hides on scroll-down and returns on scroll-up (Prime, Crunchyroll),
so a rail of posters gets the full screen while browsing, and it is always one
flick away.

## 3. The detail page: the hardest screen

The website's title page is fourteen stacked blocks. Stacking them on a phone
means the Episodes list — the thing people came for — is 4,000px down.

**The app's shape:**

```
┌──────────────────────────┐
│  backdrop, full bleed    │  collapses into the app bar as you scroll;
│  trailer autoplays muted │  the title logo shrinks into the bar's title
│  after 1.2s on Wi-Fi     │
├──────────────────────────┤
│  poster · title · meta   │  IMDb / Tomatometer / Metacritic / CineVerse
│  ▸ Play trailer          │  one filled button, the rest are icon buttons
│  ＋ ✓ ★ ↗                │
├──────────────────────────┤
│ Episodes│About│More Like │  ← sticky segmented control
├──────────────────────────┤
│  (the selected section)  │
└──────────────────────────┘
```

Three segments, not fourteen blocks. **Episodes is the first segment on a series
and is selected by default**, so the thing people came for is visible without a
single scroll. On a film the segments are About / Extras / More Like This.

The remaining website blocks live inside **About** as collapsible rows, in the
order the viewer chose on the website (`detailOrder` syncs — see §8).

**The season picker** is a horizontal chip rail pinned above the episode list, not
a dropdown: one tap instead of two, and it shows progress per season.

## 4. The tick: the most important interaction in the app

An episode row is a 72dp-tall target with the still on the left and a circular
tick on the right, 48dp, at the **right edge** — the thumb's natural arc on a
large phone. Tapping it:

- fills the circle with a spring, drawing the checkmark along its path (the
  website's `tick-draw`, which is already the right animation);
- fires a **confirm** haptic — a crisp double, not the system's generic click;
- slides the "Up next" badge down to the following episode in the same frame;
- writes to Firestore optimistically, offline-first.

**Swipe the row right** to mark everything up to and including it (the website's
"Mark up to here"), with the row tinting green and a counter sliding in under the
thumb: *"S2 E1–E7 · 7 episodes"*. Release to commit, with an undo snackbar.
**Swipe left** to un-tick. Both are rubber-banded and reversible mid-gesture.

**Long-press a season chip** for Mark season / Clear season / Set position.

## 5. What the phone can do that the website cannot

These are not decoration. Each one removes taps from the two core jobs.

| Feature | What it does |
|---|---|
| **Home-screen widget** (Glance) | Continue Watching, 1×1 to 4×2. Tapping the tick on the widget marks the episode watched **without opening the app** — the single biggest tap saving in the product |
| **App shortcuts** (long-press the icon) | Up Next · Search · My List · Surprise me |
| **Picture-in-picture** | A trailer keeps playing in a corner while you browse to the next title |
| **Predictive back** | The detail page shrinks back into the poster you opened it from, following your thumb |
| **Material You** | The whole app can take its accent from the wallpaper, or keep CineVerse red |
| **Rich haptics** | Every one of the website's haptic signatures, mapped to real `VibrationEffect` primitives instead of `navigator.vibrate` approximations |
| **Notifications** | A new episode of a tracked show aired; a film on your list releases this week; a weekly viewing recap |
| **Offline** | Firestore's own persistence plus an HTTP cache: your whole library, every poster you have seen, and every page you have opened work on a plane |
| **Per-app language** | Android 13's system setting, declared properly |
| **Share sheet** | Share a title as a rich link; receive a shared TMDB/IMDb link from any app and open it in CineVerse |
| **Monochrome icon** | A themed icon that actually matches the user's wallpaper, not a white square |

## 6. Motion

The rule: **nothing moves without telling you something.** Every animation below
either shows where something came from, how far through you are, or that a thing
you did worked.

- **Shared-element transitions** carry the poster from a rail into the detail
  page's poster slot, and the backdrop into the backdrop. Going back reverses it,
  and under predictive back it tracks the thumb.
- **The hero** cross-dissolves with a slow Ken Burns, exactly as the website does,
  and pauses when the tab is not resumed (battery).
- **Rails** stagger their first paint by 40ms per card, once, on first view.
- **Numbers count up** when they scroll into view (stats, box office) — the
  website's `observeCountUps`, which is already right.
- **The tick** draws its path. **The stub** flies from the poster to the My List
  tab when you mark something watched, and the tab's badge bumps.
- **Spring, not duration.** Everything interactive uses Compose springs so an
  interrupted gesture never snaps.
- **Reduced motion** is honoured from the system setting *and* an in-app override;
  with it on, every transition becomes a cross-fade and the Ken Burns stops.

## 7. Look

The app keeps the website's palette exactly, so they read as one product:
`#06060b` ink, `#e50914` red, the gold/cyan/green/purple accents, and the paper
light theme at `#e6e2da`. Type is the platform's own face (Roboto Flex / the
device's system font) at the website's weights and tracking — the website chose
"the platform's UI font" as its one typeface, and on Android that *is* Roboto.

Surfaces follow the website's Apple-glass pass: one radius scale, one blur, one
hairline, no tinted panels. Android gets the same four radius steps so a control
inside a panel is always visibly rounder or flatter than the panel around it.

Dark is the default and the design target. Light is fully supported, not an
afterthought, because the compiler on the website proved every colour has a paper
twin.

## 8. Data: the same Firebase, the same documents

The app signs in to the **same Firebase project** (`movies-2b6dd`) and reads and
writes the **same documents**, field for field:

```
users/{uid}                         profile
users/{uid}/watchlist/{type_id}     tmdbId type title poster rating year genres
                                    keywords runtime language country releaseDate
                                    lists[] added
users/{uid}/watched/{type_id}       … tmdbRating voteCount collectionId watchedAt
users/{uid}/ratings/{type_id}       value
users/{uid}/lists/{listId}          name items[] …
users/{uid}/progress/{tv_id}        tmdbId title poster backdrop episodeRuntime
                                    status seasons{} structure{} aired numberingMode
                                    log["s.e.stamp.bulk"] removed{} seasonPlays{}
                                    dropped droppedAt updatedAt episodeModelV
users/{uid}/movieProgress/{id}      position runtime updatedAt
users/{uid}/shared/{doc}            published snapshots (taste, shared lists)
publicProfiles/{uid}                name code avatar
friendRequests/{id}                 from to status
collabLists/{id}                    members[] items[]
```

Two rules the app must never break, both learned the hard way on the website:

1. **The episode log is a list of strings**, `"season.episode.stamp.bulk"`, never
   a nested array — Firestore rejects an array of arrays, and the website
   encodes/decodes at exactly this boundary.
2. **A merge keeps the most recent deliberate mark**, and a single tick always
   outranks a bulk sweep. The app re-implements `mergeEntries` with the same rule
   so two devices never disagree about the day you watched something.

Preferences sync through the same `cv_experience_v2`-shaped snapshot, so the
detail-page order you chose on the website is the order the app's About section
uses.

## 9. Distribution

There is no Play Store listing. The app ships as a signed APK from **GitHub
Releases**, and the app updates itself:

- On launch (at most once every 6 hours) it asks the GitHub Releases API for the
  latest tag and compares `versionCode`.
- A sheet shows the release notes and a **Download** button with a real progress
  bar — bytes, percentage, and speed — because a silent 40 MB download on mobile
  data is a hostile act.
- The APK is verified by size and SHA-256 from the release asset before it is
  handed to the package installer.
- A GitHub Pages site gives friends a QR code and a one-tap download.

CI builds and signs the APK on every tag, so a release is `git tag v1.1.0 && git
push --tags`.
