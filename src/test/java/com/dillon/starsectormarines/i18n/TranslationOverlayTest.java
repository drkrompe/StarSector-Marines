package com.dillon.starsectormarines.i18n;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Format;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each translation mod under {@code translations/<locale>/} overrides only keys the English catalog has,
 * and every override takes the same arguments in the same forms, so a caller's arguments still land.
 */
class TranslationOverlayTest {
    private static final Path TRANSLATIONS = Path.of("translations");
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)");
    private static final Pattern PRINTF = Pattern.compile("%[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]");

    private static JSONObject json(Path file) throws Exception {
        return new JSONObject(Files.readString(file).lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n")));
    }

    private static List<Path> translations() throws Exception {
        try (Stream<Path> dirs = Files.list(TRANSLATIONS)) {
            return dirs.filter(Files::isDirectory).sorted().toList();
        }
    }

    /** Argument indexes, each with the kind of format it takes ({@code choice}, {@code number} or plain). */
    private static String arguments(String text) {
        TreeSet<String> indexes = new TreeSet<>();
        for (var m = ARGUMENT.matcher(text); m.find();) indexes.add(m.group(1));
        Format[] formats = new MessageFormat(text).getFormatsByArgumentIndex();
        return indexes + Arrays.stream(formats).map(f -> f == null ? "plain" : f.getClass().getSimpleName()).toList().toString();
    }

    private static List<String> printf(String text) {
        List<String> out = new ArrayList<>();
        for (var m = PRINTF.matcher(text); m.find();) out.add(m.group());
        return out;
    }

    /** A lone apostrophe in a MessageFormat pattern quotes the text after it, which then vanishes. */
    private static boolean loneApostrophe(String text) {
        String unescaped = text.replace("''", "");
        return unescaped.matches("(?s).*'[^{}].*") || unescaped.endsWith("'");
    }

    @Test
    void everyOverrideMatchesItsEnglishKeyAndArguments() throws Exception {
        JSONObject english = json(Path.of("mod/data/strings/strings.json")).getJSONObject(Strings.NS);
        List<String> problems = new ArrayList<>();
        for (Path translation : translations()) {
            String locale = translation.getFileName().toString();
            JSONObject copy = json(translation.resolve("data/strings/strings.json")).getJSONObject(Strings.NS);
            for (var keys = copy.keys(); keys.hasNext();) {
                String key = (String) keys.next();
                String text = copy.getString(key);
                if (!english.has(key)) {
                    problems.add(locale + " " + key + ": not in the English catalog");
                    continue;
                }
                String source = english.getString(key);
                boolean pattern = ARGUMENT.matcher(source).find();
                try {
                    if (pattern && !arguments(source).equals(arguments(text)))
                        problems.add(locale + " " + key + ": arguments " + arguments(text) + ", English " + arguments(source));
                } catch (IllegalArgumentException e) {
                    problems.add(locale + " " + key + ": unparseable pattern " + e.getMessage());
                }
                if (!pattern && ARGUMENT.matcher(text).find())
                    problems.add(locale + " " + key + ": takes arguments the English does not");
                if ((pattern || source.contains("''")) && loneApostrophe(text))
                    problems.add(locale + " " + key + ": undoubled apostrophe");
                if (!printf(source).equals(printf(text)))
                    problems.add(locale + " " + key + ": printf " + printf(text) + ", English " + printf(source));
                if (source.startsWith("@") != text.startsWith("@"))
                    problems.add(locale + " " + key + ": @key reference differs from the English");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyTranslationIsAModOnTopOfStarsectorMarinesInItsOwnLanguage() throws Exception {
        for (Path translation : translations()) {
            String locale = translation.getFileName().toString();
            assertEquals(locale, json(translation.resolve("data/config/settings.json")).getString("localeOverride"));
            JSONArray dependencies = json(translation.resolve("mod_info.json")).getJSONArray("dependencies");
            boolean onMarines = false;
            for (int i = 0; i < dependencies.length(); i++)
                onMarines |= "starsector_marines".equals(dependencies.getJSONObject(i).getString("id"));
            assertTrue(onMarines, locale + " must depend on starsector_marines so it loads after it");
        }
    }
}
