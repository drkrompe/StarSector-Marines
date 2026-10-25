package com.dillon.starsectormarines.i18n;

import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a translation mod relies on: every key the code names exists, and every pattern parses. */
class TranslationSmokeTest {
    private static final Path MOD = Path.of("mod");
    private static final Pattern LOOKUP = Pattern.compile(
            "Strings\\.(?:get|format|join|resolve)\\(\\s*\"@?([A-Za-z][A-Za-z0-9_]*)\"\\s*[,)]");
    private static final Pattern NAMED = Pattern.compile("Strings\\.named\\(\\s*\"([A-Za-z][A-Za-z0-9_]*)\"");
    private static final Pattern AT_LITERAL = Pattern.compile("\"@([A-Za-z][A-Za-z0-9_]*)\"(?!\\s*\\+)");

    /** Files whose {@code "@x"} literals are cache keys or lowercased case labels, not catalog references. */
    private static final Set<String> NOT_CATALOG_REFERENCES = Set.of("WarshipRenderSystem.java", "MissionDialogue.java");
    /**
     * Prefixes {@code Strings.named} reads that the catalog has no entry for yet, so a translation
     * cannot reach them and they read as the id spelled out. The list may only shrink.
     */
    private static final Set<String> UNTRANSLATED_NAMED_PREFIXES = Set.of("shipHull");
    /** Service-record entries name their pattern key as the first string argument. */
    private static final Pattern COMMENDATION = Pattern.compile(
            "Commendations\\.(?:add\\([^,;]+,[^,;]+,|entry\\(|ref\\()\\s*\"([A-Za-z][A-Za-z0-9_]*)\"\\s*[,)]");

    private static JSONObject catalog() throws Exception {
        String source = Files.readString(MOD.resolve("data/strings/strings.json"));
        return new JSONObject(source.lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n"))).getJSONObject(Strings.NS);
    }

    @Test
    void everyPatternWithArgumentsParsesAndSurvivesATranslationOverlay() throws Exception {
        JSONObject catalog = catalog();
        List<String> broken = new ArrayList<>();
        for (var keys = catalog.keys(); keys.hasNext();) {
            String key = (String) keys.next();
            String text = catalog.optString(key, "");
            if (!text.contains("{")) continue;
            try {
                new MessageFormat(text);
            } catch (IllegalArgumentException e) {
                broken.add(key + ": " + e.getMessage());
            }
        }
        assertTrue(broken.isEmpty(), "unparseable patterns " + broken);
        // A lone apostrophe in a pattern with arguments quotes the text after it and vanishes.
        List<String> loneApostrophes = new ArrayList<>();
        for (var keys = catalog.keys(); keys.hasNext();) {
            String key = (String) keys.next();
            String text = catalog.optString(key, "");
            if (!text.matches("(?s).*\\{[0-9].*")) continue;
            String unescaped = text.replace("''", "");
            if (unescaped.matches("(?s).*'[^{}].*") || unescaped.endsWith("'")) loneApostrophes.add(key);
        }
        assertTrue(loneApostrophes.isEmpty(), "patterns with an undoubled apostrophe " + loneApostrophes);
        try (var strings = new HeadlessStrings(MOD, text -> "«" + text + "»")) {
            String any = (String) catalog.keys().next();
            assertEquals("«" + catalog.getString(any) + "»", Strings.get(any));
        }
    }

    @Test
    void everyKeyTheCodeNamesIsInTheCatalog() throws Exception {
        JSONObject catalog = catalog();
        List<String> keys = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            // Parallel reads keep a whole-tree scan inside the one-second budget.
            List<Path> sources = files.filter(f -> f.toString().endsWith(".java")).toList();
            List<String> texts = sources.parallelStream().map(f -> {
                try { return Files.readString(f); } catch (IOException e) { throw new UncheckedIOException(e); }
            }).toList();
            for (int i = 0; i < sources.size(); i++) {
                Path file = sources.get(i);
                String source = texts.get(i);
                for (var m = LOOKUP.matcher(source); m.find();) keys.add(file.getFileName() + " " + m.group(1));
                for (var m = COMMENDATION.matcher(source); m.find();) keys.add(file.getFileName() + " " + m.group(1));
                if (NOT_CATALOG_REFERENCES.contains(file.getFileName().toString())) continue;
                for (var m = AT_LITERAL.matcher(source); m.find();) keys.add(file.getFileName() + " " + m.group(1));
                for (var m = NAMED.matcher(source); m.find();) prefixes.add(file.getFileName() + " " + m.group(1));
            }
        }
        List<String> missing = new ArrayList<>();
        for (String entry : keys) {
            if (!catalog.has(entry.substring(entry.indexOf(' ') + 1))) missing.add(entry);
        }
        for (String entry : prefixes) {
            String prefix = entry.substring(entry.indexOf(' ') + 1);
            boolean any = false;
            for (var it = catalog.keys(); it.hasNext() && !any;) any = ((String) it.next()).startsWith(prefix);
            if (!any && !UNTRANSLATED_NAMED_PREFIXES.contains(prefix)) missing.add(entry + "*");
        }
        assertTrue(missing.isEmpty(), "keys with no catalog entry: " + missing);
    }

    @Test
    void everyKeyTheMarkupNamesIsInTheCatalog() throws Exception {
        JSONObject catalog = catalog();
        Pattern reference = Pattern.compile("\\{i18n\\.([A-Za-z][A-Za-z0-9_]*)}");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MOD.resolve("data/ui"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".mlx")).toList()) {
                for (var m = reference.matcher(Files.readString(file)); m.find();) {
                    if (!catalog.has(m.group(1))) missing.add(file.getFileName() + " " + m.group(1));
                }
            }
        }
        assertTrue(missing.isEmpty(), "markup keys with no catalog entry: " + missing);
    }

    /** Developer-only markup, whose copy is not shipped to players. */
    private static final Set<String> DEBUG_MARKUP = Set.of("ui-workbench.mlx", "debug-missions.mlx");
    /** The briefing's debug drawer headings, shown only on debug-source missions. */
    private static final Set<String> DEBUG_LITERALS = Set.of("/ SCALE &amp; FIXTURE", "AIR SUPPORT",
            "SQUAD KIT  ·  EVERY CARD UNLOCKED", "MECH BAY", "FIXTURE");

    @Test
    void playerFacingMarkupHasNoLiteralCopy() throws Exception {
        Pattern style = Pattern.compile("(?s)<style>.*?</style>");
        Pattern textNode = Pattern.compile(">([^<>]*)<");
        Pattern word = Pattern.compile("\\p{L}{2,}");
        List<String> literals = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MOD.resolve("data/ui"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".mlx")).toList()) {
                if (DEBUG_MARKUP.contains(file.getFileName().toString())) continue;
                String markup = style.matcher(Files.readString(file)).replaceAll("");
                for (var m = textNode.matcher(markup); m.find();) {
                    String text = m.group(1).replaceAll("\\{[^}]*}", "").strip();
                    if (word.matcher(text.replaceAll("&[a-z]+;", "")).find() && !DEBUG_LITERALS.contains(text))
                        literals.add(file.getFileName() + " \"" + text + "\"");
                }
            }
        }
        assertTrue(literals.isEmpty(), "literal copy in player-facing markup; use {i18n.key}: " + literals);
    }
}
