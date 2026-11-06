# Microphone update repair and UI test

On 2026-11-05, after APK replacement, the selected assistant role was still
`dev.tvassistant` but VoiceInteractionManager reported `mBound=false mService=null`.
TCL's AUTO_START app-op for the app was `ignore`, with a recent rejection.

Allowing AUTO_START, writing app-op settings, and switching the assistant role away
and back restored `mBound=true mService=...`. The reusable activation script checks
this state after development installs. No reboot-survival conclusion follows yet.

Later counters showed two recognition result callbacks, audio sample counts and a
nonzero sound peak. Audio route type was 15 (built-in microphone); this is not proof
that audio came from the remote's microphone. No transcripts or audio were pulled.
The user could not tell whether the previous UI worked, so speech accuracy remains
unverified.

The updated build shows a large listening state, live partial words, an audio level
bar and an explicit Heard you result. The result separately states that no action
has occurred. Installed successfully, signature verified, 26 core contract checks
passed. `voice-ui-v2.png` was inspected for readable layout. Physical retest pending.
