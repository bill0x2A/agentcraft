#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/environment.sh"
failures=0
printf 'Workspace: %s\nSystem: %s %s\n' "$WORKSPACE_ROOT" "$(uname -s)" "$(uname -m)"
for command in bash git curl tar sha256sum; do
    if command -v "$command" >/dev/null; then
        printf 'OK   %-12s %s\n' "$command" "$(command -v "$command")"
    else
        printf 'FAIL Missing %s\n' "$command"
        failures=$((failures + 1))
    fi
done
if use_workspace_java; then
    printf 'OK   JDK: %s\n' "$JAVA_HOME"
    "$JAVA_HOME/bin/java" -version
    "$JAVA_HOME/bin/javac" -version
else
    failures=$((failures + 1))
fi
if [[ -x "$WORKSPACE_ROOT/gradlew" && -f "$WORKSPACE_ROOT/gradle/wrapper/gradle-wrapper.jar" ]]; then
    printf 'OK   Gradle wrapper present (no global Gradle required)\n'
else
    printf 'FAIL Gradle wrapper missing or not executable\n'
    failures=$((failures + 1))
fi
if command -v glxinfo >/dev/null && graphics="$(glxinfo -B 2>/dev/null)"; then
    printf '%s\n' "$graphics" | grep -E 'OpenGL (vendor|renderer|core profile version) string' || true
else
    printf 'NOTE OpenGL/display access unverified in this shell; check by launching the client from your desktop.\n'
fi
df -h "$WORKSPACE_ROOT" | tail -n 1
printf '\nRequired checks failed: %s\n' "$failures"
exit "$((failures > 0))"

