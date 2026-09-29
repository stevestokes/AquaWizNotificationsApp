# Architecture

## Goal

Notify once for every newly observed AquaWiz KH measurement, whether or not the value is outside an AquaWiz alert range.

## Poll timing

For an hourly measurement interval, a measurement at `T` anchors the next probes at:

- `T + 17m`
- `T + 32m`
- `T + 47m`
- `T + 62m`

The fourth probe is therefore eligible two minutes after the next expected hourly result. Any newly observed result immediately re-anchors the schedule to that result's server timestamp.

If there is still no new result at `T+62`, probes continue every 15 minutes from that sequence: `T+77`, `T+92`, and so on. A late result re-anchors the next four-probe sequence to the late result's actual measurement timestamp.

This is implemented as chained one-time WorkManager jobs rather than a periodic worker because the desired eligibility time moves whenever a measurement arrives. Android may defer actual execution due to Doze, standby buckets, connectivity, OEM battery management, and other OS scheduling constraints. The calculated time is therefore the earliest eligible time, not a real-time guarantee.

## Measurement flow

```text
WorkManager wakes
      |
      v
POST all_field  ---- parse/match selected serial ----+
      | failed/changed schema                         |
      v                                               |
GET serial-specific graph ---- conservative parse ----+
      |
      v
measurement fingerprint == stored fingerprint?
      | yes                         | no
      v                             v
schedule next probe          save + Android notification
                                    |
                                    v
                            re-anchor to measurement time
```

## Components

- `MainActivity`: simple setup/status UI.
- `AquaWizApi`: login, `all_field`, graph fallback, and HTTP error handling.
- `MeasurementJson`: serial-aware defensive response parser.
- `SecureStore`: AES-GCM encryption backed by Android Keystore for credentials/session plus monitoring state.
- `MeasurementWorker`: fetch, one-time token refresh, dedupe, notify, error recording, and rescheduling.
- `PollCadence`: pure scheduling math.
- `PollScheduler`: chained one-time WorkManager jobs with a connected-network constraint.
- `Notifier`: local Android notification channel and formatting.

## Notification semantics

The first reading after setup/sign-in is treated as the baseline and does **not** create a potentially stale notification. Every subsequently detected reading with a different fingerprint does.

Fingerprints prefer a server measurement ID when present; otherwise they use measurement timestamp + KH value. This prevents the 15-minute probes from creating duplicate notifications for the same AquaWiz result.

A notification shows the KH value, the AquaWiz measurement time, the change from the previous KH reading, and pH when the API provides it.

## Security

The app needs the AquaWiz password so it can perform the same login again when an undocumented cloud token expires. Username, password, token, and device list are serialized together and encrypted using AES-GCM with a non-exportable key stored in Android Keystore. Android backup is disabled in the manifest.

The project does not operate a relay server, analytics service, crash collector, or push-notification backend. AquaWiz credentials stay on the Android device and are sent only to the configured AquaWiz API host.
