# Latest handoff — 6 November 2026

## Proven on the household TV

TCL Smart TV Pro / G08, Android 14 SDK 34, firmware
`tcl9618-user 14 UTT2.250416.001 AU02 release-keys`, ARMv7. No root or firmware change.

- Owner accepted physical remote speech, the compact overlay, live words and automatic
  YouTube playback without a title-choice screen.
- Owner accepted Prime profile-to-film playback after saying Mad Max Fury Road.
  Cold/warm synthetic launches also verified the exact film/year and fresh playback.
- Stremio exact Mad Max page, default Dune 2021 selection and correction to Dune 1984
  verified through production request replay.
- Real brightness/default 10-point steps, undo, Movie/Standard modes, native HDMI 1
  selection, media pause/resume/seek/volume and supported app opening tested.
- Latest overlay survived a real restart; owner then accepted Standard mode, undo,
  mic/Back cancellation, Home/Input/Settings and held-volume repeat.
- Rollback to stock Google and removal/re-enabling of only our added control/listener
  capabilities executed successfully. Existing launcher and listener entries preserved.
- Latest native build: 82 JVM checks. Android lint: 0 errors, 29 warnings.

## Still open — do not call these finished

1. **General language interpretation.** Current `Interpreter` is a finite pattern
   parser. Unrecognized wording can yield a visible unsupported/clarification response.
   No live LLM planner, ChatGPT OAuth, Gemini API key flow or subscription-backed model
   request exists in this snapshot. The user wants an action-capable agent, not a growing
   list of regex exceptions.
2. **ChatGPT subscription route.** Official Sign in with ChatGPT documentation was
   checked. It describes eligible plan-backed Responses requests for supported
   open-source/local clients. Account/client eligibility and TV onboarding are **not
   tested**. Use a fresh authorized app connection; do not reuse Codex's saved tokens.
   Keep installed Google speech for transcription; the documented plan-backed flow
   does not supply a transcription API. References:
   - https://developers.openai.com/siwc/token-sharing-open-source
   - https://developers.openai.com/siwc/token-sharing-open-source/sign-in
   - https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations
3. **Phone casting regression.** Owner confirmed remote controls, but has not tested
   a phone cast on this release. Cast/AirPlay/Miracast dependencies were preserved.
4. **Final debugging-off acceptance.** Earlier brightness release passed with ADB off.
   Do not reuse that result to claim the latest complete release passed this gate.
5. **Additional provider adapters.** Supported links exist for more apps, but real
   title/playback verification is established for the named routes above, not every
   app, account, title, UI language or firmware. Public catalog schemas can change.

## Suggested next contribution

Add a model-driven intent layer returning validated `Command` types, with explicit
unsupported/clarification outcomes, bounded latency, cancellation, and transient context.
Connect it through the existing brokers and test natural paraphrases on the real mic.
Preserve explicit app/year/version choices and settings undo. A successful model reply
is not proof that a TV action happened; keep device verification.

The checked-in source is the current installed behavior. Workspace-specific TV
transport/build scripts were replaced by portable developer tools for this repository.
Their build verification is recorded separately in `docs/PACKAGING-VALIDATION.md`.
