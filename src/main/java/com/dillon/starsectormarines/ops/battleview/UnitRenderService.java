package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.VisionService;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;

/**
 * Emits the {@link RenderLayer#UNITS} layer — the heavy world pass. Story J's
 * target shape: a stateless consumer that sweeps the unit list <em>once per
 * render-task-kind</em> (footprints, then sprites, then HP bars), branching on a
 * type-flyweight {@link RenderAppearance} + its capability tags instead of an
 * {@code instanceof}/{@code combatant}/{@code deathPoseIdx} ladder. Per-stratum
 * sweeps in paint order dissolve the per-entity decorator ordering trap: "HP bars
 * on top across all kinds" is simply "the bar sweep runs last". See
 * {@code roadmap/battle-render/stories/story-j-units.md}.
 *
 * <p><b>Complete (slice J6).</b> All six sweeps are live here — footprints, turret
 * + hub whole-sprite bodies, dead-sprites, live-infantry sprites (batched
 * {@code SHEET_QUAD}, with the SOUTH-weapon-up vertical flip), and the layer-wide
 * HP-bar pass. The inline {@code BattleRenderer.renderUnits} is gone;
 * {@code renderWorld} drains this layer ({@code drainLayer(RenderLayer.UNITS)})
 * directly at the units slot. Collect order = paint order: footprints → turret →
 * hub → dead → live → bars (bars last = layer-wide on top).
 *
 * <p><b>Sweep order is paint order.</b> The collect order below <em>is</em> the
 * submission (= paint) order under the strict-painter drain. One intentional
 * refinement vs. the old inline passes: footprints now sweep <em>before all</em>
 * bodies (the inline code interleaved them per structure type — turret pad, turret
 * sprite, hub pad, hub sprite). Pads are ground decals meant to sit under the
 * structures, so a turret sprite overhanging an adjacent hub's pad now correctly
 * paints on top of it.
 */
public final class UnitRenderService implements RenderSystem {

    /** Window (s) after a unit fires during which the weapon-up pose shows. */
    private static final float WEAPON_UP_TIME = 0.25f;

    // Faction-colored quad fallback when a unit's sprite sheet is missing/unloaded.
    private static final Color MARINE_COLOR   = new Color(0x5A, 0xA0, 0xE0);
    private static final Color DEFENDER_COLOR = new Color(0xE0, 0x6A, 0x6A);
    private static final Color CIVILIAN_COLOR = new Color(0xC8, 0xC8, 0x80);

    private final BattleSprites sprites;

    public UnitRenderService(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.UNITS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        sweepFootprints(ctx, out);
        sweepTurretBodies(ctx, out);
        sweepHubBodies(ctx, out);
        sweepDeadSprites(ctx, out);
        sweepLiveSprites(ctx, out);
        sweepHpBars(ctx, out);
    }

    /**
     * Ground pads under every live map turret + drone hub, emitted first so they
     * sit under all bodies. Faithful port of the two inline {@code ROAD_FILL}
     * footprint fills (one per structure type), now one {@code SOLID_RECT} per
     * structure via {@link GroundFootprint} — they coalesce into a single
     * {@code SolidQuadBatch} flush in the drain.
     */
    private void sweepFootprints(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        World world = ctx.sim.world();
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        for (int i = 0, n = ctx.sim.liveUnitCount(); i < n; i++) {
            Entity u = ctx.sim.liveUnitAt(i);
            if (!RenderAppearance.of(u.type).drawsFootprint) continue;
            float x0 = cam.cellToScreenX(world.cellX(u.entityId));
            float y0 = cam.cellToScreenY(world.cellY(u.entityId));
            GroundFootprint.emit(out, RenderLayer.UNITS, x0, y0, cellPx, alphaMult);
        }
    }

    /**
     * Map-turret bodies: an optional recoil-displaced barrel {@code SPRITE} under
     * the base {@code SPRITE} (both whole-texture rotated). Faithful port of the
     * inline turret pass — same recoil easing/offset, same per-{@code TurretKind}
     * base/barrel caches — minus the end-of-pass {@code setAngle(0)} reset loops
     * (the {@code SPRITE} drain resets angle after each draw). When the base sprite
     * is missing, a {@code DEFENDER_COLOR} {@code SOLID_RECT} fallback stands in
     * (and the barrel is skipped), exactly as the inline fallback did.
     */
    private void sweepTurretBodies(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        World world = ctx.sim.world();
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        for (int i = 0, n = ctx.sim.liveUnitCount(); i < n; i++) {
            Entity u = ctx.sim.liveUnitAt(i);
            if (!(u instanceof MapTurret)) continue;
            MapTurret t = (MapTurret) u;
            float cx = cam.cellToScreenX(world.cellX(t.entityId) + 0.5f);
            float cy = cam.cellToScreenY(world.cellY(t.entityId) + 0.5f);

            ShuttleSpriteCache base = sprites.turretSprites().get(t.kind);
            if (base == null) {
                float half = cellPx * BattleRenderer.UNIT_FRAC / 2f;
                emitSolidQuad(out, cx, cy, half, DEFENDER_COLOR, alphaMult);
                continue;
            }

            ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(t.kind);
            if (barrel != null) {
                float recoilT = 0f;
                if (t.recoilTimer < BattleRenderer.RECOIL_DURATION) {
                    recoilT = 1f - t.recoilTimer / BattleRenderer.RECOIL_DURATION;
                }
                float pushPx = recoilT * BattleRenderer.RECOIL_DISTANCE_FRAC * t.kind.visualCells * cellPx;
                double rad = Math.toRadians(t.facingDegrees);
                float bx = (float) Math.sin(rad) * pushPx;
                float by = -(float) Math.cos(rad) * pushPx;
                emitWholeSprite(out, barrel, t.facingDegrees, t.kind.visualCells, cellPx,
                        cx + bx, cy + by, alphaMult);
            }
            emitWholeSprite(out, base, t.facingDegrees, t.kind.visualCells, cellPx, cx, cy, alphaMult);
        }
    }

    /**
     * Drone-hub bodies: one unrotated whole-texture {@code SPRITE} per live hub.
     * Faithful port of the inline hub pass; the hub sprite is loaded at
     * {@code BattleScreen.attach} (hoisted out of the pass) so this stays GL-free,
     * and is simply skipped if the load failed (the footprints already drew).
     */
    private void sweepHubBodies(RenderContext ctx, DrawList out) {
        ShuttleSpriteCache hub = sprites.droneHubSprite();
        if (hub == null) return;
        BattleCamera cam = ctx.camera;
        World world = ctx.sim.world();
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        for (int i = 0, n = ctx.sim.liveUnitCount(); i < n; i++) {
            Entity u = ctx.sim.liveUnitAt(i);
            if (!(u instanceof DroneHubUnit)) continue;
            float cx = cam.cellToScreenX(world.cellX(u.entityId) + 0.5f);
            float cy = cam.cellToScreenY(world.cellY(u.entityId) + 0.5f);
            emitWholeSprite(out, hub, 0f, DroneHubUnit.VISUAL_CELLS, cellPx, cx, cy, alphaMult);
        }
    }

    /**
     * Emits one whole-texture rotated body sprite, sized {@code visualCells} tall
     * (× the sprite's natural aspect wide). Mirrors
     * {@code ShuttleRenderSystem.emitTurretLayer} — the {@code SPRITE} drain owns
     * size/angle/alpha/blend/color and resets angle afterward.
     */
    private static void emitWholeSprite(DrawList out, ShuttleSpriteCache cache, float facingDegrees,
                                        float visualCells, float cellPx, float cx, float cy, float alphaMult) {
        float pxH = visualCells * cellPx;
        float pxW = pxH * cache.aspect;
        out.addSprite(RenderLayer.UNITS, cache.sprite, cx, cy, pxW, pxH, facingDegrees,
                1f, 1f, 1f, alphaMult);
    }

    /**
     * Corpse sweep: dead units with a death pose draw their frozen pose frame as a
     * batched {@code SHEET_QUAD}. Faithful port of the former inline
     * {@code BattleRenderer.renderDeadUnits} — same two gates, same pose-frame
     * selection, same aspect-fit into the {@code renderScale}d cell box, no vision
     * gate (corpses persist through fog), no flip, no HP bar.
     *
     * <p>Sourced from the corpse archetype in the battle {@code EntityWorld},
     * <em>not</em> the legacy units list — a corpse entity is spawned on the
     * death event with its draw position frozen at the spot it fell and its pose
     * authored into {@code SPRITE.index}, so this sweep is a pure column walk
     * over the matched tables, no released {@link Entity} handles anywhere.
     *
     * <p>Two gates, both required: {@link RenderAppearance#hasDeathPose} is the
     * type-level "this type declares a corpse sheet" flag, but {@code SPRITE.index}
     * is a non-negative pose only for units that died through the damage resolver
     * (a cascade-killed drone keeps {@code -1}), so the per-row {@code index >= 0}
     * check is still needed — the flyweight tag does not subsume it. The cache
     * guard then covers the not-yet-loaded / empty-sheet case. The sheet itself
     * still resolves from {@code IDENTITY.type} until the unified sprite registry
     * mints handles into {@code SPRITE.sheet}. No vision gate: corpses persist
     * through fog.
     */
    private void sweepDeadSprites(RenderContext ctx, DrawList out) {
        BattleComponents c = ctx.sim.getBattleComponents();
        BattleCamera cam = ctx.camera;
        // Base cell-sprite size shared across UNITS strata; renderScale applied below.
        float unitSize = cam.cellPxSize() * BattleRenderer.UNIT_FRAC;
        float alphaMult = ctx.alphaMult;

        for (ArchetypeTable t : ctx.sim.getEntityWorld().matched(c.corpses)) {
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            int[] poseIdx = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            float[] rx = t.floats(c.RENDER_POSITION, BattleComponents.RENDER_POSITION_X).array();
            float[] ry = t.floats(c.RENDER_POSITION, BattleComponents.RENDER_POSITION_Y).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (poseIdx[r] < 0) continue;
                UnitType type = (UnitType) types[r];
                RenderAppearance app = RenderAppearance.of(type);
                if (!app.hasDeathPose) continue;
                UnitSpriteCache cache = sprites.unitDeadSprites().get(type);
                if (cache == null || cache.sheet == null || cache.frames == null
                        || cache.frames.frames.length == 0) continue;

                SpriteSheetFrames frames = cache.frames;
                int frameIdx = ((poseIdx[r] % frames.frames.length) + frames.frames.length)
                        % frames.frames.length;
                SpriteSheetFrames.Frame f = frames.frames[frameIdx];

                float scaledSize = unitSize * app.renderScale;
                float targetW, targetH;
                if (f.w >= f.h) {
                    targetW = scaledSize;
                    targetH = scaledSize * f.h / (float) f.w;
                } else {
                    targetH = scaledSize;
                    targetW = scaledSize * f.w / (float) f.h;
                }
                float cx = cam.cellToScreenX(rx[r] + 0.5f);
                float cy = cam.cellToScreenY(ry[r] + 0.5f);
                out.addSheetQuad(RenderLayer.UNITS, cache.sheet,
                        f.x, f.y, f.w, f.h,
                        cx, cy, targetW, targetH,
                        1f, 1f, 1f, alphaMult);
            }
        }
    }

    /**
     * Live infantry/civilians: facing-indexed sheet frame as a batched
     * {@code SHEET_QUAD} (the SOUTH-weapon-up pose flipped vertically via the
     * engine's {@link DrawList#addSheetQuadFlippedV} mirror). Faithful port of the
     * inline live-sprite loop + {@code renderUnitSprite}: same VIS_HIDDEN/VIS_FADING
     * gating + fade alpha, same secondary-aim sheet override, same weapon-up window,
     * same frame selection, same {@code renderScale} sizing. The per-frame sheet
     * {@code setColor}/tint-reset bookkeeping is gone — color is explicit per quad.
     *
     * <p>Claims exactly the {@link RenderAppearance.SpriteKind#SHEET} types (every
     * type that is not a turret/hub whole-sprite or a drone), the tag-driven
     * equivalent of the inline {@code !(instanceof MapTurret|DroneHubUnit|Drone)}
     * excludes. The colored-quad fallback covers a missing/unloaded sheet.
     */
    private void sweepLiveSprites(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        BattleSimulation sim = ctx.sim;
        World world = sim.world();
        float unitSize = cam.cellPxSize() * BattleRenderer.UNIT_FRAC;
        float half = unitSize / 2f;
        float alphaMult = ctx.alphaMult;
        VisionService vis = sim.getVision();

        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Entity u = sim.liveUnitAt(i);
            if (RenderAppearance.of(u.type).spriteKind != RenderAppearance.SpriteKind.SHEET) continue;
            byte uv = vis.getUnitVisibility(i);
            if (uv == VisionService.VIS_HIDDEN) continue;
            float unitAlpha = alphaMult;
            if (uv == VisionService.VIS_FADING) unitAlpha *= vis.getFadeAlpha(i);

            boolean inAim = world.hasSecondaryWeapon(u.entityId) && world.secondaryActionTimer(u.entityId) > 0f;
            UnitSpriteCache cache = sprites.unitSprites().get(u.type);
            if (inAim) {
                UnitSpriteCache aim = sprites.marineSecondaryAimSheets().get(world.secondaryWeapon(u.entityId));
                if (aim != null && aim.sheet != null && aim.frames != null
                        && aim.frames.frames.length > 0) {
                    cache = aim;
                }
            }
            if (cache == null || cache.sheet == null || cache.frames == null
                    || cache.frames.frames.length == 0) {
                Color c = u.faction == Faction.MARINE ? MARINE_COLOR
                        : u.faction == Faction.DEFENDER ? DEFENDER_COLOR : CIVILIAN_COLOR;
                float cx = cam.cellToScreenX(world.renderX(u.entityId) + 0.5f);
                float cy = cam.cellToScreenY(world.renderY(u.entityId) + 0.5f);
                emitSolidQuad(out, cx, cy, half, c, unitAlpha);
                continue;
            }
            emitLiveSprite(out, cam, sim, u, cache, unitSize, unitAlpha, inAim);
        }
    }

    /**
     * Selects the facing/weapon-up frame and emits it (vertically flipped for the
     * SOUTH-weapon-up pose). Sizing: {@code renderScale}d cell height, width by the
     * frame's aspect — matching the inline {@code renderUnitSprite}.
     */
    private void emitLiveSprite(DrawList out, BattleCamera cam, BattleSimulation sim, Entity u,
                                UnitSpriteCache cache, float unitSize, float alphaMult, boolean inAim) {
        // cooldown, this unit's cell, and the target cell all read via world by-id
        // adapters (COMBAT cooldown + POSITION cells).
        World world = sim.world();
        SpriteSheetFrames frames = cache.frames;
        float cooldown = world.cooldownTimer(u.entityId);
        boolean weaponUp = inAim || (u.type.combatant
                && cooldown > (u.attackCooldown - WEAPON_UP_TIME)
                && cooldown > 0f);
        int selfCellX = world.cellX(u.entityId);
        int selfCellY = world.cellY(u.entityId);

        int frameIdx;
        boolean flipV;
        if (u.type.frameLayout == UnitType.FrameLayout.EIGHT_WAY_NO_WEAPON_UP) {
            frameIdx = pickFrameEightWay(computeEightWayFacing(u, sim, selfCellX, selfCellY));
            flipV = false;
        } else {
            Facing facing = computeFacing(u, sim, selfCellX, selfCellY);
            frameIdx = pickFrame(facing, weaponUp);
            flipV = weaponUp && facing == Facing.SOUTH;
        }
        if (frameIdx >= frames.frames.length) frameIdx = 0;
        SpriteSheetFrames.Frame f = frames.frames[frameIdx];

        float targetH = unitSize * u.type.renderScale;
        float targetW = targetH * f.w / (float) f.h;
        float cx = cam.cellToScreenX(world.renderX(u.entityId) + 0.5f);
        float cy = cam.cellToScreenY(world.renderY(u.entityId) + 0.5f);
        if (flipV) {
            out.addSheetQuadFlippedV(RenderLayer.UNITS, cache.sheet, f.x, f.y, f.w, f.h,
                    cx, cy, targetW, targetH, 1f, 1f, 1f, alphaMult);
        } else {
            out.addSheetQuad(RenderLayer.UNITS, cache.sheet, f.x, f.y, f.w, f.h,
                    cx, cy, targetW, targetH, 1f, 1f, 1f, alphaMult);
        }
    }

    /** Centered {@code SOLID_RECT} of half-extent {@code half} — the sprite-missing quad fallback. */
    private static void emitSolidQuad(DrawList out, float cx, float cy, float half, Color c, float alpha) {
        out.addSolidRect(RenderLayer.UNITS, cx - half, cy - half, cx + half, cy + half,
                c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
    }

    /**
     * HP bars for combatants (drones excluded — they bar themselves in the DRONES
     * layer). Runs <b>last</b> so bars paint over every body in the layer — the
     * per-stratum sweep that dissolves the old per-entity decorator ordering trap.
     * Faithful port of the inline bar loop: same combatant/visibility gating + fade
     * alpha, same per-kind {@code barY} (turret/hub sit higher by their visual
     * extent), via the shared {@link HpBarDecor}. The {@code drawsHpBar} tag is the
     * combatant-and-not-drone check.
     */
    private void sweepHpBars(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        World world = ctx.sim.world();
        float cellPx = cam.cellPxSize();
        float unitSize = cellPx * BattleRenderer.UNIT_FRAC;
        float half = unitSize / 2f;
        float alphaMult = ctx.alphaMult;
        VisionService vis = ctx.sim.getVision();

        for (int i = 0, n = ctx.sim.liveUnitCount(); i < n; i++) {
            Entity u = ctx.sim.liveUnitAt(i);
            if (!RenderAppearance.of(u.type).drawsHpBar) continue;
            byte uv = vis.getUnitVisibility(i);
            if (uv == VisionService.VIS_HIDDEN) continue;
            float barAlpha = alphaMult;
            if (uv == VisionService.VIS_FADING) barAlpha *= vis.getFadeAlpha(i);

            float cx = cam.cellToScreenX(world.renderX(u.entityId) + 0.5f);
            float cy = cam.cellToScreenY(world.renderY(u.entityId) + 0.5f);
            float barY;
            if (u instanceof MapTurret) {
                barY = cy + ((MapTurret) u).kind.visualCells * cellPx / 2f + BattleRenderer.HP_BAR_GAP;
            } else if (u instanceof DroneHubUnit) {
                barY = cy + DroneHubUnit.VISUAL_CELLS * cellPx / 2f + BattleRenderer.HP_BAR_GAP;
            } else {
                barY = cy + half + BattleRenderer.HP_BAR_GAP;
            }
            HpBarDecor.emit(out, RenderLayer.UNITS, cx, barY, unitSize,
                    world.hp(u.entityId) / world.maxHp(u.entityId), barAlpha);
        }
    }

    // ---- frame selection (game-side; moved verbatim from BattleRenderer) ------

    private enum Facing { WEST, NORTH, EAST, SOUTH }

    private static Facing computeFacing(Entity u, BattleSimulation sim, int selfCellX, int selfCellY) {
        Entity target = sim != null ? sim.targetOf(u) : null;
        if (target != null) {
            World world = sim.world();
            int dx = world.cellX(target.entityId) - selfCellX;
            int dy = world.cellY(target.entityId) - selfCellY;
            if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
        }
        World world = sim != null ? sim.world() : null;
        if (world != null) {
            int[] path = world.path(u.entityId);
            int pathIdx = world.pathIdx(u.entityId);
            if (pathIdx < Paths.cellCount(path)) {
                int dx = Paths.cellX(path, pathIdx) - selfCellX;
                int dy = Paths.cellY(path, pathIdx) - selfCellY;
                if (dx != 0 || dy != 0) return facingFromDelta(dx, dy);
            }
        }
        return Facing.SOUTH;
    }

    private static Facing facingFromDelta(int dx, int dy) {
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? Facing.EAST : Facing.WEST;
        return dy > 0 ? Facing.NORTH : Facing.SOUTH;
    }

    private static int pickFrame(Facing facing, boolean weaponUp) {
        if (weaponUp) {
            switch (facing) {
                case WEST:  return 4;
                case EAST:  return 5;
                case NORTH: return 6;
                case SOUTH: return 6; // vertical mirror applied at draw time
            }
        } else {
            switch (facing) {
                case WEST:  return 0;
                case NORTH: return 1;
                case EAST:  return 2;
                case SOUTH: return 3;
            }
        }
        return 3;
    }

    private enum EightWayFacing { W, NW, N, NE, E, SE, S, SW }

    private static EightWayFacing computeEightWayFacing(Entity u, BattleSimulation sim, int selfCellX, int selfCellY) {
        Entity target = sim != null ? sim.targetOf(u) : null;
        if (target != null) {
            World world = sim.world();
            int dx = world.cellX(target.entityId) - selfCellX;
            int dy = world.cellY(target.entityId) - selfCellY;
            if (dx != 0 || dy != 0) return eightWayFromDelta(dx, dy);
        }
        World world = sim != null ? sim.world() : null;
        if (world != null) {
            int[] path = world.path(u.entityId);
            int pathIdx = world.pathIdx(u.entityId);
            if (pathIdx < Paths.cellCount(path)) {
                int dx = Paths.cellX(path, pathIdx) - selfCellX;
                int dy = Paths.cellY(path, pathIdx) - selfCellY;
                if (dx != 0 || dy != 0) return eightWayFromDelta(dx, dy);
            }
        }
        return EightWayFacing.S;
    }

    private static EightWayFacing eightWayFromDelta(int dx, int dy) {
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);
        boolean diag = Math.min(adx, ady) * 1000 >= Math.max(adx, ady) * 414;
        if (diag) {
            if (dx > 0 && dy > 0) return EightWayFacing.NE;
            if (dx > 0 && dy < 0) return EightWayFacing.SE;
            if (dx < 0 && dy > 0) return EightWayFacing.NW;
            return EightWayFacing.SW;
        }
        if (adx > ady) return dx > 0 ? EightWayFacing.E : EightWayFacing.W;
        return dy > 0 ? EightWayFacing.N : EightWayFacing.S;
    }

    private static int pickFrameEightWay(EightWayFacing f) {
        switch (f) {
            case W:  return 0;
            case NW: return 1;
            case SE: return 2;
            case S:  return 3;
            case SW: return 4;
            case NE: return 5;
            case E:  return 6;
            case N:  return 1; // no dedicated N — borrow NW
        }
        return 3;
    }
}
