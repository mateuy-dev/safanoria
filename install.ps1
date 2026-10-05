# Installs Safanoria (Windows x64) from the GitHub releases: the command-line tool,
# `safanoria-cli`, and the desktop app, `safanoria`.
#
#   irm https://raw.githubusercontent.com/mateuy-dev/safanoria/main/install.ps1 | iex
#
# $env:SAFANORIA_VERSION = "0.1.0"   a given version instead of the latest
# $env:SAFANORIA_BIN_DIR = "..."     where to put the commands (default %USERPROFILE%\.local\bin)
# $env:SAFANORIA_APP_DIR = "..."     where to put the app's files (default %LOCALAPPDATA%\safanoria)
# $env:SAFANORIA_CLI_ONLY = "1"      only safanoria-cli (CI, servers)
# $env:SAFANORIA_BASE_URL = "..."    where releases are (default the GitHub releases)
$ErrorActionPreference = "Stop"

$base = if ($env:SAFANORIA_BASE_URL) { $env:SAFANORIA_BASE_URL } else { "https://github.com/mateuy-dev/safanoria/releases" }
$binDir = if ($env:SAFANORIA_BIN_DIR) { $env:SAFANORIA_BIN_DIR } else { Join-Path $env:USERPROFILE ".local\bin" }
$appDir = if ($env:SAFANORIA_APP_DIR) { $env:SAFANORIA_APP_DIR } else { Join-Path $env:LOCALAPPDATA "safanoria" }
$url = if ($env:SAFANORIA_VERSION) { "$base/download/v$($env:SAFANORIA_VERSION.TrimStart('v'))" } else { "$base/latest/download" }
# No "cli" in the CLI asset's name: install scripts and Actions pinned to a version from before
# the app (when the CLI was `safanoria`) download the latest release by this name.
$asset = "safanoria-windows-x64.exe"
$app = "safanoria-app-windows-x64.zip"
$cliOnly = [bool]$env:SAFANORIA_CLI_ONLY

# Invoke-WebRequest (PowerShell 7) only does http(s); file:// bases (tests) are copied.
function Fetch($from, $to) {
    if ($from -like "file://*") { Copy-Item ([System.Uri]$from).LocalPath $to }
    else { Invoke-WebRequest -UseBasicParsing $from -OutFile $to }
}

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ([System.IO.Path]::GetRandomFileName())
New-Item -ItemType Directory -Path $tmp | Out-Null

# Fetched into $tmp and checked against SHA256SUMS.
function Download($name) {
    Write-Host "Downloading $name from $url"
    Fetch "$url/$name" (Join-Path $tmp $name)
    # "<hash>  <name>", or "<hash> *<name>" (binary mode)
    $line = Get-Content (Join-Path $tmp "SHA256SUMS") | Where-Object { $_ -match "[ *]$([regex]::Escape($name))$" } | Select-Object -First 1
    $expected = if ($line) { ($line -split '\s+')[0].ToLower() } else { "" }
    $actual = (Get-FileHash -Algorithm SHA256 (Join-Path $tmp $name)).Hash.ToLower()
    if (-not $expected -or $expected -ne $actual) { throw "checksum mismatch for $name (expected '$expected', got '$actual'); not installed" }
}

try {
    Fetch "$url/SHA256SUMS" (Join-Path $tmp "SHA256SUMS")
    Download $asset
    if (-not $cliOnly) { Download $app }

    New-Item -ItemType Directory -Force -Path $binDir | Out-Null
    $target = Join-Path $binDir "safanoria-cli.exe"
    Move-Item -Force (Join-Path $tmp $asset) $target
    Write-Host "Installed ${target}: $(& $target version)"

    # Up to 0.2 the CLI was safanoria.exe. That name is the app's now (safanoria.cmd below), and
    # an .exe would be found first: the old binary is removed.
    $old = Join-Path $binDir "safanoria.exe"
    if (Test-Path $old) {
        Remove-Item -Force $old
        Write-Host "Removed ${old}: the command-line tool is safanoria-cli now"
    }
    if (-not $cliOnly) {
        New-Item -ItemType Directory -Force -Path $appDir | Out-Null
        $appHome = Join-Path $appDir "safanoria"
        if (Test-Path $appHome) { Remove-Item -Recurse -Force $appHome }
        Expand-Archive -Path (Join-Path $tmp $app) -DestinationPath $appDir
        $launcher = Join-Path $binDir "safanoria.cmd"
        Set-Content -Path $launcher -Encoding ascii -Value @(
            "@echo off",
            "rem safanoria app launcher (install.ps1): the desktop app, on the project of the working directory.",
            "setlocal",
            "set `"SAFANORIA_EXE=$(Join-Path $appHome 'safanoria.exe')`"",
            'set "SAFANORIA_DIR=%~f1"',
            'if "%~1"=="" set "SAFANORIA_DIR=%CD%"',
            "rem Set in a terminal the app opened; with it the app would take its arguments as the JVM's.",
            'set "_JPACKAGE_LAUNCHER="',
            "rem Opening a window (one directory): started without a console, so the prompt comes back and",
            "rem the window outlives the terminal. SAFANORIA_FOREGROUND=1 keeps it attached, to see its output.",
            "rem Anything else (--help, a command meant for safanoria-cli) prints here and returns its exit status.",
            'if defined SAFANORIA_FOREGROUND goto foreground',
            'if not "%~2"=="" goto foreground',
            'if not exist "%SAFANORIA_DIR%\" goto foreground',
            "rem In the directory instead of given it: no quoting of the path through cmd and PowerShell.",
            'powershell -NoProfile -Command "$null = [Diagnostics.Process]::Start((New-Object Diagnostics.ProcessStartInfo $env:SAFANORIA_EXE -Property @{UseShellExecute=$false; CreateNoWindow=$true; WorkingDirectory=$env:SAFANORIA_DIR}))"',
            'exit /b %ERRORLEVEL%',
            ':foreground',
            '"%SAFANORIA_EXE%" %*',
            'exit /b %ERRORLEVEL%'
        )
        Write-Host "Installed ${launcher}: the desktop app ($(& $launcher --version), in $appDir)"
    }
    Write-Host "Hooks set up by an earlier version call ``safanoria``: in each project, run ``safanoria-cli update`` and ``safanoria-cli hook install``."
} finally {
    Remove-Item -Recurse -Force $tmp
}

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if (-not (($userPath -split ';') -contains $binDir)) {
    [Environment]::SetEnvironmentVariable("Path", "$userPath;$binDir", "User")
    Write-Host "Added $binDir to your user PATH; open a new terminal to use safanoria."
}
