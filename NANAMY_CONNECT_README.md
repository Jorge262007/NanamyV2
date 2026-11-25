# Nanamy Connect ⚡📱

**Nanamy Connect** is a futuristic, Tron-themed companion and remote control application designed for Android smartphones to connect, sync, and control **Nanamy Launcher** (NanamyV2) remotely over local Wi-Fi, Hotspot, or ADB.

---

## 🌌 Theme & Design Specs

- **Aesthetic**: Tron Cyberpunk / Neon Grid Futuristic UI
- **Primary Color Palette**:
  - Neon Cyan (`#00E5FF`)
  - Cyber Violet / Magenta (`#7C4DFF` / `#FF007F`)
  - Obsidian Dark Background (`#0A0E17`)
- **Orientation**: Fixed Horizontal / Landscape mode (`screenOrientation="landscape"`).
- **Animations**:
  - **Tron Grid Launch Animation**: Fluid startup sequence with neon line sweep and glowing grid expansion.
  - **Dynamic Pulse & Glow**: Real-time pulsing status indicators for connection state.

---

## ⚡ Key Features

1. **Smartwatch-Style Notification Relay**:
   - Includes an active `NotificationListenerService` (`NanamyNotificationRelayService`).
   - Captures incoming phone notifications (WhatsApp, Telegram, SMS, System, etc.) and streams them remotely to Nanamy Launcher's Messages Widget in real-time via `/api/notifications/relay`.

2. **Remote Nanamy Settings Management**:
   - Synchronizes and edits Nanamy Launcher preferences in real-time via `/api/settings/get` and `/api/settings/update`.
   - Controls AI Provider (Gemini / Groq), API keys, Voice speed/pitch, Eye themes, and Active Widgets directly from the phone.

3. **ADB & Network Remote Control**:
   - PING & Status diagnostic tool (`/api/control/action`).
   - Remote Media Playback controls (Play/Pause, Track Skip).

4. **Permissions & Security**:
   - Notification Listener Access (`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`).
   - Network & Internet Access (`android.permission.INTERNET`, `ACCESS_NETWORK_STATE`).
   - Storage & Accessibility permissions (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`).

---

## 🛠️ Tech Stack

- **Language**: Kotlin & Android Jetpack
- **UI Framework**: Material Components 3 + Custom Canvas Grid Rendering & ViewBinding
- **Networking**: OkHttp 4 & Coroutines
- **Orientation**: Forced Landscape (`landscape`)
