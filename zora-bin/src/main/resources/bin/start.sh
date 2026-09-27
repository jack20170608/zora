#!/bin/bash
# Start a deployed Java application in the background.
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/lifecycle.sh"

main() {
    zora_lock
    local pid jar
    if pid="$(zora_pid)" && zora_is_running "$pid"; then
        zora_error "application is already running (PID $pid)"
    fi
    zora_load_environment
    ZORA_JAVA_CMD="${ZORA_JAVA_CMD:-java}"
    zora_validate
    [[ -f "$ZORA_APP_JAR" ]] || zora_error "application JAR not found: $ZORA_APP_JAR"
    jar="$(cd -- "$(dirname -- "$ZORA_APP_JAR")" && pwd -P)/$(basename -- "$ZORA_APP_JAR")"
    command -v "$ZORA_JAVA_CMD" >/dev/null 2>&1 || zora_error "Java command not found: $ZORA_JAVA_CMD"
    mkdir -p -- "$ZORA_LOG_DIR"

    # Options are space-separated; for arguments containing spaces, use a Java argfile.
    local -a java_options=()
    if [[ -n "${ZORA_JAVA_OPTS:-}" ]]; then
        read -r -a java_options <<< "$ZORA_JAVA_OPTS"
    fi
    nohup "$ZORA_JAVA_CMD" "${java_options[@]}" -jar "$jar" "$@" \
        >> "$ZORA_LOG_DIR/app.log" 2>&1 < /dev/null &
    pid=$!
    printf '%s\n%s\n' "$pid" "$jar" > "$ZORA_PID_FILE"
    sleep 1
    if ! zora_is_running "$pid"; then
        rm -f -- "$ZORA_PID_FILE"
        zora_error "application failed to start; check $ZORA_LOG_DIR/app.log"
    fi
    echo "Started application (PID $pid)"
}

main "$@"
