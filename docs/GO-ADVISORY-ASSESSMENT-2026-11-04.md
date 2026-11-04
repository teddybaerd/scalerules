# Android Go advisory assessment — 2026-11-04

Scope: PR #19 at `5fffd1027b7d892d134c7c5447c0732b15d1c685`, Tailscale core v1.104.5, Go 1.27.3 from the Tailscale-Go revision `7ef7b403648ac3a9481a6e88e8b894eff50b0167`.

## Result

The affected OpenPGP packages and `golang.org/x/net/html` are absent from the transitive Go package dependency graph of `./libtailscale` for all four Android architectures. Therefore the functions identified by these three advisories are not reachable through an imported affected package in this configuration. This is a source/package assessment, not a claim that every vulnerability in the application has been ruled out.

| Advisory | Affected package | Android result |
| --- | --- | --- |
| GO-2026-5932 | golang.org/x/crypto/openpgp and subpackages | Not imported |
| GO-2026-6860 | golang.org/x/net/html parsing | Not imported |
| GO-2026-6866 | golang.org/x/net/html rendering | Not imported |

| GOARCH | Packages resolved | Package/dependency errors | Affected packages |
| --- | ---: | ---: | ---: |
| arm64 | 668 | 0 | 0 |
| arm (GOARM=7) | 670 | 0 | 0 |
| 386 | 667 | 0 | 0 |
| amd64 | 670 | 0 | 0 |

The x/crypto and x/net modules ARE used for other packages. Module-version scanning correctly continues to flag their versions; the absence concerns the specific affected packages, not the whole modules. No dependency override or replacement was introduced.

## Method and reproduction

Use the compiler from the pinned Tailscale-Go release, not an arbitrary host compiler. Set GOOS=android, CGO_ENABLED=1 and each GOARCH above (GOARM=7 for arm). Run:

```sh
./tool/go list -mod=readonly -deps -json \
  -tags='tailscale_go ts_omit_cachenetmap' ./libtailscale
```

Parse every JSON object, reject package/dependency errors, and inspect ImportPath for an exact `golang.org/x/net/html` match or any `golang.org/x/crypto/openpgp` package prefix. Tags match build-tags.sh and the Makefile default GOMOBILE_BUILD_TAGS. Tests/tool-only packages are not included in this runtime graph.

The local check directly invoked the downloaded pinned compiler's bin/go after extracting its archive with --no-same-owner (the container cannot restore archive ownership). The initial stock-Go cross-check was not used as the final evidence. No source files or module files were changed for analysis.

This did not compile or install an APK and did not run govulncheck call-graph analysis. Package absence makes symbol analysis of these packages unnecessary for this scope. Prior stripped-native-binary govulncheck output was module-level fallback and is not evidence of reachable vulnerable functions.

The Tailscale-Go fork is not credited with fixing these third-party packages. Their vulnerable module versions remain unchanged, and package absence is the reason for this scoped assessment.

## Follow-up

Keep issue #6 and its version-based monitoring active. Repeat package analysis when the core version, build tags, Go entry points or platform configuration change. APK build/signing CI and device testing remain required before a release. Build-tool Maven findings are outside this assessment.

Sources:
- https://github.com/golang/vulndb/blob/master/data/osv/GO-2026-5932.json
- https://github.com/golang/vulndb/blob/master/data/osv/GO-2026-6860.json
- https://github.com/golang/vulndb/blob/master/data/osv/GO-2026-6866.json
