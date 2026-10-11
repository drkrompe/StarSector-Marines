package com.dillon.starsectormarines.battle.warship;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;

/**
 * What a vanilla beam weapon looks like: the colours, width and textures the
 * game itself draws it with, read from the weapon's own spec.
 *
 * <p>A player who has flown a Paragon knows these beams — the Tachyon Lance's
 * violet and white, the Graviton Beam's cyan — and recognises them before they
 * read a number. Nothing here is authored by this mod; it is the game's data
 * carried across, so a re-balanced or modded beam arrives looking like itself.
 *
 * <p>Widths are the game's pixels at its own scale. Converting them to cells is
 * the renderer's business, because it is a decision about how big a capital is
 * on a ground map and not a fact about the weapon.
 */
public record VanillaBeamLook(
        String weaponId,
        Color fringe,
        Color core,
        Color glow,
        float widthPx,
        String fringeTexture,
        String coreTexture,
        float scrollSpeed,
        float pixelsPerTexel,
        String turretSprite,
        String turretGlowSprite,
        String hardpointSprite,
        String hardpointGlowSprite) {

    /**
     * The named texture families a spec may ask for. The game resolves these
     * names to a pair of fx textures; the pairs here are read off the install's
     * own file names and the weapons that declare them.
     */
    private static final String[][] FAMILIES = {
            {"ROUGH", "graphics/fx/beam_rough2_fringe.png", "graphics/fx/beam_rough2_core.png"},
            {"CHUNKY", "graphics/fx/beam_chunky_fringe.png", "graphics/fx/beam_chunky_core.png"},
            {"LASER", "graphics/fx/beam_laser_fringe.png", "graphics/fx/beam_laser_core.png"},
            {"SMOOTH", "graphics/fx/beamfringe.png", "graphics/fx/beamcore.png"},
    };

    /**
     * Reads a look from a parsed {@code .wpn} spec, or {@code null} when the
     * weapon is not a beam — a projectile has no beam to look like.
     */
    public static VanillaBeamLook fromSpec(String weaponId, JSONObject spec) {
        if (spec == null || !"beam".equals(spec.optString("specClass"))) return null;
        String[] textures = textures(spec.opt("textureType"));
        return new VanillaBeamLook(weaponId,
                color(spec, "fringeColor", new Color(255, 255, 255)),
                color(spec, "coreColor", Color.WHITE),
                color(spec, "glowColor", new Color(255, 255, 255)),
                (float) spec.optDouble("width", 10.0),
                textures[0], textures[1],
                (float) spec.optDouble("textureScrollSpeed", 0.0),
                (float) spec.optDouble("pixelsPerTexel", 5.0),
                spec.optString("turretSprite", null),
                spec.optString("turretGlowSprite", null),
                spec.optString("hardpointSprite", null),
                spec.optString("hardpointGlowSprite", null));
    }

    private static Color color(JSONObject spec, String key, Color fallback) {
        JSONArray a = spec.optJSONArray(key);
        if (a == null || a.length() < 3) return fallback;
        int alpha = a.length() > 3 ? a.optInt(3, 255) : 255;
        return new Color(a.optInt(0), a.optInt(1), a.optInt(2), alpha);
    }

    /** {@code [fringe, core]} for a named family, an explicit pair, or the default family. */
    private static String[] textures(Object textureType) {
        if (textureType instanceof JSONArray pair && pair.length() >= 2) {
            return new String[]{pair.optString(0), pair.optString(1)};
        }
        String name = textureType == null ? "ROUGH" : textureType.toString();
        for (String[] family : FAMILIES) {
            if (family[0].equalsIgnoreCase(name)) return new String[]{family[1], family[2]};
        }
        return new String[]{FAMILIES[3][1], FAMILIES[3][2]};
    }
}
