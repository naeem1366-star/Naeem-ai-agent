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

## Always-ready voice assistant

The user starts the voice assistant from the small sound-wave control beside the in-app microphone. Android requests microphone permission and starts `VoiceWakeService` as a microphone foreground service with an ongoing notification and a stop action.

The service listens for “ایما” (also accepts “ایما جان” and “hey ema”), greets the user, and then routes clear food and workout statements through the existing local `PersianAssistantIntentResolver`, `HealthAssistantTool`, and `SportsAssistantTool`. Food and exercise logs are stored locally; no cloud model is needed for those supported commands. For workouts, minutes and explicitly spoken calories are saved. Unrecognized or incomplete commands are not silently written.

Android may use on-device recognition when available. Otherwise the system recognizer can use network services despite the offline preference. The service cannot start listening until the user grants permission and starts it while the app is visible. Force Stop, OS microphone restrictions, battery optimization, or missing Persian recognition/TTS support can interrupt it. This is a foreground, user-controlled prototype rather than a hidden always-on listener.
