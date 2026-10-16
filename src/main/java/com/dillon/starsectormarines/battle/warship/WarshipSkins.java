package com.dillon.starsectormarines.battle.warship;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla hull skins: a Wolf (H) is the Wolf's spec, table row and engines under a different sprite.
 *
 * <p>A skin lives at {@code data/hulls/skins/<id>.skin} and names its {@code baseHullId} and its own
 * {@code spriteName}. A hull id with its own {@code .ship} is not a skin and resolves to itself. Resolutions are
 * kept, since a hull's identity does not change during a run.
 */
public final class WarshipSkins {

    /** What a hull id fits as: the base hull whose figures it uses, and its own sprite, or {@code null} to keep the base's. */
    public record Skin(String baseHullId, String spriteName) {}

    private static final Map<String, Skin> CACHE = new ConcurrentHashMap<>();

    private WarshipSkins() {}

    /** The hull {@code hullId} is fitted from; itself with no sprite override when it is not a skin. */
    public static Skin resolve(String hullId) {
        return CACHE.computeIfAbsent(hullId, WarshipSkins::read);
    }

    /** Forgets every resolution, for a test that swaps the data source. */
    static void clear() {
        CACHE.clear();
    }

    private static Skin read(String hullId) {
        if (load("data/hulls/" + hullId + ".ship") != null) return new Skin(hullId, null);
        JSONObject skin = load("data/hulls/skins/" + hullId + ".skin");
        if (skin == null) return new Skin(hullId, null);
        String base = skin.optString("baseHullId", "");
        if (base.isEmpty()) return new Skin(hullId, null);
        String sprite = skin.optString("spriteName", "");
        return new Skin(base, sprite.isEmpty() ? null : sprite);
    }

    private static JSONObject load(String path) {
        try {
            return VanillaData.json(path);
        } catch (Exception e) {
            return null;
        }
    }
}
