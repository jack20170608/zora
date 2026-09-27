#!/bin/bash
# Deploy an immutable release, or activate a previously deployed release.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lifecycle.sh"

staging=""
switched=false
was_running=false
previous=""
link=""

switch_active() {
    local version="$1"
    link="$APP_HOME/.active.$$"
    [[ ! -e "$link" && ! -L "$link" ]] || zora_error "temporary link already exists: $link"
    ln -s -- "$version" "$link"
    if [[ "$(uname -s)" == Darwin ]]; then
        mv -fh -- "$link" "$APP_HOME/active"
    else
        mv -fT -- "$link" "$APP_HOME/active"
    fi
    link=""
}

cleanup() {
    local result="$1"
    trap - EXIT
    if [[ "$result" -ne 0 && "$switched" == true ]]; then
        echo "Deployment failed; restoring previous release" >&2
        if [[ -n "$previous" ]]; then
            switch_active "$previous" || true
            if [[ "$was_running" == true ]]; then
                ZORA_DEPLOY_IN_PROGRESS=1 bash "$APP_HOME/active/bin/start.sh" || true
            fi
        else
            rm -f -- "$APP_HOME/active"
        fi
    fi
    [[ -z "$link" ]] || rm -f -- "$link"
    [[ -z "$staging" ]] || rm -rf -- "$staging"
    rmdir -- "$ZORA_DEPLOY_LOCK" || true
}

main() {
    local version jar="" libs="" config="" target pid old_path
    if [[ "${1:-}" == "--activate" && $# -eq 2 ]]; then
        version="$2"
    elif [[ $# -ge 2 && $# -le 4 ]]; then
        version="$1"
        jar="$2"
        libs="${3:-}"
        config="${4:-}"
        [[ -f "$jar" ]] || zora_error "JAR not found: $jar"
        [[ -z "$libs" || "$libs" == "-" || -d "$libs" ]] || zora_error "lib directory not found: $libs"
        [[ -z "$config" || -d "$config" ]] || zora_error "config directory not found: $config"
    else
        zora_error "usage: deploy.sh VERSION JAR [LIB_DIR|-] [CONFIG_DIR] | deploy.sh --activate VERSION"
    fi
    [[ "$version" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]*$ && "$version" != *..* ]] || zora_error "invalid version: $version"
    [[ -d "$APP_HOME" ]] || zora_error "application root not found: $APP_HOME"
    target="$APP_HOME/$version"
    if [[ -n "$jar" ]]; then
        [[ ! -e "$target" && ! -L "$target" ]] || zora_error "release already exists: $target"
    else
        [[ -f "$target/app.jar" && -f "$target/bin/start.sh" ]] || zora_error "release is incomplete: $target"
    fi
    [[ ! -e "$APP_HOME/active" || -L "$APP_HOME/active" ]] || zora_error "active must be a symbolic link"

    mkdir -p -- "$ZORA_RUN_DIR"
    mkdir -- "$ZORA_DEPLOY_LOCK" 2>/dev/null || zora_error "deployment already in progress"
    trap 'cleanup $?' EXIT
    [[ ! -d "$ZORA_LOCK_DIR" ]] || zora_error "lifecycle operation already in progress"

    if [[ -L "$APP_HOME/active" ]]; then
        old_path="$(cd -- "$APP_HOME/active" && pwd -P)" || zora_error "active is a broken link"
        [[ "$(dirname -- "$old_path")" == "$APP_HOME" ]] || zora_error "active points outside application root"
        previous="$(basename -- "$old_path")"
    fi

    if [[ -n "$jar" ]]; then
        staging="$(mktemp -d "$APP_HOME/.deploy.XXXXXXXX")"
        mkdir -- "$staging/bin"
        cp -- "$jar" "$staging/app.jar"
        cp -- "$ZORA_BIN_DIR"/*.sh "$staging/bin/"
        chmod +x "$staging/bin/"*.sh
        if [[ -n "$libs" && "$libs" != "-" ]]; then
            cp -R -- "$libs" "$staging/lib"
        fi
        if [[ -n "$config" ]]; then
            cp -R -- "$config" "$staging/config"
        fi
        mv -- "$staging" "$target"
        staging=""
    fi

    if pid="$(zora_pid)" && zora_is_running "$pid"; then
        was_running=true
        [[ -n "$previous" ]] || zora_error "cannot stop an untracked release"
        ZORA_DEPLOY_IN_PROGRESS=1 bash "$APP_HOME/active/bin/stop.sh"
    fi
    switched=true
    switch_active "$version"
    if [[ "$was_running" == true ]]; then
        ZORA_DEPLOY_IN_PROGRESS=1 bash "$APP_HOME/active/bin/start.sh"
    fi
    echo "Active release: $version"
}

main "$@"
