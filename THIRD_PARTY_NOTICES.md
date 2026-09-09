# Third-party notices

## Gradle wrapper

The Gradle wrapper scripts and JAR are distributed by the Gradle project under the Apache License, Version 2.0. Their existing notices are retained. A copy of the license is included at [docs/licenses/Apache-2.0.txt](docs/licenses/Apache-2.0.txt).

The wrapper distribution version and checksum are pinned in `gradle/wrapper/gradle-wrapper.properties`.

## Dependencies

The app resolves Kotlin, kotlinx.coroutines, AndroidX Compose, Activity, Lifecycle, Room, and Android test libraries through Gradle. Tests also use JUnit. These components remain subject to their respective upstream licenses and notices; see the dependency declarations in `app/build.gradle.kts` and `core/build.gradle.kts`.

## Simple Time Tracker

Currency interoperates with [Simple Time Tracker](https://github.com/Razeeman/Android-SimpleTimeTracker), maintained by Razeeman and its contributors. STT is a separate application, is not bundled here, and is not affiliated with Currency. Its source was consulted to understand the backup protocol; see the references in [integration documentation](docs/integration.md).

## Artwork

The clock-and-coin launcher artwork was generated for Currency with OpenAI image generation. The matching monochrome Android vector is included in the app resources.
