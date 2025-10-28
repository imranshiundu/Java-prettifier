import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

public class Prettifier {

    private static Map<String, String> iataToAirport = new HashMap<>();
    private static Map<String, String> icaoToAirport = new HashMap<>();
    private static Map<String, String> iataToCity = new HashMap<>();
    private static Map<String, String> icaoToCity = new HashMap<>();

    // Color codes for terminal output
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("-h")) {
            printUsage();
            return;
        }

        if (args.length != 3) {
            printUsage();
            return;
        }

        String inputPath = args[0];
        String outputPath = args[1];
        String airportCsvPath = args[2];

        Path inputFile = Paths.get(inputPath);
        Path airportFile = Paths.get(airportCsvPath);

        if (!Files.exists(inputFile)) {
            System.out.println(RED + "Input not found" + RESET);
            return;
        }
        if (!Files.exists(airportFile)) {
            System.out.println(RED + "Airport lookup not found" + RESET);
            return;
        }

        try {
            loadAirportLookup(airportFile);
        } catch (Exception e) {
            System.out.println(RED + "Airport lookup malformed" + RESET);
            return;
        }

        try {
            List<String> lines = Files.readAllLines(inputFile);
            StringBuilder output = new StringBuilder();
            StringBuilder coloredOutput = new StringBuilder();

            for (String line : lines) {
                line = line.replaceAll("[\\v\\f\\r]", "\n");
                
                String processedLine = replaceAirportCodes(line);
                processedLine = replaceDatesAndTimes(processedLine);
                output.append(processedLine).append("\n");
                
                String coloredLine = addColorsToLine(processedLine);
                coloredOutput.append(coloredLine).append("\n");
            }

            String finalOutput = output.toString();
            finalOutput = finalOutput.replaceAll("\\n{3,}", "\n\n");
            finalOutput = finalOutput.replaceAll("(?m)^\\s*$[\r\n]+", "\n");

            Files.write(Paths.get(outputPath), finalOutput.getBytes());

            System.out.println(BOLD + CYAN + "=== PRETTIFIED ITINERARY ===" + RESET);
            System.out.println(coloredOutput.toString());
            System.out.println(GREEN + "✓ Output successfully written to: " + outputPath + RESET);

        } catch (IOException e) {
            System.out.println(RED + "Error processing files" + RESET);
        }
    }

    private static void printUsage() {
        System.out.println(BOLD + YELLOW + "itinerary usage:" + RESET);
        System.out.println(CYAN + "$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv" + RESET);
    }

    private static void loadAirportLookup(Path csvPath) throws Exception {
        List<String> lines = Files.readAllLines(csvPath);
        if (lines.isEmpty()) throw new Exception("Empty CSV");

        String headerLine = lines.get(0);
        String[] headers = headerLine.split(",", -1);
        Map<String, Integer> colIndex = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            colIndex.put(headers[i].trim(), i);
        }

        String[] requiredCols = {"name", "iso_country", "municipality", "icao_code", "iata_code", "coordinates"};
        for (String col : requiredCols) {
            if (!colIndex.containsKey(col)) throw new Exception("Missing column: " + col);
        }

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;
            
            String[] cols = line.split(",", -1);
            if (cols.length < headers.length) throw new Exception("Malformed row");
            
            for (int j = 0; j < requiredCols.length; j++) {
                String colName = requiredCols[j];
                int index = colIndex.get(colName);
                if (index >= cols.length || cols[index].trim().isEmpty()) {
                    throw new Exception("Empty cell in column: " + colName);
                }
            }

            String name = cols[colIndex.get("name")].trim();
            String city = cols[colIndex.get("municipality")].trim();
            String iata = cols[colIndex.get("iata_code")].trim();
            String icao = cols[colIndex.get("icao_code")].trim();

            if (!iata.isEmpty() && iata.length() == 3) {
                iataToAirport.put(iata, name);
                iataToCity.put(iata, city);
            }
            if (!icao.isEmpty() && icao.length() == 4) {
                icaoToAirport.put(icao, name);
                icaoToCity.put(icao, city);
            }
        }
    }

    private static String replaceAirportCodes(String line) {
        line = replacePattern(line, "#([A-Z]{3})", iataToAirport);
        line = replacePattern(line, "##([A-Z]{4})", icaoToAirport);
        line = replacePattern(line, "\\*#([A-Z]{3})", iataToCity);
        line = replacePattern(line, "\\*##([A-Z]{4})", icaoToCity);
        return line;
    }

    private static String replacePattern(String text, String regex, Map<String, String> map) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String code = matcher.group(1);
            String replacement = map.getOrDefault(code, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceDatesAndTimes(String line) {
        line = replaceDatePattern(line, "D\\(([^)]+)\\)");
        line = replaceTimePattern(line, "T12\\(([^)]+)\\)", true);
        line = replaceTimePattern(line, "T24\\(([^)]+)\\)", false);
        return line;
    }

    private static String replaceDatePattern(String text, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        DateTimeFormatter inputFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

        while (matcher.find()) {
            String original = matcher.group(1);
            try {
                OffsetDateTime odt = OffsetDateTime.parse(original, inputFormat);
                matcher.appendReplacement(sb, odt.format(outputFormat));
            } catch (DateTimeParseException e) {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceTimePattern(String text, String regex, boolean is12Hour) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String original = matcher.group(1);
            try {
                OffsetDateTime odt = OffsetDateTime.parse(original);
                String replacement;
                if (is12Hour) {
                    String timePart = odt.format(DateTimeFormatter.ofPattern("hh:mma", Locale.ENGLISH));
                    replacement = timePart + " (" + formatOffset(odt.getOffset()) + ")";
                } else {
                    replacement = odt.format(DateTimeFormatter.ofPattern("HH:mm")) + " (" + formatOffset(odt.getOffset()) + ")";
                }
                matcher.appendReplacement(sb, replacement);
            } catch (DateTimeParseException e) {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String formatOffset(ZoneOffset offset) {
        if (offset.equals(ZoneOffset.UTC)) return "+00:00";
        int seconds = offset.getTotalSeconds();
        int hours = Math.abs(seconds / 3600);
        int minutes = Math.abs((seconds % 3600) / 60);
        String sign = seconds >= 0 ? "+" : "-";
        return String.format("%s%02d:%02d", sign, hours, minutes);
    }

    private static String addColorsToLine(String line) {
        String coloredLine = line;
        
        for (Map.Entry<String, String> entry : iataToAirport.entrySet()) {
            coloredLine = coloredLine.replace(entry.getValue(), BOLD + BLUE + entry.getValue() + RESET);
        }
        for (Map.Entry<String, String> entry : icaoToAirport.entrySet()) {
            coloredLine = coloredLine.replace(entry.getValue(), BOLD + BLUE + entry.getValue() + RESET);
        }
        
        for (Map.Entry<String, String> entry : iataToCity.entrySet()) {
            coloredLine = coloredLine.replace(entry.getValue(), PURPLE + entry.getValue() + RESET);
        }
        for (Map.Entry<String, String> entry : icaoToCity.entrySet()) {
            coloredLine = coloredLine.replace(entry.getValue(), PURPLE + entry.getValue() + RESET);
        }
        
        coloredLine = coloredLine.replaceAll("(\\d{2} [A-Z][a-z]{2} \\d{4})", BOLD + GREEN + "$1" + RESET);
        
        coloredLine = coloredLine.replaceAll("(\\d{1,2}:\\d{2}(?:[AP]M)?) \\(([+-]\\d{2}:\\d{2})\\)", 
                              YELLOW + "$1" + RESET + " (" + CYAN + "$2" + RESET + ")");
        
        return coloredLine;
    }
}