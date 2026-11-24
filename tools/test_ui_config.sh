#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="${REX_TOOLS_DIR:-/opt/rex-build}"
JDK_DIR="$TOOLS_DIR/zulu17.70.113-ca-jdk17.0.21.0.3-linux_aarch64"
mkdir -p "$TOOLS_DIR/ui-config-test"
"$JDK_DIR/bin/javac" --release 17 -d "$TOOLS_DIR/ui-config-test" \
    "$PROJECT_DIR/app/src/main/java/pl/rexlauncher/ResolutionPolicy.java" "$PROJECT_DIR/tests/ResolutionPolicyTest.java" \
    "$PROJECT_DIR/app/src/main/java/pl/rexlauncher/GraphicsPolicy.java" "$PROJECT_DIR/tests/GraphicsPolicyTest.java"
"$JDK_DIR/bin/java" -cp "$TOOLS_DIR/ui-config-test" ResolutionPolicyTest

"$JDK_DIR/bin/java" -cp "$TOOLS_DIR/ui-config-test" GraphicsPolicyTest
