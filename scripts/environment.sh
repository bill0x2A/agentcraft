#!/usr/bin/env bash
# Shared by executable scripts; no changes to the user's shell or system Java.
WORKSPACE_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

use_workspace_java() {
    local candidate
    for candidate in "$WORKSPACE_ROOT/.tools/jdk-25" "${JAVA_HOME:-}" /usr/lib/jvm/java-25-openjdk; do
        if [[ -n "$candidate" && -x "$candidate/bin/javac" ]] &&
            "$candidate/bin/javac" -version 2>&1 | grep -Eq '^javac 25([. ]|$)'; then
            export JAVA_HOME="$candidate"
            export PATH="$JAVA_HOME/bin:$PATH"
            export GRADLE_USER_HOME="$WORKSPACE_ROOT/.gradle-user-home"
            return 0
        fi
    done
    printf 'JDK 25 is missing. Run ./scripts/mc setup first.\n' >&2
    return 1
}

