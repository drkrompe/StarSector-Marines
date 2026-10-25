package com.dillon.starsectormarines.i18n;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
     * The language the game is running in, read from the one place the base game keeps it:
     * {@code localeOverride} in {@code settings.json}, which a translation mod overrides along
     * with its strings. The copy's own {@code locale} entry is only a fallback for when the
     * setting cannot be read (headless runs). Case changes and number formats follow it,
     * because both are language-dependent (Turkish dotted i is the classic case).
     */
    public static Locale locale() {
        Locale cached = locale;
        if (cached == null) {
            cached = readLocale();
            locale = cached;
        }
        return cached;
    }

    private static Locale readLocale() {
        String tag = "";
        try {
            JSONObject settings = Global.getSettings().getSettingsJSON();
            if (settings != null) tag = settings.optString("localeOverride", "");
        } catch (Exception e) {
            // no settings outside the game; fall through to the copy
        }
        if (tag.isBlank()) {
            tag = get("locale");
            if (tag.equals("locale")) tag = "";
        }
        return tag.isBlank() ? Locale.ROOT : Locale.forLanguageTag(tag.replace('_', '-'));
    }

    /** Whether the game is set up for Chinese, Japanese or Korean text ({@code cjkMode}), which wraps by character. */
    public static boolean cjk() {
        try {
            return Global.getSettings().getBoolean("cjkMode");
        } catch (Exception e) {
            return false;
        }
    }

    private static volatile Locale locale;

    /** Forgets the cached language so the next lookup rereads the setting. */
    public static void refreshLocale() {
        locale = null;
    }

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

    /**
     * The copy's text for {@code key}, or the key itself when the copy has none.
     *
     * <p>On a miss the game logs an error and answers with its own {@code Missing string: [key]} placeholder rather
     * than the key, so that answer is read as a miss here, which keeps every caller's fallback working instead of
     * drawing the placeholder. A key the game has reported missing is not asked for again: the copy is loaded once,
     * and a HUD that draws a missing key every frame would otherwise log it every frame.
     */
    public static String get(String key) {
        if (MISSING.contains(key)) return key;
        try {
            String v = Global.getSettings().getString(NS, key);
            if (v == null) return key;
            if (v.equals(missingPlaceholder(key))) {
                MISSING.add(key);
                return key;
            }
            return v;
        } catch (Exception e) {
            LOG.warn("Strings: missing key '" + NS + "/" + key + "', falling back to key");
            return key;
        }
    }

    /** What the game's lookup answers for a key its copy does not hold. */
    static String missingPlaceholder(String key) {
        return "Missing string: [" + key + "]";
    }

    /** Keys the game reported its copy does not hold, this session. */
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
}
