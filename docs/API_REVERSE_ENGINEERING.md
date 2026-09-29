# AquaWiz API notes

These notes were derived from static analysis of the publicly distributed AquaWiz Android APK supplied for this project. This is an unofficial integration and is not affiliated with or endorsed by AquaWiz.

The APK is a React Native/Expo application compiled to Hermes bytecode version 96. Hermes v96 has the same opcode set as v95, which allowed the relevant network request construction to be reconstructed directly rather than guessed from strings alone.

## Confirmed from the APK

### Servers

- Global API base: `https://server.aquawiz.net`
- China API base: `https://server.aquawiz.cn`

### Authentication

```http
POST /api/v1/KH/auth
Content-Type: application/json

{"username":"<username>","password":"<password>"}
```

The official app consumes an `access_token` from the successful response. Authenticated calls use:

```http
Authorization: Bearer <access_token>
```

The auth model also references a `user` object, `devices`, `username`, `email`, `uitimezone`, and token-expiry state.

### Current values / all fields

The official app constructs this request:

```http
POST /api/v1/KH/{username}/all_field
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "user": "<username>",
  "token": {
    "access_token": "<access_token>"
  }
}
```

The request body was reconstructed from the Hermes instructions that build the nested object immediately before `JSON.stringify`; the access-token register is explicitly copied into the nested `access_token` property.

The official device page reads these properties from its current device-data object:

- `latest_kh`
- `latest_ph`
- `latest_ph1`
- `latest_time`

AquaWiz Notifier prefers this route because it exposes the exact current-value property names used by the official app. Since `all_field` is account-level, the parser prefers data associated with the configured device serial and fails closed if multiple explicitly identified devices are present but none matches.

### Device graph/history

The official app also constructs:

```http
GET /api/v1/query/device/{deviceSerial}/graph?date={ISO-8601}
Authorization: Bearer <access_token>
Content-Type: application/json
```

The graph request is explicitly serial-specific. AquaWiz Notifier keeps it as a fallback if the undocumented `all_field` response changes or does not expose a parseable current value.

## Parsing policy

Because this is an undocumented cloud API, the client is intentionally conservative:

1. Prefer explicit fields such as `latest_kh` + `latest_time`.
2. Prefer a candidate whose serial matches the configured controller.
3. Reject implausible KH values outside 2–20 dKH.
4. Only infer KH from opaque graph fields when exactly one plausible value exists next to a valid timestamp.
5. If multiple explicitly identified devices exist and no serial matches, do not guess.
6. A 401/403 triggers one normal re-login using the encrypted stored credentials; other failures are surfaced in the app status and retried later.

Naive date/time strings (no UTC offset) are interpreted in the Android device's local timezone. ISO timestamps with an explicit offset or `Z` retain their supplied timezone.

## Release validation

Static analysis establishes the request contract, but the project should still capture a **redacted successful response fixture** from a real AquaWiz account before calling the API integration stable. A fixture lets tests pin the live response shape without storing credentials or access tokens.

Never commit usernames, passwords, access tokens, device activation codes, or unredacted account responses.
