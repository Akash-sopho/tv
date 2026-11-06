# Real brightness adapter

Google STT quality was accepted by the owner as much better on 5 November 2026.
PictureActionService uses normal TCL Picture/Brightness settings. It retrieves the
current value and uses Android 14's supported accessibility D-pad global actions,
reading the actual value after each step. No screen-dimming overlay, firmware/root
change or runtime ADB call is involved.

Only Settings package events are subscribed; event callbacks do not collect content.
UI retrieval is request-scoped. No remote key filtering or screenshots are requested.
TCL requires flagIncludeNotImportantViews to expose the preset/control hierarchy.
Without it the adapter stopped safely before any mutation. The existing HomeKeyService
was preserved when enabling the new service.

On-device test: baseline brightness 30, change by -1, verified 29; undo requested,
verified restored to 30. These are adapter tests initiated by the DUMP-protected debug
receiver, not a substitute for full physical microphone end-to-end acceptance.

Voice routing is wired for relative/absolute brightness and undo. Undo expires after
two minutes and refuses a changed preset or a changed current value. It acts on the
current TV picture context; independent source/input identity is not yet verified.
Do not claim cross-input undo or all-input mutation support.

The service stops on missing/changed UI, rejected controls, stalled values, or timeout.
It records a pre-change receipt to make a partial adjustment recoverable. Completed
values and status are diagnostic data, not speech transcripts. The normal Settings
menu remains visible, with a toast confirmation. Movie preset/content playback are
still pending adapters.

Enable/disable scripts preserve other accessibility services. Development role repair
remains necessary after APK replacement. Runtime does not need this computer or ADB.

Physical end-to-end acceptance, 5 November 2026: the owner pressed the real remote
microphone, said a little dimmer, and confirmed that Brightness opened and decreased.
This passes the initial voice-to-real-brightness flow. Reboot, foreground-app coverage,
ADB-closed runtime and physical spoken undo still need separate acceptance evidence.
