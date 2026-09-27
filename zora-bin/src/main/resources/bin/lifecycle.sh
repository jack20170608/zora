#!/bin/bash
# Shared lifecycle helpers. Source this file from the scripts in this directory.
set -euo pipefail

readonly ZORA_BIN_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
ZORA_APP_HOME="${ZORA_APP_HOME:-$(cd -- "$ZORA_BIN_DIR/../.." && pwd -P)}"
ZORA_APP_JAR="${ZORA_APP_JAR:-$ZORA_APP_HOME/active/app.jar}"
ZORA_JAVA_CMD="${ZORA_JAVA_CMD:-java}"
ZORA_RUN_DIR="${ZORA_RUN_DIR:-$ZORA_APP_HOME/run}"
ZORA_LOG_DIR="${ZORA_LOG_DIR:-$ZORA_APP_HOME/logs}"
ZORA_STOP_TIMEOUT="${ZORA_STOP_TIMEOUT:-30}"
readonly ZORA_PID_FILE="$ZORA_RUN_DIR/app.pid"
readonly ZORA_LOCK_DIR="$ZORA_RUN_DIR/.lifecycle.lock"
readonly ZORA_DEPLOY_LOCK="$ZORA_RUN_DIR/.deploy.lock"

zora_error() {
    echo "Error: $*" >&2
    exit 1
}

zora_validate() {
    [[ "$ZORA_APP_JAR" = /* ]] || zora_error "ZORA_APP_JAR must be an absolute path: $ZORA_APP_JAR"
    [[ "$ZORA_STOP_TIMEOUT" =~ ^[0-9]+$ ]] || zora_error "ZORA_STOP_TIMEOUT must be a non-negative integer"
}

zora_pid() {
    [[ -f "$ZORA_PID_FILE" ]] || return 1
    local pid
    IFS= read -r pid < "$ZORA_PID_FILE" || [[ -n "$pid" ]] || return 1
    [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 1
    printf '%s\n' "$pid"
}

# Pin the running release; resolving active again after a switch would miss the old process.
zora_running_jar() {
    local pid jar
    pid="$(zora_pid)" || return 1
    { IFS= read -r _; IFS= read -r jar; } < "$ZORA_PID_FILE" || return 1
    [[ "$jar" = /* ]] || return 1
    printf '%s\n' "$jar"
}

# Never signal a PID unless its command line names exactly our application JAR.
zora_is_running() {
    local pid="$1" jar argument previous="" command_line
    jar="$(zora_running_jar)" || return 1
    kill -0 "$pid" 2>/dev/null || return 1
    if [[ -r "/proc/$pid/cmdline" ]]; then
        while IFS= read -r -d '' argument; do
            if [[ "$previous" == "-jar" && "$argument" == "$jar" ]]; then
                return 0
            fi
            previous="$argument"
        done < "/proc/$pid/cmdline"
        return 1
    fi
    # macOS does not expose /proc; ps provides the command line instead.
    command_line="$(ps -p "$pid" -o args= -ww 2>/dev/null)" || return 1
    [[ " $command_line " == *" -jar $jar "* ]]
}

zora_lock() {
    mkdir -p -- "$ZORA_RUN_DIR"
    mkdir -- "$ZORA_LOCK_DIR" 2>/dev/null || zora_error "lifecycle operation already in progress: $ZORA_LOCK_DIR"
    trap 'rmdir -- "$ZORA_LOCK_DIR"' EXIT
    if [[ -d "$ZORA_DEPLOY_LOCK" && "${ZORA_DEPLOY_IN_PROGRESS:-}" != "1" ]]; then
        zora_error "deployment in progress: $ZORA_DEPLOY_LOCK"
    fi
}

zora_validate
