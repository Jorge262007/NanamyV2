# NanamyV2

**NanamyV2** is a modern, AI-powered Android launcher and virtual companion interface specifically designed for handheld gaming devices like the **Anbernic RG Rotate**, 1:1 square displays, and standard Android devices.

It seamlessly blends an expressive virtual face with local and cloud AI LLMs, hardware push-to-talk controls, offline wake-word detection, a modular widget system with Slack-style auto-reply messaging, and an embedded web desktop server.

---

## Key Features

### Tailored for RG Rotate & Handheld Consoles
- **Optimized for RG Rotate & 1:1 Displays**: Designed specifically to run on the **Anbernic RG Rotate** and handheld consoles (RG Cube, Retroid, Powkiddy).
- **Dynamic 4-Way Screen Rotation**: Powered by `NanamyRotationLayout` supporting 0°, 90°, 180°, and 270° hardware display and touch coordinate transformations.

### Virtual Eye Interface & AI Companion
- **Animated Eye Interface**: Custom-rendered `EyeFaceView` providing real-time visual feedback during interaction (*Idle, Listening, Thinking, Speaking*).
- **Dual AI Engine**:
  - **Cloud Mode**: High-speed AI response using **Gemini 3.5 Flash Lite** and **Groq** APIs with full tool-calling capabilities.
  - **Offline Mode**: 100% private on-device LLM inference powered by native C++ `llama.cpp` (`GGUF` model support).
- **Voice Controls**:
  - **Hardware Push-to-Talk**: Hold down the **L1** shoulder button to talk; release to instantly process and respond.
  - **Hands-Free Wake Word**: Offline passive hotword detection (*"Nanamy"*) via **Vosk Speech Recognition**.

### Local LLM Model Recommendations (<1B)
For offline local inference on handheld hardware (e.g., Unisoc T618), models under **1B parameters** are strongly recommended for speed and generation fluidity:
- **Most Recommended**: **Qwen 0.5B Q8_0** — Reaches ~**13 tokens/sec**, delivering near-instant responses that preserve conversational immersion.
- **Comparison**: **Llama 3.2 1B Q6_K** — Averages ~**6 tokens/sec**. While functional, the slower generation speed breaks the feeling of immediate response and does not justify the jump from 0.5B to 1B for quick launcher interactions.

### Messages Tab & Smart Auto-Reply Pipeline
- **Slack-Style Two-Way Gestures**:
  - **Swipe Right**: Triggers an automated AI response that drafts a brief, polite, time-buying reply in the conversation's native language.
  - **Swipe Left**: Dismisses the conversation logically from the active widget.
- **Notification Integration**: Captures incoming messages from **WhatsApp** and **Google Messages** using `NotificationListenerService`.
- **Honest RemoteInput Replier**: Direct notification reply injection with fallback options.
- **Avatar & Icon Caching**: Automatically extracts profile photos and source app icons to disk.

### Modular Widget System & Infinite Scrolling
- **Infinite Vertical Scroll**: ViewPager2 widget carousel scrolling endlessly in both directions.
- **Drag-to-Reorder**: Reorder widgets via touch-drag handles in Settings.
- **Integrated Widgets**:
  - **Music Player**: Active media session controls.
  - **Weather**: Dynamic forecast display.
  - **Calendar**: Event scheduler with exact alarm reminders.
  - **Notes**: On-device note-taking system.
  - **Messages**: Live messaging hub with AI auto-replies.

### NanamyOS Embedded Web Server
- **Embedded NanoHTTPD Server**: Serves a full web desktop UI over Wi-Fi, Hotspot, or USB tethering, allowing full desktop management and AI interaction.

---

## Tech Stack & Architecture

- **Language**: Kotlin & C++17 (NDK)
- **UI Framework**: Android ViewBinding, Material Components 3, ViewPager2, RecyclerView
- **AI & NLP**: Google Gemini API, Groq OpenAI API, Native `llama.cpp` (OpenCL / ARM NEON), Vosk Android SDK
- **Database**: Room Database (SQLite with Flow streaming and migration support)
- **Asynchrony**: Kotlin Coroutines & StateFlow / SharedFlow
- **Networking**: OkHttp 4, NanoHTTPD

---

## Getting Started

1. **Clone Repository**:
   ```bash
   git clone https://github.com/Jorge262007/NanamyV2.git
   ```
2. **Open in Android Studio**: Open the project in Android Studio.
3. **Build & Run**:
   ```bash
   ./gradlew assembleDebug
   ```

---

## License

Distributed under the MIT License. See `LICENSE` for more information.
