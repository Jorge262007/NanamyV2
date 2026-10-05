# Fix "cannot find symbol class EyeFaceView"

The compilation error occurs because the layout `fragment_eyes.xml` references `com.nanamy.launcher.eyes.EyeFaceView`, but this class is missing from the project. The file `EyeFaceView.kt` exists but contains a duplicate of `LookAssistant` instead of the `View` implementation.

## Proposed Changes

### eyes package

#### [MODIFY] [EyeFaceView.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/eyes/EyeFaceView.kt)
Replace the existing `LookAssistant` class with the actual `EyeFaceView` custom view implementation. This view will use `LookAssistant`, `BlinkAssistant`, and `EyeDrawer` to render the eyes.

#### [MODIFY] [LookAssistant.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/eyes/LookAssistant.kt)
Fix the package name from `app.lawnchair.eyes` to `com.nanamy.launcher.eyes` to match its directory structure and allow it to be used by `EyeFaceView`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugJavaWithJavac` to verify the "cannot find symbol" error is resolved.
- Run a full build: `./gradlew assembleDebug`.

### Manual Verification
- Deploy the app to a device or emulator and check if the eyes are rendered correctly in the `EyesFragment`.
