# ScaleRules 0.2.7

## English

- Update Android dependencies and Compose, build with compile SDK 37.2 (target 36, minimum 26).
- Check stable GitHub releases periodically with WorkManager when automatic checks are enabled. Notify once per newer version; tapping opens app updates with the changelog. No background download or installation. Checks are network constrained and Android may delay them.
- Add an opt-in start-after-reboot setting for rule automation. Resume only when previously started and not explicitly paused or edited. SSID rules need precise and background location; otherwise show a tap-to-resume notification. Runs after normal BOOT_COMPLETED, not before unlock; force-stop prevents recovery. Location services and Samsung battery restrictions still apply. The VPN follows the normal rule evaluator.
- Add © 2026 Markus / teddybaerd – ScaleRules in About while retaining original Tailscale copyright and trademark notices.

Device checks pending: update notification with automatic checks on/off and notifications allowed/denied; tapping opens update details; reboot with automation active/paused and boot option on/off; SSID rules with/without background permission; no premature VPN activation for unknown SSID; signed update preserving accounts/rules.

## Deutsch

- Android-Abhängigkeiten und Compose aktualisiert, Build mit compile SDK 37.2 (Ziel 36, Minimum 26).
- Bei aktivierter automatischer Updatesuche regelmäßig im Hintergrund nach stabilen GitHub-Releases suchen. Ein Hinweis je neuer Version; Antippen öffnet App-Updates mit Changelog. Kein automatischer Download oder Installation. Android kann die netzabhängigen Prüfungen verzögern.
- Optionalen Start der Regelautomatik nach einem Geräteneustart ergänzt. Nur zuvor gestartete, nicht ausdrücklich pausierte oder durch Bearbeiten angehaltene Automatik fortsetzen. WLAN-Namensregeln benötigen genaue Standort- und Hintergrundfreigabe; andernfalls führt ein Hinweis zum manuellen Fortsetzen. Start nach normalem BOOT_COMPLETED, nicht vor Entsperrung; Stopp erzwingen verhindert die Wiederaufnahme. Standortdienste und Samsung-Akkubeschränkungen gelten weiterhin. Der normale Regelentscheid steuert das VPN.
- © 2026 Markus / teddybaerd – ScaleRules unter Über ergänzt; ursprüngliche Tailscale-Copyright- und Markenhinweise erhalten.

Gerätetests ausstehend: Updatehinweis bei automatischer Suche an/aus und erlaubten/verweigerten Benachrichtigungen, Antippen; Neustart bei aktiver/pausierter Automatik und Autostart an/aus; WLAN-Regeln mit/ohne Hintergrundfreigabe; keine verfrühte VPN-Aktivierung bei unbekannter SSID; signiertes Update mit Erhalt der Konten/Regeln.
