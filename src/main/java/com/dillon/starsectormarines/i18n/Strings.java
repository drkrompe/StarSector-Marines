package com.dillon.starsectormarines.i18n;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import java.text.MessageFormat;
import java.util.Locale;

/**
 * Central lookup for Starsector Marines display strings. All user-facing
 * text in this mod goes through {@link #get(String)} — no hardcoded strings
 * in widgets, dialogs, or intel.
 *
 * <p>Translators copy {@code mod/data/strings/strings.json} into a separate
 * translation mod, change the values (not the keys), and load it after
 * Starsector Marines in the mod list — Starsector merges later JSONs on top,
 * so the override wins without touching this code.
 *
 * <p>Falls back to the raw key on miss so developers see exactly which
 * key is unmapped instead of a silent empty string.
 */
public final class Strings {

    private static final Logger LOG = Global.getLogger(Strings.class);

    /** All this mod's strings live under one namespace to avoid colliding with vanilla. */
    public static final String NS = "marineOps";

    private Strings() {}

    /** Complete translated message; arguments may be reordered by the translation. */
    public static String format(String key, Object... arguments) {
        return arguments.length == 0 ? get(key) : MessageFormat.format(get(key), arguments);
    }

    /** Catalog display fields may reference {@code @key}; addon-authored literal text stays valid. */
    public static String resolve(String text) {
        return text != null && text.startsWith("@") ? get(text.substring(1)) : text;
    }

    /**
     * The translation's own language, which the copy names under {@code locale}. Case changes
     * follow it, because uppercasing is language-dependent (Turkish dotted i is the classic case).
     */
    public static Locale locale() {
        Locale cached = locale;
        if (cached == null) {
            String tag = get("locale");
            cached = tag.equals("locale") || tag.isBlank() ? Locale.ROOT : Locale.forLanguageTag(tag);
            locale = cached;
        }
        return cached;
    }

    private static volatile Locale locale;

    /** Translated text set in capitals, by the rules of the translation's language. */
    public static String upper(String text) {
        return text == null ? "" : text.toUpperCase(locale());
    }

    /**
     * Parts joined by the separator the copy names under {@code separatorKey}, so a translation
     * chooses its own punctuation. Null and blank parts are left out.
     */
    public static String join(String separatorKey, String... parts) {
        String separator = get(separatorKey);
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (out.length() > 0) out.append(separator);
            out.append(part);
        }
        return out.toString();
    }

    /**
     * Display name of an enum constant or data id: the copy's {@code prefix + id} entry when it has
     * one, else the id read as words ({@code HOLD_NODE} becomes "Hold node"), so an id an addon
     * adds still shows something sensible until its translation catches up.
     */
    public static String named(String prefix, String id) {
        String key = prefix + id;
        String text = get(key);
        if (!text.equals(key)) return text;
        String spaced = id.replace('_', ' ').toLowerCase(locale());
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public static String get(String key) {
        try {
            String v = Global.getSettings().getString(NS, key);
            return v != null ? v : key;
        } catch (Exception e) {
            LOG.warn("Strings: missing key '" + NS + "/" + key + "', falling back to key");
            return key;
        }
    }
}
