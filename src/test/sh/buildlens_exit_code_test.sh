#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/buildlens-exit-code-test.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_MVN="$TMP_DIR/mvn"
FAKE_JAVA_HOME="$TMP_DIR/java-home"
FAKE_JAVA="$FAKE_JAVA_HOME/bin/java"
JAR="$TMP_DIR/buildlens.jar"
JAVA_ARGS="$TMP_DIR/java-args"

mkdir -p "$(dirname "$FAKE_JAVA")"
touch "$JAR"

cat > "$FAKE_MVN" <<'EOF'
#!/usr/bin/env bash
if [ "${1:-}" = "-version" ]; then
  printf '%s\n' 'Apache Maven 3.9.0'
else
  printf '%s\n' '[ERROR] BUILD FAILED'
  exit 7
fi
EOF
chmod +x "$FAKE_MVN"

cat > "$FAKE_JAVA" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" > "${BUILDENS_TEST_JAVA_ARGS:?}"
cat >/dev/null
EOF
chmod +x "$FAKE_JAVA"

set +e
BUILDENS_TEST_JAVA_ARGS="$JAVA_ARGS" \
BUILDLENS_JAR="$JAR" \
BUILDLENS_MVN="$FAKE_MVN" \
JAVA_HOME="$FAKE_JAVA_HOME" \
  "$ROOT_DIR/bin/buildlens" mvn clean package
status=$?
set -e

test "$status" -eq 7
grep -Fx -- '--exit-code-file' "$JAVA_ARGS"
