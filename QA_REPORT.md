# VArchive 1.0.1 QA report

**Date:** 2026-10-02 (Asia/Calcutta)  
**Device:** Xiaomi/Poco 22101320I (Poco X5 Pro 5G)  
**Android:** 16 / API 36  
**ABI:** arm64-v8a  
**Display:** 1080 × 2400, effective density 384 dpi

## Build gates

| Gate | Result |
|---|---|
| Debug compile/package | PASS |
| JVM unit/integration tests | PASS — 19/19 |
| Android lint | PASS — zero errors |
| Device instrumentation | PASS — 4/4 |
| R8/resource-shrunk release | PASS |
| APK v2 signature verification | PASS |
| Release install | PASS |
| Release cold launch | PASS |
| Post-SAF-permission cold launch | PASS |
| Adaptive launcher icon and in-app branding | PASS |
| Filtered AndroidRuntime/WorkManager error log | Clean |

Commands used:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

## Automated device coverage

The connected-device suite executed against the actual Android runtime:

- app launch, Home, Files, Settings, About, and creator branding;
- full-color adaptive launcher icon, splash resource, Home logo, About logo, and v1.0.1 version presentation;
- ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, TAR.ZST, GZIP, BZIP2, XZ, and ZSTD create/test/extract round trips;
- AES-256 ZIP correct-password extraction and wrong-password domain failure;
- corrupt ZIP rejection without process crash;
- RAR5 browse, full integrity read, and extraction using the Junrar upstream 130-byte test fixture.

## Manual SAF and queue flow

Using `/sdcard/Download/VArchiveTest/` only:

1. Selected `varchive-device.txt` through Android DocumentsUI.
2. Created `Archive.zip` through `ACTION_CREATE_DOCUMENT`.
3. Observed serialized WorkManager task completion (`Archive created`).
4. Opened the produced ZIP and browsed its entry without full extraction.
5. Ran `Test Archive` and observed `Archive OK`.
6. Granted a destination tree through `ACTION_OPEN_DOCUMENT_TREE`.
7. Extracted through the background queue; rename conflict policy produced `varchive-device (1).txt`.
8. Confirmed source and extracted bytes were both 5,693 bytes.
9. Repeated archive creation on the minified release build and observed `Archive created`.
10. Force-stopped and cold-launched release after persisted document permissions were present.

## Bugs found and fixed during QA

- A newer zstd Android AAR required compile SDK 37; pinned the newest validated compile-SDK-36-compatible AAR.
- Compose's coroutine-test ServiceLoader path failed on this Android build; UI smoke coverage was moved to Android UI Automator while retaining backend instrumentation.
- Persisted single-document grants were incorrectly treated as folder-tree grants on restart; invalid tree URIs are now safely filtered.
- A completed archive test could leave the archive browser over the Tasks destination; enqueue now closes the browser and exposes task state.
- Newly granted folders were not refreshed until restart; entering Files now refreshes persisted roots.
- Extraction showed a provider document ID instead of the user-visible filename; it now resolves the display name through `ContentResolver`.

## APK evidence

| Artifact | Size | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 21,800,987 bytes | `0F29BC2D08EFEAE29060949EF04D8083ECAFC18E3931DF7EBE89033BFFB9F4F9` |
| `app/build/outputs/apk/release/app-release.apk` | 2,739,500 bytes | `C5AC15E79B6E363D9B9DD323A632597E3393E827B145CCC0473F2D705470C712` |

The release is R8/resource-shrunk, arm64-v8a-only, signature-verified, and directly installable as the official VArchive Stable APK.
