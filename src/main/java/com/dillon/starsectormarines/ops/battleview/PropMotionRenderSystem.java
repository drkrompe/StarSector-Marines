package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.FixtureUse;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the moving parts of props ({@link PropMotion}) over their static bases: fans turning, belts running, a gantry
 * crane travelling its bay. One instance draws the parts that sit with the furniture ({@link RenderLayer#DOODADS},
 * after every static doodad), another the overhead ones ({@link RenderLayer#ROOFS}, above the people under them);
 * both read one shared {@link Phases}, so a prop with parts in both passes runs as one machine.
 *
 * <p>Any battle map, not only a ship's deck. A prop runs while what it needs holds, read from generic inputs: the map's
 * power ({@link BattleSimulation#propPower}, which each kind of map answers for itself), whether it is damaged (a
 * damaged prop is its own {@code -damaged} doodad, and runs only at its motion's damaged rate; a destroyed one does
 * not run), and for a worked machine somebody actually at work on it ({@link FixtureUse}). Presentation only: nothing
 * here touches a footprint, cover or line of sight.
 */
final class PropMotionRenderSystem implements RenderSystem {

    private final BattleSprites sprites;
    private final PropMotion.Layer layer;
    private final Phases phases;

    PropMotionRenderSystem(BattleSprites sprites, PropMotion.Layer layer, Phases phases) {
        this.sprites = sprites;
        this.layer = layer;
        this.phases = phases;
    }

    @Override
    public RenderLayer layer() {
        return layer == PropMotion.Layer.OVERHEAD ? RenderLayer.ROOFS : RenderLayer.DOODADS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        BattleSimulation sim = ctx.sim;
        BattleCamera cam = ctx.camera;
        VisibleCellRect view = cam.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS,
                sim.getGrid().getWidth(), sim.getGrid().getHeight());
        float now = phases.clock(ctx);
        FogOfWarService fog = layer == PropMotion.Layer.OVERHEAD ? sim.getFogOfWar() : null;
        boolean fogged = fog != null && fog.isInitialized();
        for (Doodad d : sim.getDoodads()) {
            PropMotion motion = registry.motion(d.definitionId());
            boolean damaged = false;
            if (motion == null) {
                // A damaged machine is its own doodad; it keeps running, slower, only where its motion says it may.
                motion = registry.damagedMotion(d.definitionId());
                damaged = motion != null;
            }
            if (motion == null || !motion.draws(layer)
                    || !view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY)) continue;
            float centreX = d.cellX + d.footprintCellsX * 0.5f;
            float centreY = d.cellY + d.footprintCellsY * 0.5f;
            if (fogged && !fog.isCellRevealed((int) centreX, (int) centreY)) continue;
            float phase = phases.advance(ctx, d, motion, damaged, now, centreX, centreY);
            for (PropMotion.Part part : motion.parts()) {
                if (part.layer() != layer) continue;
                DoodadDef def = registry.doodad(part.doodadId());
                SpriteAPI sheet = def == null ? null : sprites.extraTerrainSheet(def.sheetPath);
                if (sheet != null) emitPart(out, cam, sheet, def, d, part, phase, ctx.alphaMult);
            }
        }
    }

    private void emitPart(DrawList out, BattleCamera cam, SpriteAPI sheet, DoodadDef def, Doodad base,
                          PropMotion.Part part, float phase, float alpha) {
        int cell = def.sourceCellPx > 0 ? def.sourceCellPx : base.sourceCellPx;
        int srcX = def.col * cell, srcY = def.row * cell;
        int srcW = def.footprintCellsX * cell, srcH = def.footprintCellsY * cell;
        switch (part.kind()) {
            case SCROLL -> emitScroll(out, cam, sheet, base, part, phase, alpha, srcX, srcY, srcW, srcH);
            case SLIDE -> {
                float[] at = part.slideAt(phase);
                quad(out, cam, sheet, base, at[0], at[1], srcX, srcY, srcW, srcH,
                        def.footprintCellsX, def.footprintCellsY, 0f, alpha);
            }
            default -> quad(out, cam, sheet, base, part.x(), part.y(), srcX, srcY, srcW, srcH,
                    def.footprintCellsX, def.footprintCellsY, part.spinDegrees(phase), alpha);
        }
    }

    /**
     * Lays the part's frame end to end along the window as tiles one {@code period} long, shifted by how far the belt
     * has run, clipping the two end tiles to the window.
     */
    private void emitScroll(DrawList out, BattleCamera cam, SpriteAPI sheet, Doodad base, PropMotion.Part part,
                            float phase, float alpha, int srcX, int srcY, int srcW, int srcH) {
        float[] w = part.window();
        boolean alongY = part.alongY();
        float start = alongY ? w[1] : w[0];
        float length = alongY ? w[3] : w[2];
        float across = alongY ? w[0] + w[2] * 0.5f : w[1] + w[3] * 0.5f;
        float thickness = alongY ? w[2] : w[3];
        float period = part.period();
        int tilePx = alongY ? srcH : srcW;
        for (float s = start - period + part.scrollOffset(phase); s < start + length; s += period) {
            float from = Math.max(s, start), to = Math.min(s + period, start + length);
            if (to - from <= 1e-4f) continue;
            int p0 = Math.round((from - s) / period * tilePx);
            int p1 = Math.max(p0 + 1, Math.round((to - s) / period * tilePx));
            float mid = (from + to) * 0.5f;
            if (alongY) quad(out, cam, sheet, base, across, mid, srcX, srcY + p0, srcW, p1 - p0,
                    thickness, to - from, 0f, alpha);
            else quad(out, cam, sheet, base, mid, across, srcX + p0, srcY, p1 - p0, srcH,
                    to - from, thickness, 0f, alpha);
        }
    }

    /**
     * One sub-sprite at a point in the base's source frame, carried through the base's placement exactly as the base
     * is drawn ({@code DoodadRenderSystem.emit}): turned by its quarter turns about the footprint's centre, and
     * mirrored by the same vertical flip through a half turn.
     */
    private void quad(DrawList out, BattleCamera cam, SpriteAPI sheet, Doodad base, float sx, float sy,
                             int srcX, int srcY, int srcW, int srcH, float cellsW, float cellsH, float spin, float alpha) {
        float cellPx = cam.cellPxSize();
        float[] at = placed(base, sx, sy);
        float cx = cam.cellToScreenX(base.cellX + base.footprintCellsX * 0.5f) + at[0] * cellPx;
        float cy = cam.cellToScreenY(base.cellY + base.footprintCellsY * 0.5f) + at[1] * cellPx;
        float angle = base.quarterTurns * 90f + spin;
        if (base.mirrored) {
            out.addSheetQuadFlippedV(layer(), sheet, srcX, srcY, srcW, srcH, cx, cy,
                    cellsW * cellPx, cellsH * cellPx, angle + 180f, 1f, 1f, 1f, alpha);
        } else {
            out.addSheetQuad(layer(), sheet, srcX, srcY, srcW, srcH, cx, cy,
                    cellsW * cellPx, cellsH * cellPx, angle, 1f, 1f, 1f, alpha);
        }
    }

    /**
     * Where a point given in the base's source cells (from the top-left of its frame) lands, as an offset in cells
     * from the placed footprint's centre, in the screen's y-up frame.
     */
    static float[] placed(Doodad base, float sx, float sy) {
        float ux = sx - base.sourceCellsX * 0.5f;
        float uy = -(sy - base.sourceCellsY * 0.5f);
        if (base.mirrored) { ux = -ux; uy = -uy; }
        double theta = Math.toRadians(base.quarterTurns * 90.0);
        float cos = (float) Math.cos(theta), sin = (float) Math.sin(theta);
        return new float[]{ux * cos - uy * sin, ux * sin + uy * cos};
    }

    /**
     * Each prop's running phase and the points its work is done from, shared by the deck pass and the overhead pass.
     * Keyed weakly on the placement, so a deck that is let go takes its props' state with it.
     */
    static final class Phases {
        private final Map<Doodad, MotionPhase> phases = new WeakHashMap<>();
        private final Map<Doodad, List<TaskPoint>> pointsOn = new WeakHashMap<>();
        private final Map<BattleSimulation, Integer> indexedPoints = new WeakHashMap<>();
        private float fallbackClock;

        float clock(RenderContext ctx) {
            if (Float.isFinite(ctx.animationTimeSeconds)) return ctx.animationTimeSeconds;
            fallbackClock += Math.max(0f, ctx.realDt);
            return fallbackClock;
        }

        float advance(RenderContext ctx, Doodad d, PropMotion motion, boolean damaged, float now, float x, float y) {
            MotionPhase phase = phases.computeIfAbsent(d, k -> new MotionPhase(startPhase(k)));
            boolean inUse = !motion.runs.needsUse() || inUse(ctx.sim, d);
            float deficit = motion.runs.needsPower() ? ctx.sim.propPower().deficitAt(x, y) : 0f;
            return phase.advance(now, motion.targetRate(inUse, deficit, damaged), motion.spinUpSeconds);
        }

        private boolean inUse(BattleSimulation sim, Doodad d) {
            TaskPointService points = sim.taskPoints();
            Integer seen = indexedPoints.get(sim);
            if (seen == null || seen != points.registeredCount()) {
                // Points are published when the deck is staffed; an index taken before then would be empty for good.
                pointsOn.clear();
                indexedPoints.put(sim, points.registeredCount());
            }
            List<TaskPoint> on = pointsOn.computeIfAbsent(d, k -> FixtureUse.pointsOn(points, k));
            return FixtureUse.anyWorked(points, sim.ambientTasks(), on);
        }

        /** Two identical props side by side do not run in step. */
        private static float startPhase(Doodad d) {
            return Math.floorMod(d.cellX * 73_856_093 ^ d.cellY * 19_349_663, 997) * 0.1f;
        }
    }
}
