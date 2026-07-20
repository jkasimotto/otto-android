#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --console=plain detekt testDebugUnitTest assembleDebug
