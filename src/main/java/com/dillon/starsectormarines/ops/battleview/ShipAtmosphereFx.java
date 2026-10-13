package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipAtmosphere;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * What a capital does to the air and the ground while it drops through the cloud
 * deck and while it climbs back into it: the cloud streaming off the hull, the
 * re-entry heat on it, the dust its downwash kicks and the ring its arrival
 * sends across the ground.
 *
 * <p>Every figure is a function of the ship's identity and its two clocks
 * ({@link Warship#entrySeconds()}, {@link Warship#departingSeconds()}), read
 * through {@link WarshipAtmosphere}. Nothing is stored between frames and nothing
 * is random, so the same tick draws the same picture and a scene recorded twice
 * is the same recording. A ship that neither entered from above nor is leaving
 * draws nothing here.
 *
 * <p>All of it is the game's own soft particle and shield-ring art; the mod
 * authors no sprite for it.
 */
final class ShipAtmosphereFx {

    private static final String SOFT = "graphics/fx/particlealpha64linear.png";
    private static final String RING = "graphics/fx/shields256ring.png";

    private static final int WISPS = 18;
    private static final int WASH_PUFFS = 16;
    private static final int RING_PUFFS = 18;

    private final BattleSprites sprites;

    ShipAtmosphereFx(BattleSprites sprites) {
        this.sprites = sprites;
    }

    /** Whether the ship has any atmosphere to draw this frame. */
    static boolean active(Warship ship) {
        return ship.arrivedFromAbove() || ship.departing();
    }

    /**
     * The ground half: the wash of dust under the hull and the arrival's ring.
     * Drawn into the smoke layer, which is above the bodies it passes over and
     * below the roofs, which is where dust belongs.
     */
    void ground(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship,
                WarshipAtmosphere.Tint tint) {
        if (!active(ship)) return;
        SpriteAPI soft = soft();
        if (soft == null) return;
        float entry = ship.entrySeconds();
        float departing = ship.departingSeconds();
        boolean leaving = ship.departing();
        float wash = WarshipAtmosphere.groundWash(entry, departing, leaving);
        float[] cx = centre(ship);
        float length = ship.lengthCells;
        float t = Math.max(entry, 0f) + departing;
        float[] dust = tint.dust();
        if (wash > 0.01f) {
            for (int i = 0; i < WASH_PUFFS; i++) {
                float a = WarshipAtmosphere.scatter(ship.hullId, ship.faction.ordinal(), i);
                float b = WarshipAtmosphere.scatter(ship.hullId, ship.faction.ordinal(), i + 100);
                float c = WarshipAtmosphere.scatter(ship.hullId, ship.faction.ordinal(), i + 200);
                // Puffs are blown outward from under the hull and re-form, each on its own phase.
                float phase = frac(a + t * (0.25f + 0.2f * b));
                double angle = Math.PI * 2.0 * (i / (float) WASH_PUFFS + 0.08f * c) + t * 0.35f;
                float radius = length * (0.18f + 0.55f * phase) * (0.7f + 0.5f * b);
                float x = cx[0] + (float) Math.cos(angle) * radius * 0.85f;
                float y = cx[1] + (float) Math.sin(angle) * radius * 0.85f;
                float size = length * (0.16f + 0.22f * phase) * (0.7f + 0.6f * c) * cellPx;
                float fade = (float) Math.sin(Math.PI * phase);
                out.addSprite(RenderLayer.SMOKE, soft, cam.cellToScreenX(x), cam.cellToScreenY(y),
                        size, size, 0f, dust[0], dust[1], dust[2], 0.55f * wash * fade * alpha);
            }
        }
        float ring = WarshipAtmosphere.ringProgress(entry);
        if (ring >= 0f && ring < 1f) drawRing(out, cam, cellPx, alpha, ship, tint, cx, ring);
    }

    private void drawRing(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship,
                          WarshipAtmosphere.Tint tint, float[] cx, float progress) {
        float radiusCells = WarshipAtmosphere.ringRadiusHulls(progress) * ship.lengthCells;
        float strength = WarshipAtmosphere.ringAlpha(progress);
        float[] dust = tint.dust();
        float sx = cam.cellToScreenX(cx[0]);
        float sy = cam.cellToScreenY(cx[1]);
        SpriteAPI ringArt = sprite(RING);
        if (ringArt != null) {
            float d = radiusCells * 2f * cellPx;
            // Lightened dust: a shock front is brighter than the cloud it lifts.
            out.addSprite(RenderLayer.SMOKE, ringArt, sx, sy, d, d, 0f,
                    lighten(dust[0]), lighten(dust[1]), lighten(dust[2]), 0.85f * strength * alpha);
        }
        SpriteAPI soft = soft();
        if (soft == null) return;
        for (int i = 0; i < RING_PUFFS; i++) {
            float a = WarshipAtmosphere.scatter(ship.hullId, ship.faction.ordinal(), i + 300);
            double angle = Math.PI * 2.0 * (i + 0.5f * a) / RING_PUFFS;
            float r = radiusCells * (0.94f + 0.12f * a);
            float size = ship.lengthCells * (0.2f + 0.3f * progress) * (0.7f + 0.6f * a) * cellPx;
            out.addSprite(RenderLayer.SMOKE, soft,
                    cam.cellToScreenX(cx[0] + (float) Math.cos(angle) * r),
                    cam.cellToScreenY(cx[1] + (float) Math.sin(angle) * r),
                    size, size, 0f, dust[0], dust[1], dust[2], 0.6f * strength * alpha);
        }
    }

    /**
     * The sky half, drawn over the hull: the heat on a ship that has just come
     * through the deck, the cloud it is trailing, and the glow behind one that
     * is leaving.
     */
    void sky(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship,
             WarshipAtmosphere.Tint tint) {
        if (!active(ship)) return;
        SpriteAPI soft = soft();
        if (soft == null) return;
        float entry = ship.entrySeconds();
        float departing = ship.departingSeconds();
        boolean leaving = ship.departing();
        float lift = WarshipAtmosphere.lift(entry, departing, leaving);
        float glow = WarshipAtmosphere.reentryGlow(entry);
        float trail = WarshipAtmosphere.climbTrail(departing, leaving);
        float cover = Math.max(WarshipAtmosphere.cloudCover(lift), 0.7f * trail);
        float[] cx = centre(ship);
        double rad = Math.toRadians(ship.facingDegrees);
        float nx = (float) -Math.sin(rad);
        float ny = (float) Math.cos(rad);
        float t = Math.max(entry, 0f) + departing;
        if (cover > 0.01f) drawWisps(out, cam, cellPx, alpha, ship, tint, soft, cx, nx, ny, cover, t);
        float heat = Math.max(glow, 0.8f * trail);
        if (heat > 0.01f) drawHeat(out, cam, cellPx, alpha, ship, tint, soft, cx, nx, ny, glow, trail);
    }

    private void drawWisps(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship,
                           WarshipAtmosphere.Tint tint, SpriteAPI soft, float[] cx,
                           float nx, float ny, float cover, float t) {
        float[] v = tint.vapour();
        float length = ship.lengthCells;
        float width = Math.max(ship.widthCells, length * 0.4f);
        float sideX = -ny;
        float sideY = nx;
        for (int i = 0; i < WISPS; i++) {
            int f = ship.faction.ordinal();
            float a = WarshipAtmosphere.scatter(ship.hullId, f, i + 400);
            float b = WarshipAtmosphere.scatter(ship.hullId, f, i + 500);
            float c = WarshipAtmosphere.scatter(ship.hullId, f, i + 600);
            // Each wisp is shed from the hull and streams astern, then another takes its place.
            float phase = frac(a + t * (0.28f + 0.2f * c));
            float along = (b - 0.5f) * length * 0.9f - phase * length * 1.9f;
            float across = (c - 0.5f) * width * 2.4f * (0.6f + 0.8f * phase);
            float x = cx[0] + nx * along + sideX * across;
            float y = cx[1] + ny * along + sideY * across;
            float size = length * (0.3f + 0.5f * b) * (0.6f + 0.9f * phase) * cellPx;
            float fade = (float) Math.sin(Math.PI * phase);
            out.addSprite(RenderLayer.SHUTTLES, soft, cam.cellToScreenX(x), cam.cellToScreenY(y),
                    size, size, 0f, v[0], v[1], v[2], 0.62f * cover * (0.35f + 0.65f * fade) * alpha);
        }
    }

    private void drawHeat(DrawList out, BattleCamera cam, float cellPx, float alpha, Warship ship,
                          WarshipAtmosphere.Tint tint, SpriteAPI soft, float[] cx,
                          float nx, float ny, float glow, float trail) {
        float[] g = tint.glow();
        float length = ship.lengthCells;
        float width = Math.max(ship.widthCells, length * 0.4f);
        float strength = Math.max(glow, trail);
        float rot = ship.facingDegrees;
        // The leading edge: where a hull coming down through air burns brightest.
        float noseX = cx[0] + nx * length * 0.42f;
        float noseY = cx[1] + ny * length * 0.42f;
        float noseSize = length * (0.55f + 0.45f * glow) * cellPx;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, soft, cam.cellToScreenX(noseX), cam.cellToScreenY(noseY),
                noseSize, noseSize, 0f, g[0], g[1], g[2], 0.85f * glow * alpha);
        // A skin of heat along the hull's own length, thinning toward the stern.
        for (int i = 0; i < 4; i++) {
            float along = length * (0.3f - 0.22f * i);
            float size = Math.max(width, length * 0.35f) * (1.3f - 0.1f * i) * cellPx;
            out.addAdditiveSprite(RenderLayer.SHUTTLES, soft,
                    cam.cellToScreenX(cx[0] + nx * along), cam.cellToScreenY(cx[1] + ny * along),
                    size, size, 0f, g[0], g[1], g[2], 0.42f * glow * (1f - 0.18f * i) * alpha);
        }
        // The wake astern: a plasma streak behind a ship coming down, an exhaust streak behind one going up.
        float streak = length * (1.4f + 1.6f * strength);
        float centreAstern = length * 0.5f + streak * 0.5f;
        out.addAdditiveSprite(RenderLayer.SHUTTLES, soft,
                cam.cellToScreenX(cx[0] - nx * centreAstern), cam.cellToScreenY(cx[1] - ny * centreAstern),
                Math.max(width, length * 0.3f) * 1.1f * cellPx, streak * cellPx, rot,
                g[0], g[1], g[2], 0.5f * strength * alpha);
    }

    /** The hull's own centre in cells: the sprite's centre, not the centre of gravity the ship moves about. */
    private static float[] centre(Warship ship) {
        double rad = Math.toRadians(ship.facingDegrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        return new float[]{
                ship.x + (ship.pivotX * c - ship.pivotY * s),
                ship.y + (ship.pivotX * s + ship.pivotY * c)};
    }

    private SpriteAPI soft() {
        return sprite(SOFT);
    }

    private SpriteAPI sprite(String path) {
        ShuttleSpriteCache cache = sprites.spriteByPath(path);
        return cache == null ? null : cache.sprite;
    }

    private static float lighten(float v) {
        return v + (1f - v) * 0.45f;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }
}
