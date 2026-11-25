#!/bin/sh
set -eu

script_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
if command -v python3 >/dev/null 2>&1 && python3 -c 'import sys; sys.exit(sys.version_info < (3, 12))'; then
    exec python3 "$script_dir/mgc_test.py" setup "$@"
fi

case "$(uname -s)" in
    Linux) target=unknown-linux-gnu; default_cache="${XDG_CACHE_HOME:-$HOME/.cache}/mgc-game-test" ;;
    Darwin) target=apple-darwin; default_cache="$HOME/Library/Caches/mgc-game-test" ;;
    *) echo 'Use setup.ps1 on native Windows.' >&2; exit 2 ;;
esac
case "$(uname -m)" in
    x86_64|amd64) arch=x86_64 ;;
    aarch64|arm64) arch=aarch64 ;;
    *) echo 'No bundled Python for this architecture.' >&2; exit 2 ;;
esac
case "$arch-$target" in
    x86_64-unknown-linux-gnu) checksum=86ed5fcc8f99d3d85dad7336af68e914d9ce3559dc760dd6cf1f2d06df6601d5 ;;
    aarch64-unknown-linux-gnu) checksum=5f1318c691661c0d65368c4dbc13cde8cb87757252cbc0c2d6fd828a23b8a522 ;;
    x86_64-apple-darwin) checksum=0c3b06cbceff2c45077eb81718e604893401365a394f16a7ac380e92ce150e41 ;;
    aarch64-apple-darwin) checksum=ae7030747b3daddea6925f4e270918c5d977f0347fe5d03adae94ce3a630ed74 ;;
esac

cache=${MGC_TEST_CACHE:-$default_cache}
tag=20261124
runtime="$cache/python-$tag-$arch-$target"
if [ ! -x "$runtime/bin/python3" ]; then
    mkdir -p "$cache"
    temporary=$(mktemp -d "$cache/python.XXXXXXXX")
    trap 'rm -rf "$temporary"' EXIT
    asset="cpython-3.13.18+$tag-$arch-$target-install_only.tar.gz"
    curl --fail --location --silent --show-error --max-time 180 \
        "https://github.com/astral-sh/python-build-standalone/releases/download/$tag/$asset" -o "$temporary/python.tar.gz"
    if command -v sha256sum >/dev/null 2>&1; then
        actual=$(sha256sum "$temporary/python.tar.gz")
    else
        actual=$(shasum -a 256 "$temporary/python.tar.gz")
    fi
    [ "${actual%% *}" = "$checksum" ] || { echo 'Python checksum mismatch.' >&2; exit 2; }
    tar -xzf "$temporary/python.tar.gz" -C "$temporary"
    mv "$temporary/python" "$runtime"
fi
"$runtime/bin/python3" "$script_dir/mgc_test.py" setup "$@"
