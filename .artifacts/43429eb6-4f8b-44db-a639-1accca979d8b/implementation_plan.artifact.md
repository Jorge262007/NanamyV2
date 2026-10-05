# Plan de Limpieza y Consolidación de Búsqueda (Brave)

Eliminaremos los restos del motor LLM local experimental (basado en `llama.cpp`) y cualquier referencia a motores de búsqueda que no sean Brave, para simplificar el sistema y prepararlo para una nueva implementación de LLM local.

## User Review Required

> [!IMPORTANT]
> - Se eliminará la configuración de **Native Build (JNI/C++)** del proyecto.
> - Se eliminará la sección experimental de **Local LLM** en los ajustes.
> - Se confirmará que el navegador utiliza exclusivamente **Brave Search** a través del proxy interno.

## Proposed Changes

### [Component] Android Build System

#### [MODIFY] [build.gradle.kts](file:///D:/Android/Nanamy/app/build.gradle.kts)
- Eliminar los bloques `externalNativeBuild` y `ndk` que configuran CMake y llama.cpp.

### [Component] Native Engine (llama.cpp)

#### [DELETE] [CMakeLists.txt](file:///D:/Android/Nanamy/app/src/main/cpp/CMakeLists.txt)
#### [DELETE] [llama-android.cpp](file:///D:/Android/Nanamy/app/src/main/cpp/llama-android.cpp)

### [Component] UI & Settings

#### [MODIFY] [activity_settings.xml](file:///D:/Android/Nanamy/app/src/main/res/layout/activity_settings.xml)
- Eliminar la sección `EXPERIMENTAL / LOCAL LLM` (MaterialCardView y TextView asociados).

## Verification Plan

### Automated Tests
- Ejecutar `./gradlew assembleDebug` para asegurar que el proyecto sigue compilando sin la configuración nativa.

### Manual Verification
1. **Settings:** Verificar que la opción "Local LLM Fallback" ya no aparece en los ajustes.
2. **Browser:** Verificar que al realizar una búsqueda desde `NanamyOS` se utiliza Brave Search.
