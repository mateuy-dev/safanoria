# Installs the safanoria CLI (Windows x64) from the GitHub releases.
#
#   irm https://raw.githubusercontent.com/mateuy-dev/safanoria/main/install.ps1 | iex
#
# $env:SAFANORIA_VERSION = "0.1.0"   a given version instead of the latest
# $env:SAFANORIA_BIN_DIR = "..."     where to put it (default %USERPROFILE%\.local\bin)
# $env:SAFANORIA_BASE_URL = "..."    where releases are (default the GitHub releases)
$ErrorActionPreference = "Stop"

$base = if ($env:SAFANORIA_BASE_URL) { $env:SAFANORIA_BASE_URL } else { "https://github.com/mateuy-dev/safanoria/releases" }
$binDir = if ($env:SAFANORIA_BIN_DIR) { $env:SAFANORIA_BIN_DIR } else { Join-Path $env:USERPROFILE ".local\bin" }
$url = if ($env:SAFANORIA_VERSION) { "$base/download/v$($env:SAFANORIA_VERSION.TrimStart('v'))" } else { "$base/latest/download" }
$asset = "safanoria-windows-x64.exe"

# Invoke-WebRequest (PowerShell 7) only does http(s); file:// bases (tests) are copied.
function Fetch($from, $to) {
    if ($from -like "file://*") { Copy-Item ([System.Uri]$from).LocalPath $to }
    else { Invoke-WebRequest -UseBasicParsing $from -OutFile $to }
}

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    Write-Host "Downloading $asset from $url"
    Fetch "$url/$asset" (Join-Path $tmp $asset)
    Fetch "$url/SHA256SUMS" (Join-Path $tmp "SHA256SUMS")

    # "<hash>  <name>", or "<hash> *<name>" (binary mode)
    $line = Get-Content (Join-Path $tmp "SHA256SUMS") | Where-Object { $_ -match "[ *]$([regex]::Escape($asset))$" } | Select-Object -First 1
    $expected = if ($line) { ($line -split '\s+')[0].ToLower() } else { "" }
    $actual = (Get-FileHash -Algorithm SHA256 (Join-Path $tmp $asset)).Hash.ToLower()
    if (-not $expected -or $expected -ne $actual) { throw "checksum mismatch for $asset (expected '$expected', got '$actual'); not installed" }

    New-Item -ItemType Directory -Force -Path $binDir | Out-Null
    $target = Join-Path $binDir "safanoria.exe"
    Move-Item -Force (Join-Path $tmp $asset) $target
    Write-Host "Installed ${target}: $(& $target version)"
} finally {
    Remove-Item -Recurse -Force $tmp
}

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if (-not (($userPath -split ';') -contains $binDir)) {
    [Environment]::SetEnvironmentVariable("Path", "$userPath;$binDir", "User")
    Write-Host "Added $binDir to your user PATH; open a new terminal to use safanoria."
}
