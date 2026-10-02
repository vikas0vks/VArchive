# VArchive v1.0.0

First public release of VArchive, a focused archive manager for Android.

## Highlights

- Create ZIP/ZIP64, 7Z, TAR, TGZ, TBZ2, TXZ, TAR.ZST, GZIP, BZIP2, XZ, and ZSTD archives.
- Browse, test, and extract those formats plus RAR/RAR5, AR/DEB outer containers, and CPIO.
- Create and extract AES-256 encrypted ZIP archives.
- Use Android's Storage Access Framework without broad storage permission.
- Run long operations through a foreground WorkManager queue with progress and cancellation.
- Choose system, light, dark, or AMOLED themes.

## Extraction safety

- Rejects absolute paths, drive paths, control characters, and parent traversal.
- Enforces canonical output-root checks.
- Blocks TAR links and RAR redirections.
- Applies archive entry-count and declared-size limits.
- Keeps passwords out of preferences, logs, and persistent worker data.

## Validation

- 19 JVM unit/integration tests passed.
- 4 connected Android instrumentation tests passed.
- Android lint completed with zero errors.
- Debug and R8/resource-shrunk release builds completed successfully.
- SAF create, browse, test, and extract flows were verified on Android 16.

## Known limitations

- RAR/RAR5 is extraction-only.
- No in-place entry mutation, selected-entry extraction, multipart extraction, pause/resume, split volumes, or solid-archive configuration.
- Password-protected jobs must be retried after application process death.
- The attached APK is signed with an Android development key for testing. It is not production/store signed.

## APK verification

SHA-256: `d7cf6bbc0c94c8b60a364dec70c14e474306cce502957f691c54d9021b0f06ce`

The same value is provided as a separate `.sha256` release asset beside the APK.
