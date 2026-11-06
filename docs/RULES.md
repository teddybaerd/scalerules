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

A one-time optional setup prompt opens the ScaleRules app-info screen on every manufacturer, with guidance to disable unused-app permission removal. Battery optimization remains separate. The prompt appears after
the welcome screen, or when first opening rules. Existing installations see it
once when this app-info guidance is introduced, including users who saw the earlier battery-list prompt. Managed onboarding suppresses the prompt.
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


## Experimental exit-node failover

In Settings > Connection rules, choose a preferred and backup exit node from the
current account, then enable failover and resume automation. Select the preferred
node once in the normal exit-node picker. Defaults: 30-second grace period,
three failed probes, automatic return with a 120-second confirmation delay.
Changing failover settings pauses the rule service just like editing rules.

The monitor consumes the VPN engine's existing peer-online events. Healthy steady
state adds no polling timer, probes, wake lock or new permissions. An explicit
offline preferred node plus an online backup starts a bounded confirmation burst:
after the grace period, the preferred node must fail every probe (3 seconds apart),
and the backup must answer a Tailscale disco ping before selection changes. A
successful primary probe aborts that burst. Unresolved incidents retry after
60 seconds. For automatic return, the preferred node must be reported online and
answer before and after the configured return delay. Status changes cancel and
restart the confirmation; unrelated peer changes do not reset it. This is two
reachability samples, not continuous reachability verification.

The monitor runs only while rule automation, the VPN and its engine are active,
a non-VPN internet-capable network is available, and the selected exit node is
one of the configured pair. A matching disconnect rule suppresses it. VPN stops,
network changes, account changes and automation pause cancel pending decisions.
The pair is bound to the control URL and local node's stable ID. Choosing a third
exit node or disabling exit-node use suspends failover. Automatic switching edits
only ExitNodeID and records successes/errors in the existing decision journal.

If neither node is available, the monitor leaves the selection and VPN enabled;
it never falls back to unencrypted internet. Existing split-tunnel exclusions still
apply. Forwarding failures where a node remains reported online are not detected.
Online status reflects the control connection and can be delayed. Disco reachability
is not a guarantee that DNS or internet forwarding works. Configure DNS so it also
works if the preferred site is unavailable; this feature does not change DNS.
Existing sessions may break when the public exit IP changes. Android Doze and OEM
restrictions can delay timers/status delivery; no exact switch deadline or measured
battery saving is promised. No home-server API key or public ping endpoint is needed.

### Device acceptance checks before stable release

- Install the permanently signed candidate over the current app; retain account,
  rules, split tunneling and autostart settings. Verify failover defaults off.
- Choose home/VPS, enable, select home, and resume rules. Stop Tailscale on home
  while the phone uses mobile data; verify confirmed switch and journal entry.
- Restart home; verify delayed automatic return. Repeat with automatic return off.
- Stop both nodes; verify no VPN disconnection or selection of an unrelated node.
- Test a brief outage, rapid Wi-Fi/mobile handoff, screen off, and manual VPN stop
  during confirmation. No delayed switch should occur after pause or disconnect.
- Switch account or select a third node/disable exit use during confirmation;
  verify no stale switch. Test a primary that still answers despite offline status.
- With the home internet connection down, verify name resolution via the VPS.
  Confirm that unreachable home-only services stay unreachable as expected.
- Test primary online with forwarding disabled: document that this first version
  does not detect that failure. Observe screen-off behavior and battery use on
  Samsung S10/Tab S8/S24 before deciding whether more probing is justified.
