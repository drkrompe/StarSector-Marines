package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.SystemFxService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.PolyTess;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * Draws what a running integral system looks like: the suit lit up, and — when
 * the system raised one — the screen it is holding, pointed at the arc the
 * damage path is actually resolving against.
 *
 * <p><b>The arc is the whole point.</b> A breach assist protects one facing and
 * leaves the flanks open, which is the rule the player has to play around and
 * the reason a flanked breacher takes full damage
 * ({@code combat-durability-nouns.md}). An all-round glow would hide exactly
 * that property and teach the opposite of the truth, so the drawn screen is a
 * sector: it spans the authored arc and no more, and its two ends carry an
 * explicit spoke because "where does this stop" is the single most useful thing
 * in the picture. A 100-degree scrap screen and a 200-degree interlock screen
 * are meant to be told apart at a glance, and are.
 *
 * <p><b>The clock is drawn, not printed.</b> The screen's rim holds the full
 * arc for as long as the system runs while the band behind it thins toward the
 * rim as the window closes, so a viewer reads how much is left without reading
 * a number — and the arc never narrows, because the protected arc never
 * narrows. Cooldown is deliberately absent: these fire on an authored policy
 * rather than a player click, so a cooldown readout would answer a question
 * nobody is asking ({@code progression-nouns.md}).
 *
 * <p><b>Keyed on the capability, not the carrier.</b> Everything drawn comes
 * off the {@code SYSTEM_FX} appearance columns
 * ({@link SystemFxService}); this class cannot tell which armour pattern is in
 * front of it and must not learn. A running system that raises no screen
 * reports a zero arc and gets the projector bloom alone — that is the branch,
 * and it is a branch on the data rather than on the suit.
 *
 * <p>Sibling to {@link SatchelRenderSystem} and {@link PointDefenseRenderSystem}
 * in the {@code HAZARDS} layer, which is where simulation-owned field effects
 * paint over the bodies producing them.
 */
public final class SystemFxRenderSystem implements RenderSystem {

    /** Inner edge of the drawn band, in cells from the wearer. Outside a marine's own silhouette. */
    private static final float INNER_RADIUS_CELLS = 0.50f;
    /** Outer edge of the drawn band, in cells. Wide enough that the band has room to visibly thin. */
    private static final float OUTER_RADIUS_CELLS = 1.10f;
    /** Thickness of the rim that holds the full arc for the system's whole run, in cells. */
    private static final float RIM_CELLS = 0.09f;
    /** How far the end spokes overhang the band on each side, in cells. */
    private static final float SPOKE_OVERHANG_CELLS = 0.07f;
    private static final float SPOKE_WIDTH_CELLS = 0.07f;
    /** Degrees of arc per tessellated segment. Constant density, so a wide arc is not coarser than a narrow one. */
    private static final float DEGREES_PER_SEGMENT = 6f;
    /** Degrees of arc per drawn facet. Also what makes a wide screen visibly carry more of them. */
    private static final float DEGREES_PER_FACET = 24f;
    private static final float FACET_CELLS = 0.42f;
    /**
     * The projector on the wearer. Deliberately small: it says the suit is lit
     * and which way it is pointed, and must never grow into a fan wide enough
     * to be mistaken for the screen's extent — the fan's own spread is fixed
     * art, while the arc is authored, so a large one would contradict the band
     * behind it on every pattern but the one it happened to match.
     */
    private static final float EMITTER_CELLS = 0.85f;

    /** Pale, near-desaturated screen colour, so the authored art can carry the hue. */
    private static final float SCREEN_R = 0.58f;
    private static final float SCREEN_G = 0.88f;
    private static final float SCREEN_B = 1.00f;

    private final BattleSprites sprites;
    private final PolyMesh mesh = new PolyMesh(192);

    public SystemFxRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.HAZARDS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        mesh.reset();
        SystemFxService fx = ctx.sim.getRoster().systemFx();
        BattleCamera camera = ctx.camera;
        float cellPx = camera.cellPxSize();
        SpriteAPI facet = sprites.systemScreenFacetSprite();
        SpriteAPI emitter = sprites.systemEmitterSprite();
        for (int i = 0, n = ctx.sim.liveUnitCount(); i < n; i++) {
            long unit = ctx.sim.liveUnitAt(i);
            if (!fx.isRunning(unit)) continue;
            float worldX = ctx.sim.world().renderX(unit);
            float worldY = ctx.sim.world().renderY(unit);
            // A hostile's running system is as readable as a marine's — the
            // capability is symmetric and an incoming breacher should be
            // legible — but only where the player can see the ground it is
            // standing on, the same gate every other field effect uses.
            if (ctx.sim.identity().faction(unit) != Faction.MARINE
                    && !ctx.sim.getFogOfWar().isCellRevealed(
                            (int) Math.floor(worldX), (int) Math.floor(worldY))) {
                continue;
            }
            float screenX = camera.cellToScreenX(worldX);
            float screenY = camera.cellToScreenY(worldY);
            float intensity = fx.intensity(unit);
            float arc = fx.arcDegrees(unit);
            float facingDegrees = fx.arcFacingDegrees(unit);
            emitCarrierMark(out, facet, emitter, screenX, screenY, cellPx,
                    arc > 0f ? facingDegrees : Float.NaN, intensity, ctx.alphaMult);
            if (arc <= 0f) continue;
            emitScreen(out, facet, screenX, screenY, cellPx,
                    mathDegrees(facingDegrees), arc,
                    intensity, fx.arcFraction(unit), ctx.alphaMult);
        }
        if (!mesh.isEmpty()) out.addPoly(RenderLayer.HAZARDS, mesh);
    }

    /**
     * The mark on the wearer itself, drawn for every running system including
     * one that raises no screen — that is the part of the treatment which
     * answers "why is this marine suddenly faster", and it must not depend on
     * there being an arc to draw.
     *
     * <p>A system holding a screen gets the projector, pointed the way the
     * screen is. One that holds none gets an undirected bloom, because there is
     * no direction to claim and implying one would be a lie about a capability
     * that has no facing at all. {@code facingDegrees} is {@code NaN} for that
     * case, which is the branch.
     */
    private void emitCarrierMark(DrawList out, SpriteAPI facet, SpriteAPI emitter,
                                 float screenX, float screenY, float cellPx,
                                 float facingDegrees, float intensity, float alphaMult) {
        boolean directed = !Float.isNaN(facingDegrees) && emitter != null;
        SpriteAPI mark = directed ? emitter : facet;
        if (mark == null) return;
        float cells = directed ? EMITTER_CELLS : FACET_CELLS;
        // The art is authored pointing up and the sprite frame's zero angle is
        // up, so the wearer's facing goes in unmodified.
        float angle = directed ? facingDegrees : 0f;
        float size = cellPx * cells * (0.85f + 0.35f * intensity);
        out.addSprite(RenderLayer.HAZARDS, mark, screenX, screenY, size, size, angle,
                SCREEN_R, SCREEN_G, SCREEN_B, (0.32f + 0.48f * intensity) * alphaMult);
    }

    private void emitScreen(DrawList out, SpriteAPI facet,
                            float screenX, float screenY, float cellPx,
                            float centerDegrees, float arcDegrees,
                            float intensity, float fraction, float alphaMult) {
        float innerR = cellPx * INNER_RADIUS_CELLS;
        float outerR = cellPx * OUTER_RADIUS_CELLS;
        float rim = cellPx * RIM_CELLS;
        int segments = Math.max(4, Math.round(arcDegrees / DEGREES_PER_SEGMENT));
        // A heavier screen reads heavier, but never opaque: the wearer stays
        // visible through their own screen, and a screen that hid its own
        // carrier would trade one unreadable state for another.
        float weight = 0.45f + 0.35f * fraction;

        // The rim holds the whole authored arc for the whole run. It is what
        // the arc's width is read off, so it must not thin with the clock.
        PolyTess.appendSector(mesh, screenX, screenY, outerR - rim, outerR,
                centerDegrees, arcDegrees, segments,
                SCREEN_R, SCREEN_G, SCREEN_B, weight * alphaMult);

        // The band behind it drains toward the rim as the window closes: a
        // screen with a second left is a sliver under its own rim, and one just
        // raised fills the whole depth. Radially, deliberately — draining it
        // angularly would narrow the arc, which is the one thing the picture is
        // not allowed to imply.
        float bandOuter = outerR - rim;
        float bandThickness = (bandOuter - innerR) * intensity;
        if (bandThickness > 0f) {
            PolyTess.appendSector(mesh, screenX, screenY, bandOuter - bandThickness, bandOuter,
                    centerDegrees, arcDegrees, segments,
                    SCREEN_R, SCREEN_G, SCREEN_B,
                    weight * (0.24f + 0.34f * intensity) * alphaMult);
        }

        // Where the protection stops, said outright.
        float spokeInner = innerR - cellPx * SPOKE_OVERHANG_CELLS;
        float spokeOuter = outerR + cellPx * SPOKE_OVERHANG_CELLS;
        float spokeWidth = cellPx * SPOKE_WIDTH_CELLS;
        for (int side = -1; side <= 1; side += 2) {
            PolyTess.appendRadialSpoke(mesh, screenX, screenY, spokeInner, spokeOuter,
                    centerDegrees + side * arcDegrees * 0.5f, spokeWidth,
                    SCREEN_R, SCREEN_G, SCREEN_B, Math.min(1f, weight + 0.25f) * alphaMult);
        }

        if (facet != null && bandThickness > 0f) {
            int facets = Math.max(2, Math.round(arcDegrees / DEGREES_PER_FACET));
            // Riding the band's own midline, so the facets visibly creep out to
            // the rim and go as the window closes. The clock reads as motion
            // rather than as a fade a viewer has to remember the start of.
            float midR = bandOuter - bandThickness * 0.5f;
            float size = cellPx * FACET_CELLS;
            for (int i = 0; i < facets; i++) {
                // Placed at segment centres rather than at the ends, so a facet
                // never straddles the arc boundary the spokes just marked.
                float t = (i + 0.5f) / facets;
                double angle = Math.toRadians(centerDegrees + (t - 0.5f) * arcDegrees);
                out.addSprite(RenderLayer.HAZARDS, facet,
                        screenX + (float) Math.cos(angle) * midR,
                        screenY + (float) Math.sin(angle) * midR,
                        size, size, 0f,
                        SCREEN_R, SCREEN_G, SCREEN_B,
                        (0.14f + 0.66f * intensity) * weight * alphaMult);
            }
        }

    }

    /**
     * Converts a simulation facing ({@code AirBody.facingToward}: zero is north,
     * increasing counter-clockwise) into the ordinary mathematical degrees the
     * tessellation helpers take. One conversion, in one place, so nothing
     * downstream has to hold two conventions at once.
     */
    private static float mathDegrees(float facingDegrees) {
        return facingDegrees + 90f;
    }
}
