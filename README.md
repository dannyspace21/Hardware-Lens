# Hardware Lens 📡

An open-source Android app that shows you, live and visually, what your phone's hardware is picking up.

**Download (always the newest build):**
https://github.com/dannyspace21/Hardware-Lens/releases/download/latest/HardwareLens.apk

## What's inside

| Tab | What you see |
|---|---|
| **Sensors** | Sorted into **Motion** (accelerometer, linear acceleration, gravity, gyroscope), **Position** (orientation, magnetometer, proximity), **Environment** (light, barometer + altitude, temperature, humidity) and **Tools** (compass, bubble level, step counter). **All sensors** lists every sensor grouped by type with its specs. Any group can be **recorded to CSV** and shared. |
| **Wireless → Wi-Fi** | Your own connection's signal over time, every nearby network with channel, width, security and Wi-Fi generation, and a channel map for 2.4, 5 and 6 GHz. |
| **Wireless → Bluetooth** | A live radar of nearby devices, with the type recognized (AirTag, AirPods, Tile, SmartTag, iBeacon, fitness gear, keyboards…). Tap one for its signal graph and raw advertisement data. |
| **Wireless → Cellular** | Your carrier, technology (2G–5G), signal strength over time, and every cell tower the phone can hear. |
| **Devices** | Finds things on *your* Wi-Fi: PS4/PS5 (including on or in rest mode), Xbox, TVs, Chromecasts, Sonos, printers, routers, phones and PCs. Tap a device to check its open ports, ping it, or open its web page. |
| **NFC** | Tap any card, key fob, transit pass or sticker to see the chip type, ID, decoded data and raw bytes. Your last 50 scans are kept in **History** and can be shared. |
| **Phone → Battery** | Charge, charging current and watts, temperature graph, health, charge cycles and estimated capacity. |
| **Phone → GPS** | A sky map of every satellite (GPS, Galileo, GLONASS, BeiDou…), signal strengths, your fix and accuracy. |
| **Phone → Camera** | Every camera and lens: megapixels, sensor size, focal length (and full-frame equivalent), aperture, zoom, ISO and shutter range. |

Also: a one-tap **light/dark** button in the top bar, **Settings** (theme, units, Wi-Fi scan interval, keep screen on) and **About** (version, privacy, update check).

### About payment cards
When you tap a credit/debit card, Hardware Lens reads only the card's public **payment directory**, the same first step every tap-to-pay terminal performs. It intentionally does **not** request card numbers or transaction data.

---

## Get the app on your phone

1. Open the download link above on your Android phone.
2. Open `HardwareLens.apk` and allow "Install unknown apps" for your browser when asked.
3. Open the app and **allow the permissions** it asks for. Turn on Location, Wi-Fi, Bluetooth and NFC.

### Updates
- The app checks GitHub when it opens and tells you when a newer version is out (you can turn this off in Settings, or check by hand in About).
- Download the new APK and tap **Update**. **You never need to uninstall:** every build is signed with the same key and has a higher version number, so Android updates it in place and keeps your settings and NFC history.

Every push to the default branch rebuilds the app (Actions tab, ~5 minutes), runs the unit tests, and replaces the file behind the download link. Tagging a release like `v1.0.0` also attaches the APK to that release, plus a Play Store bundle if a release key is set up (see [docs/PLAY_STORE.md](docs/PLAY_STORE.md)).

> `app/debug.keystore` is a throwaway *debug* key, committed on purpose so CI builds stay
> update-compatible. It is not used for Play Store releases.

---

## Things to know
- **Wi-Fi, Bluetooth, cell and GPS** results need Location turned on. That's an Android rule. Android also limits apps to about 4 Wi-Fi scans every 2 minutes.
- **MAC addresses:** since Android 10, apps can't read other devices' hardware (MAC) addresses, and no permission unlocks that without rooting. Hardware Lens shows a MAC whenever a device announces its own (Windows PCs and NAS boxes via NetBIOS, AirPlay speakers, Linux machines), plus the MAC of the Wi-Fi access point you're on, every Wi-Fi network's BSSID, and Bluetooth addresses. It names the maker using the IEEE vendor list, which is downloaded at build time. Private (randomized) addresses are labeled as such.
- **Devices tab:** guest, school, dorm, hotel and office Wi-Fi usually block devices from seeing each other ("client isolation"). There you'll only see the router. The "How it searched" panel shows what each search method found. Android 10+ hides other devices' hardware (MAC) addresses from apps.
- **Bluetooth distance** is an estimate from signal strength; walls and bodies throw it off. Many devices randomize their Bluetooth address, so the same device can show up as "new" over time.
- **Privacy:** nothing is sent anywhere except the optional update check. See [PRIVACY.md](PRIVACY.md).

## Tech details
Kotlin · Jetpack Compose · Material 3 · minSdk 26 (Android 8) · targetSdk 35. Built by GitHub Actions with the Gradle wrapper and JDK 17; unit tests and Android Lint run on every push. To work on it in Android Studio, open the folder, or run `./gradlew testDebugUnitTest assembleDebug`.

## License
MIT
