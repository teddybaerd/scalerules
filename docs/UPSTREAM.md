# Upstream provenance

Repository: https://github.com/tailscale/tailscale-android
Release: 1.104.4-t938213198-gb9d487fe6
Commit: b9d487fe6d7203aeac889aeec2faccb9d32bb701

The imported release retains its original Git ancestry. Upstream workflows
were replaced by a foundation workflow with explicit permissions.
The VPN engine and pinned Go dependencies are unchanged.

For updates, merge a reviewed stable Android release and retain its matching
go.mod, go.sum and go.toolchain.rev together. Do not blindly run update-oss:
upstream's target tracks the development main branch.

Monitor https://tailscale.com/security-bulletins and releases in both
tailscale/tailscale-android and tailscale/tailscale.
Review platform/configuration impact, build, test, then publish a signed APK.
