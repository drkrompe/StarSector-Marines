package com.dillon.starsectormarines.assets.pipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-source unit conventions used when normalizing FBX/BVH inputs to the engine's canonical
 * convention (1 unit = 1 meter, Y-up). The asset-pipeline reads this once and consults it for
 * every model file; {@link ConventionNormalizer} applies the resulting scale factor to mesh
 * vertices, bone matrices, and animation position keys.
 *
 * <p>Each entry's {@code unit} is the size of one source unit expressed in meters: {@code cm}
 * means 0.01 (multiply cm values by 0.01 to get meters), {@code m} means 1.0.
 *
 * <p>Lookup order for a given resource path: first override whose glob pattern matches wins,
 * else the {@code default} entry.
 *
 * <p>JSON loading was lifted out of the MoonLight port — it depended on libGDX. {@link #load()}
 * currently returns a defaulted config (cm → m for everything). If we ever need per-path
 * overrides, wire in a JSON parser (e.g. org.json or Jackson) here.
 */
public record AssetConventionConfig(double defaultUnitMeters, List<PathOverride> overrides) {

    public record PathOverride(String pattern, double unitMeters) {}

    /** Returns the meters-per-source-unit factor to apply to a given resource path. */
    public double unitMetersFor(String classpathPath) {
        String normalized = classpathPath.replace('\\', '/');
        for (PathOverride o : overrides) {
            if (matchesGlob(normalized, o.pattern())) {
                return o.unitMeters();
            }
        }
        return defaultUnitMeters;
    }

    /** Convenience: returns the scale to multiply source positions by to get canonical (meter) values. */
    public double scaleFactorFor(String classpathPath) {
        return unitMetersFor(classpathPath);
    }

    /**
     * Returns a default config (cm → m for everything, no overrides). When per-path overrides
     * become necessary, replace this with a real JSON loader.
     */
    public static AssetConventionConfig load() {
        return new AssetConventionConfig(0.01, new ArrayList<>());
    }

    /**
     * Minimal glob matcher: supports {@code **} (any path including separators) and {@code *}
     * (any segment except separator). Sufficient for path patterns like {@code "models/trellis/**"}.
     */
    static boolean matchesGlob(String path, String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if ("\\.()+|^$?{}[]".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return path.matches(regex.toString());
    }
}
