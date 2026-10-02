#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "${BASH_SOURCE[0]}")/environment.sh"

for command in curl tar sha256sum mktemp; do
    command -v "$command" >/dev/null || { printf 'Required command missing: %s\n' "$command" >&2; exit 1; }
done

if use_workspace_java 2>/dev/null; then
    printf 'JDK 25 is ready: %s\n' "$JAVA_HOME"
    exit 0
fi

if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
    printf 'Automatic installation supports Linux x86_64. Install JDK 25 and set JAVA_HOME on this platform.\n' >&2
    exit 1
fi

mkdir -p "$WORKSPACE_ROOT/.tools"
if [[ -e "$WORKSPACE_ROOT/.tools/jdk-25" ]]; then
    printf '.tools/jdk-25 exists but is not a working JDK 25; inspect it before retrying.\n' >&2
    exit 1
fi
staging="$(mktemp -d "$WORKSPACE_ROOT/.tools/setup.XXXXXX")"
trap 'rm -rf -- "$staging"' EXIT

url='https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz'
checksum='dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e'
printf 'Downloading Temurin JDK 25.0.4.1+1 into this workspace...\n'
curl --fail --location --show-error --silent --retry 3 --connect-timeout 20 "$url" -o "$staging/jdk.tar.gz"
printf '%s  %s\n' "$checksum" "$staging/jdk.tar.gz" | sha256sum --check --status
mkdir "$staging/jdk"
tar -xzf "$staging/jdk.tar.gz" --strip-components=1 -C "$staging/jdk"
"$staging/jdk/bin/javac" -version
mv "$staging/jdk" "$WORKSPACE_ROOT/.tools/jdk-25"
use_workspace_java
printf 'Ready: %s\n' "$JAVA_HOME"

