# Supra AI

A Persian-first Android AI assistant built with Kotlin and Jetpack Compose.

## Included

- Persian-first chat interface
- OpenAI-compatible chat completions client
- Configurable API base URL and model
- On-device encrypted storage for the API key
- GitHub Actions workflow to build a debug APK

## Build

GitHub Actions builds the debug APK on pushes to `main` and on manual workflow dispatch. The APK is uploaded as an artifact named `SupraAI-debug`.

## Current limitations

- The API key is stored encrypted on the device; a backend is recommended before public distribution.
- Chat history is currently kept in memory and is cleared when the app process ends.
- Image, voice, file upload, account sync, and signed release builds are not included yet.
