# Packaging validation — 6 November 2026

- Portable standalone build completed, including APK alignment and verified v3 signature.
- 51 interpreter/schema checks and 31 catalog checks pass (82 total).
- Every Java/resource file from the latest installed-version source matches the handoff copy byte-for-byte. The manifest declarations moved to Gradle and the portable build adapter.
- Portable Python developer scripts compile. No device mutation was performed by the packaging checks.
- Android Studio/Gradle configuration is provided; a Gradle build has not been revalidated in this Windows filesystem sandbox. The portable ECJ build above is the verified route.
- Signing keys, APKs, dependency caches, TV screenshots, raw device dumps, old speech/cloud experiments and local network/account details are excluded from Git.
- App/device acceptance evidence is in HANDOFF.md. Packaging success does not complete the open ChatGPT reasoning, phone-casting or final debugging-off gates.

Build source/artifact metadata (local APK is intentionally untracked):

```json
{
  "apk": "tv-assistant.apk",
  "bytes": 4974357,
  "sha256": "e63d60877bf160c6be1ba332aa3b871a34f2d58f8c9fff51e1f88fbbedb2ec00",
  "compiler": "Eclipse ECJ 3.38.0",
  "compiler_sha256": "97c566b120009c203a2fc8b291f4a9adbc171cf1ccb70f06f6b4e1828c00ce8e",
  "sdk": 34,
  "min_sdk": 33,
  "core_checks": 82,
  "device_verification": "not proven by this build"
}
```
