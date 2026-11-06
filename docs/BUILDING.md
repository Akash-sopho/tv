# Build details

Use JDK 17, Python 3.10+ and an Android SDK with `platforms;android-34` and
`build-tools;34.0.0`. `ANDROID_SDK_ROOT` or `ANDROID_HOME` identifies the SDK.

The primary portable build is `python scripts/build.py`. It uses Eclipse ECJ 3.38.0,
Android aapt/D8/zipalign/apksigner, and a JVM-only JSON-java dependency. This route also
works around a Java filesystem limitation in the original Windows development sandbox.
Set `TV_ASSISTANT_D8` to another installed SDK build-tools `lib/d8.jar` if required.

`scripts/bootstrap.py` downloads only pinned, hash-checked dependencies into ignored
`.cache/deps`. The English OCR model URL uses an upstream branch, but the exact expected
SHA-256 is enforced; a changed model fails rather than silently updating. Native OCR
libraries are limited to the tested ARMv7 ABI. Licenses are included in the built APK.

The source manifest omits package/version/SDK declarations owned by Gradle. The portable
builder creates its temporary aapt manifest under `build/portable` with the same
`dev.tvassistant`, version 2, minSdk 33 and targetSdk 34 values. Java application sources
and resources match the installed app.

For a normal Android Studio environment, use the Gradle wrapper (8.9, checksum pinned)
and Android Gradle Plugin 8.5.2, matching the upstream launcher's existing toolchain.
Gradle invokes the dependency preparation script before compilation. The Gradle unit
test bridge invokes the same executable `CoreSpec` and `CatalogSpec` contracts.

Development signing uses `.local/debug.keystore`, never committed. Builds can differ
in ZIP metadata/signature from the owner's installed APK even with identical Java source.
Use the owner's original local signing key for updates to that existing installation.
Custom production signing and distribution are not configured.
