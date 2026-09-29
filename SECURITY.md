# Security

## Credential handling

AquaWiz Notifier stores the AquaWiz username, password, cloud access token, and returned device list in an AES-GCM encrypted blob. The encryption key is generated and held by Android Keystore and is not exported by the app. Android backup is disabled.

The password is retained because the app must be able to re-authenticate when the undocumented AquaWiz access token expires while the app is running in the background.

## Network behavior

The app has no project-owned backend. It sends AquaWiz credentials/tokens only to the selected AquaWiz API host (`server.aquawiz.net` or `server.aquawiz.cn`) over HTTPS.

## Reporting a vulnerability

For a public repository, use GitHub's private vulnerability reporting feature when available. Do not open a public issue containing credentials, tokens, raw account responses, or device activation information.
