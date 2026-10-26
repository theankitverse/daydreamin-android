# Daydreamin — Android, Mac, Windows & Linux

A music app, built in Kotlin and Compose Multiplatform, that streams music straight from YouTube —
resolved entirely on-device, with no backend of its own. Search, gapless playback, a recommendation
engine, downloads for offline listening, crossfade, and a liquid-glass, AMOLED-first design across
every screen. The same code runs as the Android app and as a desktop app for macOS, Windows and Linux.

This is a separate project from [Daydreamin's original web app](https://github.com/theankitverse/Daydreamin).

## What's inside

- **On-device everything.** [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
  resolves playable audio streams directly from YouTube — no server, no account, nothing of ours
  in between.
- **A Home that learns you.** Your plays, likes and early skips pick a handful of seed songs;
  YouTube Music's radio mix for each is blended into "Your Daydream Mix", top picks, and shelves
  like "Because you like …" and "Fresh finds" — all computed on the device, cached between
  launches, and rebuilt as your listening changes.
- **Playlists**: make your own, add songs from any ⋯ menu or from Now Playing, and save YouTube
  playlists (or your mix) to your library.
- **Search** merges iTunes and YouTube results into one ranked, typo-tolerant list, with lyrics
  (via [LRCLIB](https://lrclib.net/)) synced Apple Music-style.
- **Downloads for offline listening**: download any song, your liked songs or whole playlists, and
  they play with no connection. Downloads run in the background, resume after a dropped connection,
  and work on mobile data (or Wi-Fi only, if you choose).
- **Crossfade** between songs (Settings → Sound), gapless playback, disk caching, stream-URL refresh,
  retry/backoff, and audio-focus handling
  built on Media3/ExoPlayer.
- **Your library survives an uninstall.** A backup (liked songs, playlists, history, profile) is
  saved automatically to `Download/Daydreamin`, which Android doesn't touch when the app is
  removed — first-run setup can restore straight from it.
- **First-run setup**: name, photo, and an accent colour that lights up the whole app.

## Installing

**Android:** this isn't on the Play Store — grab the latest APK from
[Releases](../../releases/latest) and install it directly (Android will ask you to allow installs
from your browser or file manager the first time). The app checks in the background for newer
releases and shows a banner on Home when one's available, so after this first install you won't
need to do this manually again.

**Mac, Windows, Linux:** the desktop installers are on the [Releases](../../releases) page, in the
releases titled "Daydreamin for Mac, Windows & Linux" (`.dmg`, `.msi`, `.deb`, `.rpm`). The desktop
app has a sidebar on wide windows, keyboard shortcuts, your media keys and the system's Now Playing
controls, and it updates itself too.

## How it's built

- `app/src/commonMain` — everything shared: screens, theme, YouTube extraction, library,
  recommendations, settings.
- `app/src/androidMain` — the Android app: Media3 playback in a foreground service, downloads,
  the APK updater.
- `app/src/desktopMain` — the desktop app: its own Opus player (decoded in plain Java, with
  crossfade and an equalizer), downloads, Now Playing / MPRIS / media keys, and its updater.

```bash
git clone https://github.com/theankitverse/daydreamin-android.git
cd daydreamin-android
./gradlew assembleDebug        # Android APK → app/build/outputs/apk/debug/
./gradlew :app:run             # the desktop app, on this computer
./gradlew :app:packageDmg      # or packageMsi / packageDeb / packageRpm (on that system)
```

Requires JDK 21 (a full one, with `jpackage`, for the desktop installers) and the Android SDK
(`compileSdk 35`, `minSdk 21`, so Android 5.0 and up). Desktop installers are built and published
by `.github/workflows/desktop.yml` for every `desktop-v*` tag.

## Support

Daydreamin is free, with no ads and no account required. If you'd like to support the project,
you can [buy me a coffee](https://www.buymeacoffee.com/theankitverse) — completely optional,
and it doesn't unlock anything, since there's nothing locked to begin with.

## Privacy

No account, no ads, and nothing about what you listen to ever leaves your device. To see roughly how
many people use the app, the Android app sends one anonymous count a day to
[TelemetryDeck](https://telemetrydeck.com) (a privacy-focused analytics service): a random ID made
on the phone (hashed before sending), the app and Android version, the country, and which app
installed Daydreamin. Never songs, searches, your name or anything else. Turn it off in
**Settings → Share anonymous usage stats**.

## A note on how this works

This app plays audio by resolving stream URLs directly from YouTube (via NewPipeExtractor) rather
than through YouTube's official Data API. That's the same approach used by
[NewPipe](https://github.com/TeamNewPipe/NewPipe) and similar projects. It's shared here as a
personal, source-available project rather than distributed through an app store.

---

Made by [Ankit](https://www.instagram.com/ankitchaurasiya.o_o/).
