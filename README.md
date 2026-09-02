# LiveMeetingNotes

Android app in Kotlin for continuous background speech capture, live transcript rolling summaries, and local note storage.

## What this project includes

- Foreground microphone service that keeps listening while the app is minimized or the screen is off.
- Bluetooth microphone routing support using `AudioManager` and SCO / communication-device routing.
- On-device speech recognition via Android `SpeechRecognizer` with continuous restart logic.
- Rolling transcript chunk persistence in Room.
- AI summarization + action-item extraction via DeepSeek or Qwen chat-completions compatible endpoints.
- Encrypted local storage for the user's API key via `EncryptedSharedPreferences`.
- Compose UI showing today's note, action items, previous dates, and a start/stop listening toggle.

## Important limitations

- Android's built-in `SpeechRecognizer` is session-based rather than a raw PCM streaming API. This app keeps a near-continuous transcript by automatically restarting recognition in a foreground service.
- True cloud STT streaming (e.g. Whisper / Deepgram / Azure Speech websocket audio uplink) is not implemented in this first version. The AI API in this project is used for summarization and action-item extraction from transcript chunks.
- This machine can build the app through the bundled Android Studio JBR + local Gradle distribution scripts in this folder. A debug APK and release APK have been built successfully from this project.

## Setup

1. Open `android-live-notes/` in Android Studio.
2. If you want to rebuild from Windows without Android Studio, run one of the helper scripts in this folder such as `run-assemble-debug.cmd`, `run-assemble-release.cmd`, `run-test-debug.cmd`, or `run-lint-debug.cmd`.
3. Run on a physical device with microphone + Bluetooth permissions.
4. On first launch, enter the AI provider and API key in the app and save.
5. Grant microphone, notification, and Bluetooth permissions.

## Recommended next upgrade

If you want truly continuous microphone streaming instead of session-based `SpeechRecognizer`, swap `SpeechTranscriber` for a PCM recorder + websocket STT provider. The rest of the app (database, summarizer, UI, service orchestration) is already separated to make that replacement straightforward.
