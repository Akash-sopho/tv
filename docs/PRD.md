# TV Assistant — product requirements

Status: development draft, 5 November 2026. Owner: this household. Target: the existing
TCL Smart TV Pro / G08, Android 14, firmware UTT2.250416.001 AU02, unrooted.

Owner update, 6 November: automatically select the most popular relevant title/video instead of presenting an intermediate title-choice screen. Explicit app, year, media type and version constraints win; paid offers and unknown inputs still require a choice.

## 1. Problem and desired experience

Watching something should not require knowing which service carries it, navigating
several app menus, or remembering where TCL hides a picture setting. Press the remote's
microphone button, say what you want, and get a verified action or a short clarification.

Primary examples supplied by the owner:

1. “Watch Mad Max: Fury Road.” Identify the exact film, find its availability in India,
   prefer the household's installed/selected services, and open the matching title.
2. “Play Californication on YouTube.” Respect the explicit app and resolve ambiguity
   between a song, video, performance and television series rather than assuming.
3. “A little dimmer” and “Switch to Movie mode.” Operate the actual TV picture controls
   and provide an undo action.
4. The physical microphone button is the intended everyday entry point. An app tile,
   text box or simulated key is useful for development but does not satisfy this requirement.

The owner approved India as the availability country and cloud processing for spoken
requests. On 5 November 2026, the owner explicitly prioritized transcription accuracy
and reliability over the earlier provider-side no-audio-retention guarantee, and
authorized the TV's installed Google speech pipeline. Our app still keeps no audio
files or transcript archive by default. Existing services to preserve: Stremio, YouTube, Netflix,
Prime Video, JioHotstar, Sony LIV, YouTube Music, Alexa, Cast, AirPlay and Miracast/T-Cast.
The stock Google packages and TCL updater/casting dependencies remain installed.

## 2. Scope and boundaries

The first release includes those three content/settings interactions, the physical
microphone integration, follow-up corrections, action feedback and undo for settings.
Pause/resume/seek, named inputs and opening an installed app are useful supporting
actions, using the same verified action layer. Later candidates—routines, subtitles,
episode-end sleep, recommendations and cross-app resume—remain in the living backlog
and are not substitutes for the primary requirements.

No root, bootloader unlock, firmware flashing or change to CleanYourTV launcher code.
Develop a separate companion app. ADB may be enabled temporarily for installation and
diagnosis, with a saved rollback, but is closed after the device work. The everyday
assistant must work with the development computer absent and network ADB closed.

## 3. Functional requirements and acceptance evidence

| ID | Requirement | Evidence needed for acceptance |
|---|---|---|
| R01 | The real microphone button opens the assistant from Home and a supported foreground app | Owner presses the physical button; our session/UI starts, captures the intended utterance and performs the requested action; repeat after reboot and with ADB closed |
| R02 | Listen only after an intentional invocation; stop on result, timeout, Back or cancellation | Mic lifecycle instrumentation plus a real cancellation test; no hotword/continuous recognition loop |
| R03 | Reliably transcribe ordinary requests and title names using the approved Google speech route | Actual spoken accuracy/latency tests; inspect our app storage/logs; clearly distinguish Google's data handling from our app's storage |
| R04 | Parse title, app, media type, year, relative adjustment, picture preset and follow-up corrections | Domain tests for paraphrases, ambiguity, corrections and unsupported requests |
| R05 | Resolve exact titles, distinguishing remakes/series/songs where necessary | Real title lookup shows matching title/type/year; ambiguity is resolved using catalog popularity/view counts while preserving explicit constraints and correction context |
| R06 | Find current availability for India, distinguishing included access from rent/buy | Source-backed offers with region and freshness; no invented availability or inferred subscription ownership |
| R07 | Explicit named app overrides provider preference | “... on YouTube” stays in YouTube unless the user changes that choice |
| R08 | Open the correct title in its provider, and start playback when supported and unambiguous | Real app/title UI or media-session state confirms result; report title-page opening and search-result opening honestly |
| R09 | Adjust actual panel backlight/light output in a bounded relative step | Read initial value, change through a supported TCL route, verify value/UI, undo and verify restoration; no software overlay presented as panel control |
| R10 | Select the actual Movie/Cinema picture preset | Read available presets on this firmware; apply the matching preset; verify on the TV and restore |
| R11 | Offer immediate undo for settings and short conversational corrections for content | “Undo that”, “the 2015 one”, “use Stremio instead” tests; changes are associated with the right previous action |
| R12 | Expose a concise state: listening, working, choice, completed, unsupported or failed | Remote-friendly UI, D-pad focus, Back cancellation, clear spoken/on-screen feedback |
| R13 | Preserve installed services, casting, physical Home/Input/Settings and held-volume repeat | Before/after real checks on this TV, including casting from a phone |
| R14 | Persist installation and the selected assistant route through reboot | Real reboot test and physical microphone invocation afterward |
| R15 | Restore the former assistant and remove only our added capabilities | Tested rollback of default role/components/permissions; Google packages remain enabled |

Opening a generic search screen is useful fallback behavior, but does not pass R06/R08
when the requested outcome was identifying a provider and opening an exact title.
Likewise, demonstrating a typed request does not pass microphone-button integration.

## 4. Interaction rules

Automatically choose the most popular matching title/video. Compare Cinemeta popularity for up to three relevant film/show candidates and YouTube view counts for the first three video results; preserve source ranking when scores are unavailable. Do not use review ratings as popularity. Show at most three choices only for paid offers or unknown inputs. Preserve a short session context: the last
resolved title, media type, app choice, selected offer and previous reversible change.
Expire that context after inactivity; do not build a viewing-history database.

When a verified included provider is uniquely appropriate, open its title page or start
playback where the integration supports it. Never buy, rent, subscribe or change an
account automatically. If only paid offers are found, show them distinctly and require
a separate user decision. An installed app is not proof of a subscription.

The default meaning of “a little dimmer” is a small bounded step in the panel's backlight
or equivalent luminance control, to be chosen from the real control's range. It must not
silently alter black level, HDR metadata, color temperature or all inputs at once.
The requested preset is scoped to the active input/content mode as the TV supports it.

## 5. Architecture decision

Use a companion TV app with a conversational interpretation layer and a typed action
broker. See ADR-001 for the debate between unrestricted shell driving, fixed intents,
UI automation, root and the selected hybrid. No model-produced arbitrary shell execution.
The broker can use a tested command behind a specific action if a required control
truly needs it and a sustainable privilege path is available. ADB is not assumed to be
that long-term path.

First prove the platform constraints before committing to a speech backend or catalog:

- Test the assistant role and VoiceInteractionService route. Check whether the remote
  microphone stream follows the chosen assistant or is tied to TCL/Google's voice stack.
  A key interception alone is not proof that useful remote audio reaches our app.
- Prefer the installed Google recognizer for accuracy and integration with the TV's
  voice pipeline. Test transcript callbacks on this device; do not assume that this
  service is the same model/backend as Gemini or guarantees zero provider retention.
- Test the installed apps' search providers, content links and Google TV's existing
  title/offer UI as possible catalog/action adapters. If they cannot reliably supply
  current regional offers, select a documented availability source and surface any
  credential/cost requirement before using it. Fixtures are test inputs, not live offers.
- Inspect normal TCL picture controls and supported services/providers. The Android 16
  Media Quality API is not available merely because this Android 14 TV has picture menus.

## 6. Privacy and permissions

The companion stores no raw audio, screenshots, continuous screen history or transcript
archive by default. It keeps only short in-memory conversation state and a small local
receipt for the last reversible setting change. Diagnostics use synthetic test utterances
or redacted action/status fields; logging full speech transcripts requires an explicit
temporary diagnostic choice.

The owner waived the earlier provider-side no-audio-retention constraint in favor of
accuracy and reliability for the installed Google recognition service. Google's normal
data handling applies; this app does not claim to control its retention. This waiver
does not authorize unrelated always-on recording, transcript archives, or new paid
cloud infrastructure. Our app retains only transient recognized text for the request.

Request only capabilities the selected implementation needs and describe their purpose:
microphone for user-initiated speech; assistant/session role if supported; accessibility
only for on-request action verification and known app/settings adapters; notification
access only if required for active media controls; Internet only for approved online
services. Avoid broad storage, contacts, camera and device-admin capabilities. Never
grant a permission to compensate for an unrelated failed test.

Accessibility can observe other apps. Keep observation scoped to an active user request,
avoid password/account-entry flows, and do not upload UI trees or screenshots by default.
Content titles, subtitles, web pages and app UI are untrusted data, never instructions
to the action broker. No billing/account operations are available to autonomous plans.

## 7. Engineering and verification requirements

Use native Android components and a small dependency set suitable for this TV. All
actions carry validated structured arguments, a bounded timeout, a result state and an
action-specific verification rule. Settings mutations snapshot the previous value and
support rollback. Ambiguous/unsupported states fail visibly without speculative clicks.

Test interpretation and planning as plain JVM logic. Test Android lifecycle/permissions
with instrumented checks where useful. Run build/unit/lint checks, then exercise each
required action on the actual TV. Measure rather than promise recognition/action latency;
report cold and warm invocation times. A successful build is not end-to-end evidence.

The physical microphone, picture-control route and regional availability are release
gates. If one is blocked, document the exact evidence and continue independent work;
do not redefine success as an app-tile demo or a hard-coded title lookup.

## 8. Development sequence

1. PRD, architecture debate, source notes and requirement/evidence matrix.
2. Device capability spike: default assistant/mic routing, recognition availability,
   TCL picture controls and title/search integration. Snapshot and restore touched state.
3. Implement the action schema, parser, session state, privacy controls and TV UI.
4. Implement verified content and picture adapters; wire the proven microphone route.
5. Real-remote/end-to-end tests, ambiguity/cancellation/undo, reboot, rollback, casting
   regression and a run with network ADB closed. Deliver source, APK, instructions,
   evidence and remaining limitations.

## 9. Open decisions

- Exact installed-app subscription preferences and whether to autoplay or stop on a
  title page when both are technically possible. Until clarified: prefer included
  access, do not purchase, and never claim playback without evidence.
- Speech route and any external provider credentials, retention terms or costs.
- Availability source after device/search-provider experiments.
- Actual supported mic and picture-control mechanisms on this TCL build.

These are explicit unknowns, not reasons to reduce the primary feature scope.
