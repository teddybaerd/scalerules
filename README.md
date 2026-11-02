<p align="center">
  <img src="docs/assets/scalerules-icon.png" width="160" alt="ScaleRules: vier helle Punkte und ein graues Zahnrad auf dunklem Hintergrund" />
</p>

# ScaleRules

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
