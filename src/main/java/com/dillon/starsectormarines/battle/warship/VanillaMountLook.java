package com.dillon.starsectormarines.battle.warship;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * How a vanilla weapon is drawn on its mount: every sprite layer its {@code .wpn} names, in the order the game
 * stacks them, the barrel offsets its rounds leave from, and how far its barrel kicks back when it fires.
 *
 * <p>A weapon is not one picture. An Arbalest is a base and a barrel ({@code turretSprite} and
 * {@code turretGunSprite}); the barrel is the part that recoils, and {@code RENDER_BARREL_BELOW} slides it under the
 * base rather than over it. A Mjolnir adds an under-plate ({@code turretUnderSprite}); a blaster a glow
 * ({@code turretGlowSprite}) lit in its {@code glowColor} as it fires; an open missile rack shows the missiles it
 * holds ({@code RENDER_LOADED_MISSILES}). A hardpoint draws its own {@code hardpoint*} set instead. Drawing the base
 * alone draws an empty turret ring with no gun in it.
 *
 * <p>Offsets are the game's pixels in the weapon's own frame: {@code forward} along the barrel, {@code left} across
 * it. A turret sprite is centred on its mount; a hardpoint sprite is centred a quarter of its height forward of it,
 * which is where the game pivots a hardpoint. Converting pixels to cells is the renderer's business.
 *
 * <p>Nothing here is authored by the mod: it is the weapon's own spec, so a modded weapon arrives drawn as itself.
 */
public record VanillaMountLook(
        String weaponId,
        Sprites turret,
        Sprites hardpoint,
        Color glowColor,
        float visualRecoilPx,
        boolean barrelBelow,
        boolean additive,
        boolean loadedMissiles,
        boolean linked,
        String animationType,
        List<Barrel> turretBarrels,
        List<Barrel> hardpointBarrels,
        String missileSprite,
        float missileWidthPx,
        float missileHeightPx,
        float missileCentreForwardPx,
        int numFrames,
        float frameRate) {

    /** One mount type's sprites; any may be {@code null}. */
    public record Sprites(String under, String base, String gun, String glow) {}

    /** Where one barrel's round leaves, in the weapon's frame, and the angle it leaves at. */
    public record Barrel(float forwardPx, float leftPx, float angleDegrees) {}

    /** The layers of a mounted weapon, bottom to top as the game draws them. */
    public enum Kind { UNDER, BARREL, BASE, LOADED_MISSILES, GLOW }

    /** One drawn layer: what it is and the sprite it draws ({@code null} for loaded missiles, which use {@link #missileSprite}). */
    public record Layer(Kind kind, String sprite) {}

    /** Default glow colour when a spec names a glow sprite but no colour. */
    private static final Color DEFAULT_GLOW = new Color(255, 255, 255, 255);

    /** Reads a look from a parsed {@code .wpn} spec and its projectile spec (which may be {@code null}); {@code null} for no spec. */
    public static VanillaMountLook fromSpec(String weaponId, JSONObject wpn, JSONObject proj) {
        if (wpn == null) return null;
        String hints = hints(wpn.opt("renderHints"));
        float[] size = pair(proj == null ? null : proj.optJSONArray("size"));
        float[] centre = pair(proj == null ? null : proj.optJSONArray("center"));
        String missile = proj == null ? null : nonBlank(proj.optString("sprite", null));
        float mw = size != null ? size[0] : 0f;
        float mh = size != null ? size[1] : 0f;
        // The spawn point is the missile's own centre; the sprite's middle sits this far ahead of it.
        float ahead = size != null && centre != null ? size[1] * 0.5f - centre[1] : 0f;
        return new VanillaMountLook(weaponId,
                new Sprites(nonBlank(wpn.optString("turretUnderSprite", null)),
                        nonBlank(wpn.optString("turretSprite", null)),
                        nonBlank(wpn.optString("turretGunSprite", null)),
                        nonBlank(wpn.optString("turretGlowSprite", null))),
                new Sprites(nonBlank(wpn.optString("hardpointUnderSprite", null)),
                        nonBlank(wpn.optString("hardpointSprite", null)),
                        nonBlank(wpn.optString("hardpointGunSprite", null)),
                        nonBlank(wpn.optString("hardpointGlowSprite", null))),
                VanillaWeapon.color(wpn, "glowColor") != null ? VanillaWeapon.color(wpn, "glowColor") : DEFAULT_GLOW,
                (float) wpn.optDouble("visualRecoil", 0.0),
                hints.contains("RENDER_BARREL_BELOW"),
                hints.contains("RENDER_ADDITIVE"),
                hints.contains("RENDER_LOADED_MISSILES"),
                "LINKED".equals(wpn.optString("barrelMode", "ALTERNATING")),
                wpn.optString("animationType", "NONE"),
                barrels(wpn.optJSONArray("turretOffsets"), wpn.optJSONArray("turretAngleOffsets")),
                barrels(wpn.optJSONArray("hardpointOffsets"), wpn.optJSONArray("hardpointAngleOffsets")),
                missile, mw, mh, ahead,
                Math.max(1, wpn.optInt("numFrames", 1)),
                (float) wpn.optDouble("frameRate", 0.0));
    }

    /** The sprites for a hardpoint or a turret. */
    public Sprites sprites(boolean hardpoint) {
        return hardpoint ? hardpoint() : turret();
    }

    /** The barrels for a hardpoint or a turret; never empty (a weapon naming none fires from its pivot). */
    public List<Barrel> barrels(boolean hardpoint) {
        List<Barrel> b = hardpoint ? hardpointBarrels : turretBarrels;
        return b.isEmpty() ? List.of(new Barrel(0f, 0f, 0f)) : b;
    }

    /**
     * Every layer this weapon draws on a mount of that kind, bottom to top: the under-plate, then the barrel and
     * base (barrel first only under {@code RENDER_BARREL_BELOW}), then any loaded missiles, then the glow. The barrel
     * is drawn only when the spec gives it a recoil, as the game does ("the gun sprites are only used if this is
     * non-0"); a layer whose sprite the spec does not name is left out.
     */
    public List<Layer> layers(boolean hardpoint) {
        Sprites s = sprites(hardpoint);
        List<Layer> out = new ArrayList<>(5);
        if (s.under() != null) out.add(new Layer(Kind.UNDER, s.under()));
        boolean gun = s.gun() != null && visualRecoilPx != 0f;
        if (gun && barrelBelow) out.add(new Layer(Kind.BARREL, s.gun()));
        if (s.base() != null) out.add(new Layer(Kind.BASE, s.base()));
        if (gun && !barrelBelow) out.add(new Layer(Kind.BARREL, s.gun()));
        if (loadedMissiles && missileSprite != null) out.add(new Layer(Kind.LOADED_MISSILES, null));
        if (s.glow() != null) out.add(new Layer(Kind.GLOW, s.glow()));
        return out;
    }

    /**
     * How far back the barrel sits, in the game's pixels, {@code secondsSinceFired} after a shot from a weapon that
     * cycles every {@code cooldownSeconds}: the whole {@code visualRecoil} at the instant of firing, run back out over
     * most of the cycle (never slower than half a second, so a slow gun's barrel is home well before it fires
     * again). A negative recoil pushes the barrel forward, as the spec asks.
     */
    public float recoilPx(float secondsSinceFired, float cooldownSeconds) {
        if (visualRecoilPx == 0f || secondsSinceFired < 0f) return 0f;
        float back = Math.max(0.08f, Math.min(0.5f, cooldownSeconds * 0.9f));
        float share = 1f - secondsSinceFired / back;
        return share <= 0f ? 0f : visualRecoilPx * share;
    }

    /** How bright the glow layer is, 0-1, {@code secondsSinceFired} after a shot: lit on firing, fading over the cycle. */
    public float glowLevel(float secondsSinceFired, float cooldownSeconds) {
        if (secondsSinceFired < 0f) return 0f;
        float fade = Math.max(0.2f, Math.min(1.2f, cooldownSeconds));
        return Math.max(0f, 1f - secondsSinceFired / fade);
    }

    /**
     * Which barrels just fired, after {@code shotsFired} rounds of which the last cycle was {@code burst}: every
     * barrel for a linked weapon, otherwise the last {@code burst} barrels in turn.
     */
    public boolean[] firedBarrels(boolean hardpoint, int shotsFired, int burst) {
        int n = barrels(hardpoint).size();
        boolean[] fired = new boolean[n];
        if (shotsFired <= 0) return fired;
        if (linked) {
            Arrays.fill(fired, true);
            return fired;
        }
        int k = Math.min(n, Math.max(1, Math.min(burst, shotsFired)));
        for (int i = 0; i < k; i++) fired[Math.floorMod(shotsFired - 1 - i, n)] = true;
        return fired;
    }

    /**
     * Which frame of an animated weapon ({@code numFrames} sprites numbered {@code 00}, {@code 01}, ...: a rotary
     * cannon's spinning barrels, an ion pulser's charge) shows at {@code clockSeconds}: turning at the spec's
     * {@code frameRate} while the weapon is firing or has just fired, and at rest on the first frame.
     */
    public int frameAt(float clockSeconds, float secondsSinceFired, float cooldownSeconds) {
        if (numFrames <= 1 || frameRate <= 0f) return 0;
        if (secondsSinceFired < 0f || secondsSinceFired > Math.max(0.3f, cooldownSeconds * 2f)) return 0;
        return Math.floorMod((int) Math.floor(clockSeconds * frameRate), numFrames);
    }

    /**
     * The sprite of frame {@code frame} for a layer named by its first frame ({@code ..._base00.png}); a sprite not
     * numbered that way, or a weapon that is not animated, is itself.
     */
    public String frameSprite(String path, int frame) {
        if (path == null || numFrames <= 1 || frame <= 0) return path;
        int dot = path.lastIndexOf('.');
        if (dot < 2 || !path.startsWith("00", dot - 2)) return path;
        return path.substring(0, dot - 2) + String.format(Locale.ROOT, "%02d", frame % numFrames) + path.substring(dot);
    }

    private static List<Barrel> barrels(JSONArray offsets, JSONArray angles) {
        List<Barrel> out = new ArrayList<>();
        if (offsets == null) return out;
        for (int i = 0; i + 1 < offsets.length(); i += 2) {
            int b = i / 2;
            float angle = angles == null || angles.length() == 0 ? 0f
                    : (float) angles.optDouble(Math.min(b, angles.length() - 1), 0.0);
            out.add(new Barrel((float) offsets.optDouble(i, 0.0), (float) offsets.optDouble(i + 1, 0.0), angle));
        }
        return out;
    }

    private static String hints(Object raw) {
        if (raw instanceof JSONArray a) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.length(); i++) sb.append(a.optString(i)).append(' ');
            return sb.toString();
        }
        return raw == null ? "" : raw.toString();
    }

    private static float[] pair(JSONArray a) {
        if (a == null || a.length() < 2) return null;
        return new float[]{(float) a.optDouble(0, 0.0), (float) a.optDouble(1, 0.0)};
    }

    private static String nonBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
