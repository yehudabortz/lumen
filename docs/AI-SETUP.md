# Copy-and-paste setup prompt

Paste this into a coding assistant that can use your computer's terminal. Fill in what you know; “not sure” is fine. Do not include passwords or tokens.

```text
Help me install and configure Lumen on my TV.

Repository: https://github.com/yehudabortz/lumen
TV brand/model: [fill in, or help me find it]
TV operating system/version: [Android TV / Google TV / not sure]
My computer: [Mac / Windows / Linux]
I prefer: [download the ready-made APK / build from source]

Read README.md, docs/INSTALL.md and docs/BUILDING.md before acting.

1. Check compatibility first. Lumen supports Android TV/Google TV on Android 8+;
   do not assume every smart TV or USB port supports it. If my TV runs Tizen,
   webOS, Roku, explain the external Android TV/Google TV device option.
2. Prefer the APK attached to an official GitHub release when I do not need a
   source build. Verify its published SHA-256. If the repo is private, help me
   authenticate through GitHub's normal login; never ask me to paste a token
   or password into the chat.
3. For a source build, clone this repository into a normal development folder:
   git clone https://github.com/yehudabortz/lumen.git
   cd lumen
   Set up JDK 21, Android build-tools 35.0.0 and platform android-34, following
   docs/BUILDING.md for my computer. Use ./build.sh; there is no Gradle build.
   On Windows, use WSL with the Linux tools. Keep generated signing keys and
   local.properties private. The result is out/Lumen.apk.
4. Help me install by APK transfer if that is easiest. If using ADB, identify
   the actual debugging mode and ports shown on my TV. Pairing and connection
   ports can differ; do not assume every TV uses port 5555. Approve the TV's
   authorization prompt and select the correct device if several are connected.
5. Install and open Lumen before changing Home defaults. Check remote navigation.
   Explain the TV-listings permission and why some apps may publish no content.
6. Help me choose my weather city, units and NASA background. No API key or
   location permission is needed. Screensaver setup is optional and depends on
   the firmware; leave the system screensaver unchanged unless I ask to change it.
7. Try the supported Home-app chooser or set-home-activity command. If firmware
   overrides it, explain the model-specific options. Keep the stock launcher
   enabled unless I explicitly approve a change after you have prepared a
   fallback and exact restore commands. Never disable system apps blindly.
8. Test Home and Back, including returning from the screensaver. Finish with
   simple instructions for opening Settings, updating, switching back and
   uninstalling. Turn off debugging afterward if I do not need it.

Keep TV addresses, screenshots, app data, logs, passwords and keys outside the
repository. Do not commit or push setup data. Do not claim it works until the
installed app has been checked on my TV. Ask only for information needed to
choose the correct steps, and explain anything my model cannot support.
```
