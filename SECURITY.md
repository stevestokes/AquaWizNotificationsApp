# Security

## Credential handling

AquaWiz Notifier stores the AquaWiz username, password, cloud access token, and returned device list in an AES-GCM encrypted blob.

The AES key is generated and held by Android Keystore and is not exported by the app.

Android backup is disabled.

The password is retained because the app must be able to re-authenticate automatically if the undocumented AquaWiz access token expires while background monitoring is active.

## Network behavior

The app has no project-owned backend.

It sends AquaWiz credentials/tokens only to the selected AquaWiz API host:

- `server.aquawiz.net`
- `server.aquawiz.cn`

over HTTPS.

For application updates, the app makes an unauthenticated HTTPS request to:

- `api.github.com/repos/stevestokes/AquaWizNotificationsApp/releases/latest`

The GitHub request contains no AquaWiz username, password, token, or controller data.

Tapping a project/update link may open the user's browser to GitHub or Reef2Reef.

## Measurement notification launch behavior

When a measurement notification is tapped, the app queries installed launcher activities and opens the installed app whose visible label matches `AquaWiz`. AquaWiz Notifier excludes its own package and falls back to AquaWiz Notifier if no official AquaWiz launcher activity is found. No AquaWiz credentials are shared through this launch intent.

## Activity log

The local activity log is intended for troubleshooting app behavior.

It may contain:

- timestamps
- controller serial
- dKH/pH measurement summaries
- API error messages
- app-update status

It must not contain:

- AquaWiz passwords
- AquaWiz access tokens
- raw authentication payloads containing secrets
- device activation codes
- GitHub signing secrets

Contributors should avoid logging full raw server responses unless they are known to be redacted.

## Release signing

Public APK releases must use one stable Android signing key.

The GitHub Actions workflow expects the keystore and passwords through repository secrets. The keystore itself must not be committed.

Required secrets:

- `AQUAWIZ_KEYSTORE_BASE64`
- `AQUAWIZ_KEYSTORE_PASSWORD`
- `AQUAWIZ_KEY_ALIAS`
- `AQUAWIZ_KEY_PASSWORD`

The release keystore should also be backed up securely outside GitHub.

Loss of the signing key means future builds cannot be installed as upgrades over existing signed installations.

## GitHub updater

No GitHub personal access token is embedded in the APK.

The updater intentionally relies on a public repository/public GitHub Release. This avoids shipping a reusable GitHub secret inside the app.

## Reporting a vulnerability

Use GitHub's private vulnerability reporting feature when available.

Do not open a public issue containing:

- credentials
- AquaWiz access tokens
- raw unredacted account responses
- activation information
- Android signing material
