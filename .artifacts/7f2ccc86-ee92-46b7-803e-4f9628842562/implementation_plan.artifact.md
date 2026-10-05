# Implementation Plan - Fix NanamyOS Server and Assets

The current NanamyOS integration is broken because the `index.html` asset in the project is a truncated or modified version, not the original working file. Additionally, the `NanamyOsServer` serving logic needs to be more robust regarding MIME types and file serving.

## Proposed Changes

### Assets

#### [MODIFY] [index.html](file:///D:/Android/Nanamy/app/src/main/assets/nanamyos/index.html)
- Replace the current content with the exact content from the original `index.html` provided in the downloads.
- This restores the full UI, logic, and Spanish translations as intended.

### App Module

#### [MODIFY] [NanamyOsServer.kt](file:///D:/Android/Nanamy/app/src/main/java/com/nanamy/launcher/NanamyOsServer.kt)
- Update `getMimeTypeFromFileName` to include `charset=utf-8` for HTML files.
- Improve `serveAsset` to use `newFixedLengthResponse` when possible for the main `index.html` to ensure `Content-Length` is sent.
- Fix `handleSaveFile` and `handleUiSave` to correctly read the post body (handling the case where `NanoHTTPD` stores the body in a temporary file).
- Ensure no "activation locks" or 404 guards from the original M5Stack firmware are present (confirmed absent, but will double-check during implementation).

## Verification Plan

### Manual Verification
- Deploy the app to a device.
- Connect via USB tethering.
- Open the server IP in a PC browser (e.g., `192.168.42.129:8080`).
- Verify that the desktop icons (including fixed ones like Trash) appear.
- Verify that the context menu works (right-click on desktop).
- Verify that the fullscreen button and start menu work.
- Check the browser DevTools Console for any JS errors.
- Check the Network tab to confirm all `/api/...` calls return 200 OK with correct JSON shapes.
