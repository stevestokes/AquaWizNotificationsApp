## v0.8.4

- Measurement notifications use bold TextViews in decorated custom collapsed, expanded, and heads-up layouts. This keeps font weight in the actual notification view rather than relying only on spans in Android's standard body template.
- The collapsed message remains one line; expansion shows all selected values in the same inline text block. Android still provides the app/icon/time header. Notification stacking and actions are unchanged.
- Version code 15 and the same permanent release key allow installation over v0.8.3.

## v0.8.3

- CI downloads are signed release APKs using the permanent repository signing key. Missing signing secrets fail the build instead of distributing a new runner's debug signature. Version code 14 supports upgrades from earlier versions signed with that same key. Existing debug installations need one transition reinstall because their private keys were not retained.
- Hero and History borders follow parallel inset corner radii. pH title and value move right 12dp, with the health pill untouched. Calibrate and its gear open the official AW app.
- History omits per-row device IDs while retaining all measurement values.
- KH/pH use the left scale with target limits and 0.2 padding; ΔpH uses independent observed bounds on the right. Dose uses its own small mL plot sharing the time axis, cursor, zoom and saved line style. Secondary values never distort the KH scale.

# App updates and GitHub Releases

AquaWiz Notifier is distributed outside the Play Store, so application updates are discovered through GitHub Releases.

## Update source

The app uses the public GitHub REST endpoint:

```text
GET https://api.github.com/repos/stevestokes/AquaWizNotificationsApp/releases/latest
```

Request headers include:

```text
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2022-11-28
User-Agent: AquaWizNotifier/<installed version>
```

No GitHub authentication token is embedded in the APK.

## Requirements

The update checker works when:

1. the repository is public
2. at least one non-draft GitHub Release exists
3. the release tag is a semantic version such as `v0.4.0`

If the repository is private, unauthenticated Android clients cannot read the latest release metadata and the app records that in the local activity log.

## Check cadence

The app schedules update checks separately from AquaWiz measurement polling.

Checks occur:

- at app startup if the most recent update check is at least six hours old
- every 24 hours through WorkManager
- immediately when the user taps **Check for updates**

A failed update check does not interfere with AquaWiz measurement monitoring.

## Version comparison

Installed version:

```kotlin
BuildConfig.VERSION_NAME
```

Latest version:

```json
{
  "tag_name": "v0.4.0"
}
```

The app removes a leading `v` and compares numeric semantic-version components.

Examples:

- installed `0.2.0`, release `v0.4.0` -> no notification
- installed `0.2.0`, release `v0.2.1` -> update available
- installed `0.2.9`, release `v0.4.0` -> update available
- installed `0.9.9`, release `v1.0.0` -> update available

Pre-release suffixes are ignored for the numeric comparison.

## Update notification

When a newer release is discovered, Android displays:

```text
AquaWiz Notifier update available
Version 0.4.0 is available. Tap to update.
```

The app remembers the last version for which it displayed an update notification, so normal daily checks do not repeatedly notify for the same release.

Tapping the notification opens the GitHub Release page in the user's browser.

The app does not silently download or install APKs.

## Android signing requirement

An APK can only upgrade an existing Android installation when:

- the application ID remains the same
- `versionCode` increases
- the APK is signed by the same signing key

AquaWiz Notifier uses:

```text
applicationId = app.aquawiznotifier
```

Every public release must use the same release keystore.

## GitHub Actions signing secrets

The tagged-release workflow expects:

```text
AQUAWIZ_KEYSTORE_BASE64
AQUAWIZ_KEYSTORE_PASSWORD
AQUAWIZ_KEY_ALIAS
AQUAWIZ_KEY_PASSWORD
```

To produce the base64 value locally on Linux/macOS:

```bash
base64 < aquawiz-release.jks | tr -d '\n'
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("aquawiz-release.jks"))
```

Store the resulting string as the `AQUAWIZ_KEYSTORE_BASE64` GitHub Actions secret.

Do not commit the keystore.

## Tagged release flow

1. Update `versionCode`.
2. Update `versionName`.
3. Merge/push to `main`.
4. Confirm Android CI passes.
5. Create a matching tag:
   ```bash
   git tag v0.4.0
   git push origin v0.4.0
   ```
6. GitHub Actions:
   - restores the release keystore from secrets
   - runs unit tests
   - runs `assembleRelease`
   - creates or updates the matching GitHub Release
   - attaches the signed APK

Each release publishes both:

```text
AquaWizNotifier.apk
AquaWizNotifier-v0.4.0.apk
```

`AquaWizNotifier.apk` is the stable filename used by the README's permanent latest-download link:

```text
https://github.com/stevestokes/AquaWizNotificationsApp/releases/latest/download/AquaWizNotifier.apk
```

The versioned filename is retained for archival/debugging.

## First public release

Before publishing the first signed release:

- choose a permanent release keystore
- back it up securely
- configure the four GitHub signing secrets
- make the repository public
- ensure `versionName` matches the tag
- install the signed APK on a test phone
- for the next release, verify the new signed APK upgrades the previous version without uninstalling it

## Activity log

Update checks appear in the app's activity console, including:

- update check started
- latest GitHub release version
- update available
- update check failure

This makes updater behavior visible without requiring Android debug tools.


## Public build behavior

The public build uses app version `0.4.0`, the selected white/blue launcher icon, Alkatronic-style measurement notification wording, and measurement-notification launching into the installed official AquaWiz app when available.


## 0.4.0 UI/history changes

Version `0.4.0` introduces the three-tab Status / History / Config interface, local full-field measurement history, baseline/history persistence independent of notification toggles, Config-first startup when required setup is missing, and test notifications that reuse the most recent real stored measurement when available.


## 0.6.0 session-safety change

Version `0.6.0` removes automatic AquaWiz password re-login from background measurement polling. If the stored bearer token receives `401` or `403`, monitoring pauses rather than authenticating again. This change is based on real-device evidence that a new AquaWiz login can invalidate the session used by the official app.


## 0.6.0 shared-token experiment

Version `0.6.0` adds an experimental mode for importing an existing AquaWiz bearer token. The mode validates the token directly against measurement APIs and never calls the AquaWiz credential-login endpoint. Its purpose is to test whether the official AquaWiz app and AquaWiz Notifier can simultaneously use the same cloud token without invalidating one another.


## 0.6.0 Web Login

Version `0.6.0` promotes bearer-token authentication to the preferred path and adds an embedded **AquaWiz Web Login**. The official AquaWiz page handles credentials inside a WebView; AquaWiz Notifier captures only the returned bearer token, validates it against the configured controller, encrypts it locally, and starts monitoring. Manual bearer-token entry and direct username/password login remain as fallback options.

Real-device testing confirmed that the official AquaWiz app can remain logged in while AquaWiz Notifier polls with a web-issued bearer token.


## 0.7.0 Home dashboard

Version `0.7.0` adds Phase 1 of the AquaWiz-style Home screen: KH/pH summary cards, dosing cards, official AquaWiz app shortcut, interactive graph inspection, range buttons, graph API backfill, and a single shared Y axis. The initial Y-axis fallback uses observed KH min/max with ±0.5 dKH padding until AquaWiz KH limit settings are mapped.

## 0.8.0 Remaining dashboard phases

- Web Login is the only login method and opens automatically with no saved session. Saved tokens migrate; old passwords are discarded.
- Official-style diagonal KH/pH hero, rounded cards, official fonts, KH target, probe status, and remaining dosing solution.
- Verified field8/field15 map target and deviation; chart bounds include ±0.5 dKH padding.
- Five independent chart lines with locally saved visibility, solid/dashed/dotted styles, and range; drag inspection, pinch zoom, reset gesture.
- Range caching, loading/error/empty states, and protection against stale requests after account/device changes.
- Compact recycled History rows with full tap-through detail; timestamp deduplication preserves missing optional fields.
- All displayed dates use MM/dd/yy @ HH:mm in the device timezone.
- Corrected dose scaling for small raw graph doses.

Validation: unit tests cover settings isolation/scaling, chart bounds, current and graph measurement scaling, history merging, and date formatting. Live login, notification delivery, and pixel-level Home parity still require a physical Android device.

## 0.8.1 Layout and adaptive chart update

- Shared title/value/footer heights align the diagonal hero; numeric values and units share baselines.
- All Home cards use a uniform darker 2dp border; rounded range selectors show an explicit selected state.
- Y bounds include the target limits and all enabled line values, with 0.2 padding per edge. Zoom recalculates bounds for visible data and adjoining segments. Hidden lines do not add empty space.
- All dates display MM/dd/yy @ hh:mm AM/PM, including previous activity entries.
- History displays every measurement value inline with alternating row colors; the tap-through prompt/dialog is removed.
- Config separates connection status, notification details, and app actions. Connection editors are collapsed by default.
- Tab order: Home, History, Status, Config.

Validation: bounds tests cover below/above-target readings, enabled versus hidden lines, nonfinite values, and 0.2 padding; date tests cover midnight/noon/afternoon and old log timestamps.

## 0.8.2 Screenshot follow-up

- One bold notification text block follows the Alkatronic wording, with selected optional values appended inline and natural wrapping on narrow screens.
- Removed the manual measured-date header. Android's timestamp reflects posting time.
- Notification identity is controller plus measurement timestamp; new readings retain prior individual notifications, while retries of the same reading update it without alerting twice. Test notifications receive distinct timestamps.
- Home unit labels are positioned next to their numbers on the same baseline, replacing the stretched spacing visible in the supplied screenshot.

Validation covers notification wording, absent dates/newlines, missing optional values, toggle behavior, distinct readings/controllers, repeated test notifications, and stable identity across payload changes.
