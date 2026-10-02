#!/bin/sh
# Installs the safanoria CLI (Linux x64, macOS arm64) from the GitHub releases.
#
#   curl -fsSL https://raw.githubusercontent.com/mateuy-dev/safanoria/main/install.sh | sh
#
# SAFANORIA_VERSION=0.1.0     a given version instead of the latest
# SAFANORIA_BIN_DIR=/usr/local/bin   where to put it (default ~/.local/bin)
# SAFANORIA_BASE_URL=...      where releases are (default the GitHub releases; tests use a local copy)
set -eu

base="${SAFANORIA_BASE_URL:-https://github.com/mateuy-dev/safanoria/releases}"
bin_dir="${SAFANORIA_BIN_DIR:-$HOME/.local/bin}"

case "$(uname -s)/$(uname -m)" in
  Linux/x86_64) asset=safanoria-linux-x64 ;;
  Darwin/arm64) asset=safanoria-macos-arm64 ;;
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
echo "Downloading $asset from $url"
fetch "$url/$asset" "$tmp/$asset"
fetch "$url/SHA256SUMS" "$tmp/SHA256SUMS"

expected="$(grep "[ *]$asset\$" "$tmp/SHA256SUMS" | head -n 1 | cut -d' ' -f1)"
if command -v sha256sum >/dev/null 2>&1; then actual="$(sha256sum "$tmp/$asset" | cut -d' ' -f1)"
else actual="$(shasum -a 256 "$tmp/$asset" | cut -d' ' -f1)"
fi
if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
  echo "safanoria: checksum mismatch for $asset (expected '$expected', got '$actual'); not installed" >&2
  exit 1
fi

mkdir -p "$bin_dir"
chmod 755 "$tmp/$asset"
mv "$tmp/$asset" "$bin_dir/safanoria"
echo "Installed $bin_dir/safanoria: $("$bin_dir/safanoria" version 2>/dev/null || echo "can't run it, see below")"

# The Linux binary links libunistring (Ubuntu 24.04+: package libunistring5).
if [ "$asset" = safanoria-linux-x64 ] && ! ldconfig -p 2>/dev/null | grep -q 'libunistring.so.5'; then
  echo "warning: libunistring.so.5 not found; install it (Debian/Ubuntu: libunistring5) or safanoria won't start" >&2
fi
case ":$PATH:" in
  *":$bin_dir:"*) ;;
  *) echo "Add $bin_dir to your PATH, e.g. in ~/.profile:  export PATH=\"$bin_dir:\$PATH\"" ;;
esac
