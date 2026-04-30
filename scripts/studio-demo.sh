#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

mkdir -p reports/studio reports/batch

javac -d build Prettifier.java ItineraryStudio.java

java -cp build ItineraryStudio examples/input.txt reports/studio/itinerary.html examples/airport-lookup.csv \
  --format html \
  --title "Passenger Itinerary" \
  --report reports/studio/studio-report.md

java -cp build ItineraryStudio examples/input.txt reports/studio/boarding-pass.html examples/airport-lookup.csv \
  --format boarding-pass \
  --title "Boarding Pass"

java -cp build ItineraryStudio examples/input.txt reports/studio/itinerary.json examples/airport-lookup.csv \
  --format json

java -cp build ItineraryStudio --lookup NBO examples/airport-lookup.csv

printf '\nStudio demo complete. Open reports/studio/itinerary.html or reports/studio/boarding-pass.html\n'
