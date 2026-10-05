# Implementation Plan - LLM Optimization and Response Simplification

Optimize the local LLM performance based on `ChatterUI` patterns and simplify the communication protocol by removing the `[RESPONSE]` and `[LANG:]` requirements for the local engine.

## User Review Required

> [!IMPORTANT]
> - **Threads Configuration:** I've set the threads to 4 by default (matching `ChatterUI`). If your device has more "big" cores, we might want to adjust this, but 4 is usually the sweet spot for mobile efficiency.
> - **Memory Locking:** Enabling `use_mlock` will attempt to lock the model in RAM. This prevents the OS from swapping it out, but requires enough free physical memory.
> - **Prompt Change:** The system prompt will no longer ask the model to wrap text in `[RESPONSE]`. The UI will treat any output from the local LLM as raw text to speak.

## Proposed Changes

### [Component] Native Layer (JNI)

#### [MODIFY] [llama-android.cpp](file:///D:/Android/Nanamy/app/src/main/cpp/llama-android.cpp)
- Enable `mparams.use_mlock = true` for faster access.
- Set `cparams.n_batch = 512` to optimize prompt processing.
- Adjust `cparams.n_threads` and `cparams.n_threads_batch` to 4 (optimal for many mobile chips).
- Increase `cparams.n_ctx = 4096`.

---

### [Component] Settings & Prompts

#### [MODIFY] [NanamySettingsRepository.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/NanamySettingsRepository.kt)
- Update `DEFAULT_LOCAL_SYSTEM_PROMPT` to remove the `[RESPONSE][LANG:es]` wrapping instruction.

---

### [Component] Voice Assistant Logic

#### [MODIFY] [VoiceAssistantManager.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/voice/VoiceAssistantManager.kt)
- Update `callLocalLlm` to handle errors more cleanly without tags.
- Verify `handleAIResponse` handles raw text correctly (it already does by defaulting to 'es').

## Verification Plan

### Automated Tests
- Build verification: Run `:app:assembleDebug` to ensure C++ and Kotlin changes compile.

### Manual Verification
1. **Performance Check:** Measure generation speed in the logs.
2. **UI Check:** Verify the assistant speaks the local LLM response directly without mentioning "Response" or "Language tags".
3. **Offline Test:** Turn off data and verify a 1-sentence response in Spanish is spoken correctly.
