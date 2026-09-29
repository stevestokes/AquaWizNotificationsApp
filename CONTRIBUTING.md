# Contributing

Thanks for helping improve AquaWiz Notifier.

## Development setup

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

## API changes

The AquaWiz cloud interface used here is undocumented. Please do not "fix" an endpoint or response parser based only on guesswork. For API compatibility changes, include one of:

- a redacted real response fixture,
- a reproducible static-analysis reference from a publicly distributed AquaWiz client, or
- a failing test that demonstrates the schema difference.

Never commit credentials, access tokens, activation codes, or personally identifying account data.

## Pull requests

Keep the app Android-first and dependency-light. Background behavior should remain deterministic from the last AquaWiz measurement timestamp rather than from the time Android happened to wake the worker.
