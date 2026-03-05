# Session 1: Fork Gramophone and Build My First APK

## Goal

Get the Gramophone music player building from source on my Windows machine, producing a debug APK I can install on my Pixel 10 Pro XL via ADB. Nothing more — no feature changes, no renaming, no cosmetic tweaks. Just prove the build pipeline works end to end.

## What I Have Ready

- **Windows desktop** with USB-connected Pixel 10 Pro XL
- **ADB** at `C:\Users\chawo\Desktop\platform-tools\adb.exe` (verified working)
- **Git** installed and available in PATH
- **GitHub account** with a PAT (fine-grained, public repos read-only) — I may need to create a new one with write access for my private repo
- **Android Studio** is NOT installed — tell me if I need it or if we can build with command-line tools only
- **Java/JDK** — Eclipse Adoptium JDK 17 is installed at `C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\`

## Upstream Repo

- **URL:** https://github.com/FoedusProgramme/Gramophone
- **Clone URL:** https://github.com/FoedusProgramme/Gramophone.git
- **Default branch:** master
- **License:** GPL-3.0

## Build Requirements From Upstream README

1. Git submodules must be initialized: `git submodule update --init --recursive`
2. Create `package.properties` in repo root containing `releaseType=SelfBuilt`
3. Upstream says "latest beta version of Android Studio" — but I want to know if we can avoid Android Studio entirely and just use Gradle + command-line tools + Android SDK

## What I Need You To Do

Work through this step by step. Execute each step yourself where possible. Only hand me manual steps when you genuinely cannot automate them.

1. **Check prerequisites:** Verify I have everything needed to build (JDK, Android SDK/cmdline-tools, Gradle). If anything is missing, install it or give me the simplest possible manual steps.

2. **Fork the repo:** Create a private fork under my GitHub account (or clone and push to a new private repo if GitHub doesn't allow private forks of public repos). Set up the remote properly.

3. **Clone and initialize:** Clone the repo to my machine, init submodules, create `package.properties`.

4. **Build the APK:** Run the Gradle build. Debug any errors that come up — this is the part most likely to hit issues (SDK version mismatches, missing build tools, submodule problems, etc.). Be patient and methodical.

5. **Install on my phone:** Push the debug APK to my Pixel via ADB and confirm it launches.

6. **Save state:** Commit everything, push to my private repo, and document what we did so future sessions have context.

## Important Context

- I'm not a developer. I understand conceptually what's happening but I won't know Gradle error codes or Kotlin build issues. Translate errors into plain English and fix them.
- My Windows user directory is `C:\Users\chawo\` — OneDrive Desktop is at `C:\Users\chawo\OneDrive\Desktop\`
- The phone already has the official Gramophone installed via Obtainium (from GitHub releases). My fork will need a different package name eventually to coexist, but NOT in this session — just get the build working first.
- If the build needs Android SDK components I don't have, you can install them via `sdkmanager` command-line tool if available, or tell me the fastest way to get them.
- The upstream build.gradle.kts targets compileSdk 36 and uses Kotlin 2.3.0. Make sure whatever JDK/SDK setup we do is compatible.

## Success Criteria

Session is done when:
- [ ] Repo is cloned on my machine with submodules initialized
- [ ] Private repo exists on my GitHub
- [ ] `./gradlew assembleDebug` completes successfully  
- [ ] Debug APK is installed on my phone and launches to the main screen
- [ ] Everything is committed and pushed
