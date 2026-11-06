# ADR-001: how much control should the AI have?

5 November 2026. Status: selected development direction; device mechanisms remain unproven.

## Decision

Use AI/conversational interpretation with a typed action broker. Keep development
diagnostics separate from the everyday assistant. A broker action may use a tested
command internally; the model does not receive an unrestricted command-execution tool.
Implement as a separate companion app; preserve CleanYourTV and its no-network launcher.

## Debate

| Option | Strongest argument for it | Cost / failure mode | Decision |
|---|---|---|---|
| AI generates and runs arbitrary shell commands | Maximum flexibility for novel requests; can inspect a device and discover controls dynamically; excellent development/debugging utility | Wrong scope/arguments can affect Wi-Fi, voice, apps or boot behavior. Exit status is not a verified TV result. A remote ADB executor introduces an always-on host and privileged network channel. Untrusted titles/UI can become command-injection inputs. Recovery becomes hard to bound. | Keep for supervised development diagnostics, not everyday unrestricted control |
| Only fixed app intents | Small, fast, clear permissions and reliable app launches | Cannot supply missing availability data or many vendor controls; limited feedback when an app ignores an intent | Use where supported, but insufficient alone |
| AI operates arbitrary UI by looking at screenshots | Broad coverage without bespoke app APIs | Slow, visually fragile, sensitive screen data, focus races, expensive inference; arbitrary account/purchase screens should not become autonomous controls | Use narrow known UI adapters with on-request observation; no continuous screenshot agent |
| Root plus a general agent | More protected device APIs, system inspection and filtering may become reachable | No verified supported root route here; operational/streaming risk, maintenance burden; root does not create catalog data or app integrations | Rejected for this everyday TV, as agreed with owner |
| Typed action broker with verified adapters | Natural requests and corrections at the front; bounded, testable execution and undo underneath; can combine intents, media sessions, settings APIs and known UI paths | Each capability needs initial adapter work; unfamiliar requests must say unsupported or enter a separate diagnostic process | Selected |

## Why shell is not simply “bad”

Shell is an interface, not a permission level. A command can run as an ordinary app,
ADB's shell account or root, each with different privileges. A tiny validated command
that reads or sets a known control may be a good adapter. The key difference is whether
the model invents a program at runtime or requests a defined operation with checked data.

For example, interpreting “a little dimmer” as adjust_backlight(delta=-5) lets the broker
clamp the value, scope it to the active input, record the old value, verify the change
and undo it. The underlying method could be a vendor API, UI control or tested command.
The AI should not discover and mutate arbitrary brightness-like settings every time.

## Layers and trust boundaries

1. User invocation: the physical microphone button; no always-on recording.
2. Speech: approved on-device/cloud route, no app audio retention.
3. Interpretation: domain intent and short conversation context; optional model can
   propose only schema-valid actions.
4. Planner: explicit ambiguity checks, supported capability selection and bounded steps.
5. Broker: allowlisted actions and packages; validates arguments; owns permissions.
6. Adapters: app links/search providers, MediaSession, known TCL controls and limited UI.
7. Verification: compare actual state with the intended result; speak/show true outcome.

Film metadata, provider pages, subtitles and UI text are data. They cannot request shell
execution, new permissions, account changes or provider-credential disclosure.

## Utility versus restrictions

The everyday assistant should have enough authority to fulfill its advertised commands
without asking at every step. It should not ask to confirm a normal volume/picture step,
app launch or unambiguous included-content selection. It should clarify an ambiguous
title and confirm purchases/account changes—which are outside the first release anyway.

An unfamiliar request can produce a clear unsupported result. During development, that
becomes a proposed new action with an experiment and acceptance test. A separate explicit
diagnostic session may use shell inspection to develop it. This grows useful capability
without converting every living-room request into an unreviewed system administration task.

## Practical consequences

- No permanent plaintext network ADB dependency. Temporary ADB is for setup/test only.
- No always-on laptop requirement in the selected TV-local design.
- Do not assume the remote microphone button can be remapped just because a key event
  can be intercepted. Prove audio routing, recognition and reboot behavior as one path.
- Do not replace actual panel brightness with an overlay merely to make a demo pass.
- Current platform APIs/deep links are preferred; fallback UI behavior is reported honestly.
- A rollback restores the original Google assistant component/settings, leaves casting
  dependencies intact and removes only the companion's added capabilities.

## Primary references

- Android assistant role requirements: https://source.android.com/docs/core/permissions/android-roles
- VoiceInteractionService: https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- On-device speech recognition support: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Android media assistant/deep links: https://developer.android.com/media/implement/assistant
- Media Quality framework (Android 16+): https://developer.android.com/training/tv/mqf
- JustWatch partner API scope: https://apis.justwatch.com/docs/

These establish platform options, not evidence that this TCL firmware or every installed
streaming app supports them. The next phase records device evidence separately.
