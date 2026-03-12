# JAMZ!!!

A custom Android music player built for people who care about their music library.

## What Is This?

JAMZ!!! is a personal Android music player built for a large, meticulously tagged FLAC library (~39,500 tracks). It's focused on playback quality, detailed metadata display, and a clean Material You interface.

### Features
- Material You theming with dynamic colors
- 31-band parametric equalizer with frequency response curve, built-in presets, and auto-preamp
- 5-band simple EQ with bass boost and virtualizer
- Real-time 32-band audio visualizer (FFT-based)
- Energy-preserving crossfade between EQ presets
- Lyrics display
- Sectioned search across songs, albums, and artists
- Album art with blurred background in Now Playing

## Building

1. Clone with submodules:
   ```
   git clone --recursive https://github.com/clayboicardi/JAMZ.git
   ```
2. Create `package.properties` in the repo root:
   ```
   releaseType=SelfBuilt
   ```
3. Open in Android Studio and build, or run:
   ```
   ./gradlew :app:assembleDebug
   ```

## Attribution

This project is a fork of [Gramophone](https://github.com/FoedusProgramme/Gramophone) by FoedusProgramme. The original project and this fork are licensed under GPL-3.0.

## License

GPL-3.0 — see [LICENSE](LICENSE) for details.
