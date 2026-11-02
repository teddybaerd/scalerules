**This README is bilingual: [English](#english) first, followed by [Deutsch](#deutsch).**  
**Diese README ist zweisprachig: zuerst [Englisch](#english), darunter [Deutsch](#deutsch).**

<p align="center">
  <img src="docs/assets/scalerules-icon.png" width="160" alt="ScaleRules: four bright dots and a gray gear on a dark background" />
</p>

# ScaleRules

<a id="english"></a>

# English

ScaleRules is an independent Android VPN client for **Tailscale and Headscale**, with automatic connection rules that you define yourself.

Signed APKs are available from [GitHub Releases](https://github.com/teddybaerd/scalerules/releases). Distribution is through GitHub, the built-in updater or Obtainium. Publication on the Google Play Store is not planned.

## Features

### English and German

The app interface, connection rules and related notifications are available in **English and German**. The language follows your Android settings; Android 13 and later also let you select it through the system's per-app language settings.

Your rule names and Wi-Fi names remain unchanged. Existing journal entries retain their original language. Technical diagnostics supplied by the VPN engine may still appear in English.

### Connection rules with priorities

Under **Settings → Connection rules**, you define conditions and actions:

- Conditions: a specific Wi-Fi SSID, any Wi-Fi, mobile data, offline or any connected network.
- Actions: connect the VPN, disconnect it or leave its current state unchanged.
- The order determines priority: **the first enabled matching rule wins.**
- Without a matching rule, the VPN state stays unchanged.
- Warnings help identify conflicting rules and rules hidden by earlier rules.
- A decision journal records recent automation actions.

There is **no automatic learning feature**. Editing the rules pauses automation, which you must explicitly resume. You also need to restart automation after rebooting the device or terminating its automation process.

For SSID rules, Android requires precise location permission and enabled location services to provide the Wi-Fi name. An SSID alone does not establish that a Wi-Fi network is trustworthy.

More details: [Connection rules](docs/RULES.md).

### Split tunneling and default exclusions

Under **Settings → Split tunneling**, you can choose which apps use or bypass the VPN.

In the normal exclusion configuration, the built-in exclusions inherited from Tailscale include these apps when installed:

| App | Package name |
| --- | --- |
| Android Auto | `com.google.android.projection.gearhead` |
| Google Home / Chromecast | `com.google.android.apps.chromecast.app` |
| Google Messages | `com.google.android.apps.messaging` |

These apps send their traffic **outside the VPN tunnel**, using the normal network connection. This also applies when an exit node is selected; DNS and ad filtering provided through the VPN path therefore do not apply to excluded apps.

These default exclusions are inherited from Tailscale Android. Additional exclusions, such as Sonos, are also included. The respective inclusion and exclusion rules apply when switching to “only selected apps through VPN” or using managed MDM configurations.

### In-app updates

Under **Settings → App updates**, you can:

- manually check for a newer stable GitHub release;
- read its changelog inside the app;
- download the APK and install it through Android after verification;
- postpone an update or skip a version.

Automatic checks run at most once a day when opening or returning to the app. You can disable them, for example when using Obtainium. A manual check also shows previously skipped versions.

Before handing the APK to Android, the app checks its checksum, size, package name, version and signing certificate. Android asks you to confirm installation. Future releases use the same permanent signing key so that updates retain your settings and rules.

Details: [Updater and release requirements](docs/UPDATES.md).

## Changes in previous builds

- **Early test builds:** Tailscale/Headscale foundation, user-defined connection rules with priorities, warnings and a decision journal.
- **Revised interface:** English and German texts, plus clearly identifiable selection fields for network conditions and VPN actions.
- **0.1.0:** first version signed with the permanent key used for subsequent updates.
- **0.2.0:** custom gear icon and built-in GitHub updater.
- **0.2.1:** first follow-up release for testing the complete in-app update path; the same feature set as 0.2.0. The update was successfully tested on a device.
- **Repository monitoring:** daily checks of official Tailscale releases, security bulletins and dependencies, plus Dependabot proposals for Android and GitHub Actions.

Split tunneling, including its built-in app exclusions, is part of the inherited Tailscale foundation.

See the [release notes](https://github.com/teddybaerd/scalerules/releases) for further information about published APKs.

## Installation and updates

1. Download and install the signed APK from [GitHub Releases](https://github.com/teddybaerd/scalerules/releases).
2. Set up Tailscale or your own Headscale server.
3. Configure an exit node, DNS, split tunneling and connection rules as needed.

To update an existing permanently signed ScaleRules installation, install the new APK over the existing app. **Do not uninstall it first**, so your configuration is retained.

Temporary CI debug APKs use different signing keys and are not a permanent update channel. Moving from such a test build to the signed release app may require a one-time uninstall and reconfiguration.

## Security and development

The current foundation is **Tailscale Android 1.104.4**. ScaleRules preserves the original Git history and license notices. Its Android application ID is `io.github.teddybaerd.scalerules`; the internal Kotlin/Java namespace is retained to simplify upstream updates.

The [security and upstream monitor](https://github.com/teddybaerd/scalerules/issues/6) collects new bulletins and review candidates. Matching a package version to an advisory does not establish an exploitable Android vulnerability. VPN engine changes are reviewed and tested before adoption.

- [Provenance and upstream updates](docs/UPSTREAM.md)
- [Security monitoring and its limitations](docs/SECURITY-MONITORING.md)
- [Permanent signing and key backup](docs/SIGNING.md)
- [Upstream build instructions](docs/UPSTREAM_README.md)
- [Build and test workflow](.github/workflows/foundation.yml)

## Collaboration, license and attribution

ScaleRules is maintained by **teddybaerd**. **OpenAI ChatGPT/Codex assisted with planning, implementation, debugging, testing and documentation.** The custom app icon was also created with assistance from OpenAI image generation.

The original Tailscale authors retain their copyright and license notices. Tailscale Android is licensed under **BSD-3-Clause**; see [LICENSE](LICENSE).

The idea of user-defined connection rules was inspired by Tailscale Auto Rules. Its code has not been incorporated; the ScaleRules rule engine was implemented for this project.

ScaleRules is an independent project and is neither published nor endorsed by Tailscale Inc. The names Tailscale and Headscale identify compatibility.

---

<a id="deutsch"></a>

# Deutsch

ScaleRules ist ein unabhängiger Android-VPN-Client für **Tailscale und Headscale** mit selbst festgelegten automatischen Verbindungsregeln.

Signierte APKs stehen unter [GitHub Releases](https://github.com/teddybaerd/scalerules/releases) bereit. Die Verteilung erfolgt über GitHub, den integrierten Updater oder Obtainium. Eine Veröffentlichung im Google Play Store ist nicht vorgesehen.

## Funktionen

### Deutsch und Englisch

Die App-Oberfläche, Verbindungsregeln und zugehörigen Benachrichtigungen stehen auf **Deutsch und Englisch** zur Verfügung. Die Sprache folgt der Android-Einstellung; ab Android 13 kannst du sie auch über die System-Einstellungen für die App-Sprache auswählen.

Eigene Regelnamen und WLAN-Namen werden unverändert übernommen. Bereits gespeicherte Protokolleinträge behalten ihre ursprüngliche Sprache. Technische Diagnosemeldungen des VPN-Kerns können weiterhin englisch sein.

### Verbindungsregeln mit Priorität

Unter **Einstellungen → Verbindungsregeln** legst du Bedingungen und Aktionen selbst fest:

- Bedingungen: bestimmtes WLAN anhand seiner SSID, beliebiges WLAN, Mobilfunk, offline oder beliebiges verbundenes Netzwerk.
- Aktionen: VPN verbinden, VPN trennen oder den aktuellen Zustand unverändert lassen.
- Die Reihenfolge legt die Priorität fest: **Die erste aktivierte passende Regel gewinnt.**
- Ohne passende Regel bleibt der VPN-Zustand unverändert.
- Warnungen helfen, widersprüchliche oder durch frühere Regeln verdeckte Regeln zu erkennen.
- Ein Entscheidungsprotokoll macht die letzten Automatik-Aktionen nachvollziehbar.

Es gibt **keine automatische Lernfunktion**. Nach Änderungen an den Regeln ist die Automatik pausiert und muss bewusst wieder gestartet werden. Auch nach einem Geräteneustart oder dem Beenden des Automatik-Prozesses ist ein erneuter Start erforderlich.

Für SSID-Regeln benötigt Android die Berechtigung für den genauen Standort und aktivierte Standortdienste, um den WLAN-Namen bereitzustellen. Ein SSID-Name allein bestätigt nicht die Vertrauenswürdigkeit eines WLANs.

Weitere Details: [Verbindungsregeln](docs/RULES.md).

### Split-Tunneling und voreingestellte Ausnahmen

Über **Einstellungen → Split-Tunneling** kannst du festlegen, welche Apps den VPN verwenden oder umgehen.

Die vom Tailscale-Client übernommenen eingebauten Ausnahmen berücksichtigen in der normalen Ausschluss-Konfiguration unter anderem folgende Apps, sofern sie installiert sind:

| App | Paketname |
| --- | --- |
| Android Auto | `com.google.android.projection.gearhead` |
| Google Home / Chromecast | `com.google.android.apps.chromecast.app` |
| Google Messages | `com.google.android.apps.messaging` |

Der Netzwerkverkehr dieser Apps läuft damit **außerhalb des VPN-Tunnels** über die normale Netzwerkverbindung. Das gilt auch bei ausgewähltem Exit Node; DNS- und Werbefilterung über den VPN-Pfad greift für ausgeschlossene Apps entsprechend nicht.

Die eingebauten Ausnahmen stammen bereits aus Tailscale Android. Weitere Ausnahmen, etwa für Sonos, sind ebenfalls enthalten. Bei einem Wechsel zum Modus „nur ausgewählte Apps über VPN“ sowie bei verwalteten MDM-Konfigurationen gelten die jeweiligen Ein- und Ausschlussregeln.

### In-App-Updates

Unter **Einstellungen → App-Updates** kannst du:

- manuell nach einer neuen stabilen GitHub-Version suchen;
- das Änderungsprotokoll direkt in der App lesen;
- die APK herunterladen und nach Prüfung über Android installieren;
- ein Update auf später verschieben oder eine Version überspringen.

Automatische Prüfungen erfolgen beim Öffnen bzw. Zurückkehren zur App höchstens einmal täglich. Sie sind abschaltbar, beispielsweise wenn du Obtainium bevorzugst. Eine manuelle Prüfung zeigt auch zuvor übersprungene Versionen wieder an.

Vor der Übergabe an Android werden Prüfsumme, Dateigröße, Paketname, Version und Signierzertifikat geprüft. Android fordert deine Installationsbestätigung an. Künftige Releases verwenden denselben dauerhaften Signierschlüssel, sodass Einstellungen und Regeln beim Update erhalten bleiben.

Details: [Update-Funktion und Release-Anforderungen](docs/UPDATES.md).

## Änderungen der bisherigen Builds

- **Erste Test-Builds:** Tailscale-/Headscale-Grundlage, selbst festgelegte Verbindungsregeln mit Priorität, Warnungen und Entscheidungsprotokoll.
- **Überarbeitete Oberfläche:** deutsche und englische Texte sowie klar erkennbare Auswahlfelder für Netzwerkbedingungen und VPN-Aktionen.
- **0.1.0:** erste Version mit dauerhaftem Signierschlüssel als Grundlage für nachfolgende Updates.
- **0.2.0:** eigenes Zahnrad-Icon und integrierte GitHub-Update-Funktion.
- **0.2.1:** erste Folgeveröffentlichung zum Praxistest des vollständigen In-App-Updatewegs; gleicher Funktionsumfang wie 0.2.0. Das Update wurde auf einem Gerät erfolgreich getestet.
- **Repository-Monitoring:** tägliche Prüfung offizieller Tailscale-Releases, Sicherheitsmeldungen und Abhängigkeiten sowie Dependabot-Vorschläge für Android und GitHub Actions.

Split-Tunneling einschließlich der eingebauten App-Ausnahmen gehört zur übernommenen Tailscale-Grundlage.

Die jeweiligen [Release-Hinweise](https://github.com/teddybaerd/scalerules/releases) enthalten weitere Informationen zu den veröffentlichten APKs.

## Installation und Updates

1. Die signierte APK aus den [GitHub Releases](https://github.com/teddybaerd/scalerules/releases) herunterladen und installieren.
2. Tailscale oder den eigenen Headscale-Server einrichten.
3. Bei Bedarf Exit Node, DNS, Split-Tunneling und Verbindungsregeln konfigurieren.

Beim Update einer bereits dauerhaft signierten ScaleRules-Version die neue APK über die vorhandene App installieren. **Nicht vorher deinstallieren**, damit die Einrichtung erhalten bleibt.

Temporäre CI-Debug-APKs verwenden andere Signierschlüssel und sind kein dauerhafter Update-Kanal. Der einmalige Wechsel von einer solchen Testversion zur signierten Release-App kann eine Deinstallation und Neueinrichtung erfordern.

## Sicherheit und Weiterentwicklung

Die aktuelle Grundlage ist **Tailscale Android 1.104.4**. ScaleRules behält die ursprüngliche Git-Historie und die Lizenzhinweise bei. Die Android-Anwendungs-ID lautet `io.github.teddybaerd.scalerules`; der interne Kotlin-/Java-Namensraum bleibt zur einfacheren Übernahme von Upstream-Änderungen erhalten.

Der [Sicherheits- und Upstream-Monitor](https://github.com/teddybaerd/scalerules/issues/6) sammelt neue Meldungen und Prüfkandidaten. Paketversions-Treffer sind kein Nachweis einer ausnutzbaren Android-Schwachstelle. Änderungen am VPN-Kern werden geprüft und getestet übernommen.

- [Herkunft und Upstream-Updates](docs/UPSTREAM.md)
- [Sicherheitsüberwachung und ihre Grenzen](docs/SECURITY-MONITORING.md)
- [Dauerhafte Signatur und Schlüsselsicherung](docs/SIGNING.md)
- [Upstream-Bauanleitung](docs/UPSTREAM_README.md)
- [Build- und Test-Workflow](.github/workflows/foundation.yml)

## Zusammenarbeit, Lizenz und Herkunft

ScaleRules wird von **teddybaerd** gepflegt. **OpenAI ChatGPT/Codex hat bei Planung, Umsetzung, Fehlersuche, Tests und Dokumentation geholfen.** Auch das eigene App-Icon entstand mit Unterstützung der OpenAI-Bildgenerierung.

Die ursprünglichen Tailscale-Autoren behalten ihre Urheber- und Lizenzhinweise. Tailscale Android steht unter der **BSD-3-Clause-Lizenz**; siehe [LICENSE](LICENSE).

Die Idee nutzerdefinierter Verbindungsregeln orientiert sich an Tailscale Auto Rules. Dessen Code wurde bislang nicht übernommen; die ScaleRules-Regellogik wurde für dieses Projekt implementiert.

ScaleRules ist ein unabhängiges Projekt und wird von Tailscale Inc. weder herausgegeben noch unterstützt. Die Namen Tailscale und Headscale beschreiben die Kompatibilität.
