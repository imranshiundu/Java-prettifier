#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p build reports

echo "Compiling Prettifier.java..."
javac -d build Prettifier.java

echo "Running demo..."
java -cp build Prettifier examples/input.txt reports/output.txt examples/airport-lookup.csv --stdout --stats --unknown-report reports/unknown-report.md

echo ""
echo "Demo files written:"
echo "- reports/output.txt"
echo "- reports/unknown-report.md"
