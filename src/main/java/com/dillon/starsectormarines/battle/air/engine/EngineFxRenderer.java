package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.combat.fx.WeaponLights;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.LightAccumulator;
import com.dillon.starsectormarines.render2d.LightKernel;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

/**
 * Shared engine-FX draw pipeline for every air entity that wants thruster
 * plumes — shuttles and fighters today, room for anything else flying.
 *
 * <p>The caller passes its own world-frame state (position, facing, throttle)
 * and the entity-type's already-resolved engine slots. This stays a pure
 * draw helper — slot resolution and sprite caching live with the caller
 * (resolution: {@link EngineSlotResolver}; sprite cache: per-screen).
 *
 * <p>Per slot: rotate local offset by entity facing (with {@code scaleMult}
 * applied to keep engines glued to their hardpoints during landing-style
 * scale lerps), translate by entity position + {@code altOffsetCells},
 * compute plume world direction as {@code facing + slot.angle}, draw an
 * additive-blended glow halo + flame column tinted by
 * {@link EngineStylePalette}. Length and alpha breathe with
 * {@code intensity} so idle engines read short / dim and full throttle
 * reads long / bright.
 *
 * <p>The caller's {@code facingDegrees} MUST be in this mod's sprite-angle
 * convention ({@code 0° = +Y, CCW-positive}). Shuttles already use that
 * frame directly; fighters convert from their vanilla {@code 0° = +X}
 * frame at the call site (subtract 90).
 */
public final class EngineFxRenderer {

    /** Length scales between {@link #LEN_INTENSITY_FLOOR}× at intensity 0 and 1.0× at intensity 1. */
    private static final float LEN_INTENSITY_FLOOR = 0.4f;
    /** Width scales between {@link #WIDTH_INTENSITY_FLOOR}× at intensity 0 and 1.0× at intensity 1. */
    private static final float WIDTH_INTENSITY_FLOOR = 0.7f;
    /** Per-frame alpha multiplier — flame alpha = alphaMult × (FLOOR + (1 - FLOOR) × intensity). */
    private static final float ALPHA_INTENSITY_FLOOR = 0.5f;
    /** Glow halo is {@link #GLOW_WIDTH_MULT}× the flame width — slightly bigger than the column so it reads as a bloom around the nozzle. */
    private static final float GLOW_WIDTH_MULT = 2.0f;
    /** Minimum glow display size — guards against degenerate glows on tiny / parked entities. */
    private static final float GLOW_MIN_CELL_FRACTION = 0.4f;

    /**
     * Per-slot floor when a {@code perSlotDemand} array is supplied: an idle
     * thruster (demand 0) still draws at this fraction of the master intensity,
     * so a lit engine never blinks fully dark. Matches the old uniform
     * {@code Shuttle.HOVER_FX_FLOOR} so a stationary hover reads the same.
     */
    private static final float DEMAND_FLOOR = 0.45f;

    private EngineFxRenderer() {}

    /** Uniform overload — every slot glows at {@code intensity} (legacy / fixed-throttle callers). */
    public static void draw(
            EngineSlotData[] slots,
            float worldX, float worldY,
            float facingDegrees,
            float scaleMult,
            float altOffsetCells,
            float intensity,
            float alphaMult,
            BattleCamera camera,
            SpriteAPI glowSprite, SpriteAPI flameSprite) {
        draw(slots, worldX, worldY, facingDegrees, scaleMult, altOffsetCells,
                intensity, alphaMult, camera, glowSprite, flameSprite, null);
    }

    /**
     * Draws every engine plume for one air entity. No-ops gracefully when
     * the slot list is empty, both sprites are null, or intensity is zero.
     *
     * <p>{@code perSlotDemand} (aligned with {@code slots}, from
     * {@link ThrusterDemand}) weights each plume by how hard that thruster is
     * working — aft mains bloom on acceleration, the maneuvering side flares in
     * a turn. {@code null} keeps the old uniform behaviour (every slot at
     * {@code intensity}).
     */
    public static void draw(
            EngineSlotData[] slots,
            float worldX, float worldY,
            float facingDegrees,
            float scaleMult,
            float altOffsetCells,
            float intensity,
            float alphaMult,
            BattleCamera camera,
            SpriteAPI glowSprite, SpriteAPI flameSprite,
            float[] perSlotDemand) {

        if (slots == null || slots.length == 0) return;
        if (glowSprite == null && flameSprite == null) return;
        if (intensity <= 0f) return;

        float rad = (float) Math.toRadians(facingDegrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float cellPx = camera.cellPxSize();

        for (int si = 0; si < slots.length; si++) {
            EngineSlotData es = slots[si];
            // Each thruster's intensity is the master throttle scaled by how
            // much that nozzle is contributing this tick (1.0 when uniform).
            float intensityS = intensity * slotFactor(perSlotDemand, si);
            // Slot world position — scale-then-rotate so engines stay locked
            // to their hardpoints through any scaling the caller applies.
            float lx = es.localX * scaleMult;
            float ly = es.localY * scaleMult;
            float worldOffsetX = lx * cos - ly * sin;
            float worldOffsetY = lx * sin + ly * cos;
            float wx = worldX + worldOffsetX;
            float wy = worldY + worldOffsetY + altOffsetCells;
            float screenX = camera.cellToScreenX(wx);
            float screenY = camera.cellToScreenY(wy);

            // Plume world direction = entity facing + slot angle. Both
            // follow 0°=+Y CCW-positive (parser converted vanilla slot
            // angles into our frame), so they sum directly.
            float plumeDir = facingDegrees + es.angleDegrees;
            double plumeRad = Math.toRadians(plumeDir);
            float plumeDX = (float) -Math.sin(plumeRad);
            float plumeDY = (float)  Math.cos(plumeRad);

            float lenCells   = es.lengthCells * scaleMult
                    * (LEN_INTENSITY_FLOOR   + (1f - LEN_INTENSITY_FLOOR)   * intensityS);
            float widthCells = es.widthCells  * scaleMult
                    * (WIDTH_INTENSITY_FLOOR + (1f - WIDTH_INTENSITY_FLOOR) * intensityS);
            float lenPx = lenCells * cellPx;
            float widthPx = widthCells * cellPx;
            float flameAlpha = alphaMult
                    * (ALPHA_INTENSITY_FLOOR + (1f - ALPHA_INTENSITY_FLOOR) * intensityS);

            Color flame = EngineStylePalette.flameColor(es.style);

            if (glowSprite != null) {
                float glowSize = Math.max(widthPx * GLOW_WIDTH_MULT, cellPx * GLOW_MIN_CELL_FRACTION);
                glowSprite.setSize(glowSize, glowSize);
                glowSprite.setAngle(0f);
                glowSprite.setAlphaMult(flameAlpha);
                glowSprite.setAdditiveBlend();
                glowSprite.setColor(flame);
                glowSprite.renderAtCenter(screenX, screenY);
            }

            if (flameSprite != null) {
                // Sprite is centered, anchored with its bright base at the
                // slot — offset center half a length along the plume.
                float flameCenterX = screenX + plumeDX * lenPx * 0.5f;
                float flameCenterY = screenY + plumeDY * lenPx * 0.5f;
                flameSprite.setSize(widthPx, lenPx);
                flameSprite.setAngle(plumeDir);
                flameSprite.setAlphaMult(flameAlpha);
                flameSprite.setAdditiveBlend();
                flameSprite.setColor(flame);
                flameSprite.renderAtCenter(flameCenterX, flameCenterY);
            }
        }

        // Restore singletons so a later pass that forgets to set angle/color
        // doesn't inherit our tint or rotation.
        if (flameSprite != null) {
            flameSprite.setAngle(0f);
            flameSprite.setColor(Color.WHITE);
        }
        if (glowSprite != null) {
            glowSprite.setColor(Color.WHITE);
        }
    }

    /**
     * Mirror of {@link #draw} that emits a persistent {@link LightKernel#ENGINE_GLOW}
     * light per engine slot. Same world-frame transform as the FX draw so
     * the light tracks the visual engine — including the cruise altitude
     * lift, passed in as {@code altOffsetCells} so a hovering Valkyrie's
     * halo follows the lifted sprite rather than sitting on the ground
     * projection.
     *
     * <p>Tuning constants ({@link WeaponLights#ENGINE_RGB_SCALE},
     * {@link WeaponLights#ENGINE_INTENSITY_FLOOR}, etc.) live in
     * {@code WeaponLights} so all gun + engine lighting is tuned at one
     * file.
     *
     * <p>Caller is responsible for adding the produced ids to its
     * {@code retainPersistent} keep-set so engines that disappear (shuttle
     * leaves, fighter despawns) drop their halos.
     */
    public static void emitLights(
            EngineSlotData[] slots,
            float worldX, float worldY,
            float facingDegrees,
            float scaleMult,
            float altOffsetCells,
            float intensity,
            LightAccumulator lights,
            long baseId,
            java.util.Set<Long> seenIds) {
        emitLights(slots, worldX, worldY, facingDegrees, scaleMult, altOffsetCells,
                intensity, lights, baseId, seenIds, null);
    }

    /**
     * Per-slot overload of {@link #emitLights} — {@code perSlotDemand} (from
     * {@link ThrusterDemand}) scales each halo's brightness and radius the same
     * way it scales the plume in {@link #draw}, so a working thruster's light
     * tracks its bloom. {@code null} keeps the uniform behaviour.
     */
    public static void emitLights(
            EngineSlotData[] slots,
            float worldX, float worldY,
            float facingDegrees,
            float scaleMult,
            float altOffsetCells,
            float intensity,
            LightAccumulator lights,
            long baseId,
            java.util.Set<Long> seenIds,
            float[] perSlotDemand) {

        if (slots == null || slots.length == 0 || lights == null) return;
        if (intensity <= 0f) return;

        float rad = (float) Math.toRadians(facingDegrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        for (int i = 0; i < slots.length; i++) {
            EngineSlotData es = slots[i];
            float intensityS = intensity * slotFactor(perSlotDemand, i);
            float lx = es.localX * scaleMult;
            float ly = es.localY * scaleMult;
            float wx = worldX + lx * cos - ly * sin;
            float wy = worldY + lx * sin + ly * cos + altOffsetCells;

            Color flame = EngineStylePalette.flameColor(es.style);
            float r = flame.getRed()   / 255f * WeaponLights.ENGINE_RGB_SCALE;
            float g = flame.getGreen() / 255f * WeaponLights.ENGINE_RGB_SCALE;
            float b = flame.getBlue()  / 255f * WeaponLights.ENGINE_RGB_SCALE;

            float intensityCurve = WeaponLights.ENGINE_INTENSITY_FLOOR
                    + (WeaponLights.ENGINE_INTENSITY_CEIL - WeaponLights.ENGINE_INTENSITY_FLOOR)
                      * Math.max(0f, Math.min(1f, intensityS));

            // Halo radius scales with slot width and throttle; min floor so
            // idle engines still show a small glow.
            float radius = Math.max(0.6f,
                    es.widthCells * scaleMult * 1.8f * (0.6f + 0.4f * intensityS));

            long id = baseId ^ ((long) (i + 1) * 0x9E3779B97F4A7C15L);
            lights.putPersistent(id, wx, wy, radius, LightKernel.ENGINE_GLOW,
                    r, g, b, intensityCurve);
            if (seenIds != null) seenIds.add(id);
        }
    }

    /**
     * The effective per-slot intensity multiplier: {@code 1} when no demand
     * array is supplied (uniform), else {@link #DEMAND_FLOOR} lerped to 1 by the
     * clamped demand for slot {@code i}.
     */
    private static float slotFactor(float[] perSlotDemand, int i) {
        if (perSlotDemand == null || i >= perSlotDemand.length) return 1f;
        float d = perSlotDemand[i];
        if (d < 0f) d = 0f;
        else if (d > 1f) d = 1f;
        return DEMAND_FLOOR + (1f - DEMAND_FLOOR) * d;
    }
}
