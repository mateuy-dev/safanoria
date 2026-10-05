#!/bin/sh
# Installs Safanoria (Linux x64, macOS arm64) from the GitHub releases: the command-line tool,
# `safanoria-cli`, and the desktop app, `safanoria`.
#
#   curl -fsSL https://raw.githubusercontent.com/mateuy-dev/safanoria/main/install.sh | sh
#
# SAFANORIA_VERSION=0.1.0     a given version instead of the latest
# SAFANORIA_BIN_DIR=/usr/local/bin   where to put the commands (default ~/.local/bin)
# SAFANORIA_APP_DIR=...       where to put the app's files (default ~/.local/share/safanoria)
# SAFANORIA_CLI_ONLY=1        only safanoria-cli (CI, servers)
# SAFANORIA_BASE_URL=...      where releases are (default the GitHub releases; tests use a local copy)
set -eu

base="${SAFANORIA_BASE_URL:-https://github.com/mateuy-dev/safanoria/releases}"
bin_dir="${SAFANORIA_BIN_DIR:-$HOME/.local/bin}"
app_dir="${SAFANORIA_APP_DIR:-$HOME/.local/share/safanoria}"

# The CLI assets have no "cli" in their name: install scripts and Actions pinned to a version
# from before the app (when the CLI was `safanoria`) download the latest release by these names.
case "$(uname -s)/$(uname -m)" in
  Linux/x86_64) asset=safanoria-linux-x64; app=safanoria-app-linux-x64.tar.gz; app_exe=safanoria/bin/safanoria ;;
  Darwin/arm64) asset=safanoria-macos-arm64; app=safanoria-app-macos-arm64.tar.gz; app_exe=safanoria.app/Contents/MacOS/safanoria ;;
  *) echo "safanoria: no binary for $(uname -s) $(uname -m); build it from source (README, Development)" >&2; exit 1 ;;
esac

if [ -n "${SAFANORIA_VERSION:-}" ]; then
  url="$base/download/v${SAFANORIA_VERSION#v}"
else
  url="$base/latest/download"
fi

fetch() { # url file
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$1" -o "$2"
  elif command -v wget >/dev/null 2>&1; then wget -q "$1" -O "$2"
  else echo "safanoria: needs curl or wget" >&2; exit 1
  fi
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fetch "$url/SHA256SUMS" "$tmp/SHA256SUMS"

download() { # asset: fetched into $tmp and checked against SHA256SUMS
  echo "Downloading $1 from $url"
  fetch "$url/$1" "$tmp/$1"
  expected="$(grep "[ *]$1\$" "$tmp/SHA256SUMS" | head -n 1 | cut -d' ' -f1)"
  if command -v sha256sum >/dev/null 2>&1; then actual="$(sha256sum "$tmp/$1" | cut -d' ' -f1)"
  else actual="$(shasum -a 256 "$tmp/$1" | cut -d' ' -f1)"
  fi
  if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
    echo "safanoria: checksum mismatch for $1 (expected '$expected', got '$actual'); not installed" >&2
    exit 1
  fi
}

download "$asset"
[ -n "${SAFANORIA_CLI_ONLY:-}" ] || download "$app"

mkdir -p "$bin_dir"
chmod 755 "$tmp/$asset"
mv "$tmp/$asset" "$bin_dir/safanoria-cli"
echo "Installed $bin_dir/safanoria-cli: $("$bin_dir/safanoria-cli" version 2>/dev/null || echo "can't run it, see below")"

# Up to 0.2 the CLI was `safanoria`. That name is the app's now: the launcher below replaces the
# old binary, and without the app it is removed rather than left behind at its old version.
launcher_mark="safanoria app launcher"
if [ -z "${SAFANORIA_CLI_ONLY:-}" ]; then
  mkdir -p "$app_dir"
  rm -rf "$app_dir/safanoria" "$app_dir/safanoria.app"
  tar -xzf "$tmp/$app" -C "$app_dir"
  rm -f "$bin_dir/safanoria"
  # Given the directory, so the app opens the project you are in however it is started.
  cat > "$bin_dir/safanoria" <<LAUNCHER
#!/bin/sh
# $launcher_mark (install.sh): the desktop app, on the project of the working directory.
[ \$# -eq 0 ] && set -- "\$PWD"
exec "$app_dir/$app_exe" "\$@"
LAUNCHER
  chmod 755 "$bin_dir/safanoria"
  echo "Installed $bin_dir/safanoria: the desktop app ($("$bin_dir/safanoria" --version 2>/dev/null || echo "can't run it"), in $app_dir)"
elif [ -f "$bin_dir/safanoria" ] && ! grep -q "$launcher_mark" "$bin_dir/safanoria" 2>/dev/null; then
  rm -f "$bin_dir/safanoria"
  echo "Removed $bin_dir/safanoria: the command-line tool is safanoria-cli now"
fi
echo "Hooks set up by an earlier version call \`safanoria\`: in each project, run \`safanoria-cli update\` and \`safanoria-cli hook install\`."

# The Linux binary links libunistring (Ubuntu 24.04+: package libunistring5).
if [ "$asset" = safanoria-linux-x64 ] && ! ldconfig -p 2>/dev/null | grep -q 'libunistring.so.5'; then
  echo "warning: libunistring.so.5 not found; install it (Debian/Ubuntu: libunistring5) or safanoria-cli won't start" >&2
fi
case ":$PATH:" in
  *":$bin_dir:"*) ;;
  *) echo "Add $bin_dir to your PATH, e.g. in ~/.profile:  export PATH=\"$bin_dir:\$PATH\"" ;;
esac
