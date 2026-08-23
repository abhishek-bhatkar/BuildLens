#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/buildlens-home-test.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_MVN="$TMP_DIR/mvn"
FAKE_JAVA_HOME="$TMP_DIR/java-home"
FAKE_JAVA="$FAKE_JAVA_HOME/bin/java"
JAR="$TMP_DIR/buildlens.jar"
MVN_ARGS="$TMP_DIR/mvn-args"
JAVA_ARGS="$TMP_DIR/java-args"
CUSTOM_HOME="$TMP_DIR/custom-home"

mkdir -p "$(dirname "$FAKE_JAVA")"
touch "$JAR"

cat > "$FAKE_MVN" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" >> "${BUILDENS_TEST_MVN_ARGS:?}"
if [ "${1:-}" = "-version" ]; then
  printf '%s\n' 'Apache Maven 3.9.0'
else
  printf '%s\n' '[INFO] BUILD SUCCESS'
fi
EOF
chmod +x "$FAKE_MVN"

cat > "$FAKE_JAVA" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" > "${BUILDENS_TEST_JAVA_ARGS:?}"
cat >/dev/null
EOF
chmod +x "$FAKE_JAVA"

BUILDENS_TEST_MVN_ARGS="$MVN_ARGS" \
BUILDENS_TEST_JAVA_ARGS="$JAVA_ARGS" \
BUILDLENS_JAR="$JAR" \
BUILDLENS_MVN="$FAKE_MVN" \
JAVA_HOME="$FAKE_JAVA_HOME" \
  "$ROOT_DIR/bin/buildlens" mvn clean package --home "$CUSTOM_HOME"

if grep -Fx -- '--home' "$MVN_ARGS"; then
  echo "--home must not be forwarded to Maven" >&2
  exit 1
fi

grep -Fx -- '--home' "$JAVA_ARGS"
grep -Fx -- "$CUSTOM_HOME" "$JAVA_ARGS"
