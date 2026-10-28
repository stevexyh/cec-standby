# Development

Requires JDK 17, Android SDK platform 36 and build-tools 36.0.0. Set JAVA_HOME and optionally ANDROID_HOME. Run `./build.sh`; it signs a non-debuggable APK. The development key is created under ignored `.keys/`. Production signing must use a stable externally supplied key; never commit it.

Build and tests must not call ADB. Package migration baseline is 0.1.0 / code 1, derived from the in-tree 0.6.3 prototype. Configuration milestone is 0.2.0 / code 2. There are no published APK releases yet.

Use synthetic parser fixtures. Review `git diff --cached` and scan staged files before pushing. Preserve upstream MIT attribution. Never include local test reports or signing materials in a commit.
