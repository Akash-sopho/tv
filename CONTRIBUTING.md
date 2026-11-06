# Contributing

Create a feature branch, make a focused change, and open a pull request against `main`.
Describe the user-visible behavior, tests run, and device/firmware used. Owner acceptance
and synthetic replay results are different kinds of evidence; label them accurately.

Start with [the handoff](docs/HANDOFF.md), [requirements](docs/PRD.md), and
[architecture](docs/ADR-001-control-architecture.md).

## Code map

- `AssistantSession`, `AssistantPanel`, `VoicePulse`, `OverlayLayout`: mic invocation,
  transparent overlay, partial text and cancellation.
- `GoogleSpeech`, `SpeechController`, `RecognitionProxyService`: installed recognizer.
- `core/`: typed commands, bounded arguments, pattern interpreter, five-minute context.
- `ContentCoordinator`: automatic title choice, offer lookup, explicit app routing.
- `catalog/`: live catalog parsing, popularity selection, provider/deep-link validation.
- `MediaActions`: foreground/title/playback verification and transport controls.
- `PictureActionService`: request-only accessibility adapter for TCL picture controls
  and known primary-title UI. `LocalScreenReader`: request-only Prime OCR.
- `InputActions`: TV-reported labels/IDs and observed TCL source verification.
- `ProbeReceiver`: diagnostics/replay/visual-preview endpoint, restricted by Android's
  `DUMP` permission. It is not an unprotected remote control API.
- `scripts/`: developer-only ADB setup, replay, rollback and build tools.

## Validation

Run `python scripts/build.py` (includes 82 contract checks) or the Gradle commands in
README. Add meaningful tests for changed interpretation, unsafe provider data, result
ranking, cancellation or broker argument validation. Test affected behavior on the
real TV and report what remains unverified. Do not invent a passing physical mic,
casting, reboot or playback result from a successful build.

Maintain the current app/key identity for owner builds. Never commit `.local/`, SDK
paths, credentials, signing keys, ADB keys, private screenshots, device dumps or APKs.
Use placeholders for TV IPs and account information in documentation.

Keep language planning separate from action execution. A future model chooses typed
capabilities; the broker still validates arguments and verifies the actual result.
Do not add arbitrary shell execution to the Android app. Do not automate purchases,
account changes or profile creation. Preserve unrelated accessibility/listener entries.

This repository is the companion app only. Changes to the upstream CleanYourTV
launcher or debloating lists belong in that project's own contribution flow.
