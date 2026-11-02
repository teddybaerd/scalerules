# Dauerhafte APK-Signatur

Der Workflow **Signed APK** erstellt einen Release-Build, signiert ihn mit deinem
festen Schlüssel und prüft Signatur, Zertifikats-Fingerabdruck, Paketname,
Versionsname, deaktivierten Debug-Modus und APK-Ausrichtung. Er veröffentlicht
noch kein GitHub Release; die geprüfte APK wird zunächst als Workflow-Artefakt
bereitgestellt. Es gibt keinen Play-Store-Schritt.

## 1. Schlüssel auf deinem Windows-PC erzeugen

Das Skript `scripts/New-ScaleRulesSigningKey.ps1` verwendet Windows-Bordmittel;
Java und Android Studio müssen dafür nicht installiert werden. Öffne eine normale
Windows PowerShell und lade das Skript aus dem überprüften `main`-Branch:

```powershell
$script = Join-Path $env:USERPROFILE 'Downloads\New-ScaleRulesSigningKey.ps1'
Invoke-WebRequest 'https://raw.githubusercontent.com/teddybaerd/scalerules/main/scripts/New-ScaleRulesSigningKey.ps1' -OutFile $script
notepad.exe $script
```

Starte danach das Skript. Die ExecutionPolicy-Option gilt nur für diesen Prozess:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script
```

Verwende ein starkes, im Passwortmanager gesichertes Passwort mit mindestens
12 Zeichen. Das Passwort wird verdeckt abgefragt und nicht in einer Datei gespeichert.
Das Skript erzeugt unter `%USERPROFILE%\ScaleRules-signing`:

| Datei | Bedeutung |
|---|---|
| `scalerules-release.p12` | Dauerhafter privater Schlüssel, mit AES-256 passwortgeschützt |
| `github-keystore-base64.txt` | Derselbe verschlüsselte Schlüssel als Base64, ebenfalls geheim |
| `scalerules-certificate.cer` | Öffentliches Zertifikat |
| `certificate-sha256.txt` | Öffentlicher SHA-256-Fingerabdruck des Zertifikats |

Der RSA-Schlüssel hat 4096 Bit; das selbstsignierte Zertifikat gilt 30 Jahre.
Das ursprüngliche Zertifikat bleibt zusätzlich im persönlichen Windows-Zertifikatsspeicher.
Das Skript verweigert das Überschreiben vorhandener Dateien. Einen vorhandenen
Schlüssel weiterverwenden, nicht bei jedem Release neu erzeugen.

**Sichere die `.p12`-Datei an einem zweiten Ort**, beispielsweise auf deinem NAS.
Bewahre das Passwort getrennt im Passwortmanager auf. Weder private Schlüsseldateien
noch Passwort in Issues, Pull Requests oder Chat-Nachrichten einfügen. Base64 ist
nur eine Transportkodierung, kein zusätzlicher Verschlüsselungsschutz.

## 2. GitHub-Environment einrichten

Öffne im Repository **Settings → Environments → New environment**.
Name exakt: **`release-signing`**. Dann **Configure environment**.

Unter **Deployment branches and tags**:

1. **Selected branches and tags** auswählen.
2. **Add deployment branch or tag rule** anklicken.
3. Typ **Branch**, Muster **`main`** hinzufügen.

Es sind keine Required reviewers oder Wartezeiten nötig. Die vorhandenen
Branch-Regeln bleiben aktiv. So können Feature-Branches die privaten
Signierdaten nicht verwenden.

## 3. Zwei Environment-Secrets und eine Variable hinterlegen

Auf derselben Environment-Seite unter **Environment secrets → Add environment secret**:

| Name | Wert |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Gesamter Inhalt von `github-keystore-base64.txt` |
| `ANDROID_KEYSTORE_PASSWORD` | Das beim Erzeugen verwendete Passwort |

Zum Kopieren der Schlüsseldatei ohne Terminal-Ausgabe:

```powershell
Get-Content "$env:USERPROFILE\ScaleRules-signing\github-keystore-base64.txt" -Raw | Set-Clipboard
```

Danach unter **Environment variables → Add environment variable**:

| Name | Wert |
|---|---|
| `ANDROID_SIGNING_CERT_SHA256` | Gesamter Inhalt von `certificate-sha256.txt` |

```powershell
Get-Content "$env:USERPROFILE\ScaleRules-signing\certificate-sha256.txt" -Raw | Set-Clipboard
```

Dieser Fingerabdruck ist öffentlich. Er verhindert, dass versehentlich ein anderer
Schlüssel für ein Update verwendet wird. Die GitHub-Anbindung des Assistenten kann
keine Secrets verwalten; diese Werte musst du selbst in GitHub eintragen.

## 4. Erste dauerhaft signierte APK bauen

Unter **Actions → Signed APK → Run workflow**:

- Branch **main**
- Version beispielsweise **0.1.0**

Die Ausgabe **`scalerules-0.1.0-signed`** enthält die APK, `SHA256SUMS`,
`signing-certificate-sha256.txt` und `build-info.json`. Der Signierjob verwendet
keine Schreibrechte auf das Repository. Er erhält die privaten Daten erst im
Environment-geschützten Job, baut dort keinen Quellcode und entfernt die temporäre
Schlüsseldatei vor dem Hochladen des Ergebnisses. Kein privater Schlüssel wird als
Artefakt hochgeladen.

Vorhandene CI-Debug-APKs sind weiterhin nur Entwicklungsartefakte und nicht der
Updatekanal. Der Übergang von der bisherigen Debug-Signatur erfordert einmalig
eine Neuinstallation mit erneuter Headscale-Anmeldung und Einrichtung der Regeln.
Danach immer APKs mit diesem dauerhaften Schlüssel und steigender Versionsnummer
verwenden. Die bisherigen Android-Versionscodes werden weiterhin aus der Build-Zeit
abgeleitet; aufeinanderfolgende Releases mit mindestens einer Minute Abstand bauen.
Den Schlüssel, das Passwort und den erwarteten Zertifikats-Fingerabdruck für
normale Updates unverändert lassen.

## Automatische Prüfung ohne deinen privaten Schlüssel

Die normale CI erzeugt nur kurzlebige Testschlüssel:

- Windows: Schlüsselerzeugung, Export und Einlesen mit Java sowie Schutz gegen Überschreiben.
- Android: tatsächlicher Release-Build.
- Linux: zweimaliges Signieren derselben APK mit demselben Testschlüssel, vollständige
  APK-Prüfung und bewusste Ablehnung eines falschen Zertifikats-Fingerabdrucks.

Diese Tests greifen nicht auf das Environment `release-signing` oder deine Secrets zu.
