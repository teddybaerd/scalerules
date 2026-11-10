#!/bin/bash

# Exclude upstream diagnostic upload and network-flow logging in every APK.
if [[ -z "$TOOLCHAIN_DIR" ]]; then
    # By default, if TOOLCHAIN_DIR is unset, we assume we're
    # using the Tailscale Go toolchain (github.com/tailscale/go)
    # at the revision specified by go.toolchain.rev. If so,
    # we tell our caller to use the "tailscale_go" build tag.
    echo "tailscale_go ts_omit_logtail ts_omit_netlog"
else
    # Otherwise, if TOOLCHAIN_DIR is specified, we assume
    # we're F-Droid or something using a stock Go toolchain.
    # That's fine. But we don't set the tailscale_go build tag.
    # The local-only diagnostics policy also applies to stock-Go builds.
    echo "not_tailscale_go ts_omit_logtail ts_omit_netlog"
fi
