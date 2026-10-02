# Third-party notices

VArchive uses the following third-party components. Each component remains governed by its own license; these notices do not change the licensing of the VArchive project itself.

## Archive backends

| Component | Version | License | Use | Source |
|---|---:|---|---|---|
| Apache Commons Compress | 1.28.0 | Apache License 2.0 | 7Z, TAR, AR, CPIO and compressor streams | [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) |
| Zip4j | 2.11.6 | Apache License 2.0 | ZIP/ZIP64 and AES-256 ZIP | [Zip4j](https://github.com/srikanth-lingala/zip4j) |
| XZ for Java | 1.12 | 0BSD | XZ/LZMA stream support | [XZ for Java](https://tukaani.org/xz/java.html) |
| zstd-jni | 1.5.7-12 | BSD 2-Clause; bundled Zstandard is BSD 3-Clause or GPL-2.0 dual-licensed | Zstandard streams and Android JNI | [zstd-jni](https://github.com/luben/zstd-jni) |
| Junrar | 8.1.1 | UnRAR License | RAR/RAR5 reading and extraction only | [Junrar](https://github.com/junrar/junrar) |

### Apache Commons Compress notice

> Apache Commons Compress  
> Copyright 2002-2025 The Apache Software Foundation  
> This product includes software developed at The Apache Software Foundation (https://www.apache.org/).

Junrar's UnRAR terms prohibit using the code to recreate the proprietary RAR compression algorithm. VArchive contains no RAR writer and exposes RAR/RAR5 only for browse, test, and extraction operations.

## Android and application libraries

VArchive also depends on AndroidX, Jetpack Compose, Material components, Kotlin coroutines, SLF4J, and their transitive dependencies. Their exact resolved versions are controlled by `app/build.gradle.kts` and the Compose BOM. AndroidX, Compose, Kotlin, and coroutine artifacts are distributed under Apache License 2.0; SLF4J is distributed under the MIT License.

Test-only dependencies include JUnit, AndroidX Test, Espresso, and UI Automator under their respective upstream licenses.

Upstream license texts and copyright notices remain authoritative. A resolved dependency report can be generated with:

```bash
./gradlew :app:dependencies
```
