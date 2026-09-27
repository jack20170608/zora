#!/bin/bash
# Report whether the deployed Java application is running.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lifecycle.sh"

main() {
    [[ $# -eq 0 ]] || zora_error "usage: status.sh"
    local pid
    if pid="$(zora_pid)" && zora_is_running "$pid"; then
        echo "Application is running (PID $pid)"
        return 0
    fi
    echo "Application is not running"
    return 1
}

main "$@"
