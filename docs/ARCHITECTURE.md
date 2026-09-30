# Architecture

## Goal

Notify once for every newly observed AquaWiz KH measurement, whether or not the reading is outside an AquaWiz alert threshold.

The app is intentionally Android-first and dependency-light.

## Measurement flow

```text
WorkManager wakes
      |
      v
POST device-scoped all_field
      |
      | parse current KH / latest pH
      | if unavailable or incompatible
      v
GET serial-specific graph
      |
      | parse field22 / field26 / field27 / field28
      v
Build Measurement model
      |
      v
measurement fingerprint == stored fingerprint?
      | yes                         | no
      v                             v
schedule next probe          save measurement
                             notify once
                             append activity log
                                    |
                                    v
                            re-anchor schedule to
                            AquaWiz measurement time
```

## Poll timing

For an hourly controller interval, a measurement at `T` anchors the next probes at:

- `T + 17m`
- `T + 32m`
- `T + 47m`
- `T + 62m`

The fourth probe is therefore two minutes after the next expected hourly result.

If there is still no new result at `T+62`, checks continue every 15 minutes from that sequence:

- `T+77`
- `T+92`
- etc.

A new result immediately re-anchors the sequence to the new measurement's actual timestamp.

The app uses chained one-time WorkManager jobs rather than a simple fixed periodic worker because the desired schedule changes whenever AquaWiz produces a result.

Android may defer jobs because of Doze, standby buckets, connectivity, or OEM battery management. Scheduled times are therefore earliest-eligible times rather than hard real-time deadlines.

## Measurement model

`Measurement` currently contains:

- `kh`
- `ph`
- `phOpenAir` / pH(O)
- `deltaPh`
- `doseMl`
- `measuredAt`
- optional raw/server measurement ID

Known KH-series graph mappings:

| Field | Meaning | Transform |
| --- | --- | --- |
| `field22` | dKH | / 1000 |
| `field26` | Dose (mL) | / 5000 |
| `field27` | tank pH | / 1000 |
| `field28` | pH(O) | / 1000 |
| derived | ΔpH | pH - pH(O) |

## Notification semantics

The first successfully read measurement after setup/sign-in becomes the baseline and does **not** notify.

Every subsequently detected measurement with a different fingerprint generates one notification.

The notification layout is:

```text
[KH1-00-05117] New measurement result:
8.42 dKH, 8.27 pH
pH(O) 8.35 • ΔpH -0.08 • Dose 1.20 mL
Measured 1:04 PM • ↑ 0.06 dKH
```

The optional second-line fields are individually configurable:

- pH(O)
- ΔpH
- Dose (mL)

If a selected optional value is unavailable from AquaWiz, it is omitted rather than replaced with a guessed value.

Tapping a measurement notification attempts to open the installed official AquaWiz app. The notifier discovers an installed launcher activity whose visible label is `AquaWiz`, excludes its own package, and falls back to AquaWiz Notifier if the official app cannot be found.

## Web Login and bearer-token authentication

The recommended authentication path uses `AquaWizWebLogin`, a WebView pointed at the official AquaWiz web login page. The web page handles credentials; a narrowly scoped JavaScript bridge watches successful fetch/XHR responses and web storage for an AquaWiz `access_token`. The notifier never receives or stores the web password.

The official AquaWiz bytecode shows that credential login calls:

```text
POST /api/v1/KH/auth
{"user":"...","password":"...","token":{"access_token":""}}
```

and later stores the returned `access_token` inside the app's private `user_token` in Expo SecureStore. Normal API requests read `accessToken` from local auth state and send `Authorization: Bearer <accessToken>`.

After capture, the bearer token is validated with `latestMeasurement()`, stored through `SecureStore`, and used for normal polling. Manual bearer-token entry follows the same validation path. Android sandboxing prevents direct reading of the official AquaWiz app's Expo SecureStore, so Web Login provides the practical token handoff without root.

## AquaWiz session-conflict handling

Real-device behavior indicates AquaWiz may invalidate an older token when the same account logs in again. The notifier therefore treats `401`/`403` from measurement polling as a session conflict:

1. mark monitoring as paused
2. clear the next scheduled poll timestamp
3. stop chaining measurement polls
4. show an Android notification explaining that automatic login was intentionally avoided
5. require a deliberate user-initiated **Sign in & start** before monitoring resumes

The background worker never calls the password-login endpoint as a recovery action. This prevents repeated polling from continuously taking the active session away from the official AquaWiz app.

## Deduplication

Fingerprints prefer a server measurement ID when present. Otherwise they use:

```text
measurement timestamp + KH
```

This prevents repeated 15-minute checks from generating duplicate notifications for the same measurement.

## Activity / diagnostics

`SecureStore` keeps a rolling activity log of up to 5,000 local entries.

Events include:

- sign-in attempts and failures
- background AquaWiz checks
- baseline creation
- detected measurements
- API/network errors
- update checks
- available versions

`MainActivity` displays this data in a fixed-height independently scrollable text console and automatically scrolls to the bottom after refresh so the newest event remains visible.

## Three-tab UI

The app has three top-level tabs:

- **Status**: live state plus rolling diagnostics/activity. The status console is intentionally slightly shorter than the original single-screen console and auto-scrolls to the latest line.
- **History**: newest-first local measurement history. Storage is independent of notification display toggles and preserves all available measurement values.
- **Config**: server/account/device setup, measurement interval, notification detail preferences, test notification, updater controls, and sign-out.

If account/device setup is incomplete, Config is the initial tab. Otherwise Status is the initial tab.

## Local measurement history

Every newly retrieved AquaWiz measurement is persisted locally, including the initial baseline measurement. Entries are deduplicated by device serial plus measurement fingerprint and retained newest-first. The current implementation keeps up to 2,000 measurements.

Stored fields include:

- device serial
- timestamp
- dKH
- pH
- pH(O)
- ΔpH
- Dose (mL)
- raw/server ID when available

Notification toggles affect only notification presentation; they do not filter History storage.

The most recently stored measurement is also used by **Test notification**. Before any real measurement exists, the test action selects from several normal hard-coded sample readings.

## Components

- `MainActivity`
  - Status / History / Config tab navigation
  - setup/config controls
  - notification detail checkboxes
  - manual AquaWiz check
  - manual update check
  - local History rendering
  - auto-scrolling activity console
  - GitHub/Reef2Reef links

- `AquaWizApi`
  - login
  - device-scoped `all_field`
  - graph fallback
  - official graph transforms
  - HTTP error handling

- `MeasurementJson`
  - current-value parsing
  - graph row parsing
  - serial-aware candidate selection
  - defensive numeric validation

- `SecureStore`
  - Android Keystore-backed encrypted AquaWiz session
  - monitoring state
  - complete local measurement history
  - last stored measurement snapshot
  - notification preferences
  - update state
  - activity history

- `MeasurementWorker`
  - background fetch
  - no background re-authentication after 401/403
  - deduplication
  - notification
  - error logging
  - rescheduling

- `PollCadence`
  - pure adaptive timing math

- `PollScheduler`
  - chained one-time WorkManager requests

- `Notifier`
  - measurement notifications
  - sign-in-required notification
  - update-available notification

- `UpdateChecker`
  - public GitHub Releases API client
  - semantic version comparison
  - startup/manual/daily update scheduling

- `UpdateWorker`
  - background GitHub Releases check

## GitHub update flow

```text
App opens / daily WorkManager / manual button
                 |
                 v
GET GitHub releases/latest
                 |
                 v
compare tag vs BuildConfig.VERSION_NAME
       | same/older             | newer
       v                        v
store latest version      notification:
                         "Update available"
                                |
                                v
                       tap opens GitHub Release
```

No GitHub token is embedded in the application. This requires the repository and release to be publicly readable.

## Release signing

GitHub Release APKs must use one stable Android signing key.

The tagged workflow reconstructs the release keystore from GitHub Actions secrets and runs `assembleRelease`.

If the signing key changes or is lost, Android will not accept a future APK as an upgrade to an existing installation.

## Security

AquaWiz username, password, cloud token, and device list are serialized and encrypted with AES-GCM. The AES key is non-exportable and stored in Android Keystore.

Android backup is disabled.

The app does not operate:

- a project-owned backend
- a notification relay
- analytics
- a crash collection service

Network destinations are limited to:

- the selected AquaWiz HTTPS API host
- the public GitHub Releases API for update metadata
- the user's browser when opening project/release links


## Launcher icon

The public build uses the selected white-background blue/cyan droplet-and-alert icon. Android adaptive and legacy launcher resources both point to the selected artwork for visual consistency across launchers.
