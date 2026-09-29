# Contributing

Thanks for helping improve AquaWiz Notifier.

## Development setup

Requirements:

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0

Run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

or:

```bash
./scripts/verify.sh
```

## Project principles

Keep the app:

- Android-first
- dependency-light
- auditable
- usable without a project-owned backend
- safe for sideloaded upgrades

Background measurement behavior should remain anchored to AquaWiz measurement timestamps, not to the arbitrary time Android happened to wake a worker.

## AquaWiz API changes

The AquaWiz cloud interface used here is undocumented.

Do not change an endpoint, request body, graph-field mapping, or transform based only on a plausible guess.

For compatibility changes, include at least one of:

- a redacted real response fixture
- a reproducible static-analysis reference from a publicly distributed AquaWiz client
- a failing test that demonstrates the schema difference

Current known contracts are documented in [docs/API_REVERSE_ENGINEERING.md](docs/API_REVERSE_ENGINEERING.md).

Important currently validated details include:

- login uses `user`, not `username`
- login includes `token.access_token = ""`
- `all_field` is scoped by device serial in the URL
- `latest_ph1` is displayed pH; `latest_ph` is a probe/status field
- graph `field22 / 1000` = dKH
- graph `field26 / 5000` = Dose (mL)
- graph `field27 / 1000` = pH
- graph `field28 / 1000` = pH(O)
- ΔpH = pH - pH(O)

## Notification behavior

The primary notification line should keep the controller serial visible:

```text
[DEVICE_SERIAL] n.nn dKH, n.nn pH
```

Optional detail values belong on the second line and must respect the user's saved toggles.

Do not invent placeholder pH/dosing values when AquaWiz does not provide them.

## Activity log

Meaningful state changes should be written to the local activity log when they help diagnose behavior, including:

- sign-in
- fetches
- new measurements
- errors
- update checks

Avoid logging:

- passwords
- access tokens
- raw authentication responses
- device activation codes

Keep log entries concise.

## App versioning

Every release must increment Android `versionCode`.

Use semantic `versionName` values that match GitHub tags:

```text
versionName 0.2.0
tag         v0.2.0
```

The in-app updater compares the latest GitHub Release tag against `BuildConfig.VERSION_NAME`.

## GitHub Releases updater

The app checks:

```text
GET /repos/stevestokes/AquaWizNotificationsApp/releases/latest
```

Do not add a personal GitHub token to the APK.

The updater is intentionally unauthenticated and therefore expects a public repository/public release.

## Release signing

Public release APKs must use the same signing key forever if existing Android installations are expected to upgrade in place.

The tagged workflow expects:

- `AQUAWIZ_KEYSTORE_BASE64`
- `AQUAWIZ_KEYSTORE_PASSWORD`
- `AQUAWIZ_KEY_ALIAS`
- `AQUAWIZ_KEY_PASSWORD`

Never commit the keystore or its passwords.

## Pull requests

When changing behavior:

1. add/update unit tests where practical
2. update the relevant documentation in the same pull request
3. ensure `./scripts/verify.sh` passes
4. describe any real-device validation performed

For AquaWiz API changes, explicitly state whether the evidence came from APK analysis, a redacted live response, or both.
