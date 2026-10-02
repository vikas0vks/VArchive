# Archive engine

VArchive deliberately combines focused, mature libraries behind one Kotlin `ArchiveEngine` boundary. No compression algorithm is reimplemented by the application.

## Integrated libraries

| Library | Version | License | Responsibility | Integration |
|---|---:|---|---|---|
| Apache Commons Compress | 1.28.0 | Apache-2.0 | 7Z, TAR, AR, CPIO and compressor streams | Maven JVM library |
| Zip4j | 2.11.6 | Apache-2.0 | ZIP/ZIP64 and AES-256 ZIP | Maven JVM library |
| XZ for Java | 1.12 | 0BSD | XZ/LZMA stream support used by Commons Compress | Maven JVM library |
| zstd-jni | 1.5.7-12 | BSD-2-Clause; bundled Zstandard is BSD-3-Clause or GPL-2.0 dual-licensed | Zstandard stream support | Published Android AAR, arm64-v8a packaged |
| Junrar | 8.1.1 | UnRAR License | RAR and RAR5 parsing/extraction only | Maven JVM library |

Junrar's license permits handling RAR archives but prohibits using its code to recreate the proprietary RAR compression algorithm. VArchive therefore exposes RAR/RAR5 as browse/extract/test only and contains no RAR writer.

## Engine routing

- ZIP creation/listing/extraction/testing routes through Zip4j. AES uses 256-bit keys.
- 7Z uses Commons Compress random-access readers/writers. Passwords are accepted for reading; encrypted 7Z creation is not exposed.
- TAR and TAR combined with GZIP, BZIP2, XZ, or ZSTD use streaming Commons Compress APIs.
- GZIP, BZIP2, XZ, and ZSTD are semantically single-stream formats: creation requires exactly one source file.
- RAR/RAR5 uses Junrar with a 256 MiB maximum dictionary setting. Redirection/link headers are blocked.
- AR and CPIO use Commons Compress stream readers. A DEB can be opened as its outer AR container; nested payload expansion is a separate user operation.

## Security boundaries

Before writing any archive entry, VArchive:

1. converts backslashes to separators;
2. rejects absolute paths, Windows drive paths, NUL/control characters, `..`, and empty paths;
3. resolves the target canonically beneath an app-private extraction root;
4. blocks TAR symbolic/hard links and RAR redirections;
5. enforces configurable bomb checks (100,000 files and 50 GiB declared uncompressed size by default);
6. copies the validated private tree to the granted SAF destination using the selected conflict policy.

The private extraction directory is removed on completion, failure, or cancellation. Archive work uses 64 KiB bounded buffers and `Long` byte counts. Passwords are not logged or serialized into WorkManager's persistent input data.

## Native safety

Only zstd-jni contributes native code in this build. Its Android AAR owns JNI loading and resource lifetime. VArchive does not pass native pointers across its architecture boundary. The initial APK intentionally packages only `arm64-v8a`, matching the verified device. Commons Compress, Zip4j, XZ, and Junrar execute as managed Java/Kotlin code.
