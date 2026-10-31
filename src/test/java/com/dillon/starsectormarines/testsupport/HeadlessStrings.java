package com.dillon.starsectormarines.testsupport;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.LayoutLint;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.function.UnaryOperator;

/** Loads the shipped localization catalog at the same SettingsAPI seam used in game. */
public final class HeadlessStrings implements AutoCloseable {
    /**
     * {@code -Dsnapshot.translation=<locale>}: suites that render translated copy also render the
     * translation mod under {@code translations/<locale>/} ({@link #translation}).
     */
    public static final String TRANSLATION_PROPERTY = "snapshot.translation";

    /** The translation mod whose files resolve ahead of the mod's and the game's, or null. */
    private static volatile Path overlayRoot;

    private final SettingsAPI previous = Global.getSettings();
    /** The layout lint's label for pages drawn under this overlay; null for the shipped strings. */
    private final String lintLabel;
    private final Path previousOverlay = overlayRoot;
    public HeadlessStrings(Path modRoot) throws Exception {
        this(modRoot, UnaryOperator.identity(), null, null);
    }
    /** Translation overlays exercise the same lookup without rewriting production assets. */
    public HeadlessStrings(Path modRoot, UnaryOperator<String> translation) throws Exception {
        this(modRoot, translation, "translated", null);
    }
    /**
     * The translation mod {@code translations/<locale>/} beside {@code modRoot}, as the game loads
     * it after the mod: its strings merged over the English catalog, its {@code settings.json}
     * (locale, {@code cjkMode}) and its files, fonts included, found before the mod's and the
     * game's own until {@link #close}.
     */
    public static HeadlessStrings translation(Path modRoot, String locale) throws Exception {
        Path root = modRoot.toAbsolutePath().getParent().resolve("translations").resolve(locale);
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("No translation mod at " + root);
        JSONObject settings = readJson(root.resolve("data/config/settings.json"));
        if (settings != null && settings.optBoolean("cjkMode")) {
            try (var fonts = Files.list(root.resolve("graphics/fonts"))) {
                if (fonts.noneMatch(path -> path.toString().endsWith(".fnt"))) throw new IOException();
            } catch (IOException missing) {
                throw new IllegalStateException(locale + " has no CJK fonts: run gradlew :asset-pipeline:generateCjkFonts");
            }
        }
        return new HeadlessStrings(modRoot, UnaryOperator.identity(), locale, root);
    }
    /** The translation mod a {@link #translation} overlay has open, or null. */
    public static Path overlayRoot() {
        return overlayRoot;
    }
    private HeadlessStrings(Path modRoot, UnaryOperator<String> translation, String lintLabel, Path overlay)
            throws Exception {
        // The layout lint counts pages drawn under an overlay apart from the shipped strings.
        this.lintLabel = lintLabel;
        if (lintLabel != null) LayoutLint.translated(lintLabel, true);
        JSONObject catalog = readJson(modRoot.resolve("data/strings/strings.json"));
        JSONObject settings = overlay == null ? null : readJson(overlay.resolve("data/config/settings.json"));
        if (overlay != null) {
            JSONObject overrides = readJson(overlay.resolve("data/strings/strings.json"));
            for (Iterator<?> categories = overrides.keys(); categories.hasNext(); ) {
                String category = (String) categories.next();
                JSONObject into = catalog.optJSONObject(category);
                if (into == null) catalog.put(category, into = new JSONObject());
                JSONObject from = overrides.getJSONObject(category);
                for (Iterator<?> keys = from.keys(); keys.hasNext(); ) {
                    String key = (String) keys.next();
                    into.put(key, from.get(key));
                }
            }
        }
        Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getString") && args.length == 2)
                        return translation.apply(catalog.getJSONObject((String) args[0]).getString((String) args[1]));
                    if (method.getName().equals("loadText")) {
                        Path own = overlay == null ? null : overlay.resolve((String) args[0]);
                        return Files.readString(own != null && Files.isRegularFile(own) ? own : modRoot.resolve((String) args[0]));
                    }
                    if (settings != null && method.getName().equals("getSettingsJSON")) return settings;
                    if (settings != null && method.getName().equals("getBoolean") && settings.has((String) args[0]))
                        return settings.getBoolean((String) args[0]);
                    if (previous != null) return method.invoke(previous, args);
                    if (method.getReturnType() == float.class) return 1f;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
        if (overlay != null) {
            overlayRoot = overlay;
            Strings.refreshLocale();
        }
    }
    private static JSONObject readJson(Path file) throws IOException, JSONException {
        if (!Files.isRegularFile(file)) return null;
        // Starsector's JSON allows # comment lines.
        return new JSONObject(Files.readString(file).lines().filter(line -> !line.stripLeading().startsWith("#"))
                .collect(Collectors.joining("\n")));
    }
    @Override public void close() {
        Global.setSettings(previous);
        if (overlayRoot != previousOverlay) {
            overlayRoot = previousOverlay;
            Strings.refreshLocale();
        }
        if (lintLabel != null) LayoutLint.translated(lintLabel, false);
    }
}
