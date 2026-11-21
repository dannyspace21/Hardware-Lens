# Publishing Hardware Lens on Google Play

GitHub already builds a sideloadable APK on every push. Publishing to Google Play needs a few things only you can do.

## 1. Create a release key (once, keep it forever)
Run on your computer (needs Java):
```
keytool -genkeypair -v -keystore hardwarelens-release.jks -alias hardwarelens \
  -keyalg RSA -keysize 4096 -validity 10000
```
Back the file and both passwords up somewhere safe. **If you lose this key you can't update the Play listing** (unless you enrol in Play App Signing, which is recommended — Google then holds the final signing key and this one becomes your "upload key").

## 2. Add it to GitHub Secrets
Repository → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | output of `base64 -w0 hardwarelens-release.jks` (macOS: `base64 -i hardwarelens-release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `hardwarelens` |
| `RELEASE_KEY_PASSWORD` | the key password |

Never commit the `.jks` file or the passwords.

## 3. Build a release
Create a GitHub release with a new tag such as `v1.0.0`. The workflow attaches:
- `HardwareLens.apk` — the sideload build (same as the "latest" download)
- `HardwareLens-play.aab` — the signed bundle for Google Play

The Play build has the in-app GitHub update check switched off (Play handles updates).

## 4. Play Console
1. Create a developer account at https://play.google.com/console (one-time $25 fee, identity verification).
   New personal accounts must run a **closed test with at least 12 testers for 14 days** before going to production.
2. Create the app → upload `HardwareLens-play.aab` to a testing track.
3. Store listing: short/long description, screenshots (phone), the icon (512×512), feature graphic (1024×500).
4. **Privacy policy URL:** `https://github.com/dannyspace21/Hardware-Lens/blob/HEAD/PRIVACY.md`
5. **Data safety form:** "No data collected" and "No data shared".
6. **Permissions declaration:** explain that precise location is required by Android for Wi-Fi/Bluetooth/cell/GPS scanning and is never stored or transmitted.
7. Content rating questionnaire → Utility, no objectionable content.

## Notes
- Play and GitHub builds are signed with different keys, so a phone can't switch between them without uninstalling once.
- `versionCode` comes from the GitHub run number, so every build is higher than the last — Play requires that.
