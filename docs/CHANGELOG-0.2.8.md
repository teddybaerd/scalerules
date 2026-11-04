# ScaleRules 0.2.8 — VPN core maintenance / Wartung des VPN-Kerns

## English

- Update the VPN core from Tailscale 1.104.4 to 1.104.5 using the official Android release d8673104f600c05b8c20b64d0e70951ed670236a. Its only changes from our original upstream baseline are go.mod and go.sum. Go 1.27.3 and the Tailscale-Go revision remain unchanged.
- Upstream includes UDP reachability error handling, DNS request cancellation and randomized query IDs, TCP loss recovery changes, and SSH lifecycle corrections. Android device behavior needs validation; these are not all Android-specific changes.
- Remove the maintainer’s first name from the current About copyright line and README: © 2026 teddybaerd – ScaleRules. Preserve original Tailscale copyright and trademark notices.
- This update does not resolve GO-2026-5932, GO-2026-6860 or GO-2026-6866: x/crypto remains v0.58.0 and x/net remains v0.60.0. Source dependency analysis for all four Android architectures found none of the affected OpenPGP/HTML packages imported by libtailscale; see docs/GO-ADVISORY-ASSESSMENT-2026-11-04.md. Version-based monitoring remains active in issue #6. It is not presented as remediation for these advisories.

Validation pending: CI builds/tests/lint/signing checks; permanently signed upgrade preserving accounts/settings/rules; Headscale login, home LAN and Internet/DNS via exit node, Wi-Fi/mobile transitions, automation and reboot.

## Deutsch

- VPN-Kern von Tailscale 1.104.4 auf 1.104.5 aktualisiert, anhand des offiziellen Android-Releases d8673104f600c05b8c20b64d0e70951ed670236a. Gegenüber unserer ursprünglichen Upstream-Grundlage ändern sich ausschließlich go.mod und go.sum. Go 1.27.3 und die Tailscale-Go-Revision bleiben erhalten.
- Upstream enthält Änderungen zur Behandlung von UDP-Erreichbarkeitsfehlern, DNS-Abbruch und zufälligen Anfrage-IDs, TCP-Verlustbehandlung sowie SSH-Prozesskorrekturen. Die Wirkung auf Android ist im Gerätetest zu prüfen; nicht alle Änderungen betreffen Android.
- Vornamen aus dem aktuellen Copyright-Hinweis unter Über und aus der README entfernt: © 2026 teddybaerd – ScaleRules. Ursprüngliche Tailscale-Copyright- und Markenhinweise bleiben erhalten.
- GO-2026-5932, GO-2026-6860 und GO-2026-6866 werden dadurch nicht behoben: x/crypto bleibt v0.58.0, x/net bleibt v0.60.0. Die Quellpaketprüfung für alle vier Android-Architekturen zeigt: libtailscale importiert keines der betroffenen OpenPGP-/HTML-Pakete; siehe docs/GO-ADVISORY-ASSESSMENT-2026-11-04.md. Die Versionsüberwachung in Issue #6 bleibt aktiv. Das Update wird nicht als Behebung dieser Meldungen dargestellt.

Prüfung ausstehend: CI-Builds/Tests/Lint/Signatur; signiertes Update mit Erhalt von Konten/Einstellungen/Regeln; Headscale-Anmeldung, Heimnetz sowie Internet/DNS über Exit Node, WLAN-/Mobilfunkwechsel, Regelautomatik und Neustart.
