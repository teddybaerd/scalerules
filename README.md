<p align="center">
  <img src="docs/assets/scalerules-icon.png" width="160" alt="ScaleRules logo: four bright corner dots and a gray gear on charcoal" />
</p>

# ScaleRules

Android VPN client for Tailscale and Headscale with automatic connection rules.

**Development foundation — no production release yet.** This first milestone
establishes a buildable Tailscale Android fork. Manual connection rules are implemented on the feature branch; the in-app
updater is planned. See [manual rules](docs/RULES.md) for behavior and test limits.

## Foundation

- Based on Tailscale Android 1.104.4.
- Application ID: io.github.teddybaerd.scalerules.
- Existing upstream Kotlin/Java namespace is retained to simplify updates.
- Original Git history and BSD-3-Clause license are preserved.
- CI builds and tests a debug APK; it does not publish a release.
- Some inherited branding remains during development.
- Built with assistance from OpenAI ChatGPT/Codex; upstream authors retain credit.

## Planned milestones

1. Verify the foundation build and Headscale login on a physical Android device.
2. Integrate trusted Wi-Fi/mobile connection rules and a decision journal.
3. Test DNS, exit nodes, split tunneling, Android Auto and background operation.
4. Add GitHub release notes, direct APK download, install, postpone and skip.
5. Publish signed APKs on GitHub Releases, compatible with manual installs and Obtainium.

No Google Play distribution is planned. Users will be able to disable the
built-in update checks when using Obtainium.

## Build

See [upstream build instructions](docs/UPSTREAM_README.md) and
[the foundation workflow](.github/workflows/foundation.yml).
CI artifacts are temporary debug builds with a disposable signing key.
They are not suitable as the long-term update channel.

## License and attribution

Tailscale Android is BSD-3-Clause licensed; see [LICENSE](LICENSE).
ScaleRules is an independent project, not affiliated with or endorsed by
Tailscale Inc. Tailscale and Headscale names identify interoperability.
Tailscale Auto Rules is a planned source for MIT-licensed rule logic;
its code has not yet been incorporated. Its license and attribution will
be included when that code is imported.

## Permanently signed APKs

See [signing setup (German)](docs/SIGNING.md) to create and back up your own key on
Windows and configure the main-only GitHub signing environment. The **Signed APK**
workflow produces verified release APKs with a stable certificate. Normal CI debug
artifacts remain disposable and are not the update channel.
