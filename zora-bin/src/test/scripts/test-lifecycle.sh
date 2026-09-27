#!/bin/bash
# Integration tests for the lifecycle scripts; no real application is required.
set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
readonly BIN_DIR="$(cd -- "$SCRIPT_DIR/../../main/resources/bin" && pwd -P)"
TEMP_DIR="$(mktemp -d)"

cleanup() {
    if [[ -f "$TEMP_DIR/app/run/app.pid" ]]; then
        local pid
        pid="$(cat "$TEMP_DIR/app/run/app.pid")"
        if [[ "$pid" =~ ^[1-9][0-9]*$ ]]; then
            kill "$pid" 2>/dev/null || true
        fi
    fi
    rm -rf -- "$TEMP_DIR"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

mkdir -p -- "$TEMP_DIR/app/1.0/bin"
cp -- "$BIN_DIR"/*.sh "$TEMP_DIR/app/1.0/bin/"
: > "$TEMP_DIR/app/1.0/app.jar"
ln -s 1.0 "$TEMP_DIR/app/active"
cat > "$TEMP_DIR/fake-java" <<'FAKE_JAVA'
#!/bin/bash
trap 'exit 0' TERM
if [[ "$2" == */2.0/app.jar ]]; then exit 1; fi
while :; do sleep 1; done
FAKE_JAVA
chmod +x "$TEMP_DIR/fake-java"
export ZORA_JAVA_CMD="$TEMP_DIR/fake-java"
export ZORA_STOP_TIMEOUT=5

cd -- "$TEMP_DIR"
if bash app/active/bin/status.sh; then fail "stopped status must fail"; fi
bash app/active/bin/start.sh
bash app/active/bin/status.sh || fail "running status must succeed"
if bash app/active/bin/start.sh; then fail "duplicate start must fail"; fi
bash app/active/bin/stop.sh
if bash app/active/bin/status.sh; then fail "stopped status must fail"; fi

mkdir -p -- app/run
printf '%s\n' "$$" > app/run/app.pid
if bash app/active/bin/status.sh; then fail "unrelated PID must not match"; fi
if bash app/active/bin/stop.sh; then fail "unrelated PID must not be stopped"; fi
bash app/active/bin/start.sh
[[ "$(sed -n '2p' app/run/app.pid)" == "$TEMP_DIR/app/1.0/app.jar" ]] || fail "PID file must pin release"
: > app.jar
bash app/active/bin/deploy.sh 1.1 app.jar
[[ "$(readlink app/active)" == 1.1 ]] || fail "new release not activated"
[[ -f app/1.0/app.jar && -f app/1.1/app.jar ]] || fail "old release not retained"
bash app/active/bin/status.sh || fail "new release must be running"
if bash app/active/bin/deploy.sh 1.1 app.jar; then fail "existing release must not be overwritten"; fi
if bash app/active/bin/deploy.sh ../other app.jar; then fail "invalid version must be rejected"; fi
[[ "$(readlink app/active)" == 1.1 ]] || fail "rejected release changed active"
if bash app/active/bin/deploy.sh 2.0 app.jar; then fail "failed startup must roll back"; fi
[[ "$(readlink app/active)" == 1.1 ]] || fail "failed release not rolled back"
bash app/active/bin/status.sh || fail "old release must be restarted"
bash app/active/bin/deploy.sh --activate 1.0
[[ "$(readlink app/active)" == 1.0 ]] || fail "activation of old release failed"
bash app/active/bin/stop.sh
echo "Lifecycle tests passed"
