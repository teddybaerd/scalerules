// Copyright (c) 2026 ScaleRules contributors
// SPDX-License-Identifier: BSD-3-Clause

// Package privacypolicy enforces ScaleRules' local-only diagnostic logging.
package privacypolicy

import (
	"path"
	"strings"

	"tailscale.com/envknob"
)

// Initialize runs before the VPN backend is constructed. Stored Android settings
// and device-management policy must not re-enable diagnostic uploads.
func Initialize() {
	envknob.SetNoLogsNoSupport()
}

// AllowsLocalAPI blocks legacy diagnostic-export operations even if a future UI
// accidentally calls the generic LocalAPI bridge. VPN/control operations remain available.
func AllowsLocalAPI(urlPath string) bool {
	p := path.Clean("/" + urlPath)
	p = strings.TrimPrefix(p, "/localapi/v0")
	switch p {
	case "/bugreport", "/debug-log", "/upload-client-metrics":
		return false
	default:
		return true
	}
}
