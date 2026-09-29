# Daydreamin (Android)

A native Android music app, built in Kotlin and Jetpack Compose, that streams music straight from
YouTube — resolved entirely on-device, with no backend of its own. Search, gapless playback, a
recommendation engine, on-device caching for offline listening, and a liquid-glass, AMOLED-first
design across every screen.

This repo is the Android app only. It's a separate project from [Daydreamin's original web
app](https://github.com/theankitverse/Daydreamin).

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
- **Gapless playback**, disk caching, stream-URL refresh, retry/backoff, and audio-focus handling
  built on Media3/ExoPlayer.
- **Your library survives an uninstall.** A backup (liked songs, playlists, history, profile) is
  saved automatically to `Download/Daydreamin`, which Android doesn't touch when the app is
  removed — first-run setup can restore straight from it.
- **First-run setup**: name, photo, and an accent colour that lights up the whole app.

## Building

```bash
git clone https://github.com/theankitverse/daydreamin-android.git
cd daydreamin-android
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. Requires JDK 17 and the Android SDK
(`compileSdk 35`, `minSdk 26`).

## Installing

This isn't on the Play Store — grab the latest APK from
[Releases](../../releases/latest) and install it directly (Android will ask you to allow installs
from your browser or file manager the first time). The app checks in the background for newer
releases and shows a banner on Home when one's available, so after this first install you won't
need to do this manually again.

## Support

Daydreamin is free, with no ads and no account required. If you'd like to support the project,
you can [buy me a coffee](https://www.buymeacoffee.com/theankitverse) — completely optional,
and it doesn't unlock anything, since there's nothing locked to begin with.

## A note on how this works

This app plays audio by resolving stream URLs directly from YouTube (via NewPipeExtractor) rather
than through YouTube's official Data API. That's the same approach used by
[NewPipe](https://github.com/TeamNewPipe/NewPipe) and similar projects. It's shared here as a
personal, source-available project rather than distributed through an app store.

---

Made by [Ankit](https://www.instagram.com/ankitchaurasiya.o_o/).
