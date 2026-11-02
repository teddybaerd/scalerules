# Copyright (c) 2026 ScaleRules contributors
# SPDX-License-Identifier: BSD-3-Clause
# Run locally on Windows. Never paste the private files or password into chat.
[CmdletBinding()]
param(
    [string]$OutputDirectory = (Join-Path $env:USERPROFILE 'ScaleRules-signing'),
    [Security.SecureString]$Password
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This script requires Windows.' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$KeyStore = Join-Path $OutputDirectory 'scalerules-release.p12'
$Base64File = Join-Path $OutputDirectory 'github-keystore-base64.txt'
$CertificateFile = Join-Path $OutputDirectory 'scalerules-certificate.cer'
$FingerprintFile = Join-Path $OutputDirectory 'certificate-sha256.txt'
foreach ($File in @($KeyStore, $Base64File, $CertificateFile, $FingerprintFile)) {
    if (Test-Path -LiteralPath $File) {
        throw "File already exists: $File. Reuse your existing signing key; do not replace it."
    }
}
if (-not $Password) {
    $Password = Read-Host 'Enter a strong password saved in your password manager (at least 12 characters)' -AsSecureString
}
if ($Password.Length -lt 12) { throw 'Use a password with at least 12 characters.' }
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$Certificate = New-SelfSignedCertificate `
    -Type CodeSigningCert `
    -Subject 'CN=ScaleRules' `
    -FriendlyName 'ScaleRules Android signing key' `
    -CertStoreLocation 'Cert:\CurrentUser\My' `
    -Provider 'Microsoft Software Key Storage Provider' `
    -KeyAlgorithm RSA `
    -KeyLength 4096 `
    -HashAlgorithm SHA256 `
    -KeyExportPolicy Exportable `
    -NotBefore (Get-Date).AddDays(-1) `
    -NotAfter (Get-Date).AddYears(30)
Export-PfxCertificate -Cert $Certificate -FilePath $KeyStore -Password $Password `
    -ChainOption EndEntityCertOnly -CryptoAlgorithmOption AES256_SHA256 | Out-Null
[IO.File]::WriteAllBytes($CertificateFile, $Certificate.RawData)
$Hasher = [Security.Cryptography.SHA256]::Create()
try {
    $Fingerprint = ([BitConverter]::ToString($Hasher.ComputeHash($Certificate.RawData))).Replace('-', '').ToLowerInvariant()
} finally { $Hasher.Dispose() }
[IO.File]::WriteAllText($FingerprintFile, $Fingerprint, [Text.Encoding]::ASCII)
[IO.File]::WriteAllText($Base64File, [Convert]::ToBase64String([IO.File]::ReadAllBytes($KeyStore)), [Text.Encoding]::ASCII)
Write-Host "Encrypted key saved: $KeyStore"
Write-Host "GitHub secret file: $Base64File (keep private)"
Write-Host "Public certificate SHA-256: $Fingerprint"
Write-Host 'Back up the .p12 file and keep the password separately in your password manager.'
# The original certificate also remains in the current Windows user's personal certificate store.
[pscustomobject]@{
    KeyStore = $KeyStore
    Base64File = $Base64File
    CertificateFile = $CertificateFile
    FingerprintFile = $FingerprintFile
    Fingerprint = $Fingerprint
    Thumbprint = $Certificate.Thumbprint
}
