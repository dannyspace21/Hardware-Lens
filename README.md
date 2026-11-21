# Hardware Lens 📡

An open-source Android app that shows you, live and visually, what your phone's hardware is picking up.

| Tab | What you see |
|---|---|
| **Sensors** | Live scrolling graphs for the accelerometer, gyroscope, compass, light, proximity and barometer, plus a list of every sensor in the phone. |
| **Wi-Fi** | Every nearby Wi-Fi network with signal strength, channel, band and security, plus a "channel map" showing which networks overlap. |
| **Bluetooth** | A live radar of nearby Bluetooth devices (headphones, watches, phones, trackers) with maker and rough distance. |
| **Devices** | Finds things on *your* Wi-Fi network: PS4/PS5 (including whether it's on or in rest mode), Xbox, smart TVs, Chromecasts, Sonos, printers, routers, Hue bridges. |
| **NFC** | Tap any card, key fob, transit pass or NFC sticker to see the chip type, ID, technologies, decoded data tree and raw bytes. |

### About payment cards
When you tap a credit/debit card, Hardware Lens reads only the card's public **payment directory** — the same first step every tap-to-pay terminal performs. You'll see the network (Visa, Mastercard, Amex…), the payment app IDs and the decoded response. It intentionally does **not** request card numbers or transaction data. Modern cards also use a random chip ID that changes on every tap.

---

## Get the app on your phone

**Direct download (always the newest build):**
https://github.com/dannyspace21/Hardware-Lens/releases/download/latest/HardwareLens.apk

1. Open that link on your Android phone. It downloads `HardwareLens.apk`.
2. Open the file and allow "Install unknown apps" for your browser when asked.
3. Open the app and **allow all permissions** it asks for. Turn on Location, Wi-Fi, Bluetooth and NFC.

Every push to GitHub rebuilds the app automatically (Actions tab, ~5 minutes) and replaces the
file behind that link. Builds are signed with the same key each time, so installing a newer one
updates the app in place instead of complaining.

**Versioned releases:** Releases → Draft a new release → new tag like `v0.1.0` → Publish.
The workflow attaches `HardwareLens.apk` to that release.

If a build fails (red X in the Actions tab), open the failed run and copy the error near the bottom of the log.

> `app/debug.keystore` is a throwaway *debug* key, committed on purpose so CI builds stay
> update-compatible. It is not suitable for the Play Store — use a separate release key kept in
> GitHub Secrets for that.

---

## Things to know
- **Wi-Fi scanning** requires Location to be on — that's an Android rule, not this app. Android also limits apps to about 4 Wi-Fi scans every 2 minutes.
- **Devices tab** only sees devices on the *same* Wi-Fi network as your phone, and only ones that announce themselves. A PS4/PS5 in rest mode will usually still answer.
- **Bluetooth distance** is an estimate from signal strength; walls and bodies throw it off.
- Many phones and watches randomize their Bluetooth address for privacy, so the same device may show up as "new" over time.
- Nothing is sent anywhere. The app has no servers, no analytics and no ads.

## Tech details
Kotlin · Jetpack Compose · Material 3 · minSdk 26 (Android 8) · targetSdk 34. Built by GitHub Actions with the Gradle wrapper (8.9) and JDK 17. To work on it in Android Studio, just open the folder, or run `./gradlew assembleDebug`.

## License
MIT
