# Review Guide

Use this guide during a code review, school review, or demo session.

## 1. Project summary

`java-prettifier` converts machine-style airline itinerary text into readable passenger-facing text.

It demonstrates:

- file input/output
- command-line argument parsing
- string manipulation
- regular expressions
- airport-code lookups
- CSV parsing
- date/time formatting
- validation and error handling

## 2. Fast demo

```bash
javac -d build Prettifier.java
java -cp build Prettifier examples/input.txt reports/output.txt examples/airport-lookup.csv --stdout --stats --unknown-report reports/unknown-report.md
```

Then open:

```bash
cat reports/output.txt
cat reports/unknown-report.md
```

## 3. Expected behavior

The program should replace:

- `#NBO` with `Jomo Kenyatta International Airport`
- `*#NBO` with `Nairobi`
- `#DXB` with `Dubai International Airport`
- `##EGLL` with `London Heathrow Airport`
- `D(2026-05-01T09:30+03:00)` with `01 May 2026`
- `T24(...)` with a 24-hour time and UTC offset
- `T12(...)` with a 12-hour time and UTC offset

Unknown tokens such as `#ZZZ` should remain unchanged unless strict mode is enabled.

## 4. Strict-mode demo

```bash
java -cp build Prettifier examples/input.txt reports/strict-output.txt examples/airport-lookup.csv --strict
```

Expected result: the command should fail because `#ZZZ` is intentionally present in the sample input.

## 5. Smoke test

```bash
bash scripts/test.sh
```

Expected result:

```txt
All smoke tests passed.
```

## 6. Design questions and answers

### Why keep it dependency-free?

The project is easier to run, review, and understand. It works with only the Java standard library.

### Why preserve unknown tokens?

Deleting unresolved codes can destroy travel information. Preserving unknown tokens makes the tool safer.

### Why implement CSV parsing manually?

Airport coordinates contain commas. A naive `split(",")` breaks quoted coordinate cells such as `"36.9278,-1.3192"`.

### Why add strict mode?

Default mode is forgiving for users. Strict mode is useful for validation, automated checks, and review demonstrations.

## 7. Suggested explanation of challenges

The hardest part was not replacing simple text. The harder part was making the program safe around malformed CSV data, unknown airport codes, and invalid date/time tokens without silently destroying information.
