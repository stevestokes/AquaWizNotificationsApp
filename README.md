# AquaWiz Notifier

Unofficial, open-source Android companion for AquaWiz KH controllers. AquaWiz Notifier signs into the AquaWiz cloud, checks for new measurements, and creates a normal Android notification for **every newly detected measurement**, not only readings outside AquaWiz's email-alert thresholds.

**Made by Biff0rz.** Find me on [Reef2Reef](https://www.reef2reef.com/members/biff0rz.154703/).

Project: [github.com/stevestokes/AquaWizNotificationsApp](https://github.com/stevestokes/AquaWizNotificationsApp)

> Not affiliated with or endorsed by AquaWiz. This project uses an undocumented interface recovered from the publicly distributed AquaWiz Android APK. AquaWiz may change that interface at any time.

## Download

[**Download the latest AquaWiz Notifier APK**](https://github.com/stevestokes/AquaWizNotificationsApp/releases/latest/download/AquaWizNotifier.apk)

Android only. This is a sideloaded APK. On first install, Android may ask you to allow installs from your browser or file manager.

[View all releases](https://github.com/stevestokes/AquaWizNotificationsApp/releases)

## Features

- Android notification for every newly detected AquaWiz measurement.
- Notification title includes controller ID, dKH, and pH:
  - `[KH1-00-05117] 8.42 dKH, 8.27 pH`
- Optional second notification line:
  - `pH(O) 8.35 • ΔpH -0.08 • Dose 1.20 mL`
- Individual on/off controls for pH(O), ΔpH, and Dose.
- Adaptive polling anchored to the controller's actual measurement timestamp.
- Encrypted AquaWiz credentials/token storage using Android Keystore.
- Automatic re-login when the AquaWiz token expires.
- Persistent, auto-scrolling activity/diagnostic log in the app.
- Global and China AquaWiz server support.
- GitHub Releases update checker with an Android notification when a newer app version is available.
- Manual **Check for updates** button.
- GitHub and Reef2Reef links directly in the app.

## Why native Kotlin instead of Flutter?

This project is Android-only and most of its important behavior is Android-specific: WorkManager scheduling, notifications, secure credential storage, and sideloaded APK updates. Native Kotlin keeps the project small, dependency-light, and easier to audit.

## Measurement timing

With the default 60-minute controller interval, a measurement timestamped `T` causes future checks to become eligible at approximately:

- `T + 17m`
- `T + 32m`
- `T + 47m`
- `T + 62m`

The fourth check is two minutes after the next expected hourly result. If a new result appears during any earlier check, the schedule is immediately re-anchored to that result's own AquaWiz timestamp.

If no new result exists at `T+62`, the app continues in 15-minute steps: `T+77`, `T+92`, etc.

Android can defer WorkManager jobs during Doze, standby, or OEM battery optimization, so these are **earliest eligible times**, not real-time guarantees.

## AquaWiz API mapping

The key request contracts recovered from the official Android APK are:

### Login

```http
POST https://server.aquawiz.net/api/v1/KH/auth
Content-Type: application/json

{
  "user": "<username>",
  "password": "<password>",
  "token": {
    "access_token": ""
  }
}
```

The successful response contains an AquaWiz access token. Authenticated requests use:

```http
Authorization: Bearer <access_token>
```

### Current device data

```http
POST https://server.aquawiz.net/api/v1/KH/{DEVICE_SERIAL}/all_field
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "user": "<username>",
  "token": {
    "access_token": "<access_token>"
  }
}
```

The device serial, not the username, belongs in the route.

Important current-value fields used by the official AquaWiz client include:

- `latest_kh` — current KH
- `latest_ph` — pH probe/status field, **not the displayed pH**
- `latest_ph1` — displayed tank pH
- `latest_time` — measurement timestamp

### Graph/history fallback

```http
GET https://server.aquawiz.net/api/v1/query/device/{DEVICE_SERIAL}/graph?date={ISO-8601}
Authorization: Bearer <access_token>
```

For KH-series graph rows, the official app transforms these raw fields:

| Raw field | Meaning | Official transform |
| --- | --- | --- |
| `field22` | dKH | value / 1000 |
| `field26` | Dose (mL) | value / 5000 |
| `field27` | Tank pH | value / 1000 |
| `field28` | pH(O), fully aerated pH | value / 1000 |
| derived `delta` | ΔpH | pH - pH(O) |

See [docs/API_REVERSE_ENGINEERING.md](docs/API_REVERSE_ENGINEERING.md) for the detailed reverse-engineering notes.

## Setup

1. Install the APK on Android.
2. Allow notifications.
3. Select the AquaWiz Global or China server.
4. Enter the same AquaWiz username/password you use with the official app.
5. Enter the controller serial if it is not discovered automatically.
6. Leave the measurement interval at 60 minutes unless your controller is configured differently.
7. Choose which optional notification fields to show.
8. Tap **Test notification**.
9. Tap **Sign in & start**.

The first successfully read measurement becomes the baseline and does **not** create an old/stale notification. The next new measurement does.

## Activity / diagnostics

The bottom of the app contains a fixed-height scrollable activity console. New entries are appended at the bottom and the view automatically scrolls to the newest information.

The activity log records events such as:

- AquaWiz sign-in attempts/results
- background measurement checks
- baseline establishment
- new measurement notifications
- API/network errors
- GitHub update checks
- available app updates

A rolling history of up to 5,000 entries is kept locally.

## App updates

AquaWiz Notifier uses the public GitHub Releases API:

```text
GET https://api.github.com/repos/stevestokes/AquaWizNotificationsApp/releases/latest
```

The app checks:

- when the app opens, if it has not checked recently
- once every 24 hours in the background
- whenever **Check for updates** is tapped

When a newer semantic version is found, Android displays an update notification. Tapping it opens the GitHub Release page.

The repository must be **public** and have at least one published GitHub Release for the unauthenticated updater to work. No GitHub token is embedded in the APK.

## Versioning and release signing

Every installable release must increment:

```kotlin
versionCode = 2
versionName = "0.2.0"
```

Future releases must use the **same Android signing key**. Otherwise Android will reject the new APK as an update to the installed app.

The tagged-release workflow expects these GitHub Actions secrets:

- `AQUAWIZ_KEYSTORE_BASE64`
- `AQUAWIZ_KEYSTORE_PASSWORD`
- `AQUAWIZ_KEY_ALIAS`
- `AQUAWIZ_KEY_PASSWORD`

A tag such as `v0.2.0` builds a signed release APK and publishes two release assets: `AquaWizNotifier.apk` for the permanent latest-download link and `AquaWizNotifier-v0.2.0.apk` for versioned archives.

Do not lose the release keystore. If it is lost, existing users cannot install future APKs as normal upgrades.

## Build

Requirements:

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0

Development build:

```bash
./gradlew testDebugUnitTest assembleDebug
```

or:

```bash
./scripts/verify.sh
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The repository currently uses Android Gradle Plugin 9.4.0, Gradle 9.6.0, and WorkManager 2.12.0.

## Security

AquaWiz credentials and cloud token are stored in an AES-GCM encrypted blob protected by a non-exportable Android Keystore key. Android backup is disabled.

The project does not operate its own backend, notification relay, analytics service, or crash collector.

See [SECURITY.md](SECURITY.md).

## License

MIT. See [LICENSE](LICENSE).
