# Release checklist

Before publishing a tagged release:

1. Build with JDK 17, Android SDK Platform 36, and Build Tools 36.0.0.
2. Run `./scripts/verify.sh`.
3. Install the debug APK on a real Android device and grant notifications.
4. Sign in to a real AquaWiz account and verify the selected controller serial.
5. Confirm the current reading is established as a baseline without a notification.
6. Let the controller complete a new measurement and confirm exactly one notification appears.
7. Confirm repeated background polls do not duplicate that notification.
8. Confirm a 401/403 causes one automatic re-login and monitoring continues.
9. Test at least one device reboot and confirm WorkManager monitoring resumes.
10. Capture a redacted successful `all_field` or graph response as a fixture and add/update parser tests if the live schema differs from the static APK mapping.

Do not publish account credentials, access tokens, activation codes, or unredacted API responses.
