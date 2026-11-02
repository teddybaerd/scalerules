# Sicherheits- und Upstream-Monitoring

Der Workflow **Security and upstream monitor** prüft täglich um 06:17 UTC sowie
bei relevanten Änderungen an `main`. Er kann unter Actions auch manuell gestartet
werden. Pull Requests führen dieselben Leseprüfungen aus, schreiben aber keine Issues.

## Quellen und Umfang

- Offizielle stabile GitHub-Releases von `tailscale/tailscale-android` und
  `tailscale/tailscale`, verglichen mit `docs/UPSTREAM.md` und `go.mod`.
- Offizieller Tailscale-Sicherheitsfeed. Neue Meldungen und inhaltliche Änderungen
  älterer Meldungen werden erfasst. Die erste Ausführung erstellt eine Bestandsaufnahme.
  Aufgelistete historische Meldungen sind **keine Liste offener Android-Lücken**.
- OSV-Abgleich der direkten und indirekten Go-Paketversionen aus `go.mod` sowie
  der dort genannten Go-Standardbibliotheksversion. Ersetzungen in `go.mod` lassen
  die Prüfung bewusst fehlschlagen, bis ihre Inventarisierung unterstützt wird.
- OSV-Abgleich der tatsächlich von Gradle aufgelösten Android-Release-Laufzeit-
  und Buildscript-Abhängigkeiten. Damit werden auch BOM-gesteuerte und indirekte
  Maven-Bibliotheken erfasst. Die Zuordnung zu Android-Laufzeit bzw. Build-Werkzeugen
  bleibt pro Paket im Bericht erhalten. Es wird keine APK gebaut und keine Version geändert.

Ein OSV-Treffer bedeutet eine passende Paketversion in der Datenbank. Er beweist
keine ausnutzbare Lücke in ScaleRules. Build-Tags, Aufrufpfade, Android-Plattform,
Vorkonfiguration und Patches im Tailscale-Go-Fork müssen geprüft werden. Das
Monitoring ersetzt weder Codeprüfung noch Gerätetests. Nicht erfasst sind unter
anderem unbekannte Schwachstellen, Android-/NDK-Systemkomponenten und Sicherheits-
korrekturen, die noch nicht in den beobachteten Quellen veröffentlicht wurden.

## Meldungen und Benachrichtigungen

Nach einer vollständigen erfolgreichen Prüfung aktualisiert ein separater Job ein
einziges Issue **ScaleRules: Sicherheits- und Upstream-Monitor** und weist es
`teddybaerd` zu. Das erste Issue enthält die Bestandsaufnahme. Bei Änderungen an
Releases, Sicherheitsmeldungen oder Paketbefunden ergänzt der Bot einen Kommentar;
ein geschlossenes Issue öffnet er nur bei solchen Änderungen erneut. Unveränderte
Tagesprüfungen aktualisieren ausschließlich den Zeitstempel/Bericht und erzeugen
keinen neuen Kommentar. Geschlossene, unveränderte Issues bleiben geschlossen.

GitHub-Benachrichtigungen für dieses Issue aktivieren bzw. abonniert lassen und
im eigenen GitHub-Konto die gewünschte E-Mail-/Mobile-Zustellung wählen. Das ist
eine persönliche GitHub-Einstellung; der Workflow richtet keinen externen
Mail-, ntfy- oder Chat-Zugang ein.

Quellenausfälle, leere oder unvollständige Daten, ungelöste Gradle-Abhängigkeiten
und unvollständige OSV-Seiten führen zu einem **fehlgeschlagenen Workflow**.
Der letzte erfolgreiche Bericht wird dann nicht mit einer Entwarnung überschrieben.
Der Zeitstempel im Issue bezeichnet deshalb ausdrücklich die letzte vollständig
erfolgreiche Prüfung. Auch Actions-Fehlerbenachrichtigungen sollten aktiviert sein.
GitHub kann Zeitpläne verzögern und nach längerer Repository-Inaktivität deaktivieren;
den aktuellen Betrieb unter Actions kontrollieren und bei Bedarf manuell starten.

Die vollständigen Berichte und die Maven-Inventarliste liegen 30 Tage als
Workflow-Artefakt vor. Sie enthalten öffentliche Paketnamen und -versionen,
keine Headscale-Anmeldungen, WLAN-Namen, Regeln oder privaten Signierschlüssel.
An OSV werden Paketkoordinaten und -versionen übermittelt.

## Dependabot

`.github/dependabot.yml` prüft wöchentlich GitHub Actions und die Android-/Gradle-
Abhängigkeiten. Kleinere Updates werden gruppiert, normale offene Vorschläge sind
auf drei pro Ökosystem begrenzt. Es gibt **kein Auto-Merge** und keine automatische
Veröffentlichung oder Installation.

Für Go sind normale Versions-PRs deaktiviert, damit der eng gekoppelte native Kern
über geprüfte Android-Upstream-Releases aktualisiert wird. Dependabot-Sicherheits-
PRs bleiben grundsätzlich möglich, sofern Dependency Graph, Dependabot Alerts und
Security Updates in den Repository-Einstellungen aktiviert sind. Gradle-Security-
Updates benötigen zusätzlich einen eingereichten Dependency Graph; dieser Workflow
richtet keine Dependency Submission ein. Die tägliche OSV-Prüfung arbeitet davon
unabhängig und ist hier die zusätzliche Sicherheitsüberwachung für Go und Maven.

Auch ein automatisch vorgeschlagener Go-Sicherheits-PR muss gegen den passenden
Android-Upstream-Stand geprüft werden. `go.mod`, `go.sum` und `go.toolchain.rev`
nicht ungeprüft voneinander lösen. Neue normale Android-/Actions-Vorschläge durch
Dependabot sind Prüfangebote und dürfen ebenfalls erst nach CI und Review hinein.

## Reaktion auf einen Befund

1. Quelle lesen, betroffene Version und Plattform/Konfiguration feststellen.
2. Falls relevant: passende stabile Android-Upstream-Version prüfen und die
   Provenienz in `docs/UPSTREAM.md` aktualisieren. Nicht blind `make update-oss`
   ausführen, weil dieses Ziel dem Entwicklungszweig folgt.
3. ScaleRules-Anpassungen erhalten; CI, Signaturprüfung und Gerätetest durchführen.
4. Neue signierte APK mit Versionshinweisen als GitHub-Release veröffentlichen.
5. Befund im Monitor-Issue nachvollziehbar bewerten; gegebenenfalls schließen.
   Es wird bei einer späteren Änderung wieder geöffnet.

## Berechtigungen und Prüfung der Überwachung

Der Scan hat nur Lesezugriff auf Repository-Inhalte und keinen Zugriff auf die
Umgebung `release-signing`. Der Veröffentlichungsjob bekommt zusätzlich
`issues: write`, nur für `main` und niemals für einen Pull Request. Er verarbeitet
ausschließlich den Bericht desselben erfolgreich abgeschlossenen Workflows.
Keine Commit-, Release-, Paket-, Deployment- oder Signierrechte.

Lokale Logiktests: `python3 -m unittest discover -s tests -v`.
Nach Änderungen den Monitor in CI vollständig ausführen. OSV-Treffer lassen den
Scan erfolgreich enden, werden aber im Bericht aufgeführt; Daten-/Quellenfehler
lassen ihn fehlschlagen. Deshalb ist „grüner Scan“ keine Aussage „keine Befunde“.

## Offizielle Referenzen

- https://tailscale.com/security-bulletins
- https://tailscale.com/security-bulletins/index.xml
- https://tailscale.com/security-policies/incident-disclosure
- https://google.github.io/osv.dev/post-v1-querybatch/
- https://docs.github.com/en/code-security/reference/supply-chain-security/supported-ecosystems-and-repositories
- https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/configure-dependabot/configure-dependabot-version-updates
