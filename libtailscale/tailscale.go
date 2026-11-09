// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package libtailscale

import (
	"context"
	"log"
	"runtime/debug"

	"tailscale.com/net/netmon"
	"tailscale.com/types/key"
	"tailscale.com/util/syspolicy/rsop"
	"tailscale.com/util/syspolicy/setting"
)

const defaultMTU = 1280 // minimalMTU from wgengine/userspace.go

const (
	loginMethodPrefKey       = "loginmethod"
	customLoginServerPrefKey = "customloginserver"
)

func newApp(dataDir, directFileRoot string, hardwareAttestationPref bool, appCtx AppContext) Application {
	a := &App{
		directFileRoot: directFileRoot,
		dataDir:        dataDir,
		appCtx:         appCtx,
	}
	a.ready.Add(2)

	a.store = newStateStore(a.appCtx)
	a.policyStore = &syspolicyStore{a: a}
	netmon.RegisterInterfaceGetter(a.getInterfaces)
	rsop.RegisterStore("DeviceHandler", setting.DeviceScope, a.policyStore)

	hwAttestEnabled := appCtx.HardwareAttestationKeySupported() && hardwareAttestationPref
	if hwAttestEnabled {
		key.RegisterHardwareAttestationKeyFns(
			func() key.HardwareAttestationKey { return emptyHardwareAttestationKey(appCtx) },
			func() (key.HardwareAttestationKey, error) { return createHardwareAttestationKey(appCtx) },
		)
	} else {
		log.Printf("HardwareAttestationKey is not supported on this device")
	}
	go a.watchFileOpsChanges()

	go func() {
		defer func() {
			if p := recover(); p != nil {
				log.Printf("panic in runBackend %s: %s", p, debug.Stack())
				panic(p)
			}
		}()

		ctx := context.Background()
		if err := a.runBackend(ctx, hwAttestEnabled); err != nil {
			fatalErr(err)
		}
	}()

	return a
}

func fatalErr(err error) {
	// TODO: expose in UI.
	log.Printf("fatal error: %v", err)
}

// osVersion returns android.os.Build.VERSION.RELEASE. " [nogoogle]" is appended
// if Google Play services are not compiled in.
func (a *App) osVersion() string {
	version, err := a.appCtx.GetOSVersion()
	if err != nil {
		panic(err)
	}
	return version
}

// deviceName returns the user-set name if available, or MANUFACTURER + MODEL from
// android.os.Build.
func (a *App) deviceName() string {
	model, err := a.appCtx.GetDeviceName()
	if err != nil {
		panic(err)
	}
	return model
}

func (a *App) isChromeOS() bool {
	isChromeOS, err := a.appCtx.IsChromeOS()
	if err != nil {
		panic(err)
	}
	return isChromeOS
}
