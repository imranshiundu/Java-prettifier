# ItineraryStudio Bonus Layer

`ItineraryStudio.java` is the creative/advanced layer for `java-prettifier`.

The original `Prettifier.java` remains the stable review entry point. The studio layer adds richer export and workflow features without changing default behaviour.

## What was added

- Batch processing for whole folders of `.txt` itineraries
- Printable HTML export
- Boarding-pass style HTML export
- Markdown export
- JSON export for downstream tools
- Airport lookup cards for IATA or ICAO codes
- Processing reports with replacement counts and unresolved tokens
- A demo script that produces multiple formats from the same input

## Commands

Compile both tools:

```bash
javac -d build Prettifier.java ItineraryStudio.java
```

Create printable HTML:

```bash
java -cp build ItineraryStudio examples/input.txt reports/studio/itinerary.html examples/airport-lookup.csv --format html --title "Passenger Itinerary"
```

Create boarding-pass HTML:

```bash
java -cp build ItineraryStudio examples/input.txt reports/studio/boarding-pass.html examples/airport-lookup.csv --format boarding-pass --title "Boarding Pass"
```

Create Markdown:

```bash
java -cp build ItineraryStudio examples/input.txt reports/studio/itinerary.md examples/airport-lookup.csv --format markdown
```

Create JSON:

```bash
java -cp build ItineraryStudio examples/input.txt reports/studio/itinerary.json examples/airport-lookup.csv --format json
```

Lookup an airport:

```bash
java -cp build ItineraryStudio --lookup NBO examples/airport-lookup.csv
```

Run the full studio demo:

```bash
bash scripts/studio-demo.sh
```

## Why this is useful in review

This shows that the project can stay simple for the original assignment while still being extended professionally. The core program demonstrates string manipulation, CLI arguments, file IO, CSV parsing, validation, and date/time formatting. The studio layer demonstrates product thinking: the same cleaned itinerary can now become a terminal output, Markdown document, printable HTML page, boarding-pass view, JSON payload, or batch-exported folder.

## Important design rule

Do not move the original assignment behaviour into the studio layer. Keep this separation:

- `Prettifier.java` = stable default project
- `ItineraryStudio.java` = bonus/creative features
