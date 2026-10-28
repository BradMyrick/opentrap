#!/usr/bin/env bash
# Compile and run the test suite.
#
# Tests live under tests/ rather than src/ so they are never packed into the
# shipped jar. They compile against the compiled application classes in build/.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

# Prefer the pinned portable JDK (fetch-jdk.sh) when present, so the tests run
# against the same toolchain the release and AppImage use. Falls back to a
# system JDK if jdk/ is absent. Without this, test.sh fails with
# "javac: command not found" on a machine with no system Java even though
# fetch-jdk.sh installed one (build.sh already does this).
if [ -x "$DIR/jdk/bin/javac" ]; then
    export JAVA_HOME="$DIR/jdk"
    export PATH="$DIR/jdk/bin:$PATH"
fi

echo "Compiling application..."
# Start from an empty build/ like build.sh does, so classes left by a deleted
# source or by the AppImage packaging cannot leak onto the test classpath.
rm -rf build && mkdir -p build
mapfile -t APP_SRCS < <(find src -name '*.java')
javac -nowarn -d build -cp "lib/*:build" "${APP_SRCS[@]}"

echo "Compiling tests..."
rm -rf build-tests && mkdir -p build-tests
mapfile -t TEST_SRCS < <(find tests -name '*.java')
javac -nowarn -d build-tests -cp "lib/*:build" "${TEST_SRCS[@]}"

echo "Running tests..."
echo
java -cp "lib/*:build:build-tests" RunTests
