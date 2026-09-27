#!/bin/bash
# Gracefully stop a deployed Java application.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lifecycle.sh"

main() {
    [[ $# -eq 0 ]] || zora_error "usage: stop.sh"
    zora_lock
    local pid elapsed
    if ! pid="$(zora_pid)" || ! zora_is_running "$pid"; then
        zora_error "application is not running (missing or stale PID file)"
    fi
    kill -TERM "$pid"
    for ((elapsed = 0; elapsed < ZORA_STOP_TIMEOUT; elapsed++)); do
        if ! zora_is_running "$pid"; then
            rm -f -- "$ZORA_PID_FILE"
            echo "Stopped application (PID $pid)"
            return 0
        fi
        sleep 1
    done
    if ! zora_is_running "$pid"; then
        rm -f -- "$ZORA_PID_FILE"
        echo "Stopped application (PID $pid)"
        return 0
    fi
    zora_error "application did not stop within $ZORA_STOP_TIMEOUT seconds (PID $pid)"
}

main "$@"
