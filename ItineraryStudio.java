import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Advanced bonus layer for java-prettifier.
 *
 * Prettifier.java keeps the default school/review behaviour stable.
 * This file adds creative extras: batch processing, lookup cards, Markdown,
 * printable HTML, boarding-pass style HTML, JSON summaries, and reports.
 */
public class ItineraryStudio {
    private static final Pattern AIRPORT_TOKEN = Pattern.compile("\\*?##[A-Z]{4}|\\*?#[A-Z]{3}");
    private static final Pattern DATE_TOKEN = Pattern.compile("D\\(([^)]+)\\)");
    private static final Pattern T12_TOKEN = Pattern.compile("T12\\(([^)]+)\\)");
    private static final Pattern T24_TOKEN = Pattern.compile("T24\\(([^)]+)\\)");

    private static final Map<String, Airport> byIata = new HashMap<>();
    private static final Map<String, Airport> byIcao = new HashMap<>();

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.help) {
                help();
                return;
            }

            if (options.csv == null) {
                help();
                System.exit(1);
            }

            Stats stats = loadAirports(Path.of(options.csv));

            if (options.lookupCode != null) {
                lookup(options.lookupCode, stats);
                return;
            }

            if (options.batch) {
                runBatch(options, stats);
            } else {
                runOne(Path.of(options.input), Path.of(options.output), options, stats);
            }
        } catch (Exception error) {
            System.err.println("ItineraryStudio error: " + error.getMessage());
            System.exit(1);
        }
    }

    private static void help() {
        System.out.println("java-prettifier advanced studio");
        System.out.println();
        System.out.println("Default advanced run:");
        System.out.println("  java ItineraryStudio.java input.txt output.html airport-lookup.csv --format html");
        System.out.println();
        System.out.println("Batch processing:");
        System.out.println("  java ItineraryStudio.java --batch raw-folder output-folder airport-lookup.csv --format markdown");
        System.out.println();
        System.out.println("Airport lookup card:");
        System.out.println("  java ItineraryStudio.java --lookup NBO airport-lookup.csv");
        System.out.println();
        System.out.println("Formats:");
        System.out.println("  --format text | markdown | html | boarding-pass | json");
        System.out.println();
        System.out.println("Useful extras:");
        System.out.println("  --title \"Passenger Itinerary\"");
        System.out.println("  --report reports/studio-report.md");
    }

    private static void runBatch(Options options, Stats totals) throws IOException {
        Path inputDir = Path.of(options.input);
        Path outputDir = Path.of(options.output);
        Files.createDirectories(outputDir);

        List<Path> files;
        try (Stream<Path> stream = Files.list(inputDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }

        for (Path file : files) {
            Stats fileStats = totals.copyAirportCountOnly();
            String base = stripExtension(file.getFileName().toString());
            Path destination = outputDir.resolve(base + extension(options.format));
            runOne(file, destination, options, fileStats);
            totals.merge(fileStats);
            totals.filesProcessed++;
        }

        if (options.report != null) writeReport(Path.of(options.report), totals, "Batch output: " + outputDir);
        System.out.println("Batch complete. Files processed: " + files.size());
    }

    private static void runOne(Path input, Path output, Options options, Stats stats) throws IOException {
        String raw = Files.readString(input, StandardCharsets.UTF_8);
        String pretty = prettify(raw, stats);
        String rendered = switch (options.format) {
            case "markdown" -> markdown(pretty, options.title, input.getFileName().toString(), stats);
            case "html" -> html(pretty, options.title, input.getFileName().toString(), stats, false);
            case "boarding-pass" -> html(pretty, options.title, input.getFileName().toString(), stats, true);
            case "json" -> json(pretty, stats);
            default -> pretty;
        };

        if (output.getParent() != null) Files.createDirectories(output.getParent());
        Files.writeString(output, rendered, StandardCharsets.UTF_8);

        if (!options.batch && options.report != null) writeReport(Path.of(options.report), stats, "Output: " + output);
        if (!options.batch) System.out.println("Created " + output);
    }

    private static Stats loadAirports(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new IOException("airport CSV is empty");

        List<String> header = csvLine(lines.get(0));
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) index.put(header.get(i), i);

        String[] required = {"name", "iso_country", "municipality", "icao_code", "iata_code", "coordinates"};
        for (String column : required) {
            if (!index.containsKey(column)) throw new IOException("missing CSV column: " + column);
        }

        Stats stats = new Stats();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> cells = csvLine(lines.get(i));
            Airport airport = new Airport(
                    cell(cells, index.get("name")),
                    cell(cells, index.get("iso_country")),
                    cell(cells, index.get("municipality")),
                    cell(cells, index.get("icao_code")).toUpperCase(Locale.ROOT),
                    cell(cells, index.get("iata_code")).toUpperCase(Locale.ROOT),
                    cell(cells, index.get("coordinates"))
            );
            if (airport.iata.matches("[A-Z]{3}")) byIata.put(airport.iata, airport);
            if (airport.icao.matches("[A-Z]{4}")) byIcao.put(airport.icao, airport);
            stats.airportsLoaded++;
        }
        return stats;
    }

    private static List<String> csvLine(String line) throws IOException {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (quoted) throw new IOException("unclosed quote in CSV");
        cells.add(current.toString().trim());
        return cells;
    }

    private static String cell(List<String> cells, int index) throws IOException {
        if (index >= cells.size() || cells.get(index).isBlank()) throw new IOException("CSV row has an empty required cell");
        return cells.get(index).trim();
    }

    private static String prettify(String raw, Stats stats) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\u000B', '\n').replace('\f', '\n');
        text = replaceAirports(text, stats);
        text = replaceDateTime(text, DATE_TOKEN, "dd MMM yyyy", stats, "date");
        text = replaceDateTime(text, T12_TOKEN, "hh:mma", stats, "t12");
        text = replaceDateTime(text, T24_TOKEN, "HH:mm", stats, "t24");
        return text.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").stripTrailing() + "\n";
    }

    private static String replaceAirports(String text, Stats stats) {
        Matcher matcher = AIRPORT_TOKEN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group();
            String replacement = airportReplacement(token);
            if (replacement == null) {
                stats.unknownTokens.add(token);
                matcher.appendReplacement(out, Matcher.quoteReplacement(token));
            } else {
                stats.airportReplacements++;
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String airportReplacement(String token) {
        Airport airport;
        if (token.startsWith("*##")) {
            airport = byIcao.get(token.substring(3));
            return airport == null ? null : airport.city;
        }
        if (token.startsWith("##")) {
            airport = byIcao.get(token.substring(2));
            return airport == null ? null : airport.name;
        }
        if (token.startsWith("*#")) {
            airport = byIata.get(token.substring(2));
            return airport == null ? null : airport.city;
        }
        airport = byIata.get(token.substring(1));
        return airport == null ? null : airport.name;
    }

    private static String replaceDateTime(String text, Pattern pattern, String format, Stats stats, String kind) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            try {
                OffsetDateTime value = OffsetDateTime.parse(matcher.group(1));
                String replacement = value.format(DateTimeFormatter.ofPattern(format, Locale.ENGLISH));
                if (kind.equals("t12") || kind.equals("t24")) replacement += " (" + value.getOffset() + ")";
                if (kind.equals("date")) stats.dateReplacements++;
                if (kind.equals("t12")) stats.time12Replacements++;
                if (kind.equals("t24")) stats.time24Replacements++;
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
            } catch (DateTimeParseException error) {
                stats.unknownTokens.add(matcher.group());
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static void lookup(String code, Stats stats) {
        String clean = code.replace("#", "").replace("*", "").toUpperCase(Locale.ROOT);
        Airport airport = clean.length() == 4 ? byIcao.get(clean) : byIata.get(clean);
        if (airport == null) throw new IllegalArgumentException("airport code not found: " + code);
        System.out.println("╭──────────────── airport card ────────────────╮");
        System.out.println("  " + airport.name);
        System.out.println("  City:        " + airport.city);
        System.out.println("  Country:     " + airport.country);
        System.out.println("  IATA / ICAO: " + airport.iata + " / " + airport.icao);
        System.out.println("  Coordinates: " + airport.coordinates);
        System.out.println("  CSV rows:    " + stats.airportsLoaded);
        System.out.println("╰──────────────────────────────────────────────╯");
    }

    private static String markdown(String pretty, String title, String source, Stats stats) {
        StringBuilder out = new StringBuilder("# " + md(title) + "\n\n");
        out.append("Generated from `").append(md(source)).append("`.\n\n");
        for (String line : pretty.split("\\n")) {
            if (line.isBlank()) out.append("\n");
            else out.append("- ").append(md(line)).append("\n");
        }
        out.append("\n---\n\n").append(summaryTable(stats));
        return out.toString();
    }

    private static String html(String pretty, String title, String source, Stats stats, boolean boardingPass) {
        StringBuilder rows = new StringBuilder();
        for (String line : pretty.split("\\n")) {
            if (line.isBlank()) rows.append("<div class=\"gap\"></div>\n");
            else if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                rows.append("<div class=\"row\"><span>").append(htmlEscape(parts[0].trim())).append("</span><strong>")
                        .append(htmlEscape(parts[1].trim())).append("</strong></div>\n");
            } else {
                rows.append("<p>").append(htmlEscape(line)).append("</p>\n");
            }
        }

        String mode = boardingPass ? "boarding-pass" : "document";
        return "<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n" +
                "<title>" + htmlEscape(title) + "</title>\n" +
                "<style>body{margin:0;background:#f4f4f5;color:#111827;font-family:Inter,Arial,sans-serif;}main{max-width:860px;margin:40px auto;padding:34px;background:white;border-radius:28px;box-shadow:0 30px 90px rgba(0,0,0,.10);}body.boarding-pass main{max-width:760px;border:2px dashed #111827}.eyebrow{font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:#6d28d9;font-weight:800}h1{margin:8px 0 6px;font-size:38px;line-height:1.05}.source{color:#6b7280;margin-bottom:26px}.row{display:grid;grid-template-columns:220px 1fr;gap:18px;border-bottom:1px solid #ececec;padding:14px 0}.row span{color:#6b7280}.row strong{font-size:18px}.gap{height:18px}.summary{margin-top:28px;padding:18px;border-radius:18px;background:#faf5ff}footer{margin-top:24px;color:#6b7280;font-size:12px}@media print{body{background:white}main{box-shadow:none;margin:0;border-radius:0}}</style>\n" +
                "</head>\n<body class=\"" + mode + "\">\n<main>\n<div class=\"eyebrow\">java-prettifier studio</div>\n<h1>" + htmlEscape(title) + "</h1>\n" +
                "<div class=\"source\">Source: " + htmlEscape(source) + "</div>\n" + rows +
                "<section class=\"summary\">" + htmlEscape(stats.shortSummary()) + "</section>\n<footer>Generated locally by ItineraryStudio.java</footer>\n</main>\n</body>\n</html>\n";
    }

    private static String json(String pretty, Stats stats) {
        return "{\n" +
                "  \"content\": \"" + jsonEscape(pretty) + "\",\n" +
                "  \"summary\": {\n" +
                "    \"airportsLoaded\": " + stats.airportsLoaded + ",\n" +
                "    \"airportReplacements\": " + stats.airportReplacements + ",\n" +
                "    \"dateReplacements\": " + stats.dateReplacements + ",\n" +
                "    \"time12Replacements\": " + stats.time12Replacements + ",\n" +
                "    \"time24Replacements\": " + stats.time24Replacements + ",\n" +
                "    \"unknownTokens\": " + stats.unknownTokens.size() + "\n" +
                "  }\n" +
                "}\n";
    }

    private static void writeReport(Path path, Stats stats, String target) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        StringBuilder report = new StringBuilder("# ItineraryStudio report\n\n");
        report.append(target).append("\n\n");
        report.append(summaryTable(stats)).append("\n");
        report.append("## Unknown tokens\n\n");
        if (stats.unknownTokens.isEmpty()) report.append("None\n");
        else for (String token : stats.unknownTokens) report.append("- `").append(md(token)).append("`\n");
        Files.writeString(path, report.toString(), StandardCharsets.UTF_8);
    }

    private static String summaryTable(Stats stats) {
        return "## Processing summary\n\n" +
                "| Metric | Value |\n|---|---:|\n" +
                "| Airports loaded | " + stats.airportsLoaded + " |\n" +
                "| Files processed | " + stats.filesProcessed + " |\n" +
                "| Airport replacements | " + stats.airportReplacements + " |\n" +
                "| Date replacements | " + stats.dateReplacements + " |\n" +
                "| T12 replacements | " + stats.time12Replacements + " |\n" +
                "| T24 replacements | " + stats.time24Replacements + " |\n" +
                "| Unknown tokens | " + stats.unknownTokens.size() + " |\n";
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot == -1 ? name : name.substring(0, dot);
    }

    private static String extension(String format) {
        return switch (format) {
            case "markdown" -> ".md";
            case "html", "boarding-pass" -> ".html";
            case "json" -> ".json";
            default -> ".txt";
        };
    }

    private static String md(String text) {
        return text.replace("\\", "\\\\").replace("`", "\\`").replace("*", "\\*").replace("_", "\\_");
    }

    private static String htmlEscape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String jsonEscape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private record Airport(String name, String country, String city, String icao, String iata, String coordinates) {}

    private static class Stats {
        int airportsLoaded;
        int filesProcessed = 1;
        int airportReplacements;
        int dateReplacements;
        int time12Replacements;
        int time24Replacements;
        final List<String> unknownTokens = new ArrayList<>();

        Stats copyAirportCountOnly() {
            Stats copy = new Stats();
            copy.airportsLoaded = airportsLoaded;
            copy.filesProcessed = 1;
            return copy;
        }

        void merge(Stats other) {
            airportReplacements += other.airportReplacements;
            dateReplacements += other.dateReplacements;
            time12Replacements += other.time12Replacements;
            time24Replacements += other.time24Replacements;
            unknownTokens.addAll(other.unknownTokens);
        }

        String shortSummary() {
            return airportReplacements + " airport replacements · " + dateReplacements + " dates · " +
                    (time12Replacements + time24Replacements) + " times · " + unknownTokens.size() + " unresolved tokens";
        }
    }

    private static class Options {
        String input;
        String output;
        String csv;
        String title = "Passenger Itinerary";
        String format = "text";
        String report;
        String lookupCode;
        boolean help;
        boolean batch;

        static Options parse(String[] args) {
            Options options = new Options();
            List<String> positional = new ArrayList<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h", "--help" -> options.help = true;
                    case "--batch" -> options.batch = true;
                    case "--lookup" -> options.lookupCode = value(args, ++i, "--lookup");
                    case "--title" -> options.title = value(args, ++i, "--title");
                    case "--report" -> options.report = value(args, ++i, "--report");
                    case "--format" -> options.format = value(args, ++i, "--format").toLowerCase(Locale.ROOT);
                    default -> {
                        if (arg.startsWith("--")) throw new IllegalArgumentException("unknown option: " + arg);
                        positional.add(arg);
                    }
                }
            }

            if (!List.of("text", "markdown", "html", "boarding-pass", "json").contains(options.format)) {
                throw new IllegalArgumentException("format must be text, markdown, html, boarding-pass, or json");
            }

            if (options.help) return options;
            if (options.lookupCode != null) {
                if (positional.size() != 1) throw new IllegalArgumentException("lookup mode needs one airport CSV path");
                options.csv = positional.get(0);
                return options;
            }

            if (positional.size() != 3) throw new IllegalArgumentException("expected input, output, and airport CSV paths");
            options.input = positional.get(0);
            options.output = positional.get(1);
            options.csv = positional.get(2);
            return options;
        }

        private static String value(String[] args, int index, String flag) {
            if (index >= args.length) throw new IllegalArgumentException(flag + " requires a value");
            return args[index];
        }
    }
}
