# java-prettifier

A professional Java command-line tool for converting raw airline itinerary text into clean, readable travel documents.

The program reads a plain text itinerary, replaces airport code tokens with real airport or city names using an airport lookup CSV, formats ISO dates and times, normalizes messy line breaks, and writes a polished output file.

## Project overview

Travel systems often export itinerary text in compact machine-style formats. For example:

```txt
Flight departs from #NBO on D(2026-05-01T09:30+03:00)
Boarding time: T24(2026-05-01T07:45+03:00)
Arrival city: *#LHR
Arrival airport: ##EGLL
```

`java-prettifier` turns that into something more readable:

```txt
Flight departs from Jomo Kenyatta International Airport on 01 May 2026
Boarding time: 07:45 (+03:00)
Arrival city: London
Arrival airport: London Heathrow Airport
```

## What you will learn

This project demonstrates core Java fundamentals:

- String manipulation
- Regular expressions
- Command-line arguments
- Navigating the file system
- Reading files
- Writing files
- CSV parsing
- Date and time formatting
- Defensive input validation
- Small CLI design

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

The parser supports quoted CSV cells, including quoted coordinates containing commas.

## Setup and installation

### Requirements

- Java 17 or newer recommended
- No external dependencies
- No Maven or Gradle required

Check your Java version:

```bash
java --version
```

Clone the repository:

```bash
git clone https://github.com/imranshiundu/java-prettifier.git
cd java-prettifier
```

Compile:

```bash
javac Prettifier.java
```

## Usage guide

Run directly with Java source mode:

```bash
java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv
```

Or compile first and run the class:

```bash
javac Prettifier.java
java Prettifier ./input.txt ./output.txt ./airport-lookup.csv
```

Show help:

```bash
java Prettifier.java --help
```

## Example

Create `input.txt`:

```txt
Passenger itinerary
Departure: #NBO
Departure city: *#NBO
Departure date: D(2026-05-01T09:30+03:00)
Departure time: T24(2026-05-01T09:30+03:00)
Arrival: ##EGLL
Arrival city: *##EGLL
Arrival time: T12(2026-05-01T16:10+01:00)
```

Create `airport-lookup.csv`:

```csv
name,iso_country,municipality,icao_code,iata_code,coordinates
Jomo Kenyatta International Airport,KE,Nairobi,HKJK,NBO,"36.9278,-1.3192"
London Heathrow Airport,GB,London,EGLL,LHR,"-0.461941,51.4706"
```

Run:

```bash
java Prettifier.java input.txt output.txt airport-lookup.csv --stdout --stats
```

Expected output file:

```txt
Passenger itinerary
Departure: Jomo Kenyatta International Airport
Departure city: Nairobi
Departure date: 01 May 2026
Departure time: 09:30 (+03:00)
Arrival: London Heathrow Airport
Arrival city: London
Arrival time: 04:10PM (+01:00)
```

## Bonus functionality implemented

The default functional behavior is preserved: the program still accepts:

```bash
java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv
```

Additional bonus flags are optional:

| Flag | Purpose |
|---|---|
| `--stdout` | Print the prettified itinerary in the terminal |
| `--no-color` | Disable ANSI terminal colors |
| `--quiet` | Suppress success output and only show errors |
| `--stats` | Print replacement statistics |
| `--strict` | Fail if unknown airport codes are found |
| `--validate-only` | Validate files and tokens without writing output |
| `--dry-run` | Alias for `--validate-only` |
| `--unknown-report <file>` | Save unknown airport/date-time tokens to a Markdown report |

Example with bonus flags:

```bash
java Prettifier.java input.txt output.txt airport-lookup.csv --stdout --stats --unknown-report reports/unknown.md
```

Strict validation:

```bash
java Prettifier.java input.txt output.txt airport-lookup.csv --strict
```

Validation only:

```bash
java Prettifier.java input.txt output.txt airport-lookup.csv --validate-only --stats
```

## Design choices

### Single-file Java

The project stays easy to review and run. A reviewer can open one file, compile it, and understand the full flow without installing dependencies.

### No external libraries

CSV parsing, token parsing, and formatting are implemented using Java standard library features only.

### Defensive file handling

The program checks whether the input and airport lookup files exist and whether they are regular files before processing.

### CSV parser upgrade

The original simple `split(",")` approach breaks when coordinates contain commas. The upgraded version includes a small CSV parser that handles quoted cells correctly.

### Unknown tokens are preserved

If an airport code or date/time token cannot be resolved, it is left unchanged instead of being deleted. This makes the program safer because it does not silently destroy information.

## Review demonstration checklist

During review, be ready to demonstrate:

1. Running the help command
2. Running the default three-argument command
3. Showing airport-name replacement with `#XXX` and `##XXXX`
4. Showing city-name replacement with `*#XXX` and `*##XXXX`
5. Showing date formatting with `D(...)`
6. Showing 12-hour and 24-hour time formatting
7. Showing messy blank-line cleanup
8. Showing `--stats`
9. Showing `--strict` with an unknown airport code
10. Explaining why quoted CSV parsing was needed

## Common errors

### `Input not found`

The input file path is wrong or the file does not exist.

### `Airport lookup not found`

The CSV path is wrong or the file does not exist.

### `Airport lookup malformed`

The CSV is missing required columns, has unclosed quotes, or has empty required cells.

### Unknown airport codes left unchanged

The input contains a code that is not available in the CSV lookup file.

## Project structure

```txt
java-prettifier/
├── Prettifier.java
└── README.md
```

## Future improvements

Possible future additions:

- Unit tests with JUnit
- Maven or Gradle build file
- JSON output mode
- Batch processing for folders
- Timezone name display
- Export summary report as HTML

## License

This project is currently provided for learning and review purposes. Add a license file before publishing it as an open-source package.
