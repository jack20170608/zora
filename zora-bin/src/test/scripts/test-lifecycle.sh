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

mkdir -p -- "$TEMP_DIR/app/1.0/bin" "$TEMP_DIR/app/1.0/config" "$TEMP_DIR/new-config"
cp -- "$BIN_DIR"/*.sh "$TEMP_DIR/app/1.0/bin/"
: > "$TEMP_DIR/app/1.0/app.jar"
printf 'sit\n' > "$TEMP_DIR/app/env.tag"
printf 'ZORA_TEST_VALUE=general\n' > "$TEMP_DIR/app/1.0/config/setenv"
printf 'ZORA_TEST_VALUE=sit\nAPP_ENV=incorrect\n' > "$TEMP_DIR/app/1.0/config/setenv-sit"
printf 'ZORA_TEST_VALUE=deployed\n' > "$TEMP_DIR/new-config/setenv-sit"
ln -s 1.0 "$TEMP_DIR/app/active"
cat > "$TEMP_DIR/fake-java" <<'FAKE_JAVA'
#!/bin/bash
trap 'exit 0' TERM
printf '%s:%s\n' "${APP_ENV:-}" "${ZORA_TEST_VALUE:-}" > "$TEST_CAPTURE"
if [[ "$2" == */2.0/app.jar ]]; then exit 1; fi
while :; do sleep 1; done
FAKE_JAVA
chmod +x "$TEMP_DIR/fake-java"
export ZORA_JAVA_CMD="$TEMP_DIR/fake-java"
export ZORA_STOP_TIMEOUT=5
export TEST_CAPTURE="$TEMP_DIR/launch.env"
export APP_ENV=prod

cd -- "$TEMP_DIR"
if bash app/active/bin/status.sh; then fail "stopped status must fail"; fi
bash app/active/bin/start.sh
[[ "$(cat "$TEST_CAPTURE")" == sit:sit ]] || fail "env.tag and environment-specific setenv must take precedence"
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
bash app/active/bin/deploy.sh 1.1 app.jar - new-config
[[ "$(readlink app/active)" == 1.1 ]] || fail "new release not activated"
[[ -f app/1.0/app.jar && -f app/1.1/app.jar ]] || fail "old release not retained"
[[ -f app/1.1/config/setenv-sit ]] || fail "release config not copied"
[[ "$(cat "$TEST_CAPTURE")" == sit:deployed ]] || fail "deployment must load new release config"
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
rm -- app/env.tag
printf 'ZORA_TEST_VALUE=prod\n' > app/1.0/config/setenv-prod
bash app/active/bin/start.sh
[[ "$(cat "$TEST_CAPTURE")" == prod:prod ]] || fail "APP_ENV fallback must load matching setenv"
bash app/active/bin/stop.sh
printf '../outside\n' > app/env.tag
if bash app/active/bin/start.sh; then fail "invalid env.tag must reject startup"; fi
if bash app/active/bin/status.sh; then fail "invalid tag must not start the process"; fi
mkdir -- "$TEMP_DIR/bootstrap"
APP_HOME="$TEMP_DIR/bootstrap" bash "$BIN_DIR/deploy.sh" 3.0 "$TEMP_DIR/app.jar"
[[ "$(readlink "$TEMP_DIR/bootstrap/active")" == 3.0 ]] || fail "APP_HOME override must direct initial deployment"
[[ -f "$TEMP_DIR/bootstrap/3.0/bin/start.sh" ]] || fail "APP_HOME override must install release scripts"
echo "Lifecycle tests passed"
