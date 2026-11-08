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

- Reboot recovery is optional; other process termination or force-stop may require manual resumption.
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


<a id="experimental-exit-node-failover"></a>

## Exit-node failover (Beta)

In Settings > Exit-node failover, choose a primary, optional secondary and final
backup exit node from the current account. Enable failover, select the primary
once in the normal exit-node picker and resume the shared rule automation.
Defaults remain a 30-second grace period, three failed probes and optional
automatic return with a 120-second confirmation delay. Changing settings pauses
rule automation, which can be resumed on either settings page.

Priority is primary > secondary > final backup. An empty secondary preserves
existing two-node configurations. Duplicate selections are rejected. A reported
offline active node must fail every confirmation probe (3 seconds apart), then
candidates are checked in priority order. Only a reported-online peer still
offering exit routes and answering a disco ping can become the destination.
A successful probe of the active node aborts the outage confirmation.

While using a backup, automatic return checks higher priorities every 60 seconds,
with reachability checks before and after the configured return delay. From the
final backup, return directly to primary when available, otherwise secondary.
A higher priority becoming reachable during confirmation defers a lower return.
These are reachability samples, not continuous internet/DNS health verification.
Healthy operation on primary adds no polling timer or probes. Unresolved outages
retry after 60 seconds and are recorded in the decision journal.

Monitoring requires active rules and VPN, a non-VPN internet-capable network,
the matching account/control URL and one of the configured nodes selected.
Pausing rules, disconnect rules, VPN stops and account/network changes cancel
pending decisions. A manual selection outside the configured list or disabling
exit-node use suspends failover. Automatic switching edits only ExitNodeID.

If all nodes fail, selection and VPN remain enabled; no direct-internet fallback
is introduced. Existing split-tunnel exclusions still apply. Peer status can be
delayed; online forwarding or DNS failures are not detected. DNS must remain
available when the primary site fails. Sessions may break when the public exit IP
changes. Android background restrictions may delay checks and switching.

### Device acceptance checks before stable release

- Verify 1→2, 1→3 and 2→3 on failure; 3→2, 3→1 and 2→1 on recovery,
  including with the display locked. Stop all three and verify no unconfirmed switch.
- Upgrade an existing two-node configuration and verify its settings still work.
- Check the separate settings page, labeled dropdowns, Beta label and GitHub issue link.

- Install the permanently signed candidate over the current app; retain account,
  rules, split tunneling and autostart settings. Verify failover defaults off.
- Choose primary/secondary/VPS, enable, select primary, and resume rules. Stop Tailscale on home
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


Failover diagnostics in the decision journal show the configuration/profile match,
network readiness, VPN/engine state, active-node role and each configured peer's
online/exit-route flags (`null` means unknown). Status entries are emitted only
when that snapshot changes. Probe results are journaled only during an incident;
no URLs, node IDs, keys or raw backend errors are recorded. An offline primary
remains monitored even if its exit routes have been withdrawn. The destination
must still offer exit routes when switching. Capability-only network updates do
not reset a running grace period; network arrival/loss and rule edits still do.

## Recovery after app updates

Installing an update resumes previously active rule automation in the background,
independently of the optional reboot setting. Manually paused automation stays
paused, including when configuration edits paused it. The existing persisted active
flag is reused, so upgrades from earlier versions retain the intended state.
Account, rules, exit-node selection, failover, DNS and exclusions are not rewritten.
The resumed service evaluates current network conditions and saved rules; it does
not blindly reconnect on a network whose rule says disconnect. A keep-state rule
or no matching rule does not request a new VPN connection.

The package-replaced receiver uses the existing permission checks and foreground
service path. SSID rules need background location permission for automatic startup;
VPN approval is also required. If startup is blocked, an update-specific notification
opens the rules screen for manual recovery. Notifications must be allowed. Successful
update recovery is recorded in the decision journal. Android/OEM restrictions still
apply; this is not a guarantee after force-stop.

Validation: install a signed update without opening the app, with automation active
and paused, with reboot recovery disabled, and with missing background permissions.
Check recovery while the display is off, retained settings, current disconnect rules,
and the update-specific journal/notification text.

Device testing of 0.2.9-rc.2 confirmed preferred-to-backup switching, website access
through the backup, delayed return, and both switches while the display was locked.
Earlier delayed peer status and a transient DNS warning were observed; their causes
have not been established and this does not prove internet-forwarding detection.
