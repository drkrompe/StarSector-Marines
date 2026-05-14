package com.dillon.starsectormarines.i18n;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

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
