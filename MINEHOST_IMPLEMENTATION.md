# MineHost implementation handoff

## UI architecture

```text
MainActivity
  -> MineHostTheme
  -> MineHostNavHost
     -> five root destinations
        Dashboard / Servers / Plugins / Files / Profile
     -> server detail tabs
        Overview / Console / Players / Settings / More
     -> More tools
        Activity / Local Assistant / Recommendations / Crash Analysis /
        Server Health / Auto Optimization
```

Reusable MineHost components live under `app/src/main/java/com/example/ui/components/`. All major screens use shared colors, typography, glass cards, status badges, buttons, headers, metric cards, and bottom navigation.

## Runtime architecture

```text
Compose screen
  -> MainViewModel
  -> LocalServerDataService or ServerManager
  -> current ServerEngine
  -> real local server process and server directory
```

The existing server engine was preserved. Small controlled additions expose the process ID, start time, real restart, configured memory, and configured port so the new UI can show and control actual state.

## Features connected to real local state

- Dashboard status and process metrics
- Server Overview controls and address copy
- Live Console logs, search, clear, and command sending
- Player list parsed from real join/leave console messages
- Server settings and `server.properties`
- Read/write local file manager
- Local plugin folder scanner/import/toggle
- Local backups and restore
- Local world import/list/switch
- Engine/version selection from the existing template registry
- Process CPU/RAM/storage/uptime monitoring
- Crash/error extraction from real logs
- Health score and recommendations derived from real local state
- Local assistant derived from real logs/settings/metrics
- Local server notifications
- Up to three crash auto-restart attempts
- Optional six-hour local backups while MineHost is active

## External services not fabricated

- Account/subscription: not connected
- Online marketplace downloads: no trusted catalog/API supplied
- Cloud generative AI: not connected; current assistant is explicitly local and rules-based
- Production remote tunneling: not configured
- Multiple simultaneous server profiles: not implemented by the existing runtime

## Safety rules implemented

- File paths are canonicalized and constrained to the active server directory.
- ZIP imports reject path traversal entries.
- Server settings, plugin toggles, world switching, backup restore, and engine changes require the server to be stopped where necessary.
- A backup is created before the safe optimization profile is applied.
- Unsupported values are not replaced with fake metrics or fake success states.

## Build verification performed in this handoff

- Gradle wrapper JAR structure and manifest were repaired and validated.
- Android manifest and all resource XML files were parsed successfully.
- Kotlin source was parsed with the local Kotlin compiler; no syntax/parser errors were found.
- Drawable references and packaged resources were checked.
- A full Android Gradle build could not be completed in the artifact container because it cannot resolve the public Gradle distribution host. Run `./gradlew assembleDebug` in AI Studio/Android Studio and fix only genuine compiler errors without redesigning the UI.
