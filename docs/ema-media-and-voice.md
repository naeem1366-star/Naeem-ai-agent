# EMA paid media routing and always-ready voice assistant

## Paid media routing

The Android app must call the authenticated Supabase Edge Function; it must never hold a NetArz API key.

- `GET /v1/media/models?type=music|video` returns the live NetArz model catalog.
- `POST /v1/media/music` proxies music generation to NetArz `/audio/music`.
- `POST /v1/media/videos` creates a professional video job through NetArz `/videos`.
- `GET /v1/media/videos/{id}` polls a job owned by the authenticated EMA user.
- `GET /v1/media/videos/{id}/content` streams the completed MP4 through the gateway.

The Edge Function reads `CHAT_PRIMARY_API_KEY` (legacy fallback `Netarz`) and `CHAT_PRIMARY_BASE_URL`. The NetArz catalog's live `pricing.per_request`, `pricing.per_minute`, and `pricing.per_second` values are used to estimate reservations. Wallet billing requires the existing EMA billing switch and USD-to-toman rate to be configured. Subscriptions and admins follow the existing paid-feature entitlement path. Asynchronous video reservations are settled after completion or released after failure.

Apply `supabase/migrations/0006_ema_media_jobs.sql` before deploying the updated Edge Function. Keep the existing GapGPT free image, image-analysis, and speech routes separate. The free video creator is not changed.

## EMA Life Assistant — target behavior

The intended user experience is a two-level, explicit opt-in voice workflow:

1. **Standby wake word:** while the user has enabled the foreground voice service, recognize “ایما” and reply with the prerecorded Persian phrase “در خدمتم”. This short acknowledgement must work offline and must not depend on Android Persian TTS being installed.
2. **Smart listening activation:** after the wake word, recognize “به گوش باش” and enter the clearly indicated smart-listening state. The green in-app control and persistent Android notification must show that this higher-privacy-impact mode is active.
3. **Smart listening stop:** recognize “شنود رو خاموش کن” (and the equivalent explicit stop phrase) and leave smart-listening mode, returning to wake-word standby. The user can also stop the foreground service immediately from the notification or app control.
4. **Analysis and routing:** while smart listening is active, identify relevant user-directed statements about nutrition, exercise, finance, work/projects, travel, and planning; prepare structured entries for the corresponding EMA spaces. Ambiguous or consequential entries must be shown for review/confirmation rather than silently committed.
5. **Network loss:** local wake-word recognition and the local “در خدمتم” sound remain available. Do not claim that online analysis works offline; queue only the minimum necessary user-approved structured data for later sync, and show its pending status.

### Privacy and platform requirements

- Smart listening is opt-in, visibly indicated, and stops immediately on the spoken stop phrase or the user's stop control.
- Explain the microphone behavior and the exact activation/stop phrases on first launch and in a dedicated “EMA Life Assistant” help screen.
- Do not continuously archive raw ambient audio. Process transient audio for speech recognition and retain only the minimum structured information needed for the feature, subject to the user's review and retention controls.
- Do not capture or upload bystander conversations without appropriate notice and consent. The UI must make it clear when the microphone is active.
- Android microphone foreground-service restrictions, notification permission, device-specific battery management, and force-stop behavior must be tested on physical devices. Never promise uninterrupted background operation.

### Implementation status

The current prototype below is **not yet** the full EMA Life Assistant. It now has a basic “به گوش باش” / “شنود رو خاموش کن” command state and can route recognized speech to the existing local food/workout tools. It still uses Android SpeechRecognizer in a restart loop (not a dedicated offline keyword-spotting model), does not guarantee that Persian recognition or speech playback is offline, and does not yet provide authenticated background GapGPT analysis or broad category routing for finance, work, travel, and planning. The onboarding and guided-tour text describe these limits explicitly. Do not advertise the full life-assistant capability as complete until those remaining parts are implemented and tested.
The user starts the voice assistant from the small sound-wave control beside the in-app microphone. Android requests microphone permission and starts `VoiceWakeService` as a microphone foreground service with an ongoing notification and a stop action.

The service listens for “ایما” (also accepts “ایما جان” and “hey ema”), greets the user, and then routes clear food and workout statements through the existing local `PersianAssistantIntentResolver`, `HealthAssistantTool`, and `SportsAssistantTool`. Food and exercise logs are stored locally; no cloud model is needed for those supported commands. For workouts, minutes and explicitly spoken calories are saved. Unrecognized or incomplete commands are not silently written.

Android may use on-device recognition when available. Otherwise the system recognizer can use network services despite the offline preference. The service cannot start listening until the user grants permission and starts it while the app is visible. Force Stop, OS microphone restrictions, battery optimization, or missing Persian recognition/TTS support can interrupt it. This is a foreground, user-controlled prototype rather than a hidden always-on listener.
