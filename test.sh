#!/usr/bin/env bash
# Compile and run the test suite.
#
# Tests live under tests/ rather than src/ so they are never packed into the
# shipped jar. They compile against the compiled application classes in build/.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

echo "Compiling application..."
mkdir -p build
javac -nowarn -d build -cp "lib/*:build" $(find src -name '*.java')

echo "Compiling tests..."
rm -rf build-tests && mkdir -p build-tests
javac -nowarn -d build-tests -cp "lib/*:build" $(find tests -name '*.java')

echo "Running tests..."
echo
java -cp "lib/*:build:build-tests" RunTests
