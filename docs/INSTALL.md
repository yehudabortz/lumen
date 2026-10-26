# Install Lumen on your TV

## 1. Check your TV

In Settings, look for **About** or **System → About**. You need **Android TV or Google TV, Android 8.0 or newer**, regardless of brand. The TV must allow installing APK files.

Samsung Tizen, LG webOS, Roku cannot install Lumen directly. Connect an Android TV/Google TV box or stick to HDMI instead. Fire TV and other modified Android systems are not currently supported.

## 2. Get the app

Open [Releases](https://github.com/yehudabortz/lumen/releases) on your phone or computer. Download the `.apk` attachment, not “Source code.” If the page is unavailable, the repository may still be private; you need an account with access.

You can use the APK as-is. No developer tools, account inside Lumen, subscription or API key is needed.

## 3. Send it to the TV and install

Choose whichever route your devices support:

- **Phone or computer:** install [Send Files to TV](https://www.sendfilestotv.app/) on both devices, connect them to the same network, receive the APK on the TV and open it. This is a separate third-party transfer app.
- **USB drive:** copy the APK to the drive, plug it into the TV and open it with a file manager. Not every TV/streaming device has a usable USB port.
- **Computer with ADB:** use the commands below.

If Android blocks installation, allow **Install unknown apps** for the specific app opening the APK. Older TVs call this **Unknown sources**. Look under **Apps → Special app access**, or **Device Preferences → Security & restrictions**. If the option is locked, enable Developer options as described below and try again. Managed-device restrictions may still prevent installation.

Choose **Install**, then **Open**. If Lumen asks to read TV listings, allow it to show recommendations; you can decline and still use the app dock.

## 4. Make it yours

Use the remote's arrows and OK to move and select. Hold OK on an app to move it or change favorites.

Press Up to the clock/weather bar, then OK for Settings. Search for your city under **Location**; choose °F or °C under **Units**. NASA photos are the default on a fresh install and need no setup. Back from the home dock opens the screensaver; Back again returns Home.

## 5. Choose your Home screen

Look in Android Settings for **Default apps → Home app** and choose Lumen. Not all firmware exposes this setting or allows another launcher to remain the default.

With ADB connected, try:

```sh
adb shell cmd package set-home-activity dev.lumen.tv/.HomeActivity
adb shell input keyevent KEYCODE_HOME
```

If the original Home returns, your firmware is overriding the choice. Lumen can still be opened from Apps. Use the [AI setup prompt](AI-SETUP.md) for model-specific help. Do not disable the stock launcher without a working fallback and tested recovery commands.

## Computer / ADB route

Install Google's [Platform Tools](https://developer.android.com/tools/releases/platform-tools). Keep the TV and computer on the same local network.

Enable Developer options by selecting **Build** about seven times in About; some TVs call it **Android TV OS build**. Then enable **ADB debugging**, **USB debugging**, **Network debugging** or **Wireless debugging**, whichever your TV provides.

- With **Wireless debugging**, use the TV's **Pair with pairing code** screen: run `adb pair TV_IP:PAIRING_PORT`, enter the code, then `adb connect TV_IP:DEBUG_PORT`. The pairing and connection ports can differ.
- With legacy **network ADB**, run `adb connect TV_IP:5555` and approve the TV's authorization prompt.
- If neither is available, use the APK-transfer route above. A random USB port on a TV is not necessarily an ADB port.

Replace the placeholders with values shown on your TV. Do not put them in the repo or share unredacted screenshots. See Google's [ADB connection guide](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi) if connection fails.

```sh
adb devices
adb install -r Lumen.apk
adb shell am start -n dev.lumen.tv/.HomeActivity
```

Use the downloaded APK's actual filename. For a local build, install `out/Lumen.apk`. If several devices are connected, add `-s DEVICE_SERIAL` after `adb`. Turn debugging off when finished if you do not need it.

## Switch back or fix an installation

- **Return to the original Home:** choose it in Default apps. Before changing defaults with ADB, record the installed Home components using `adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME`. Restore the original with `adb shell cmd package set-home-activity ORIGINAL_PACKAGE/ACTIVITY`.
- **Remove Lumen:** uninstall it from Android's Apps settings, or run `adb uninstall dev.lumen.tv`. This deletes Lumen's own settings/cache, not other apps.
- **Update fails with a signature error:** use an APK from the same publisher/signing key. A self-built APK cannot update an officially signed one; uninstalling first works but resets Lumen's settings.
- **No recommendations:** grant TV-listings access if offered. Apps must publish recommendations; some do not, and some require sign-in within that app.
- **APK will not install / unknown-source option is unavailable:** check the Android version, installer permission and device-management restrictions. Firmware can block sideloading or custom Home apps; there is no universal override.
