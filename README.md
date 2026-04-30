# java-prettifier

A dependency-free Java command-line tool that converts raw airline itinerary text into clean, readable travel documents.

It replaces airport-code tokens with real airport or city names, formats ISO date/time values, cleans messy line breaks, validates lookup data, and writes a polished output file.

## New in this upgraded version

This project now has two layers:

- `Prettifier.java` — the stable assignment/review version. Its default behaviour remains simple and unchanged.
- `ItineraryStudio.java` — the creative bonus layer with HTML, Markdown, JSON, boarding-pass export, batch processing, lookup cards, and reports.

That means reviewers can still run the original program normally, while the project also demonstrates stronger product thinking and more advanced CLI design.

## Why this project matters

Raw itinerary exports are often easy for machines to read but uncomfortable for people. This project turns compact travel text into a passenger-friendly format while staying simple enough to review, explain, and run on any machine with Java installed.

## Core features

- Replace IATA airport codes such as `#NBO`
- Replace ICAO airport codes such as `##HKJK`
- Replace airport codes with city names using `*#NBO` and `*##HKJK`
- Format ISO dates using `D(...)`
- Format 12-hour and 24-hour times using `T12(...)` and `T24(...)`
- Normalize Windows, Unix, old Mac, vertical-tab, and form-feed line breaks
- Collapse excessive blank lines
- Validate CSV structure before processing
- Preserve unknown tokens by default instead of deleting information
- Optional strict mode for review and automated validation
- Optional terminal output, colored preview, stats, and unknown-token report
- Example files and smoke-test script included

## Advanced studio features

`ItineraryStudio.java` adds:

- Batch processing for whole folders of `.txt` itineraries
- Printable HTML export
- Boarding-pass style HTML export
- Markdown export
- JSON export for downstream systems
- Airport lookup cards for IATA or ICAO codes
- Processing reports with replacement counts and unresolved tokens
- GitHub Actions CI workflow
- Dedicated studio demo script

More details are available in [`STUDIO_FEATURES.md`](STUDIO_FEATURES.md).

## What you will learn

This project demonstrates:

- String manipulation
- Regular expressions
- Command-line arguments
- Navigating the file system
- Reading from files
- Writing to files
- CSV parsing
- Defensive validation
- Date and time formatting
- Small CLI design
- Batch file workflows
- Multi-format exports
- CI smoke testing

## Supported itinerary tokens

| Token format | Meaning | Example |
|---|---|---|
| `#XXX` | IATA airport code to airport name | `#NBO` |
| `##XXXX` | ICAO airport code to airport name | `##HKJK` |
| `*#XXX` | IATA airport code to city/municipality | `*#NBO` |
| `*##XXXX` | ICAO airport code to city/municipality | `*##HKJK` |
| `D(...)` | ISO date/time to readable date | `D(2026-05-01T09:30+03:00)` |
| `T12(...)` | ISO date/time to 12-hour time | `T12(2026-05-01T09:30+03:00)` |
| `T24(...)` | ISO date/time to 24-hour time | `T24(2026-05-01T09:30+03:00)` |

## Airport lookup CSV format

The CSV must include these columns:

```csv
name,iso_country,municipality,icao_code,iata_code,coordinates
```

Example:

```csv
name,iso_country,municipality,icao_code,iata_code,coordinates
Jomo Kenyatta International Airport,KE,Nairobi,HKJK,NBO,"36.9278,-1.3192"
London Heathrow Airport,GB,London,EGLL,LHR,"-0.461941,51.4706"
```

The parser supports quoted CSV cells, including coordinates that contain commas.

## Requirements

- Java 17 or newer recommended
- Bash for the helper scripts
- No Maven, Gradle, or external dependencies required

Check Java:

```bash
java --version
```

## Installation

```bash
git clone https://github.com/imranshiundu/java-prettifier.git
cd java-prettifier
javac Prettifier.java
```

For the advanced studio layer:

```bash
javac -d build Prettifier.java ItineraryStudio.java
```

## Core usage

Run in Java source mode:

```bash
java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv
```

Or compile first:

```bash
javac -d build Prettifier.java
java -cp build Prettifier ./input.txt ./output.txt ./airport-lookup.csv
```

Show help:

```bash
java Prettifier.java --help
```

## Advanced studio usage

Create printable HTML:

```bash
java -cp build ItineraryStudio examples/input.txt reports/studio/itinerary.html examples/airport-lookup.csv --format html --title "Passenger Itinerary"
```

Create boarding-pass style HTML:

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

Run the studio demo:

```bash
bash scripts/studio-demo.sh
```

## Quick demo

The repository includes example input and airport data.

```bash
bash scripts/demo.sh
```

This creates:

```txt
reports/output.txt
reports/unknown-report.md
```

## Smoke test

```bash
bash scripts/test.sh
```

Expected result:

```txt
All smoke tests passed.
```

## Example

Input:

```txt
Passenger itinerary
Departure airport: #NBO
Departure city: *#NBO
Departure date: D(2026-05-01T09:30+03:00)
Departure time: T24(2026-05-01T09:30+03:00)
Arrival airport: ##EGLL
Arrival city: *##EGLL
Arrival time: T12(2026-05-01T20:10+01:00)
```

Output:

```txt
Passenger itinerary
Departure airport: Jomo Kenyatta International Airport
Departure city: Nairobi
Departure date: 01 May 2026
Departure time: 09:30 (+03:00)
Arrival airport: London Heathrow Airport
Arrival city: London
Arrival time: 08:10PM (+01:00)
```

## Bonus functionality

The default behavior remains the same:

```bash
java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv
```

Optional flags add extra functionality without changing the default behavior.

| Flag | Purpose |
|---|---|
| `--stdout` | Print the prettified itinerary in the terminal |
| `--no-color` | Disable ANSI terminal colors |
| `--quiet` | Suppress success output and only show errors |
| `--stats` | Print replacement statistics |
| `--strict` | Fail if unknown airport codes are found |
| `--validate-only` | Validate input and CSV without writing output |
| `--dry-run` | Alias for `--validate-only` |
| `--unknown-report <file>` | Save unknown airport/date-time tokens to a Markdown report |

Example:

```bash
java Prettifier.java input.txt output.txt airport-lookup.csv --stdout --stats --unknown-report reports/unknown-report.md
```

Strict validation:

```bash
java Prettifier.java input.txt output.txt airport-lookup.csv --strict
```

Validation only:

```bash
java Prettifier.java input.txt output.txt airport-lookup.csv --validate-only --stats
```

## Project structure

```txt
java-prettifier/
├── .github/
│   └── workflows/
│       └── java-ci.yml
├── Prettifier.java
├── ItineraryStudio.java
├── README.md
├── REVIEW_GUIDE.md
├── STUDIO_FEATURES.md
├── examples/
│   ├── airport-lookup.csv
│   └── input.txt
└── scripts/
    ├── demo.sh
    ├── studio-demo.sh
    └── test.sh
```

## Design choices

### Stable core plus creative layer

`Prettifier.java` remains the clean assignment solution. `ItineraryStudio.java` is separated so bonus features do not make the default review path harder to understand.

### Single-file Java core

The main implementation stays in one Java file so reviewers can understand the full program without jumping through a framework.

### Dependency-free

Everything uses the Java standard library. That keeps setup lightweight and makes the project easier to run in class, review, or interview settings.

### Safer unknown-token handling

Unknown airport or date/time tokens are preserved by default. This prevents the tool from silently destroying useful itinerary information.

### Strict mode for quality checks

Strict mode gives reviewers and automated scripts a way to fail fast when unresolved tokens are present.

### Real CSV handling

The CSV parser handles quoted fields, including coordinates that contain commas. This avoids the common beginner mistake of using `split(",")` for CSV data.

## Review checklist

During review, demonstrate:

1. `java Prettifier.java --help`
2. Default three-argument usage
3. `#XXX` IATA airport-name replacement
4. `##XXXX` ICAO airport-name replacement
5. `*#XXX` and `*##XXXX` city replacement
6. `D(...)` date formatting
7. `T12(...)` and `T24(...)` time formatting
8. `--stats`
9. `--unknown-report`
10. `--strict` with the provided `#ZZZ` sample token
11. `bash scripts/test.sh`
12. `bash scripts/studio-demo.sh`
13. Open `reports/studio/itinerary.html`
14. Open `reports/studio/boarding-pass.html`
15. Run `java -cp build ItineraryStudio --lookup NBO examples/airport-lookup.csv`

A more detailed review flow is available in [`REVIEW_GUIDE.md`](REVIEW_GUIDE.md).

## Common errors

### `Input not found`

The input file path is wrong or the file does not exist.

### `Airport lookup not found`

The CSV path is wrong or the file does not exist.

### `Airport lookup malformed`

The CSV is missing required columns, has unclosed quotes, or has empty required cells.

### Unknown airport codes left unchanged

The input contains a code that is not available in the CSV lookup file.

## Future improvements

Possible future additions:

- JUnit tests
- Maven or Gradle build profile
- Timezone name display
- Calendar invite export
- QR code generation for boarding-pass pages
- GUI wrapper

## License

This project is currently provided for learning and review purposes. Add a license file before publishing it as a reusable open-source package.
