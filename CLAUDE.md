# JacRed TV Project Instructions

Before committing anything, read [Commits](#commits).

## Project overview

**JacRed TV** is a native Android TV torrent-streaming client for a Xiaomi Mi
Box 4. It searches jac.red, streams the chosen release through TorrServer, and
hands the stream to an installed video player. It is intended to be operated
with a TV remote, not a touchscreen.

Flow: **browse** (TMDB titles) → **releases** (jac.red, with filters) →
**detail** (TorrServer files, Watch / Keep / Remove).

The app has no third-party dependencies — only the Android framework, `org.json`
and `HttpURLConnection` — so Gradle never needs to resolve external artifacts.

### Screens

| Activity | Role |
| --- | --- |
| `MainActivity` | Search box, TMDB poster grid, recently-watched row, TorrServer status chip, update banner |
| `ReleasesActivity` | jac.red releases for one title, with quality/HDR/codec/60fps/sort chips |
| `DetailActivity` | Adds the magnet to TorrServer, lists files, plays them |
| `SettingsActivity` | jac.red URL, TorrServer URL and credentials, TMDB key/URLs, default player, **Check for updates** |

### Code map

Four activities and sixteen helpers, all in `tv.jacred.client`. The project
sets `android.useAndroidX=false`, so the activities extend plain
`android.app.Activity`, not `AppCompatActivity`, and no helper subclasses a
framework type at all. Reach for a framework API, never an AndroidX one.

| Class | Role |
| --- | --- |
| `JacRed` | Search against either jac.red source, returning `Torrent` list |
| `TorrServer` | Add/list/remove torrents, build stream and playlist URLs, read watch marks |
| `Tmdb` | Poster and plot lookup, image URL building |
| `Http` | `GET` and `POST` JSON over `HttpURLConnection`, with optional basic auth |
| `Torrent` | Parses one API result; also `humanSize` and `shortDate` formatting |
| `TorrFile` | One file inside a torrent (id, path, length) |
| `TorrStatus` | Parsed TorrServer status payload |
| `Prefs` | Every setting, over `SharedPreferences` |
| `History` | Recently-watched list, persisted through `Prefs` |
| `Filters` | Quality/HDR/codec/60fps/sort state, applied to a release list |
| `Players` | Discovers installed video players and hands a stream to one |
| `Updates` | Reads the newest GitHub release, compares versions, downloads and installs the APK |
| `UpdatePrompt` | The modal behind both ways into an update — the banner and the Settings button |
| `Ui` | Background-work helper and named thread factory |
| `TvDialog` | D-pad-navigable dialog |
| `ImageLoader` | Async poster loading into an `ImageView` |

### External services

- **jac.red search** — `GET /api/v1.0/torrents?search=&sort=seeders` is primary:
  it matches titles far better than the Jackett-compatible endpoint (hundreds of
  hits for "matrix" versus two). `GET /api/v2.0/indexers/all/results?query=` is
  the fallback and is the only one carrying `ffprobe` stream details. Neither
  needs an API key on the public host, but both need browser-like headers.
- **TorrServer** (`YouROK/TorrServer`, verified against MatriX.142 at
  `192.168.1.7:5665`) — `POST /torrents` with `action` add/get/list/rem,
  `GET /stream/<name>?link=<hash>&index=<file id>&play` for playback,
  `POST /viewed` for watch marks, `GET /echo` for the version, and
  `GET /tmdb/settings` which the Settings screen can import. The swagger spec
  lives at `/swagger/doc.json` on the server itself.
- **TMDB** — optional. Without an API key the app still works, but there are no
  posters or plots. Lookups happen on the detail/releases screens only, never
  per result row.

### Updating itself

The app offers its own releases: Settings has a **Check for updates** button, and the home
screen shows a banner when `releases/latest` carries a newer `versionName`. The APK is
downloaded from the release asset and handed to `PackageInstaller`, and the dialog shows
that version's `CHANGELOG.md` entry read from the tag. `PackageInstaller`, which is what lets a
failure reach the screen — there is nowhere to read a log on a TV.

Android replaces an installed app only when the new APK carries the same signature, so
`release.yml` signs every release with one long-lived key held in `homelab-infra` as
`ANDROID_KEYSTORE_BASE64`. A build installed from `assembleDebug` is signed with the local
debug key instead and cannot be updated by a release — it has to be uninstalled once.

Useful facts learned from the live APIs: `info.quality` is often wrong (1080p
releases reported as 480), so resolution comes from the ffprobe video stream when
present; and neither API exposes a frame rate, so the 60 fps filter is a title
heuristic.


## Project facts

- Project type: Android application
- Language: Java
- UI: Android XML views/resources
- Gradle module: `app`
- Application ID: `tv.jacred.client`
- Main activity: `tv.jacred.client.MainActivity`
- Java package root: `app/src/main/java/tv/jacred/client/`
- Launcher type: Android TV Leanback launcher
- Orientation: landscape
- Minimum SDK: 28
- Target/compile SDK: 34
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`
- Java source/target compatibility: 17
- Release build: R8 minify plus resource shrinking, signed from
  `keystore.properties` when that file exists (see README)

Keep all controls usable with the D-pad. Every interactive control must have a
clear focused state, and the app must not require touch input. Design for a
large-screen, ten-foot viewing distance and preserve landscape layouts.

## Target device

- Device: Xiaomi Mi Box 4 / model `MIBOX4`
- Android version: Android 9
- Android API level: 28
- Current network ADB address: `192.168.1.128:5555`
- Display: 4K television, normally using 2160p60 output
- Input: Bluetooth/infrared TV remote with D-pad and OK button

The IP address is assigned by the local network and can change. If connection
fails, check the Mi Box's current address under **Settings > Network & Internet**.
The development computer and Mi Box must be on the same local network, without
a VPN isolating local traffic.

## ADB location

`adb` is expected on `PATH`. If the command is not found, install the Android
SDK platform-tools and add them to your shell `PATH`.

## Connect to the Mi Box

Make sure USB debugging/network debugging remains enabled in the Mi Box
Developer options, then run:

```sh
adb connect 192.168.1.128:5555
adb devices -l
```

The expected device serial is `192.168.1.128:5555`. On the first connection,
accept the RSA debugging authorization prompt on the television and optionally
select **Always allow from this computer**.

If the device is shown as `offline` or a previous session is stale:

```sh
adb disconnect 192.168.1.128:5555
adb connect 192.168.1.128:5555
```

If it is shown as `unauthorized`, unlock/wake the TV, accept the authorization
dialog, and reconnect. Do not reset or erase the device to fix an ADB connection.

## Build and deploy

Build the debug APK from the project root:

```sh
./gradlew assembleDebug
```

The generated APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Lint runs as a build gate (`abortOnError`), so keep it clean:

```sh
./gradlew lintDebug
```

Anything deliberately ignored belongs in `app/lint.xml` with the reason written
next to it, never suppressed silently. The same goes for compiler warnings:
javac runs with `-Xlint:all`, and the few deprecations that remain carry a
`@SuppressWarnings` explaining why the modern API is out of reach on API 28.

For a build to hand to someone else:

```sh
./gradlew assembleRelease
```

That one is minified and resource-shrunk; keep
`app/build/outputs/mapping/release/mapping.txt` alongside it, or its stack
traces are unreadable. It is signed only when `keystore.properties` exists in
the project root — otherwise the output is `app-release-unsigned.apk`.

The application id changed from `com.example.hellotv` to `tv.jacred.client`,
so the first install after that change sits beside the old copy. Remove the old
one once:

```sh
adb -s 192.168.1.128:5555 uninstall com.example.hellotv
```

Install or update it without clearing app data:

```sh
adb -s 192.168.1.128:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch the TV activity:

```sh
adb -s 192.168.1.128:5555 shell am start \
  -n tv.jacred.client/.MainActivity
```

Stop it when needed:

```sh
adb -s 192.168.1.128:5555 shell am force-stop tv.jacred.client
```

## Continuous integration

`.github/workflows/build.yml` assembles both variants on every push to `main`,
every pull request and on demand. There is nothing to configure: the app
resolves no third-party artifacts, so the build is the whole job, and lint
fails it rather than filing a report nobody opens.

Each run uploads two artifacts:

| Artifact | Installs on the Mi Box |
| --- | --- |
| `jacred-tv-debug` | yes — signed with the debug key, `adb install -r` it directly |
| `jacred-tv-release-unsigned` | no — minified and unsigned, sign it first |

The runner holds no signing material, so a release artifact from CI always
needs signing by hand. Build locally with `keystore.properties` in place when
a signed release is what you need.

## Useful development commands

Verify that JacRed TV is the foreground activity:

```sh
adb -s 192.168.1.128:5555 shell dumpsys window windows | \
  grep -E 'mCurrentFocus|mFocusedApp'
```

Send remote-control keys for smoke testing:

```sh
adb -s 192.168.1.128:5555 shell input keyevent KEYCODE_DPAD_UP
adb -s 192.168.1.128:5555 shell input keyevent KEYCODE_DPAD_DOWN
adb -s 192.168.1.128:5555 shell input keyevent KEYCODE_DPAD_CENTER
adb -s 192.168.1.128:5555 shell input keyevent KEYCODE_BACK
```

Inspect logs for this app:

```sh
adb -s 192.168.1.128:5555 logcat --pid="$(
  adb -s 192.168.1.128:5555 shell pidof -s tv.jacred.client
)"
```

When changing the application, build successfully before deployment, install
with `-r`, launch the main activity, and verify D-pad focus plus OK-button
behavior on the actual Mi Box.

## Commits

- **Author every commit `Pavel Usanli <pavel.usanli@gmail.com>`.** Read
  `git config --local user.name` and `user.email` before the first commit in a
  clone and set them if they differ. A fresh clone carries no identity of its
  own, so it inherits the machine's global one — a different account, which
  leaves the commit unlinked from `kalpak44`.
- **Never `git config --global`.** That identity belongs to unrelated work and
  is correct there; overriding it silently retags those repositories too.
- **A commit message is one short, clear imperative line.** No body, no
  description, no bullet list of what changed — the diff already says that.
- **No trailers.** No `Co-Authored-By`, no generated-with line, no sign-off.
- **Push straight to `main`.** No feature branches and no pull requests. The
  build workflow runs on the pushed commit; check it went green rather than
  waiting on it before pushing.
