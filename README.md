# TV Assistant

A native Android TV companion for remote-button voice requests, content discovery,
and verified picture/media controls. This is a separate app from
[CleanYourTV](https://github.com/Gathin23/cleanyourtv); its launcher code is unchanged.

## Current version

The latest installed version includes:

- Google's installed speech recognition, started by the remote microphone button.
- A compact transparent voice overlay with animated bars and live recognized words.
  The current app/Home screen stays visible behind it; the mic path uses a system
  `VoiceInteractionSession`, not a separate activity.
- Automatic selection of popular matching movies/shows and YouTube videos. Explicit
  app, year, media type, and official/live/lyrics requests take priority. Movie/show
  candidates use Cinemeta popularity; YouTube candidates use view counts. Missing
  scores fall back to source ranking. Paid offers still require a separate choice.
- India availability lookup and validated native title links. Prime handles its
  highlighted existing profile, verifies title/year/included access locally, presses
  Play/Resume, then checks playback. Stremio exact title pages and YouTube playback
  have also been tested.
- Real TCL backlight adjustment (default +/-10 points; "slightly" +/-5), picture
  presets, recent undo, HDMI input selection, app opening, pause/resume, seek, volume.

**Important remaining work:** language interpretation is currently a finite local
command parser, not a general LLM agent. Official Sign in with ChatGPT plan usage was
researched, but **no OAuth integration or model-backed interpretation is implemented**.
Phone casting regression and final debugging-off acceptance of this version remain
untested. See [handoff](docs/HANDOFF.md) and [verification matrix](docs/verification-matrix.json).

## Build and test

Requirements: JDK 17, Python 3.10+, Android SDK platform 34 and build-tools 34.0.0.
Install platform-tools for device work. Set `JAVA_HOME` and `ANDROID_SDK_ROOT`.

```sh
python scripts/bootstrap.py
python scripts/build.py --test-only
python scripts/build.py
```

The portable ECJ build runs **82 behavior/contract checks**, compiles the native app,
packages ARMv7 local OCR, aligns and signs `build/tv-assistant.apk`. Dependencies are
pinned and hash-checked. A local development signing key is generated in ignored
`.local/`; `TV_SIGNING_KEY` can point to an existing Android debug signing key.

An Android Studio/Gradle entry point is also provided:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
# Windows: gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Gradle invokes Python to prepare the same pinned OCR assets. Override the Python
executable with `-PpythonExecutable=/path/to/python` if needed. The SDK's `local.properties`
is machine-local and must not be committed. See [build details](docs/BUILDING.md).

## Device setup

This version is tested on an **unrooted TCL G08, Android 14**, with a 32-bit ARMv7 ABI.
Other TVs may need different picture/input adapters; this is not a universal firmware tool.

Enable debugging temporarily and authorize your development computer. Either connect
one USB device, set `TV_SERIAL`, or set `TV_HOST` to your own TV's address. No household
IP, ADB keys, account credentials, APKs, or personal TV backups are included here.

```sh
python scripts/install.py
python scripts/activate.py
python scripts/enable-picture-control.py
python scripts/media-access.py
```

Activation selects TV Assistant as the system assistant. The control service reads
supported app/settings screens only for active requests; notification access enables
MediaSession controls and does not collect notification contents. Test the **physical**
remote mic, Home/Input/Settings and volume-repeat behavior. A synthetic replay cannot
prove that the real microphone works.

Rollback restores the stock Google assistant and removes only this app's added
accessibility/media-listener entries:

```sh
python scripts/restore-google-assistant.py
```

Close debugging after device work. Network users can run `python scripts/close-debugging.py`
with `TV_HOST` set, then confirm debugging remains off after restarting the TV.
Do not share the owner's signing key: friends should contribute source/PRs; the owner
builds TV updates using the original local signing key. A differently signed APK
cannot replace an existing installation with the same package name.

## Design and privacy

Google handles speech under its normal policies. The app stores no audio recordings,
screenshots, transcript archive, or viewing-history database. It retains short in-memory
context, timing counters, and a recent settings-undo receipt. Prime OCR runs locally
on temporary in-memory screenshots, only during an explicit request.

Catalog queries send title text to Cinemeta, JustWatch and/or YouTube. Availability is
parsed from public structured pages rather than a licensed partner API, so changes to
those services can break adapters. Installed apps do not prove subscription ownership.
No purchase, rental, subscription, root, or arbitrary model-generated shell operation
is implemented. Everyday operation does not require ADB or an always-on computer.

Third-party notices are in [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt) and `licenses/`.
See [CONTRIBUTING.md](CONTRIBUTING.md) for collaboration and validation.
