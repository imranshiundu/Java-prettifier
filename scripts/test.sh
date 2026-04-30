#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p build reports
javac -d build Prettifier.java
java -cp build Prettifier examples/input.txt reports/test-output.txt examples/airport-lookup.csv --no-color --quiet

grep -q "Jomo Kenyatta International Airport" reports/test-output.txt
grep -q "Dubai International Airport" reports/test-output.txt
grep -q "London Heathrow Airport" reports/test-output.txt
grep -q "01 May 2026" reports/test-output.txt
grep -q "#ZZZ" reports/test-output.txt

java -cp build Prettifier examples/input.txt reports/strict-output.txt examples/airport-lookup.csv --strict --no-color >/tmp/java-prettifier-strict.log 2>&1 && {
  echo "Strict mode should fail when #ZZZ is present"
  exit 1
}

echo "All smoke tests passed."
