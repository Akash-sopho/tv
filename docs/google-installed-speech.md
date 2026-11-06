# Installed Google speech

The active backend delegates bounded, user-initiated capture to the TV-installed
Google Katniss recognition service, falling back to the installed Google TTS
recognizer only when the first provider is unavailable before readiness.

The app receives partial/final text and does not write audio buffers to disk.
The owner accepted transcription accuracy, real microphone invocation, Back
cancellation, and the compact overlay before and after reboot. No separate
Google Cloud bridge is deployed. Provider retention follows Google policies;
our app does not claim to control it. See HANDOFF.md for current evidence.
