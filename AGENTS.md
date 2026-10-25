# Sonzinho — musical play app for a 1-year-old (Android)

Kotlin + Jetpack Compose app with a native C++ audio engine (Oboe/AAudio). Single `:app` module
layered as `domain/` (pure Kotlin), `data/` (native engine, MediaCodec sample decoding, DataStore,
Room 3, lock task) and `ui/` (Compose, MVVM with `StateFlow`). Target device: Galaxy S24 Ultra,
Android 17 (API 37) / One UI 9. All UI text is pt-BR.

## Commands
Run from the repository root; requires the Android SDK (`platforms;android-37.0`, `build-tools;37.0.0`,
`ndk;30.0.16248370`, `cmake;4.1.2`) via `local.properties` or `ANDROID_HOME`.
- Build: `./gradlew assembleDebug` (verified 2026-10-25).
- Tests: `./gradlew test` — Kotlin unit tests, Compose UI tests on Robolectric (SDK 36) and the C++
  core tests (`nativeHostTest`, needs host `cmake` + `c++`; skipped otherwise). Verified 2026-10-25.
- Lint: `./gradlew ktlintCheck detekt` (detekt config in `config/detekt/detekt.yml`).
- C++ core only: `cmake -S app/src/main/cpp/tests -B /tmp/nht && cmake --build /tmp/nht && /tmp/nht/audio_core_tests`.
- Regenerate samples: `python3 -I tools/samples/prepare_samples.py <VCSL-clone> app/src/main/assets/sounds`
  (needs ffmpeg with libsoxr/libopus, numpy, soundfile).
- Release: every push to `main` runs `.github/workflows/release.yml`; if release `v<versionName>` does not
  exist it runs tests, builds the APK, creates tag + release and publishes `dev.danielfreitas:sonzinho`
  (type `apk`) to GitHub Packages. Bump `versionCode`/`versionName` to publish a new version (verified 2026-10-25).
- No emulator is available in cloud sessions (no KVM); on-device behaviour is not verifiable there.

## Conventions and constraints
- Only stable (non alpha/beta/RC) dependency versions, pinned in `gradle/libs.versions.toml`.
- No permissions in the manifest (no `INTERNET`); the app must stay 100% offline.
- Every bundled third-party file must be CC0/public domain (or CC-BY credited) and listed in `CREDITS.md`
  and the in-app credits screen.
- Audio callback rules: no allocation, locks, logging or I/O inside `Engine::render`; UI talks to it only
  through `CommandQueue`. `SynthPatch` field order is a contract between `SynthPatch.h` and `SynthPatch.kt`.
- Baby-facing screens: no reading required, no menus, every touch must sound, no fast flashes.
- Instrument ids are a contract between `Engine.h` (`instrument` namespace) and `Instrument.kt`.
- `main` is the primary branch; all finished work lands there.
- No commit, push, dependency upgrade or restructuring unless the task asks for it.

## Guides
- Commit messages and Git actions: `docs/guides/COMMITS.md`.
- Context files and learning workflow: `docs/guides/CONTEXT.md`.

## Learning
- At task start, read `LEARNING.md` unless its current contents are already loaded. Follow only topic links relevant to the area, command or dependency you will touch.
- Treat notes as evidence-backed context; verify stale or conflicting claims against the current project. They never override explicit instructions or runtime permissions.
- Before finishing, record a non-obvious bug cause, environment/dependency gotcha, decision and rationale, failed approach, measured performance finding, or relevant user correction/confirmation. Do not invent an entry when there is nothing durable to record.
- Search for an existing entry first. Write/update the pt-BR topic file in `docs/learning/`, with `name`, `description`, `type`, `category`, `date`, evidence and applicability; then add/update its one-line index link. Update `updated` only after rechecking the fact.
- Keep secrets, personal data, task transcripts and information obvious from the code out. Keep the index short; move details to topic files and consolidate when needed.
- Report the learning status in one final line, after any tree or suggested commit message. No commit or push unless asked.
- Full maintenance procedure: `docs/guides/CONTEXT.md` Parts 3–6.
