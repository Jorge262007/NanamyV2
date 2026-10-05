# Implementation Plan - Local LLM Fallback (llama.cpp)

Implement a local LLM fallback for NanamyLauncher using `llama.cpp` via JNI. This will allow the voice assistant to work offline using a small GGUF model (Qwen 2 0.5B).

## User Review Required

> [!IMPORTANT]
> - **NDK & CMake:** This implementation assumes the Android NDK and CMake are installed in your environment.
> - **llama.cpp Source:** We will set up the CMake to include `llama.cpp`. You will need to ensure the `llama.cpp` source code is available in your project (e.g., as a git submodule or copied into `app/src/main/cpp/llama.cpp`).
> - **Model Path:** The default model path will be `/storage/emulated/0/Documents/NanamyOS/models/qwen2_05b.gguf`.

## Proposed Changes

### [Component] Project Configuration

#### [MODIFY] [build.gradle.kts](file:///D:/Android/Nanamy/app/build.gradle.kts)
- Add `externalNativeBuild` configuration for CMake.
- Configure `ndk` abiFilters for `arm64-v8a`.

### [Component] Native Layer (JNI)

#### [NEW] [CMakeLists.txt](file:///D:/Android/Nanamy/app/src/main/cpp/CMakeLists.txt)
- Define the build process for `libllama.so`.
- Link `llama.cpp` core.

#### [NEW] [llama-android.cpp](file:///D:/Android/Nanamy/app/src/main/cpp/llama-android.cpp)
- Implement JNI functions: `loadModel`, `freeModel`, `completion`.
- Handle token streaming and context management.

### [Component] Local LLM Engine (Kotlin)

#### [NEW] [LocalLlmEngine.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/localllm/LocalLlmEngine.kt)
- Kotlin wrapper for native calls.
- Use `StateFlow` or callbacks for token streaming.
- Lifecycle management (unload when not in use).

### [Component] Settings & UI

#### [MODIFY] [NanamySettingsRepository.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/NanamySettingsRepository.kt)
- Add `KEY_LOCAL_LLM_ENABLED` and `KEY_LOCAL_LLM_MODEL_PATH`.

#### [MODIFY] [activity_settings.xml](file:///D:/Android/Nanamy/app/src/main/res/layout/activity_settings.xml)
- Add the "Local LLM Fallback" toggle and "Import Model" button.

#### [MODIFY] [SettingsActivity.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/SettingsActivity.kt)
- Implement logic for the toggle (with warning dialog) and file picker.

### [Component] Voice Assistant Integration

#### [MODIFY] [VoiceAssistantManager.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/voice/VoiceAssistantManager.kt)
- Add connectivity check and fallback logic.
- Implement a simplified system prompt for local mode.

## Verification Plan

### Automated Tests
- N/A (Manual verification on hardware is required for JNI/LLM performance).

### Manual Verification
1. **Model Loading:** Check if the GGUF model loads correctly via logs.
2. **Offline Search:** Turn off Wi-Fi and verify the assistant responds using the local LLM.
3. **UI Feedback:** Verify the assistant says "modo local limitado" when fallback is active.
4. **Settings:** Verify the toggle shows a red warning and the file picker saves the correct path.
