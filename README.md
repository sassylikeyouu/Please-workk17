# MineHost

MineHost is an Android app for hosting and managing a real Minecraft Bedrock-compatible server locally on the phone.

This project now includes the complete MineHost Jetpack Compose interface, navigation, and local management tools while preserving the existing server runtime.

## What works locally

- Install/start/stop/restart the existing local Bedrock server runtime
- Real server status, local IP address, configured UDP port, engine, memory, and live logs
- Send real console commands while the server is running
- Read, create, edit, rename, import, and delete files inside the server directory
- Scan/import/enable/disable compatible local `.jar` plugins while the server is stopped
- Edit supported `server.properties` values
- Create, restore, and delete local ZIP backups
- Import and switch local worlds while the server is stopped
- Read server-process CPU, RAM, storage, uptime, parsed players, and parsed TPS when available
- Local activity history, crash/log analysis, server health, recommendations, and safe optimization
- Local notification preferences, crash auto-restart, and scheduled local backups
- A local rules-based assistant that analyzes real app state and logs without claiming to be a cloud AI service

## Honest limitations

The app still controls one local server profile. A real hosted account, billing/subscription service, online marketplace catalog, cloud AI provider, and production remote-tunnel service are not included because no external backend or credentials were provided. Those screens clearly show their actual local/unavailable state rather than simulated results.

Player and TPS data depend on messages emitted by the selected server engine. If the engine does not print a parseable event or TPS value, the UI shows an empty state or `—`.

## Open in Android Studio or AI Studio

1. Extract the ZIP.
2. Open the project root, the folder containing `settings.gradle.kts`.
3. Let Gradle sync.
4. Run `./gradlew assembleDebug` or use the IDE **Run** action.
5. Install the debug APK on a real ARM64 Android device for server-runtime testing.

A standard debug keystore is included because this project has an explicit debug signing configuration.

## AI Studio instruction

Use this source as the new project base. Do not regenerate or simplify the Compose UI. Run a build first and only fix genuine compiler/import issues without changing the design or server architecture.
