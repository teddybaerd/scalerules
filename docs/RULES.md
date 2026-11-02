# Manual connection rules

Open Settings > Verbindungsregeln. No rules are created automatically. There is
no learning function. Create rules, set their order, then explicitly start automation.

Conditions: exact Wi-Fi SSID, any Wi-Fi, mobile, offline, any connected network.
Actions: connect, disconnect, or preserve the current VPN state.
The first enabled matching rule wins. No match preserves the current state.
Warnings identify rules completely hidden by earlier rules, including conflicting actions.
A specific SSID exception should precede a general Wi-Fi rule.

Every configuration change pauses automation. Review changes and start it again.
Pausing never disconnects the VPN. A separate foreground notification permits pausing
while the VPN itself is off. Physical networks are chosen with the same selection
logic as the existing Tailscale client; the VPN interface is excluded.

SSID rules require precise location permission and Android location enabled, solely
for reading the Wi-Fi name. No GPS locations or network scans are collected.
If an SSID cannot be read while SSID rules are enabled, Wi-Fi evaluation preserves
the VPN state rather than falling through to a general rule.

Decisions are debounced for two seconds and only reapplied when the network context,
selected rule, or rule configuration changes. Manual VPN changes do not cause a
continuous reconnect fight. The journal contains the latest 200 decisions and requests;
a connection request is not proof that the tunnel successfully established.
Exit node, DNS and app exclusions are not modified.

## Test milestone limitations

- Automation must be resumed explicitly after reboot, process termination or force-stop.
- No import/export, Bluetooth, time, battery, or combined conditions in this first version.
- No automated learning and no remote rules.
- SSID names alone do not authenticate a Wi-Fi access point.
- Device tests still required: mobile/Wi-Fi transitions, unknown SSID, permission denial,
  screen off, Samsung background restrictions, pause and rapid network changes.
- Production signing and the in-app updater are separate milestones. CI debug builds
  may use different signing certificates and cannot be assumed to update existing installs.

## Languages and rule editor

English is the default fallback; German devices use the complete German string
resources. Android 13+ also exposes English and German in the system app-language
settings. Rule names and SSIDs remain user data and are never translated. Existing
journal entries retain the language in which they were recorded; new entries use
the app language. Diagnostic messages supplied by the VPN backend are not translated.

Condition and VPN action are labeled, outlined dropdown fields with an arrow and
a tap hint. Their full field area opens the menu without opening a keyboard.

## Copy the current Wi-Fi into a rule

For a specific-SSID condition, **Use current Wi-Fi / Aktuelles WLAN übernehmen**
reads the connected Wi-Fi name only after an explicit tap and puts it in the
current draft. Save explicitly to persist the rule; cancelling discards the draft.
Manual entry remains available. Failures preserve any existing SSID text.

The button can request Android coarse/precise location permission together;
precise permission and enabled Android location services are required to read
SSID information. If permission is denied, settings can be opened from the editor.
After changing settings, tap the capture button again. No Wi-Fi scans, GPS reads,
background listeners or VPN changes are performed by this button.

## Background-operation setup

A one-time optional setup prompt opens Android battery optimization settings after
the welcome screen, or when first opening rules. Existing installations see it
once when this feature is introduced. Managed onboarding suppresses the prompt.
Later/dismiss never blocks login, VPN or rule setup. Instructions remain available
under Settings > Background operation and in the rules screen.

The page refreshes Android battery exemption status on resume. Samsung standby
lists require separate manual verification; the documented Samsung intent opens
Never sleeping apps when supported, with app-info fallback. No exemption is granted
automatically, and global battery-saving policies are not changed. There is no
new permission for requesting a direct battery exemption. Device checks are needed
for OEM settings links and foreground/background operation after configuration.

References: https://developer.android.com/training/monitoring-device-state/doze-standby
and https://developer.samsung.com/mobile/app-management.html.
