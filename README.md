# AquaWiz Notifier

Unofficial, open-source Android companion for AquaWiz KH controllers. It signs into the AquaWiz cloud, checks for a new KH result, and creates a normal Android notification for **every new measurement** — not only values outside AquaWiz's email alert thresholds.

> Not affiliated with or endorsed by AquaWiz. This project uses an undocumented interface recovered from the publicly distributed AquaWiz Android APK; server behavior may change.

## Why native Kotlin instead of Flutter?

This is Android-only and the important code is Android background work, notifications, and secure storage. Native Kotlin keeps the source small, avoids Flutter/plugin version coupling, and makes the background behavior easier to audit. The UI intentionally uses basic Android widgets.

## Timing behavior

With the default 60-minute controller interval, a result timestamped `T` causes future checks to be eligible at approximately **T+17, T+32, T+47, and T+62 minutes**. The final probe is two minutes after the next expected hourly measurement. A new result immediately re-anchors the next cycle to the result's own timestamp.

The app uses chained one-time WorkManager requests. Android can defer background jobs under Doze/battery restrictions, so `T+62` is an *earliest eligible* target, not a real-time guarantee.

## Build

Requirements:

- JDK 17
- Android SDK Platform 36 / Build Tools 36.x
- Internet access for the first dependency download

```bash
./gradlew assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The repository pins Android Gradle Plugin 9.4.0, Gradle 9.6.0, and WorkManager 2.12.0. AGP 9.x uses its built-in Kotlin support, so the project intentionally does not apply a separate Kotlin Gradle plugin.

## Setup

1. Install the APK on Android and allow notifications.
2. Choose Global or China server.
3. Enter your normal AquaWiz username/password.
4. Sign in. If AquaWiz returns device serials, the first is selected automatically; otherwise enter the controller serial manually.
5. Leave measurement interval at 60 minutes unless your controller is configured differently.
6. Tap **Test notification** to verify Android notification permissions.

Credentials are encrypted with an Android Keystore AES-GCM key so the app can automatically re-authenticate after an expired cloud token.

## API mapping

See [`docs/API_REVERSE_ENGINEERING.md`](docs/API_REVERSE_ENGINEERING.md). The most important recovered contract is:

```text
POST https://server.aquawiz.net/api/v1/KH/auth
Content-Type: application/json
{"username":"…","password":"…"}

POST https://server.aquawiz.net/api/v1/KH/{username}/all_field
Authorization: Bearer <access_token>
Content-Type: application/json
{"user":"…","token":{"access_token":"…"}}

GET https://server.aquawiz.net/api/v1/query/device/{serial}/graph?date={ISO-8601}
Authorization: Bearer <access_token>
```

The app uses the official client's `all_field` call first (`latest_kh`, `latest_time`, and pH fields) and falls back to the serial-specific graph call.

## Release-hardening checklist

Before publishing a `1.0.0` release, use a real AquaWiz account to save **redacted `all_field` and/or graph JSON responses** as test fixtures. That lets CI verify the exact server schema without storing credentials or access tokens.

## License

MIT. See [LICENSE](LICENSE).
