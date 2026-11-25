# ایما

Ima — a Persian-first intelligent assistant built with Kotlin and Jetpack Compose.

## Included

- Persian-first chat interface
- OpenAI-compatible chat completions client
- Configurable API base URL and model
- On-device encrypted storage for the API key
- Built by Naeem Dehghani

## Build

GitHub Actions builds the debug APK on pushes to `main` and on manual workflow dispatch. The APK is uploaded as an artifact named `Ima-debug`.

## Current limitations

- The API key is stored encrypted on the device; a backend is recommended before public distribution.
- Chat history is currently kept in memory and is cleared when the app process ends.
- Image, voice, file upload, account sync, and signed release builds are not included yet.
