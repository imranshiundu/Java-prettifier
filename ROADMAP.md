# Roadmap

These are strong next upgrades that keep the original project behavior safe while making the tool more professional.

## Implemented now

- Professional README
- Review guide
- Demo script
- Smoke-test script
- Sample airport lookup CSV
- Sample itinerary input
- Git ignore rules for Java builds and generated reports

## Next code upgrades

### 1. Batch folder processing

Process every `.txt` itinerary in a folder and write all prettified files into an output folder.

Example future command:

```bash
java Prettifier.java --batch ./raw-itineraries ./prettified-itineraries ./airport-lookup.csv
```

### 2. Markdown export

Generate a cleaner Markdown itinerary with a title and bullet formatting.

Example future command:

```bash
java Prettifier.java input.txt itinerary.md airport-lookup.csv --markdown --title "Passenger Itinerary"
```

### 3. Airport lookup mode

Allow users to quickly check one airport code without processing an itinerary.

Example future command:

```bash
java Prettifier.java --lookup NBO airport-lookup.csv
```

### 4. JSON report

Generate machine-readable processing stats for automated validation.

Example future command:

```bash
java Prettifier.java input.txt output.txt airport-lookup.csv --json-report reports/report.json
```

### 5. HTML export

Generate a simple printable HTML itinerary.

Example future command:

```bash
java Prettifier.java input.txt itinerary.html airport-lookup.csv --html
```

### 6. JUnit test suite

Move from smoke tests to formal unit tests covering:

- IATA replacements
- ICAO replacements
- city replacements
- date formatting
- 12-hour time formatting
- 24-hour time formatting
- unknown airport behavior
- strict mode behavior
- malformed CSV behavior

## Product direction

The project can become a small, clean travel-text normalization engine. The most valuable direction is not adding random features, but making it reliable, testable, and safe around messy real-world text exports.
