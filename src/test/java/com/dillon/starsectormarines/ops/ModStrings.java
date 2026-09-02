package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The mod's own display copy, read off disk for headless evidence.
 *
 * <p>{@link Strings#get} goes through {@code Global.getSettings()}, which does not
 * exist outside the game and falls back to returning the key. A screenshot of a
 * panel labelled with its own string keys is evidence of nothing — neither the
 * copy nor the layout, since real sentences are what a column has to hold — so a
 * headless preview reads the shipped file instead and every screen that takes a
 * copy lookup is projected with the words the player will see.
 *
 * <p>The file carries {@code #} line comments, which Starsector's loader tolerates;
 * they are stripped here before parsing. Every {@code #} in it begins a comment
 * line, so this is a line filter rather than a parser.
 */
final class ModStrings {

    private ModStrings() {}

    /** The copy lookup, reading the working tree's own {@code mod/} folder. */
    static UnaryOperator<String> fromDisk() {
        return fromDisk(Path.of("mod"));
    }

    /** The copy lookup for a stated mod root. An unknown key reads as itself. */
    static UnaryOperator<String> fromDisk(Path modRoot) {
        Map<String, String> values = read(modRoot.resolve("data/strings/strings.json"));
        return key -> values.getOrDefault(key, key);
    }

    private static Map<String, String> read(Path file) {
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read " + file, failure);
        }
        StringBuilder stripped = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            stripped.append(line.stripLeading().startsWith("#") ? "" : line).append('\n');
        }
        Map<String, String> values = new LinkedHashMap<>();
        try {
            JSONObject namespace =
                    new JSONObject(stripped.toString()).getJSONObject(Strings.NS);
            for (Iterator<?> keys = namespace.keys(); keys.hasNext(); ) {
                String key = String.valueOf(keys.next());
                values.put(key, namespace.getString(key));
            }
        } catch (JSONException failure) {
            throw new IllegalStateException("Could not parse " + file, failure);
        }
        return Map.copyOf(values);
    }
}
