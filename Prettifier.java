import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Prettifier {
    private static final Map<String, String> iataToAirport = new HashMap<>();
    private static final Map<String, String> icaoToAirport = new HashMap<>();
    private static final Map<String, String> iataToCity = new HashMap<>();
    private static final Map<String, String> icaoToCity = new HashMap<>();

    private static final Pattern AIRPORT_TOKEN = Pattern.compile("\\*?##[A-Z]{4}|\\*?#[A-Z]{3}");
    private static final Pattern DATE_TOKEN = Pattern.compile("D\\(([^)]+)\\)");
    private static final Pattern TIME_12_TOKEN = Pattern.compile("T12\\(([^)]+)\\)");
    private static final Pattern TIME_24_TOKEN = Pattern.compile("T24\\(([^)]+)\\)");

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int run(String[] args) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
        } catch (IllegalArgumentException error) {
            System.out.println(color(RED, error.getMessage(), true));
            printUsage();
            return 1;
        }

        if (options.help) {
            printUsage();
            return 0;
        }

        if (options.inputPath == null || options.outputPath == null || options.airportCsvPath == null) {
            printUsage();
            return 1;
        }

        Path inputFile = Paths.get(options.inputPath);
        Path outputFile = Paths.get(options.outputPath);
        Path airportFile = Paths.get(options.airportCsvPath);

        if (!Files.exists(inputFile)) {
            System.out.println(color(RED, "Input not found", options.color));
            return 1;
        }
        if (!Files.isRegularFile(inputFile)) {
            System.out.println(color(RED, "Input is not a file", options.color));
            return 1;
        }
        if (!Files.exists(airportFile)) {
            System.out.println(color(RED, "Airport lookup not found", options.color));
            return 1;
        }
        if (!Files.isRegularFile(airportFile)) {
            System.out.println(color(RED, "Airport lookup is not a file", options.color));
            return 1;
        }

        Stats stats = new Stats();
        try {
            stats.airportsLoaded = loadAirportLookup(airportFile);
        } catch (Exception error) {
            System.out.println(color(RED, "Airport lookup malformed: " + error.getMessage(), options.color));
            return 1;
        }

        try {
            String input = Files.readString(inputFile, StandardCharsets.UTF_8);
            String pretty = prettify(input, stats);

            if (options.strict && !stats.unknownAirportTokens.isEmpty()) {
                System.out.println(color(RED, "Unknown airport codes found. Output was not written.", options.color));
                printWarnings(stats, options.color);
                writeUnknownReportIfNeeded(options, stats);
                return 2;
            }

            if (options.validateOnly) {
                System.out.println(color(GREEN, "Validation passed. No output file was written.", options.color));
                if (options.stats) printStats(stats, options.color);
                writeUnknownReportIfNeeded(options, stats);
                return 0;
            }

            if (outputFile.getParent() != null) {
                Files.createDirectories(outputFile.getParent());
            }
            Files.writeString(outputFile, pretty, StandardCharsets.UTF_8);

            if (!options.quiet) {
                System.out.println(color(CYAN + BOLD, "=== PRETTIFIED ITINERARY ===", options.color));
                if (options.stdout) {
                    System.out.println(addColorsToText(pretty, options.color));
                }
                System.out.println(color(GREEN, "✓ Output successfully written to: " + outputFile, options.color));
                if (options.stats) printStats(stats, options.color);
                printWarnings(stats, options.color);
            }

            writeUnknownReportIfNeeded(options, stats);
            return 0;
        } catch (IOException error) {
            System.out.println(color(RED, "Error processing files: " + error.getMessage(), options.color));
            return 1;
        }
    }

    private static void printUsage() {
        System.out.println(color(YELLOW + BOLD, "java-prettifier usage:", true));
        System.out.println(color(CYAN, "$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv [options]", true));
        System.out.println();
        System.out.println("Required arguments:");
        System.out.println("  input.txt            Raw itinerary text file");
        System.out.println("  output.txt           Destination file for prettified output");
        System.out.println("  airport-lookup.csv   Airport lookup CSV");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help                 Show this help screen");
        System.out.println("  --stdout                   Print prettified output to the terminal");
        System.out.println("  --no-color                 Disable ANSI terminal colors");
        System.out.println("  --quiet                    Only print errors");
        System.out.println("  --stats                    Print replacement statistics");
        System.out.println("  --strict                   Fail if unknown airport codes are found");
        System.out.println("  --validate-only            Validate input and CSV without writing output");
        System.out.println("  --unknown-report <file>    Save unknown airport codes to a report file");
        System.out.println();
        System.out.println("Supported tokens:");
        System.out.println("  #LHR       -> airport name by IATA code");
        System.out.println("  ##EGLL     -> airport name by ICAO code");
        System.out.println("  *#LHR      -> city name by IATA code");
        System.out.println("  *##EGLL    -> city name by ICAO code");
        System.out.println("  D(...)     -> date, formatted as dd MMM yyyy");
        System.out.println("  T12(...)   -> time, formatted as 12-hour time with offset");
        System.out.println("  T24(...)   -> time, formatted as 24-hour time with offset");
    }

    private static int loadAirportLookup(Path csvPath) throws Exception {
        iataToAirport.clear();
        icaoToAirport.clear();
        iataToCity.clear();
        icaoToCity.clear();

        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        if (lines.isEmpty()) throw new Exception("CSV is empty");

        List<String> headers = parseCsvLine(lines.get(0));
        Map<String, Integer> colIndex = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            colIndex.put(headers.get(i).trim(), i);
        }

        String[] requiredCols = {"name", "iso_country", "municipality", "icao_code", "iata_code", "coordinates"};
        for (String col : requiredCols) {
            if (!colIndex.containsKey(col)) throw new Exception("missing column: " + col);
        }

        int loaded = 0;
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;

            List<String> cols = parseCsvLine(line);
            if (cols.size() < headers.size()) {
                throw new Exception("row " + (i + 1) + " has fewer columns than the header");
            }

            for (String colName : requiredCols) {
                int index = colIndex.get(colName);
                if (index >= cols.size() || cols.get(index).trim().isEmpty()) {
                    throw new Exception("row " + (i + 1) + " has an empty required cell: " + colName);
                }
            }

            String name = cols.get(colIndex.get("name")).trim();
            String city = cols.get(colIndex.get("municipality")).trim();
            String iata = cols.get(colIndex.get("iata_code")).trim().toUpperCase(Locale.ROOT);
            String icao = cols.get(colIndex.get("icao_code")).trim().toUpperCase(Locale.ROOT);

            if (iata.matches("[A-Z]{3}")) {
                iataToAirport.put(iata, name);
                iataToCity.put(iata, city);
            }
            if (icao.matches("[A-Z]{4}")) {
                icaoToAirport.put(icao, name);
                icaoToCity.put(icao, city);
            }
            loaded++;
        }
        return loaded;
    }

    private static List<String> parseCsvLine(String line) throws Exception {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (insideQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (ch == ',' && !insideQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        if (insideQuotes) throw new Exception("CSV row has unclosed quotes");
        cells.add(current.toString());
        return cells;
    }

    private static String prettify(String input, Stats stats) {
        String normalized = normalizeLineBreaks(input);
        String withAirports = replaceAirportCodes(normalized, stats);
        String withDates = replaceDatePattern(withAirports, stats);
        String withTimes12 = replaceTimePattern(withDates, TIME_12_TOKEN, true, stats);
        String withTimes24 = replaceTimePattern(withTimes12, TIME_24_TOKEN, false, stats);
        return collapseBlankLines(withTimes24);
    }

    private static String normalizeLineBreaks(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u000B', '\n')
                .replace('\f', '\n');
    }

    private static String collapseBlankLines(String text) {
        return text.replaceAll("[ \t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .stripTrailing() + "\n";
    }

    private static String replaceAirportCodes(String text, Stats stats) {
        Matcher matcher = AIRPORT_TOKEN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String token = matcher.group();
            String replacement = airportReplacement(token);
            if (replacement == null) {
                stats.unknownAirportTokens.add(token);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
            } else {
                stats.airportReplacements++;
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String airportReplacement(String token) {
        if (token.startsWith("*##")) return icaoToCity.get(token.substring(3));
        if (token.startsWith("##")) return icaoToAirport.get(token.substring(2));
        if (token.startsWith("*#")) return iataToCity.get(token.substring(2));
        if (token.startsWith("#")) return iataToAirport.get(token.substring(1));
        return null;
    }

    private static String replaceDatePattern(String text, Stats stats) {
        Matcher matcher = DATE_TOKEN.matcher(text);
        StringBuffer sb = new StringBuffer();
        DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

        while (matcher.find()) {
            String original = matcher.group(1);
            try {
                OffsetDateTime odt = OffsetDateTime.parse(original);
                stats.dateReplacements++;
                matcher.appendReplacement(sb, Matcher.quoteReplacement(odt.format(outputFormat)));
            } catch (DateTimeParseException error) {
                stats.invalidDateTimeTokens.add(matcher.group());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceTimePattern(String text, Pattern pattern, boolean is12Hour, Stats stats) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String original = matcher.group(1);
            try {
                OffsetDateTime odt = OffsetDateTime.parse(original);
                String replacement;
                if (is12Hour) {
                    replacement = odt.format(DateTimeFormatter.ofPattern("hh:mma", Locale.ENGLISH)) + " (" + formatOffset(odt.getOffset()) + ")";
                    stats.time12Replacements++;
                } else {
                    replacement = odt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)) + " (" + formatOffset(odt.getOffset()) + ")";
                    stats.time24Replacements++;
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } catch (DateTimeParseException error) {
                stats.invalidDateTimeTokens.add(matcher.group());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String formatOffset(ZoneOffset offset) {
        int seconds = offset.getTotalSeconds();
        int hours = Math.abs(seconds / 3600);
        int minutes = Math.abs((seconds % 3600) / 60);
        String sign = seconds >= 0 ? "+" : "-";
        return String.format("%s%02d:%02d", sign, hours, minutes);
    }

    private static String addColorsToText(String text, boolean enableColor) {
        if (!enableColor) return text;

        String colored = text;
        Set<String> airportNames = new HashSet<>();
        airportNames.addAll(iataToAirport.values());
        airportNames.addAll(icaoToAirport.values());
        for (String name : airportNames) {
            if (!name.isBlank()) {
                colored = colored.replace(name, color(BLUE + BOLD, name, true));
            }
        }

        Set<String> cityNames = new HashSet<>();
        cityNames.addAll(iataToCity.values());
        cityNames.addAll(icaoToCity.values());
        for (String city : cityNames) {
            if (!city.isBlank()) {
                colored = colored.replace(city, color(PURPLE, city, true));
            }
        }

        colored = colored.replaceAll("(\\d{2} [A-Z][a-z]{2} \\d{4})", BOLD + GREEN + "$1" + RESET);
        colored = colored.replaceAll("(\\d{1,2}:\\d{2}(?:[AP]M)?) \\(([+-]\\d{2}:\\d{2})\\)",
                YELLOW + "$1" + RESET + " (" + CYAN + "$2" + RESET + ")");
        return colored;
    }

    private static String color(String ansi, String text, boolean enableColor) {
        if (!enableColor) return text;
        return ansi + text + RESET;
    }

    private static void printStats(Stats stats, boolean color) {
        System.out.println(color(CYAN + BOLD, "Replacement stats", color));
        System.out.println("  Airports loaded:       " + stats.airportsLoaded);
        System.out.println("  Airport replacements:  " + stats.airportReplacements);
        System.out.println("  Date replacements:     " + stats.dateReplacements);
        System.out.println("  T12 replacements:      " + stats.time12Replacements);
        System.out.println("  T24 replacements:      " + stats.time24Replacements);
        System.out.println("  Unknown airport codes: " + stats.unknownAirportTokens.size());
        System.out.println("  Invalid date/time:     " + stats.invalidDateTimeTokens.size());
    }

    private static void printWarnings(Stats stats, boolean color) {
        if (!stats.unknownAirportTokens.isEmpty()) {
            System.out.println(color(YELLOW, "Unknown airport codes left unchanged: " + String.join(", ", stats.unknownAirportTokens), color));
        }
        if (!stats.invalidDateTimeTokens.isEmpty()) {
            System.out.println(color(YELLOW, "Invalid date/time tokens left unchanged: " + String.join(", ", stats.invalidDateTimeTokens), color));
        }
    }

    private static void writeUnknownReportIfNeeded(CliOptions options, Stats stats) throws IOException {
        if (options.unknownReportPath == null) return;

        Path report = Paths.get(options.unknownReportPath);
        if (report.getParent() != null) Files.createDirectories(report.getParent());

        StringBuilder content = new StringBuilder();
        content.append("# java-prettifier unknown token report\n\n");
        content.append("## Unknown airport codes\n");
        if (stats.unknownAirportTokens.isEmpty()) {
            content.append("None\n");
        } else {
            for (String token : stats.unknownAirportTokens) content.append("- ").append(token).append("\n");
        }
        content.append("\n## Invalid date/time tokens\n");
        if (stats.invalidDateTimeTokens.isEmpty()) {
            content.append("None\n");
        } else {
            for (String token : stats.invalidDateTimeTokens) content.append("- ").append(token).append("\n");
        }

        Files.writeString(report, content.toString(), StandardCharsets.UTF_8);
    }

    private static class Stats {
        int airportsLoaded;
        int airportReplacements;
        int dateReplacements;
        int time12Replacements;
        int time24Replacements;
        final Set<String> unknownAirportTokens = new HashSet<>();
        final Set<String> invalidDateTimeTokens = new HashSet<>();
    }

    private static class CliOptions {
        String inputPath;
        String outputPath;
        String airportCsvPath;
        String unknownReportPath;
        boolean help;
        boolean stdout;
        boolean color = true;
        boolean quiet;
        boolean stats;
        boolean strict;
        boolean validateOnly;

        static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            List<String> positional = new ArrayList<>();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h":
                    case "--help":
                        options.help = true;
                        break;
                    case "--stdout":
                        options.stdout = true;
                        break;
                    case "--no-color":
                        options.color = false;
                        break;
                    case "--quiet":
                        options.quiet = true;
                        break;
                    case "--stats":
                        options.stats = true;
                        break;
                    case "--strict":
                        options.strict = true;
                        break;
                    case "--validate-only":
                    case "--dry-run":
                        options.validateOnly = true;
                        break;
                    case "--unknown-report":
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException("--unknown-report requires a file path");
                        }
                        options.unknownReportPath = args[++i];
                        break;
                    default:
                        if (arg.startsWith("--")) {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                        positional.add(arg);
                        break;
                }
            }

            if (positional.size() > 3) {
                throw new IllegalArgumentException("Too many positional arguments");
            }
            if (positional.size() >= 1) options.inputPath = positional.get(0);
            if (positional.size() >= 2) options.outputPath = positional.get(1);
            if (positional.size() >= 3) options.airportCsvPath = positional.get(2);
            return options;
        }
    }
}
