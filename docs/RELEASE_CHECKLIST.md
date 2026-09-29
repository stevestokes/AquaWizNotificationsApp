# Release checklist

Before publishing a tagged AquaWiz Notifier release:

1. Confirm `versionCode` increased.
2. Confirm `versionName` matches the intended Git tag.
   - Example: `versionName = "0.2.0"` -> tag `v0.2.0`.
3. Run:
   ```bash
   ./scripts/verify.sh
   ```
4. Confirm CI passes on `main`.
5. Install the latest build on a real Android device.
6. Grant notification permission.
7. Sign in using a real AquaWiz account.
8. Confirm the configured controller serial is correct.
9. Confirm the current reading becomes a baseline without creating an old/stale notification.
10. Confirm the next AquaWiz measurement creates exactly one notification.
11. Verify notification title format:
    ```text
    [DEVICE_SERIAL] n.nn dKH, n.nn pH
    ```
12. Verify the optional second line when all toggles are enabled:
    ```text
    pH(O) n.nn • ΔpH +/-n.nn • Dose n.nn mL
    ```
13. Disable each optional field individually and confirm the notification omits it.
14. Confirm repeated background polls do not duplicate a measurement notification.
15. Confirm the next-poll schedule re-anchors to the actual AquaWiz measurement timestamp.
16. Confirm a 401/403 results in one automatic AquaWiz re-login and monitoring continues.
17. Confirm the activity/diagnostic console:
    - appends new events
    - retains prior events
    - auto-scrolls to the newest event
18. Confirm **Test notification** renders a representative dKH/pH/detail notification.
19. Confirm **Check now** produces a log entry and queues an AquaWiz fetch.
20. Confirm the GitHub project and Reef2Reef attribution links open correctly.
21. Confirm **Check for updates** produces a log entry.
22. Confirm the app's installed version is shown in the status console.
23. If a newer public GitHub Release exists, confirm:
    - the app detects it
    - only one update notification is created for that version
    - tapping the notification opens the correct GitHub Release page
24. Confirm the repository is public before relying on the unauthenticated GitHub Releases updater.
25. Confirm the repository has a published GitHub Release.
26. Confirm the stable signing secrets are configured:
    - `AQUAWIZ_KEYSTORE_BASE64`
    - `AQUAWIZ_KEYSTORE_PASSWORD`
    - `AQUAWIZ_KEY_ALIAS`
    - `AQUAWIZ_KEY_PASSWORD`
27. Confirm the release keystore is backed up securely outside GitHub.
28. Push the matching version tag:
    ```bash
    git tag v0.2.0
    git push origin v0.2.0
    ```
29. Confirm the **Tagged APK Release** workflow succeeds.
30. Confirm the GitHub Release contains a signed APK named like:
    ```text
    AquaWizNotifier-v0.2.0.apk
    ```
31. Install the signed release APK.
32. For later releases, verify the new signed APK installs **over the previous signed release without uninstalling it**. This is the practical confirmation that the signing key has remained stable.
33. Capture/update redacted AquaWiz response fixtures when live API behavior changes.

## Current AquaWiz contracts to verify

Login:

```json
{
  "user": "<username>",
  "password": "<password>",
  "token": {
    "access_token": ""
  }
}
```

Current-data route:

```text
POST /api/v1/KH/{DEVICE_SERIAL}/all_field
```

Graph route:

```text
GET /api/v1/query/device/{DEVICE_SERIAL}/graph?date=...
```

Known KH graph transforms:

- `field22 / 1000` = dKH
- `field26 / 5000` = Dose (mL)
- `field27 / 1000` = pH
- `field28 / 1000` = pH(O)
- `ΔpH = pH - pH(O)`

## Never publish

Do not commit or attach:

- AquaWiz usernames/passwords
- AquaWiz access tokens
- device activation codes
- unredacted account/API responses
- the raw release keystore
- signing passwords
