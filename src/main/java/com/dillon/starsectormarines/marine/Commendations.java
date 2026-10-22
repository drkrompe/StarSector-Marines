package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * A captain's service record, saved in a form a translation can still read.
 *
 * <p>The record is a list of strings on {@link MarineCaptain}, so its shape in a save
 * cannot change. A new entry is saved as a marker naming the catalog pattern and its
 * arguments rather than as finished English, and is formatted in the reader's language
 * each time it is shown. Arguments that are themselves catalog text (a rank, a duty)
 * are saved as their keys for the same reason; names and places are saved as given.
 *
 * <p>Entries written before this existed are finished English prose. They cannot be
 * parsed back into a pattern reliably, so they are shown exactly as they were saved.
 */
public final class Commendations {

    private static final String MARKER = "@commendation:";
    private static final char SEPARATOR = '|';
    private static final char ESCAPE = '\\';

    private Commendations() {}

    /** A catalog key passed as an argument, resolved when the entry is read. */
    public record Ref(String key) {}

    public static Ref ref(String key) {
        return new Ref(key);
    }

    /** Appends a dated entry; {@code day} is the pattern's first argument. */
    public static void add(MarineCaptain captain, int day, String key, Object... arguments) {
        Object[] all = new Object[arguments.length + 1];
        all[0] = day;
        System.arraycopy(arguments, 0, all, 1, arguments.length);
        captain.commendations().add(entry(key, all));
    }

    /** The saved form of an entry: stable across languages and never translated text. */
    public static String entry(String key, Object... arguments) {
        StringBuilder out = new StringBuilder(MARKER).append(key);
        for (Object argument : arguments) {
            out.append(SEPARATOR);
            if (argument instanceof Ref ref) out.append('k').append(escape(ref.key()));
            else if (argument instanceof Integer || argument instanceof Long) out.append('i').append(argument);
            else out.append('s').append(escape(String.valueOf(argument)));
        }
        return out.toString();
    }

    /** What the reader sees: a marker formatted now, legacy prose exactly as saved. */
    public static String display(String saved) {
        if (saved == null || !saved.startsWith(MARKER)) return saved;
        List<String> fields = split(saved.substring(MARKER.length()));
        String key = fields.get(0);
        Object[] arguments = new Object[fields.size() - 1];
        for (int i = 1; i < fields.size(); i++) {
            String field = fields.get(i);
            String value = field.isEmpty() ? "" : field.substring(1);
            char type = field.isEmpty() ? 's' : field.charAt(0);
            arguments[i - 1] = switch (type) {
                case 'k' -> Strings.get(value);
                case 'i' -> parseNumber(value);
                default -> value;
            };
        }
        return Strings.format(key, arguments);
    }

    private static Object parseNumber(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == SEPARATOR || c == ESCAPE) out.append(ESCAPE);
            out.append(c);
        }
        return out.toString();
    }

    private static List<String> split(String body) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == ESCAPE && i + 1 < body.length()) {
                field.append(body.charAt(++i));
            } else if (c == SEPARATOR) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }
}
