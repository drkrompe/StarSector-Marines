package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ambient.FixtureUse;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.PropMotion;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.VisibleCellRect;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Arrays;
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
 *
 * <p>A fixed overhead part (a shop awning, a pipe rack) fades to {@link #SEEN_UNDER_ALPHA} while a figure the player
 * can see stands under its prop, so nobody can hide under one from the player's view; it eases back once the ground
 * under it is clear. Moving parts (a crane's bridge) keep their full alpha.
 *
 * <p>An overhead prop that stands inside a building, under its intact roof (a mega forge's crane runway), is inside the
 * building and not over it: the roof pass draws its roof first, so the prop's every part draws at what the roof leaves
 * of it, {@link #underRoof} of the roof's own fade. Hidden while the roof is shut, it shows as the roof fades for the
 * people inside. A prop under no roof draws as it always has.
 *
 * <p>A prop hung on a building's wall ({@link WallMounts}: an awning, a wall lamp) is left out of the overhead pass:
 * the roof pass draws it through {@link #collectMounts}, after the faces and before the roofs, stood up on the face it
 * hangs from, so it moves with that face as the view pans and a roof standing in front of it hides it.
 */
final class PropMotionRenderSystem implements RenderSystem {

    /** How much of a fixed overhead part still draws while a seen figure stands under it. */
    static final float SEEN_UNDER_ALPHA = 0.4f;
    /** Seconds a fade takes, in either direction. */
    static final float FADE_SECONDS = 0.25f;

    private final BattleSprites sprites;
    private final PropMotion.Layer layer;
    private final Phases phases;
    /** Each overhead prop's current fade; weakly keyed, as {@link Phases} is. */
    private final Map<Doodad, float[]> fades = new WeakHashMap<>();
    /** Cells where a figure the player can see stands this frame, row-major. */
    private boolean[] seenFigures = new boolean[0];
    private final float[] mountCorners = new float[8];
    private final float[] mountPatch = new float[16];

    /** Where a wall mount hangs ({@link WallMounts#standUp}). */
    interface MountAnchor {
        /**
         * Stands the mount's frame up, in place in {@code corners} (laid on the ground by {@link WallMounts#frame}, in
         * cells); false where it is not drawn at all, its wall gone.
         */
        boolean stand(Doodad d, float[] corners);
    }

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
        int width = sim.getGrid().getWidth();
        if (layer == PropMotion.Layer.OVERHEAD) markSeenFigures(sim, fog, width, sim.getGrid().getHeight());
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
            // A wall mount draws with the faces, through collectMounts.
            if (layer == PropMotion.Layer.OVERHEAD && WallMounts.hangs(d.definitionId())) continue;
            float centreX = d.cellX + d.footprintCellsX * 0.5f;
            float centreY = d.cellY + d.footprintCellsY * 0.5f;
            // Moving machinery waits for the fog; a prop that is its own fixed overhead art stands like a roof.
            if (fogged && !motion.liftsItself() && !fog.isCellRevealed((int) centreX, (int) centreY)) continue;
            float phase = phases.advance(ctx, d, motion, damaged, now, centreX, centreY);
            float fade = layer == PropMotion.Layer.OVERHEAD
                    ? fade(d, seenUnder(seenFigures, width, d), ctx.realDt) : 1f;
            float roof = layer == PropMotion.Layer.OVERHEAD ? roofLeaves(sim, (int) centreX, (int) centreY) : 1f;
            if (roof <= 0f) continue;
            for (PropMotion.Part part : motion.parts()) {
                if (part.layer() != layer) continue;
                DoodadDef def = registry.doodad(part.doodadId());
                SpriteAPI sheet = def == null ? null : sprites.extraTerrainSheet(def.sheetPath);
                float alpha = (part.kind() == PropMotion.Kind.FIXED ? ctx.alphaMult * fade : ctx.alphaMult) * roof;
                if (sheet != null) emitPart(out, cam, sheet, def, d, part, phase, alpha);
            }
        }
    }

    /**
     * The props hung on building walls in view, each stood up on its wall by {@code anchor} and drawn whole in the
     * roof layer, fading under a seen figure as any fixed overhead part does. Called by the roof pass between the faces
     * and the roofs; only the overhead instance draws them.
     */
    void collectMounts(RenderContext ctx, DrawList out, MountAnchor anchor) {
        if (layer != PropMotion.Layer.OVERHEAD) return;
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        BattleSimulation sim = ctx.sim;
        BattleCamera cam = ctx.camera;
        int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
        VisibleCellRect view = cam.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS + 2, width, height);
        boolean marked = false;
        for (Doodad d : sim.getDoodads()) {
            if (!WallMounts.hangs(d.definitionId())
                    || !view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY)) continue;
            PropMotion motion = registry.motion(d.definitionId());
            if (motion == null || !motion.draws(layer)) continue;
            if (!marked) {
                markSeenFigures(sim, sim.getFogOfWar(), width, height);
                marked = true;
            }
            WallMounts.frame(d, mountCorners);
            if (!anchor.stand(d, mountCorners)) continue;
            float alpha = ctx.alphaMult * fade(d, seenUnder(seenFigures, width, d), ctx.realDt);
            for (PropMotion.Part part : motion.parts()) {
                if (part.layer() != layer) continue;
                DoodadDef def = registry.doodad(part.doodadId());
                SpriteAPI sheet = def == null ? null : sprites.extraTerrainSheet(def.sheetPath);
                if (sheet != null) emitMount(out, cam, sheet, def, d, alpha);
            }
        }
    }

    /** A mount's whole frame drawn over its four stood-up corners. */
    private void emitMount(DrawList out, BattleCamera cam, SpriteAPI sheet, DoodadDef def, Doodad d, float alpha) {
        int cell = def.sourceCellPx > 0 ? def.sourceCellPx : d.sourceCellPx;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            float x = cam.cellToScreenX(mountCorners[k * 2]), y = cam.cellToScreenY(mountCorners[k * 2 + 1]);
            mountPatch[k * 2] = x;
            mountPatch[k * 2 + 1] = y;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        float w = Math.max(maxX - minX, 1e-3f), h = Math.max(maxY - minY, 1e-3f);
        float[] u = d.mirrored ? WallMounts.MIRRORED_U : WallMounts.U;
        for (int k = 0; k < 4; k++) {
            mountPatch[k * 2] = (mountPatch[k * 2] - minX) / w;
            mountPatch[k * 2 + 1] = (mountPatch[k * 2 + 1] - minY) / h;
            mountPatch[8 + k * 2] = u[k];
            mountPatch[8 + k * 2 + 1] = WallMounts.V[k];
        }
        out.addSheetPatch(RenderLayer.ROOFS, sheet, def.col * cell, def.row * cell, def.footprintCellsX * cell,
                def.footprintCellsY * cell, (minX + maxX) / 2f, (minY + maxY) / 2f, w, h, alpha, mountPatch.clone());
    }

    /** How much of an overhead prop at a cell the roof over it leaves to draw: all of it where no intact roof is. */
    private static float roofLeaves(BattleSimulation sim, int x, int y) {
        CellTopology topology = sim.getTopology();
        Buildings buildings = sim.getBuildings();
        if (topology == null || buildings == null || !topology.inBounds(x, y) || !topology.isRoofIntact(x, y)) {
            return 1f;
        }
        Building building = buildings.get(topology.getBuildingId(x, y));
        return building == null ? 1f : underRoof(building.currentAlpha);
    }

    /**
     * What a roof drawn at {@code roofAlpha} leaves of what stands under it: nothing under a shut roof, all of it once
     * the roof has faded away.
     */
    static float underRoof(float roofAlpha) {
        return 1f - Math.max(0f, Math.min(1f, roofAlpha));
    }

    /** Marks every cell a live figure stands on where the player can see it. */
    private void markSeenFigures(BattleSimulation sim, FogOfWarService fog, int width, int height) {
        if (seenFigures.length != width * height) seenFigures = new boolean[width * height];
        else Arrays.fill(seenFigures, false);
        UnitRosterService roster = sim.getRoster();
        World world = sim.world();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = roster.get(i);
            int x = world.cellX(u), y = world.cellY(u);
            if (x < 0 || y < 0 || x >= width || y >= height) continue;
            if (fog == null || fog.isCellRevealed(x, y)) seenFigures[y * width + x] = true;
        }
    }

    /** Whether a seen figure stands anywhere on the prop's footprint. */
    static boolean seenUnder(boolean[] seen, int width, Doodad d) {
        if (width <= 0) return false;
        int height = seen.length / width;
        for (int y = Math.max(0, d.cellY); y < Math.min(height, d.cellY + d.footprintCellsY); y++) {
            for (int x = Math.max(0, d.cellX); x < Math.min(width, d.cellX + d.footprintCellsX); x++) {
                if (seen[y * width + x]) return true;
            }
        }
        return false;
    }

    /** The prop's fade this frame, eased toward its target; a prop seen for the first time starts there. */
    private float fade(Doodad d, boolean under, float dt) {
        float target = under ? SEEN_UNDER_ALPHA : 1f;
        float[] state = fades.get(d);
        if (state == null) {
            fades.put(d, new float[]{target});
            return target;
        }
        state[0] = ease(state[0], target, dt);
        return state[0];
    }

    /**
     * One step of a fade from {@code from} toward {@code to}, covering the full range in {@link #FADE_SECONDS}. A frame
     * with no time behind it (a still) shows the fade where it is heading, not wherever an earlier still left it.
     */
    static float ease(float from, float to, float dt) {
        if (dt <= 0f) return to;
        float step = (1f - SEEN_UNDER_ALPHA) * dt / FADE_SECONDS;
        return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
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
