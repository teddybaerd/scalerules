// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause
package privacypolicy

import (
	"testing"

	"tailscale.com/envknob"
	"tailscale.com/feature/buildfeatures"
)

func TestProductionOmitsDiagnosticUploadFeatures(t *testing.T) {
	if buildfeatures.HasLogTail || buildfeatures.HasNetLog {
		t.Fatal("diagnostic/flow uploads compiled in; use production build-tags.sh")
	}
}

func TestInitializeOverridesPreviouslyEnabledLogging(t *testing.T) {
	t.Setenv("TS_NO_LOGS_NO_SUPPORT", "false")
	Initialize()
	if !envknob.NoLogsNoSupport() {
		t.Fatal("startup did not enforce no-upload mode")
	}
	Initialize() // Repeated startup cannot re-enable logging.
	if !envknob.NoLogsNoSupport() {
		t.Fatal("no-upload mode not retained")
	}
}

func TestLegacyDiagnosticAPIsBlockedWithoutBreakingVPNOperations(t *testing.T) {
	for _, p := range []string{"bugreport", "/localapi/v0/bugreport", "/localapi/v0/debug-log", "/localapi/v0/upload-client-metrics", "/localapi/v0/../v0/bugreport"} {
		if AllowsLocalAPI(p) {
			t.Errorf("diagnostic operation allowed: %s", p)
		}
	}
	for _, p := range []string{"/localapi/v0/prefs", "/localapi/v0/status", "/localapi/v0/dns-query", "/localapi/v0/ping", "/localapi/v0/start", "/localapi/v0/login-interactive", "/localapi/v0/profiles/", "/localapi/v0/file-put"} {
		if !AllowsLocalAPI(p) {
			t.Errorf("VPN operation blocked: %s", p)
		}
	}
}
