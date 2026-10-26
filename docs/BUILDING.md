# Build Lumen

Most users can [download the APK](https://github.com/yehudabortz/lumen/releases) and skip building. Use these steps to work on the source or make your own build.

## Requirements

Git, **JDK 21**, Android SDK **build-tools 35.0.0**, **platforms android-34**, and a POSIX shell. No Gradle or third-party Java libraries are required. Use build-tools 35; d8 from build-tools 34 caused crashes.

## macOS

With [Homebrew](https://brew.sh/) installed:

```sh
brew install openjdk@21 android-commandlinetools
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$(brew --prefix)/share/android-commandlinetools"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager 'build-tools;35.0.0' 'platforms;android-34' 'platform-tools'
```

## Linux or Windows

On Linux, install Git and JDK 21 through your distribution, then install Google's [Android command-line tools](https://developer.android.com/studio#command-line-tools-only). Set `JAVA_HOME` to the JDK and `ANDROID_HOME` to the SDK directory, then add their `bin`, `cmdline-tools/latest/bin` and `platform-tools` directories to `PATH`. Run the same `sdkmanager` command above.

On Windows, use [WSL with Ubuntu](https://learn.microsoft.com/en-us/windows/wsl/install), and install the **Linux** JDK and Android tools inside WSL. The build script does not support the native Windows SDK executables. The ready-made APK works without WSL.

## Clone and build

```sh
git clone https://github.com/yehudabortz/lumen.git
cd lumen
./build.sh
sh tests/run-scenic-tests.sh
```

The result is `out/Lumen.apk`. For installation, see [INSTALL.md](INSTALL.md). GitHub authentication is required while the repository is private.

The first build creates `lumen.keystore` and `local.properties`. Keep them private and keep the same key for future updates. A self-built APK has a different signature from the release APK; switching between them requires uninstalling and loses Lumen's settings. No API key is needed or embedded.

For contributions, see [CONTRIBUTING.md](../CONTRIBUTING.md). Android-specific checks: [tests](../tests/README.md). Screenshot capture: [workflow](../tools/screenshots/README.md).
