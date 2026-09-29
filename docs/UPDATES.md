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
3. the release tag is a semantic version such as `v0.3.0`

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
  "tag_name": "v0.3.0"
}
```

The app removes a leading `v` and compares numeric semantic-version components.

Examples:

- installed `0.2.0`, release `v0.3.0` -> no notification
- installed `0.2.0`, release `v0.2.1` -> update available
- installed `0.2.9`, release `v0.3.0` -> update available
- installed `0.9.9`, release `v1.0.0` -> update available

Pre-release suffixes are ignored for the numeric comparison.

## Update notification

When a newer release is discovered, Android displays:

```text
AquaWiz Notifier update available
Version 0.3.0 is available. Tap to update.
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
   git tag v0.3.0
   git push origin v0.3.0
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
AquaWizNotifier-v0.3.0.apk
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

The public build uses app version `0.3.0`, the selected white/blue launcher icon, Alkatronic-style measurement notification wording, and measurement-notification launching into the installed official AquaWiz app when available.
