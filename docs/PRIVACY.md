# Diagnostic logging in ScaleRules

ScaleRules removes the Android-to-Go diagnostic log forwarding and the native
logtail uploader, its buffer/transport setup, upload ID generation and runtime
logging toggle. The obsolete Tailscale support-report screen and API bindings
are removed; Report a bug opens the ScaleRules GitHub Issues page.

Go diagnostics continue to use Android Logcat. Rule and failover journals remain
local. A user may choose what information to include in a GitHub issue; no journal
or Logcat data is attached automatically.

Every APK build uses `ts_omit_logtail` and `ts_omit_netlog`. In the pinned Tailscale
core these tags select no-op logtail implementations and omit registration of
the network-flow logging extension. Backend startup also sets
`TS_NO_LOGS_NO_SUPPORT=true` before constructing the VPN engine. Previous logging
preferences and MDM state no longer enable an uploader. Generic LocalAPI access
to bugreport, debug-log and upload-client-metrics is rejected.

This removes diagnostic uploads, not all network connections to Tailscale.
Account sign-in and coordination still use the selected control server (Tailscale
or Headscale), which receives the metadata required for VPN operation. Depending
on the server configuration and connectivity, DERP relays and STUN servers can
still be used. DNS and exit-node traffic follow the user's configuration. The
GitHub updater still checks GitHub when enabled. No GPS upload is claimed to have
existed or been removed by this change.

References for the pinned core:

- https://github.com/tailscale/tailscale/blob/v1.104.5/logtail/logtail_omit.go
- https://github.com/tailscale/tailscale/blob/v1.104.5/feature/condregister/netlog/maybe_netlog.go
- https://github.com/tailscale/tailscale/blob/v1.104.5/envknob/envknob.go

Validation: native/Android rebuild after removing gomobile interfaces; privacy
policy tests under production tags; existing rule, DNS, health and updater tests.
A signed candidate still needs a device test of startup, VPN connect/reconnect,
DNS and exit-node failover before stable publication. Packet capture can provide
additional runtime evidence; a source/build review alone is not a capture of all
traffic on a real device.
