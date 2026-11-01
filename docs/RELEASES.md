# Distribution and update requirements

Distribution is exclusively through GitHub Releases. All installation paths
must use the same application ID and production signing certificate:
in-app updater, manual GitHub download and Obtainium.

Before the first release, create and securely back up a production signing
key. Never commit private keys or passwords. Debug CI keys are disposable.

Planned updater:
- Show version and release notes before download.
- Install now, remind later or skip that version.
- Download the APK with progress and cancellation.
- Check package ID, newer version and expected signing certificate.
- Hand installation to Android's package installer with user confirmation.
- Support disabling automatic checks and checking manually.
- Exclude prereleases unless explicitly opted in.
- Warn that updating the app can briefly interrupt its VPN connection.

Security maintenance will combine official bulletins, upstream release
monitoring and dependency checks. These are requirements, not implemented
automation in the foundation milestone.
