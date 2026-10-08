# AquaWiz API reverse-engineering notes

These notes are derived from static analysis of the publicly distributed AquaWiz Android APK used for this project, plus real-device validation while building AquaWiz Notifier.

This is an unofficial integration and is not affiliated with or endorsed by AquaWiz.

## Client format

The official AquaWiz Android app is a React Native / Expo application compiled to Hermes bytecode version 96. Hermes v96 uses the same opcode set as v95 for the relevant instructions, which allows the network request construction and graph-field transforms to be recovered directly.

## API servers

- Global: `https://server.aquawiz.net`
- China: `https://server.aquawiz.cn`

## Authentication

The official app sends:

```http
POST /api/v1/KH/auth
Content-Type: application/json

{
  "user": "<username>",
  "password": "<password>",
  "token": {
    "access_token": ""
  }
}
```

### Important correction

An earlier prototype incorrectly sent:

```json
{"username":"...","password":"..."}
```

The AquaWiz server responded with:

```text
400 User not found
```

Tracing the Hermes bytecode showed that the real field name is `user`, and that the official client includes an initially empty nested token object.

The successful response exposes an `access_token`. Authenticated calls use:

```http
Authorization: Bearer <access_token>
```

The official app distinguishes at least these authentication errors:

- `User not found`
- `Wrong password`

## Device current-data request

The official app constructs:

```http
POST /api/v1/KH/{DEVICE_SERIAL}/all_field
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "user": "<username>",
  "token": {
    "access_token": "<access_token>"
  }
}
```

### Important correction

An earlier prototype incorrectly used the username in the path:

```text
/api/v1/KH/{username}/all_field
```

A real AquaWiz account returned:

```text
400 User not owned
```

Re-tracing the official app showed that the path parameter is the **device serial**, while the username remains inside the JSON body.

## Current-value fields

The official device page reads:

- `latest_kh`
- `latest_ph`
- `latest_ph1`
- `latest_time`

The important pH distinction is:

- `latest_ph` = pH probe/status value
- `latest_ph1` = displayed tank pH value

AquaWiz Notifier therefore does **not** treat `latest_ph` as the tank pH.

## Graph/history request

The official app also constructs:

```http
GET /api/v1/query/device/{DEVICE_SERIAL}/graph?date={ISO-8601}
Authorization: Bearer <access_token>
Content-Type: application/json
```

The response contains a `results` array whose entries are shaped like:

```text
[date, { raw field values... }]
```

The official client maps these rows into objects with `date` plus transformed graph fields.

## Multi-point graph retrieval

For Home/history charting, the notifier now passes a caller-selected start timestamp to the same graph endpoint and parses the complete `results` array into a sorted list of `Measurement` values. Supported UI windows are 1D, 3D, 1W, 1M, and 1Y. These graph reads use the already stored bearer token and do not perform authentication.

## KH-series graph field map

The following mappings were recovered from the official client's graph transform and chart/widget code:

| Field | Meaning | Transform |
| --- | --- | --- |
| `field22` | Tank KH / dKH | raw / 1000 |
| `field26` | Dosing amount (mL) | raw / 2 (verified KH1 capture + CSV) |
| `field27` | Tank pH | raw / 1000 |
| `field28` | pH(O), fully aerated pH | raw / 1000 |
| derived `delta` | ΔpH | field27 - field28 |

The official app explicitly computes:

```text
ΔpH = pH - pH(O)
```

Its user-facing help describes pH(O) as pH after full aeration and ΔpH as a relative indicator.

### Previous graph-parser correction

An earlier notifier build treated `field23` as pH because it was a plausibly scaled pH-looking field. Further chart tracing proved that this was incorrect. The actual pH / pH(O) pair is `field27` / `field28`.

## Dosing field

The official chart legend associates its **Dosing** series with the animated graph value sourced from `field26`.

The earlier APK-derived `/5000` interpretation was incorrect for the graph payload. An authenticated KH1 response captured on October 8 has `field26=100` at 09:46:32 local time; the matching official CSV reports 50 mL. Graph dose conversion now uses `/2`. This is verified for the captured KH1 response; independent KH2 verification is still outstanding.

AquaWiz Notifier exposes this as optional `Dose (mL)` notification data.

## Measurement selection policy

The notifier uses this order:

1. Authenticate with the same contract as the official AquaWiz app.
2. Read `all_field` for controller settings only. Never save its latest/status values as measurements.
3. Read measurements exclusively from serial-specific graph results in `[timestamp, fields]` format. Ignore summary objects and metadata.
4. Use the selected controller serial; never guess between multiple explicitly identified devices.
5. Reject implausible KH values outside 2–20 dKH.
6. Use explicit AquaWiz graph mappings instead of generic number guessing where the official mapping is known.
7. On HTTP 401/403, pause monitoring rather than automatically re-authenticating.
8. Record API failures in the app activity log.

## Notification data model

The notifier's measurement model currently supports:

- dKH
- tank pH
- pH(O)
- ΔpH
- Dose (mL)
- AquaWiz measurement timestamp
- measurement ID/fingerprint when available

The first successfully read result after configuration becomes a baseline and does not create a stale notification.

## Timestamp handling

- ISO timestamps with `Z` or an explicit offset retain the supplied timezone.
- Naive local timestamps are interpreted using the Android device timezone.
- Measurement polling is anchored to the AquaWiz result timestamp, not to when Android happened to wake the worker.

## Validation and fixtures

Static analysis gives us the request/transform contracts, but real-device response fixtures are still valuable because this API is undocumented.

When capturing fixtures for tests:

- redact usernames
- redact passwords
- redact access tokens
- redact activation codes
- redact personally identifying account information
- keep only the response structure/fields needed by the parser tests

Do not commit raw account responses containing secrets.

## GitHub updater is separate

The app-update mechanism does **not** use AquaWiz infrastructure. It makes an unauthenticated request to:

```text
https://api.github.com/repos/stevestokes/AquaWizNotificationsApp/releases/latest
```

No AquaWiz credentials or access token are sent to GitHub.


## Notification integration

AquaWiz Notifier presents new measurements in an Alkatronic-inspired layout:

```text
[DEVICE_SERIAL] New measurement result:
n.nn dKH, n.nn pH
```

Optional notification values may include pH(O), ΔpH, and Dose (mL). Measurement notifications open AquaWiz Notifier. This behavior is separate from the AquaWiz cloud API and does not alter request payloads.

## In-app True Tank KH calibration

Verified against the official Android APK with SHA-256 `62a0ee7721a652f06f1363684ec8760e75d74a8e8a88be0b32a673ec6cb97d84` (Hermes bytecode v96): `CalibrateKhModal` function 29661 reads `Number(allFields.field10) / 1000`; its submit function 29664 calls `changeConfig({serial, field10: (Number(trueTankKh) * 1000).toString()})`. `changeConfig` function 29656 posts to `/api/v1/KH/start-config`, combining `user`, `token.access_token`, and those two fields. Only `field10` is written. This is a configuration request, not an immediate hardware SYNC command.

The official form uses decimal input, initializes from the server setting, and disables submission until the form is changed and valid. Zero disables calibration. The app's instructions tell the user to select `[SYNC]` on the KHA LCD for immediate application and allow about 30 minutes for calibration to finish. The notifier follows that sequence and does not modify historical measurement values.

The probe widget (`TankKhPhProbeWidget`, function 29327) reads `latest_ph`: below 100 is a health percentage, 100 through 999 is healthy, and 1000 or higher is failure. `latest_ph1` supplies the actual pH value. The notifier displays healthy status alongside 100%, caps the percentage at 100, and never interprets failure codes as percentages.

## KH target and dosing settings (v0.8.10)

Static provenance: same official APK/Hermes v96 bundle used for calibration. TargetKhSettingsModal (#30790) and submit (#30793), plus KhDosingSettingsModal (#29929) and submit (#29932), call changeConfig (#29656). Both POST `/api/v1/KH/start-config` with `user`, `token.access_token`, `serial`, and only the relevant settings.

| Sheet value | Server field | Encoding |
| --- | --- | --- |
| Target KH | field8 | dKH × 1000, string |
| Email KH deviation | field15 | dKH × 1000, string |
| Measurement interval and sleep | field13 | One interval digit + two sleep-start hour digits + two sleep-end hour digits, e.g. `12207` |
| Remaining solution | field14 | Whole mL, string |
| Email low-solution threshold | field16 | Whole mL, string |
| mL to increase 1 dKH | field5 | KH1: direct whole mL; other models: 3-digit mL prefix + preserved raw field5 % 1000 as 3 digits |
| Maximum dosing per hour | field6 | KH1: direct whole mL; other models: 3-digit mL prefix + preserved raw field6 % 10000 as 4 digits |

The APK picker offers intervals 1–6 and sleep hours 0–23. The native UI uses these same options. Sleep hours remain controller-local hour values; no timezone conversion is applied. Maximum dosing 0 stops dosing. The native client rereads current fields immediately before applying edits to preserve the latest packed calibration suffixes; it does not automatically retry hardware-setting writes. Missing settings prevent submission. These server email thresholds do not change the notifier’s local notification toggles.

### Measurement interval investigation (2026-10-05)

Rechecked the original APK's actual Hermes instructions. Function #30790 reads `field13.substring(0, 1)`, `(1, 3)`, and `(3, 5)` into interval/start/end. Function #30793 joins the interval, start padded to two digits, and end padded to two digits with an empty separator. There is no Date, timezone, UTC offset, or milliseconds conversion in this path. The notifier matches this contract: `32207` means 3 hours with sleep hours 22 and 07; `10000` means 1 hour with both sleep hours 00. The Config measurement interval is a separate local polling setting and does not write `field13`.

The APK's own settings instructions say changes apply at the next measurement cycle, or immediately through physical `[SYNC]`. A reading one hour after submitting a longer interval is therefore compatible with a previously scheduled cycle, but does not establish the cause. Sleep hours, device-clock configuration, delayed synchronization, and the server's actual returned values still require live evidence. No authenticated live controller response was available during this investigation; firmware scheduling behavior was not verified.

Previously the target sheet cached submitted values on any successful HTTP response. Target saves now perform one `all_field` readback and retain returned settings. A matching readback confirms only that AquaWiz returns the requested values; it does not confirm hardware application. Mismatched or failed reads are reported as an accepted request without confirmed values, and never cause an automatic repeat write. Tests cover interval encoding in Detroit, UTC, and Shanghai, matching/stale readback, readback auth failure, and exactly one write per submission.

### Future 08:00 point on app opening (2026-10-06)

User reproduction at 00:39 local time shows an extra 08:00 point with KH 8.18, pH 8.46 and pH(O) 8.35, while the official CSV ends at 00:34 with KH 8.312, pH 8.400 and pH(O) 8.340. The CSV's preceding 20:34, 21:34, 22:45 and 23:34 rows have arithmetic means KH 8.181, pH 8.4635 and pH(O) 8.3505 (delta 0.113), matching every displayed value of the extra point. This is strong evidence of an aggregate rather than an additional test, but the original raw API payload for that incident has not been captured. The 08:00 label could be an aggregate timestamp; its exact server origin remains unverified.

Confirmed code defects: `findLatest` traversed the entire graph response after extracting `results`, allowing dated statistics/metadata to compete with real rows, and API/cache paths accepted future timestamps. Home and History then persisted and selected the maximum timestamp. Opening Home calls both current and graph APIs without any settings write, so this ingestion path explains why changing the interval is unnecessary to reproduce the symptom.

Graph fallback now accepts only `results` rows. Current status accepts only explicit latest KH/time fields. API ingestion and stored-history reads reject measurements more than five minutes ahead of the device clock; cached future points are removed, their polling anchors repaired, and startup immediately restarts a displaced poll. Diagnostic messages identify current measurements and rejected graph timestamps/values without credentials. The tests use a constructed graph response to demonstrate the parser defect; it is not presented as the user's captured server response. No synthetic point is retimed into a real measurement, and historical rows that are already in the past are not blindly deleted.

### Status values are not measurement history (2026-10-08)

The official CSV contains no 08:00 row. Its six readings from 2026-10-07 21:34 through 2026-10-08 07:34 average to KH 8.044, pH 8.3295 and pH(O) 8.306333, matching the reported spurious point. At 09:40 it passes the future-date guard. The original API responses remain unavailable, so the exact endpoint supplying this point is unconfirmed.

Production ingestion now uses all_field only for controller settings. Current readings, notifications and baseline measurements come solely from graph results in the verified [timestamp, fields] row format. Object-shaped results and out-of-results metadata are ignored. Tests cover a past-dated latest-value aggregate in the status response and past-dated summary objects in results. This does not establish that all server graph array rows are individual tests; raw response verification is still needed if the point recurs. Already-cached past rows are not removed based on time alone.


### Authenticated graph capture, October 8, 2026

The downloaded graph response contains 12 `[epochMilliseconds, fields]` rows, from October 7 13:34 through October 8 11:34 America/Detroit. There is no 08:00 row. All 11 rows overlapping the earlier official CSV match its timestamps to the displayed minute, KH (`field22/1000`), tank pH (`field27/1000`), and aerated pH (`field28/1000`) exactly. The 09:46 raw dose is 100, matching 50 mL in the CSV after dividing by 2.

This capture supports using graph rows directly and independently confirms these units. It does not prove which endpoint returned the earlier average at the time it occurred: the graph capture happened later, and an authenticated all_field response has not been captured. Previously cached 08:00 points remain until separately repaired; the capture alone is insufficient to delete arbitrary historical rows.
