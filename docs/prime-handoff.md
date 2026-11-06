# Prime profile-to-title playback

Installed and tested on TCL G08 / Android 14, 2026-11-06.

Availability is checked through the India catalog before choosing a provider. The assistant sends the selected provider its validated title link; it does not search apps sequentially.

Prime's canvas UI exposes no usable title/button labels through accessibility. During an explicit title launch, the assistant therefore uses local English OCR over temporary in-memory screenshots. It recognizes the highlighted existing profile, selects it once, verifies the requested title logo and release year, and requires the Included with Prime badge plus a focused Play/Resume/Watch again button before pressing Center. Kids, New profile and account/sign-in flows are excluded. There is no purchase automation, cloud OCR, screenshot archive or passive screen collection.

For long complete logos, one character of OCR noise is tolerated only with the matching release year. Short titles require exact matching. Unrecognized layouts stop without guessing. Provider layout, language and stylized logos can require further adapter work; this test does not establish every Prime title or every platform.

Prime reports a generic PrimeVideo media title. Playback success therefore requires the locally verified film/year and primary-button action, followed by a fresh same-app PLAYING transition after that action. Opening an app/title alone is reported separately from playing.

Real TV request replay results through the production interpreter and brokers:

| Request | Result | Time after native app launch |
|---|---|---|
| Play Mad Max Fury Road on Prime | Profile → verified film/year/membership → Resume → fresh PLAYING | 13.9 s |
| Play Mad Max Fury Road | India provider selection → same verified playback | 19.2 s |
| Play Mad Max Fury Road, Prime force-stopped first | Cold launch → profile → same verified playback | 21.8 s |

An earlier cold run ended with YouTube foreground and was recorded as unverified, not playback success. The untouched-remote repeat passed. Tests above replay requests rather than claiming new physical speech tests. Google microphone routing was previously accepted; physical content voice/regression and other remaining gates are tracked separately.

Build: 65 JVM checks pass; Android lint has 0 errors and 32 warnings. OCR dependencies are hash-pinned by scripts/prepare-ocr.py; upstream notices accompany the deliverable in licenses/.
