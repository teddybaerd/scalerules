# Compile SDK configuration

`androidCompileSdk=37` and `androidCompileSdkMinor=2` selects the SDK used to compile the app. `androidApiLevel=36` continues to select targetSdk and the Makefile emulator API; minSdk remains 26. Build-tools remain at the existing configured version.

CI foundation, signed APK builds, security inventory and `make androidsdk` install the compile SDK platform. The integration Dockerfile also installs API 37.2 while retaining its existing emulator platform. Rebuild existing Docker images before using the new configuration.

This prerequisite does not merge the dependency upgrades in PRs #7 and #9. They need fresh CI after this change reaches their branches. Further compiler or lint failures may become visible once AAR metadata validation succeeds.

Google publishes the platform as `platforms;android-37.2`, not `platforms;android-37`. Gradle pins the matching minor API with the expanded compileSdk DSL.
