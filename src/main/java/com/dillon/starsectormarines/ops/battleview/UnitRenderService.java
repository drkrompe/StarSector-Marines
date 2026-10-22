package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.StaggerService;
import com.dillon.starsectormarines.battle.appearance.LiveAppearance;
import com.dillon.starsectormarines.battle.appearance.SystemFxService;
import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.RavBruteRig;
import com.dillon.starsectormarines.battle.appearance.RavMatriarchRig;
import com.dillon.starsectormarines.battle.appearance.RavRunnerRig;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.appearance.WorkMotion;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.AnimationClip;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.air.GroundWreck;
import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import com.dillon.starsectormarines.battle.air.engine.HullPivotResolver;
import com.dillon.starsectormarines.battle.drone.DroneHub;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.MissilePodMountGeometry;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService.PodLaunch;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.sim.TurretStateService;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.turret.StructureArt;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem;
import com.dillon.starsectormarines.battle.turret.StructureFootprint;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.marine.SpecialEquipmentPresentationDef.LayerClips;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Emits the {@link RenderLayer#UNITS} layer as a stateless consumer that sweeps
 * the unit list <em>once per
 * render-task-kind</em> (footprints, then sprites, then HP bars), branching on a
 * type-flyweight {@link RenderAppearance} + its capability tags instead of an
 * {@code instanceof}/{@code combatant}/{@code deathPoseIdx} ladder. Per-stratum
 * sweeps in paint order dissolve the per-entity decorator ordering trap: "HP bars
 * on top across all kinds" is simply "the bar sweep runs last". See
 * {@code battle-render-nouns.md}.
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
 *
 * <p><b>Live-appearance Phase 2.</b> {@link #sweepLiveSprites} is now a pure
 * {@code Query} collector over {@link BattleComponents#liveSprites}, reading the
 * facing/pose frame {@code battle.appearance.FacingSystem} authors into
 * {@code SPRITE} every tick instead of deriving it per render — the epic's
 * "render is a pure collector" shape, extending the {@link #sweepDeadSprites}
 * pattern to the live side. The remaining sweeps still walk the dense roster
 * ({@code sim.liveUnitAt}); each converts to a {@code Query} column walk on its
 * own schedule as the systems-to-columns migration reaches it.
 */
public final class UnitRenderService implements RenderSystem {

    // Faction-colored quad fallback when a unit's sprite sheet is missing/unloaded.
    private static final Color MARINE_COLOR   = new Color(0x5A, 0xA0, 0xE0);
    private static final Color DEFENDER_COLOR = new Color(0xE0, 0x6A, 0x6A);
    private static final Color CIVILIAN_COLOR = new Color(0xC8, 0xC8, 0x80);
    /** Composition-wide scale relative to the original layered infantry sizing. */
    static final float LAYERED_INFANTRY_SCALE = 0.60f;
    /**
     * A lying body against the live shoulder scale. At 1.0 a body is anatomically long beside
     * its own shoulders (about 1.8 cells), but the live figure shows only head and shoulders,
     * so a fallen marine read as four times the size of a standing one. Two thirds lays a body
     * about 1.2 cells long: plainly a person on the ground, not a giant.
     */
    static final float DOWNED_BODY_SCALE = 0.65f;
    /**
     * A sleeper against the live shoulder scale. Larger than a fallen body because it lies in a
     * real-length bunk beside its neighbours: at this scale the straight lying pose spans the
     * mattress's width with its arms at its sides and lies head on the pillow.
     */
    static final float SLEEPER_BODY_SCALE = 0.9f;
    /**
     * A full pool's width in cells at render scale 1. A body lies about 1.2 cells long, and
     * the pool spreads past its torso and out from under its arms rather than hiding beneath it.
     */
    static final float BLOOD_POOL_CELLS = 1.4f;
    /** How long a pool takes to spread to its full size under a fresh body. */
    static final float BLOOD_SPREAD_SECONDS = 4f;
    /** A pool's size the moment its body falls, as a fraction of its full spread. */
    static final float BLOOD_START_FRACTION = 0.3f;

    /**
     * A pool's size as a fraction of its full spread, {@code ageSeconds} after the body fell:
     * quick at first and easing out, the way a wound drains, then still.
     */
    static float bloodSpread(float ageSeconds) {
        float t = Math.max(0f, Math.min(1f, ageSeconds / BLOOD_SPREAD_SECONDS));
        float eased = 1f - (1f - t) * (1f - t);
        return BLOOD_START_FRACTION + (1f - BLOOD_START_FRACTION) * eased;
    }

    /**
     * How much wider than its nominal cell size a body's drawn silhouette may
     * be, for {@link ViewCull}.
     *
     * <p>The widest authored body is a heavy mech: render scale 1.6, and
     * {@code LayeredMechAppearance.hullWidthCells} makes that a chassis 1.84
     * cells across, with arms, shoulders, a muzzle flash and the system halo all
     * reaching past the chassis. Four cells — two either side of the centre —
     * clears that with room, and every infantry body is a third of it.
     *
     * <p>Deliberately generous, because the two ways of being wrong are not
     * symmetric. A cull that rejects a body wrongly is silent and shows up as a
     * marine popping into existence at the screen edge; a cull that is too
     * loose costs only the bodies in a thin band outside the view. At the
     * framings this matters at, the view is dozens of cells across and two
     * cells of slack per side is a few per cent of the area.
     */
    private static final float BODY_SILHOUETTE_CELLS = 4f;

    /**
     * Slack on a structure or hull whose extent is authored, for
     * {@link ViewCull}.
     *
     * <p>These already state their size in cells — a mount's {@code
     * visualCells}, a hull's resolved visual length — so this only has to cover
     * what moves that size around: recoil displacement, a rotated hull's
     * diagonal, a wreck's pieces shifted where they lie, the durability bar
     * standing above the body.
     */
    private static final float AUTHORED_EXTENT_SLACK = 1.6f;
    /** Shared physical sizing authority for battle and shipboard room projections. */
    static float layeredMechHullWidth(float cellPx, float renderScale) {
        return cellPx * BattleRenderer.UNIT_FRAC
                * LayeredMechAppearance.hullWidthCells(renderScale);
    }

    /** Shared physical sizing authority for layered infantry room projections. */
    static float layeredInfantryShoulderWidth(float cellPx, float renderScale) {
        return cellPx * BattleRenderer.UNIT_FRAC * renderScale * LAYERED_INFANTRY_SCALE;
    }

    private final BattleSprites sprites;
    private final SystemHaloComposer halo = new SystemHaloComposer();
    /**
     * How each destroyed airframe's hull came apart, worked out on first draw
     * and kept for the battle. Keyed by identity on whichever object carries
     * that wreck's position — a {@link AirfieldService.Berth} for one burned
     * on its pad, a {@link GroundWreck} for one that came down away from a
     * berth — since either is a stable, never-replaced object for as long as
     * the wreck exists.
     */
    private final Map<Object, HullBreakup> wrecks = new IdentityHashMap<>();

    public UnitRenderService(BattleSprites sprites) {
        this.sprites = sprites;
    }

    @Override
    public RenderLayer layer() {
        return RenderLayer.UNITS;
    }

    @Override
    public void collect(RenderContext ctx, DrawList out) {
        classifyRoster(ctx);
        sweepFootprints(ctx, out);
        sweepTurretBodies(ctx, out);
        sweepHubBodies(ctx, out);
        sweepBasedAircraft(ctx, out);
        sweepDeadSprites(ctx, out);
        sweepLiveSprites(ctx, out);
        sweepDurabilityBars(ctx, out);
    }

    /**
     * One walk over the roster, sorting it into the strata that want it.
     *
     * <p>Five of this class's sweeps used to walk the whole live roster
     * themselves, each asking every unit on the field what type it was so it
     * could skip the ones that were not its business. On the canonical Conquest
     * that is five passes over four hundred bodies and two thousand identity
     * lookups to emit a few dozen footprints and a few hundred bars — and it was
     * two thirds of the whole layer's collection cost, measured by taking the
     * sweeps out one group at a time. Culling did not touch it, because the walk
     * happens whether or not anything is emitted.
     *
     * <p>So the type is resolved once, here, and each sweep is handed the ids
     * that concern it. Paint order is untouched: the sweeps still run in the
     * same order and each still emits in roster order, because this fills its
     * lists in roster order.
     *
     * <p>Grow-and-stay scratch, reused every frame — a collector allocating five
     * lists per frame is the thing this was meant to stop doing.
     */
    private void classifyRoster(RenderContext ctx) {
        footprints.reset();
        turrets.reset();
        hubs.reset();
        basedAircraft.reset();
        bars.reset();
        boolean wantsBars = ctx.hostProfile.unitDecorationsVisible();
        for (int i = 0, n = ctx.sim.liveUnitCount(); i < n; i++) {
            long u = ctx.sim.liveUnitAt(i);
            UnitType type = ctx.sim.identity().type(u);
            RenderAppearance appearance = RenderAppearance.of(type);
            if (appearance.drawsFootprint) footprints.add(u, i, type);
            if (type.isTurret()) turrets.add(u, i, type);
            else if (type.isDroneHub()) hubs.add(u, i, type);
            else if (type.isBasedAircraft()) basedAircraft.add(u, i, type);
            if (wantsBars && appearance.drawsDurabilityBar) bars.add(u, i, type);
        }
    }

    /**
     * One stratum's share of the roster: the unit ids, and the dense roster slot
     * each came from.
     *
     * <p>The slot is carried because fog keys on it — {@code getUnitVisibility}
     * takes a dense index, not an id — and recovering it later would put back
     * the per-unit lookup this pass exists to remove.
     */
    private static final class Stratum {
        private long[] ids = new long[64];
        private int[] slots = new int[64];
        private UnitType[] types = new UnitType[64];
        private int size;

        void reset() {
            size = 0;
        }

        void add(long id, int slot, UnitType type) {
            if (size == ids.length) {
                ids = Arrays.copyOf(ids, size * 2);
                slots = Arrays.copyOf(slots, size * 2);
                types = Arrays.copyOf(types, size * 2);
            }
            ids[size] = id;
            slots[size] = slot;
            types[size] = type;
            size++;
        }

        int size() {
            return size;
        }

        long id(int i) {
            return ids[i];
        }

        int slot(int i) {
            return slots[i];
        }

        UnitType type(int i) {
            return types[i];
        }
    }

    /** Structure animation clock, and the accumulated angle of each spinning art layer keyed by structure id and layer index. */
    private float structureClock;
    private final Map<Long, Float> layerAngles = new HashMap<>();
    private final Stratum footprints = new Stratum();
    private final Stratum turrets = new Stratum();
    private final Stratum hubs = new Stratum();
    private final Stratum basedAircraft = new Stratum();
    private final Stratum bars = new Stratum();

    /**
     * Whether every cell under a turret's body carries an authored ground surface (the concrete a prepared position is
     * paved with). The flat road-coloured pad exists to seat a structure on bare ground; over a drawn surface it would
     * paint a blue-grey square across the concrete instead.
     */
    private static boolean standsOnSurface(CellTopology topology, int minX, int minY, int spanX, int spanY) {
        if (topology == null) return false;
        for (int x = minX; x < minX + spanX; x++) {
            for (int y = minY; y < minY + spanY; y++) {
                if (topology.getSurfaceId(x, y) == null) return false;
            }
        }
        return true;
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
        ViewCull view = ViewCull.of(cam);
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        TurretStateService turretState = ctx.sim.turretState();
        for (int i = 0, n = footprints.size(); i < n; i++) {
            long u = footprints.id(i);
            int cx = world.cellX(u);
            int cy = world.cellY(u);
            // One cell, drawn from the cell's corner, so its centre is half a
            // cell up and right of the coordinate -- unless the body is a
            // multi-cell structure, whose pad covers its whole footprint.
            int spanX = 1;
            int spanY = 1;
            if (turretState.isTurret(u)) {
                StructureDef structure = turretState.structure(u);
                if (structure.art != null && !structure.art.groundPad) continue;
                spanX = structure.footprintCellsX;
                spanY = structure.footprintCellsY;
                cx = StructureFootprint.minX(structure, cx);
                cy = StructureFootprint.minY(structure, cy);
            }
            if (!view.visible(cx + spanX * 0.5f, cy + spanY * 0.5f, Math.max(spanX, spanY))) continue;
            ShuttleSpriteCache artBase = turretState.isTurret(u) ? structureBase(u, turretState) : null;
            if (artBase != null) {
                // Painted base: the whole footprint, upright, in place of the flat pad.
                float w = spanX * cellPx;
                float h = spanY * cellPx;
                out.addSprite(RenderLayer.UNITS, artBase.sprite,
                        cam.cellToScreenX(cx) + w * 0.5f, cam.cellToScreenY(cy) + h * 0.5f,
                        w, h, 0f, 1f, 1f, 1f, alphaMult);
                continue;
            }
            if (standsOnSurface(ctx.sim.getTopology(), cx, cy, spanX, spanY)) continue;
            float x0 = cam.cellToScreenX(cx);
            float y0 = cam.cellToScreenY(cy);
            if (spanX == 1 && spanY == 1) {
                GroundFootprint.emit(out, RenderLayer.UNITS, x0, y0, cellPx, alphaMult);
            } else {
                // Same corner convention as the one-cell pad, just wider.
                GroundFootprint.emit(out, RenderLayer.UNITS, x0, y0, spanX * cellPx, spanY * cellPx, alphaMult);
            }
        }
    }

    /**
     * Map-turret bodies: an optional recoil-displaced barrel {@code SPRITE} under
     * the base {@code SPRITE} (both whole-texture rotated). Faithful port of the
     * inline turret pass — same recoil easing/offset, same per-structure
     * base/barrel caches — minus the end-of-pass {@code setAngle(0)} reset loops
     * (the {@code SPRITE} drain resets angle after each draw). When the base sprite
     * is missing, a {@code DEFENDER_COLOR} {@code SOLID_RECT} fallback stands in
     * (and the barrel is skipped), exactly as the inline fallback did.
     */
    private void sweepTurretBodies(RenderContext ctx, DrawList out) {
        BattleCamera cam = ctx.camera;
        World world = ctx.sim.world();
        TurretStateService turretState = ctx.sim.turretState();
        ViewCull view = ViewCull.of(cam);
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        float dt = advanceStructureClock(ctx);
        float clock = structureClock;
        emitRemains(ctx, out, view, cellPx, alphaMult);
        for (int i = 0, n = turrets.size(); i < n; i++) {
            long id = turrets.id(i);
            StructureDef structure = turretState.structure(id);
            if (!view.visible(world.renderX(id), world.renderY(id),
                    bodyExtentCells(structure) * AUTHORED_EXTENT_SLACK)) continue;
            float facingDegrees = turretState.facingDegrees(id);
            float cx = cam.cellToScreenX(world.renderX(id));
            float cy = cam.cellToScreenY(world.renderY(id));

            ShuttleSpriteCache base = sprites.turretSprites().get(structure.id);
            if (base == null) {
                // A structure whose body is all painted art has no mount sprite on purpose.
                if (structure.art != null) {
                    emitArtLayers(ctx, out, structure, id, cx, cy, cellPx, alphaMult, clock, dt);
                    continue;
                }
                float half = cellPx * BattleRenderer.UNIT_FRAC / 2f;
                emitSolidQuad(out, cx, cy, half, DEFENDER_COLOR, alphaMult);
                continue;
            }

            TurretLayerPose pose = TurretLayerPose.resolve(
                    cx, cy, facingDegrees, structure.mount.visualCells, cellPx,
                    turretState.recoilTimer(id), BattleRenderer.RECOIL_DURATION,
                    BattleRenderer.RECOIL_DISTANCE_FRAC);

            ShuttleSpriteCache barrel = sprites.turretRecoilSprites().get(structure.id);
            if (barrel != null) {
                emitWholeSprite(out, barrel, pose.facingDegrees(), pose.spriteHeightPx(),
                        pose.recoilCenterX(), pose.recoilCenterY(), alphaMult);
            }
            emitWholeSprite(out, base, pose.facingDegrees(), pose.spriteHeightPx(),
                    pose.baseCenterX(), pose.baseCenterY(), alphaMult);
            if (structure.art != null) {
                emitArtLayers(ctx, out, structure, id, cx, cy, cellPx, alphaMult, clock, dt);
            }
        }
    }

    /** Longest extent, in cells, that a structure body can be drawn across: its mount sprite, its art layers and its footprint. */
    private static float bodyExtentCells(StructureDef structure) {
        float extent = Math.max(structure.mount.visualCells,
                Math.max(structure.footprintCellsX, structure.footprintCellsY));
        if (structure.art != null) {
            for (StructureArt.Layer layer : structure.art.layers()) extent = Math.max(extent, layer.cells());
        }
        return extent;
    }

    /** The painted base of a structure that has one and whose sprite loaded, else null (the flat pad is drawn). */
    private ShuttleSpriteCache structureBase(long id, TurretStateService turretState) {
        StructureDef structure = turretState.structure(id);
        if (structure.art == null || structure.art.basePath == null) return null;
        return sprites.spriteByPath(structure.art.basePath);
    }

    /**
     * Advances the structure animation clock: the host's absolute clock when it supplies one (so repeated passes
     * agree), otherwise real time accumulated here. Returns the seconds since the previous pass, bounded so a stalled
     * frame does not spin a layer through a lap.
     */
    private float advanceStructureClock(RenderContext ctx) {
        float now = Float.isFinite(ctx.animationTimeSeconds)
                ? ctx.animationTimeSeconds
                : structureClock + Math.max(0f, ctx.realDt);
        float dt = Math.max(0f, Math.min(0.25f, now - structureClock));
        structureClock = now;
        return dt;
    }

    /**
     * Overlay layers of a structure's art, upright and centre-pivoted on the structure. A spinning layer's angle is
     * accumulated per layer rather than read off the clock, so a dome layer that stops while its dome is down resumes
     * from where it stopped. A dome layer is tinted by the side holding the dome, dims to a smoulder while the dome
     * is overloaded and flares white when it takes a round.
     */
    private void emitArtLayers(RenderContext ctx, DrawList out, StructureDef structure, long id,
                               float cx, float cy, float cellPx, float alphaMult, float clock, float dt) {
        List<StructureArt.Layer> layers = structure.art.layers();
        ShieldDomeService.DomeView dome = null;
        for (StructureArt.Layer layer : layers) {
            if (layer.dome()) {
                dome = findDome(ctx, id);
                break;
            }
        }
        int variant = structure.art.variantFor(id);
        float durability = durability(ctx, structure, id);
        boolean wounded = structure.art.woundedBelow > 0f && structure.art.woundedAt(durability);
        for (int i = 0; i < layers.size(); i++) {
            StructureArt.Layer layer = layers.get(i);
            ShuttleSpriteCache sprite = sprites.spriteByPath(layer.spritePath(variant, wounded));
            if (sprite == null) continue;
            float spin = 1f;
            float bright = 1f;
            float r = 1f;
            float g = 1f;
            float b = 1f;
            if (layer.dome() && dome != null) {
                float[] tint = ShieldDomeRenderSystem.tintFor(dome.holder());
                float flash = dome.hitFlash();
                r = tint[0] + (1f - tint[0]) * flash;
                g = tint[1] + (1f - tint[1]) * flash;
                b = tint[2] + (1f - tint[2]) * flash;
                if (dome.overloaded()) {
                    spin = 0f;
                    bright = 0.22f;
                }
            }
            if (layer.pulseDepth() > 0f && layer.pulseHz() > 0f) {
                double wave = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * layer.pulseHz() * clock);
                bright *= 1f - layer.pulseDepth() * (float) wave;
            }
            long key = id * 16L + i;
            float angle = layerAngles.getOrDefault(key, 0f) + layer.spinDegPerSec() * spin * dt;
            angle %= 360f;
            layerAngles.put(key, angle);
            float size = layer.cells() * cellPx;
            if (layer.fireFlash() > 0f) {
                // The structure's own fire: snap to full brightness and swell, settling as the recoil clock runs.
                float since = ctx.sim.turretState().recoilTimer(id);
                float flash = layer.fireFlash() * Math.max(0f, 1f - since / StructureArt.FIRE_FLASH_SECONDS);
                bright = bright + (1f - bright) * Math.min(1f, flash * 2f);
                size *= 1f + StructureArt.FIRE_FLASH_SWELL * flash;
            }
            out.addSprite(RenderLayer.UNITS, sprite.sprite, cx, cy, size * sprite.aspect, size, angle,
                    r * bright, g * bright, b * bright, alphaMult);
        }
        if (wounded && structure.art.bleed != null) {
            emitBleed(out, structure.art, id, variant, durability, cx, cy, cellPx, alphaMult, clock);
        }
    }

    /**
     * The ooze a wounded body bleeds from its open wounds (`StructureArt.Bleed`): one looping frame per open wound,
     * pinned to the wound on the painting, upright, the run's direction chosen from the sheet rather than turned.
     */
    private void emitBleed(DrawList out, StructureArt art, long id, int variant, float durability,
                           float cx, float cy, float cellPx, float alphaMult, float clock) {
        StructureArt.Bleed bleed = art.bleed;
        float[][] wounds = bleed.woundsOf(variant);
        int open = StructureArt.Bleed.openWounds(wounds.length, durability, art.woundedBelow);
        if (open == 0) return;
        float edge = 0f;
        for (StructureArt.Layer layer : art.layers()) edge = Math.max(edge, layer.cells());
        edge *= cellPx;
        for (int w = 0; w < open; w++) {
            float[] wound = wounds[w];
            StructureArt.Bleed.Family family = bleed.familyOf(wound);
            ShuttleSpriteCache sheet = sprites.spriteByPath(family.sheet());
            if (sheet == null) continue;
            int sheetW = sheet.pxW > 0 ? sheet.pxW : (int) sheet.sprite.getWidth();
            int framePx = sheetW / family.frames();
            if (framePx <= 0) continue;
            float size = family.cells() * cellPx;
            long mix = mix(id * 31L + w);
            int kind = (int) Math.floorMod(mix, (long) family.kinds());
            float phase = ((mix >>> 16) & 0xFFFF) / 65536f;
            int frame = family.frame(clock, phase);
            int row = family.row(kind, wound[2]);
            // The anchor is in image space, v down; the screen's y runs up.
            out.addSheetQuad(RenderLayer.UNITS, sheet.sprite, frame * framePx, row * framePx, framePx, framePx,
                    cx + wound[0] * edge, cy - wound[1] * edge, size, size, 1f, 1f, 1f, alphaMult);
        }
    }

    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The share of a structure's structure and armour left, 0 to 1. */
    private static float durability(RenderContext ctx, StructureDef structure, long id) {
        float full = structure.maxStructure + structure.armorCapacity;
        if (full <= 0f) return 1f;
        World world = ctx.sim.world();
        return Math.max(0f, Math.min(1f, (world.hp(id) + world.armor(id)) / full));
    }

    /**
     * What dead grown structures left on the ground (a caved-in bore, a burst colony), under every living body.
     * Upright and centre-pivoted on where the structure stood, in the painting it wore.
     */
    private void emitRemains(RenderContext ctx, DrawList out, ViewCull view, float cellPx, float alphaMult) {
        List<TurretDemolitionSystem.Remains> remains = ctx.sim.getTurretDemolitionSystem().remains();
        BattleCamera cam = ctx.camera;
        for (int i = 0, n = remains.size(); i < n; i++) {
            TurretDemolitionSystem.Remains r = remains.get(i);
            StructureArt art = r.structure().art;
            if (!view.visible(r.x(), r.y(), art.remainsCells * AUTHORED_EXTENT_SLACK)) continue;
            ShuttleSpriteCache sprite = sprites.spriteByPath(art.remainsPath(r.variant()));
            if (sprite == null) continue;
            float size = art.remainsCells * cellPx;
            out.addSprite(RenderLayer.UNITS, sprite.sprite, cam.cellToScreenX(r.x()), cam.cellToScreenY(r.y()),
                    size * sprite.aspect, size, 0f, 1f, 1f, 1f, alphaMult);
        }
    }

    private static ShieldDomeService.DomeView findDome(RenderContext ctx, long structureId) {
        for (ShieldDomeService.DomeView dome : ctx.sim.shieldDomes().domes()) {
            if (dome.entityId() == structureId) return dome;
        }
        return null;
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
        ViewCull view = ViewCull.of(cam);
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        for (int i = 0, n = hubs.size(); i < n; i++) {
            long u = hubs.id(i);
            if (!view.visible(world.renderX(u), world.renderY(u),
                    DroneHub.VISUAL_CELLS * AUTHORED_EXTENT_SLACK)) continue;
            float cx = cam.cellToScreenX(world.renderX(u));
            float cy = cam.cellToScreenY(world.renderY(u));
            emitWholeSprite(out, hub, 0f, DroneHub.VISUAL_CELLS * cellPx,
                    cx, cy, alphaMult);
        }
    }

    /**
     * Airframes standing on their hardstands: one whole-hull sprite each, at
     * the berth's facing.
     *
     * <p>Drawn here, with the units, rather than beside the scenery hulls in
     * {@link ParkedAircraftRenderSystem}. The two look identical and are not the
     * same thing: a scenery hull on a civilian berth is a prop, while this is a
     * live unit that fog gates, that takes fire, and that carries a durability
     * bar. Drawing it in the unit pass is what keeps those for free.
     *
     * <p>Hull length and pivot come from the same resolvers the scenery pass
     * and the flying pass use, so one aircraft looks like itself parked, based,
     * and in the air.
     *
     * <p>A berth whose aircraft burned where it stood keeps drawing that hull,
     * charred and in pieces. It is the same sprite deliberately: what is left
     * on the concrete is the aircraft, and a reader recognises which one it was
     * and that it is not going anywhere. The unit is gone by then — dead,
     * released, and never coming back — so the wreck is drawn off the berth,
     * which is the thing on this field that has identity and outlives what
     * stands on it.
     *
     * <p>A craft killed under its own power away from any berth — taxiing,
     * holding short, mid-roll — has no berth to be drawn off, so its wreck is
     * a {@link GroundWreck} instead, carrying its own position and bearing.
     * Same charred hull, same tear, drawn wherever it actually stopped.
     */
    private void sweepBasedAircraft(RenderContext ctx, DrawList out) {
        AirfieldService airfield = ctx.sim.getAirfieldService();
        if (airfield.berths().isEmpty() && airfield.groundWrecks().isEmpty()) return;
        BattleCamera cam = ctx.camera;
        ViewCull view = ViewCull.of(cam);
        float cellPx = cam.cellPxSize();
        float alphaMult = ctx.alphaMult;
        World world = ctx.sim.world();
        for (int i = 0, n = basedAircraft.size(); i < n; i++) {
            long u = basedAircraft.id(i);
            AirfieldService.Berth berth = airfield.berthOf(u);
            if (berth == null) continue;
            if (!view.visible(world.renderX(u), world.renderY(u), hullExtentCells(berth.airframe)))
                continue;
            emitHull(out, cam, berth.airframe, berth.facingDegrees, world.renderX(u), world.renderY(u),
                    cellPx, 1f, 1f, 1f, alphaMult);
        }
        for (AirfieldService.Berth berth : airfield.berths()) {
            if (!berth.wreckOnPad) continue;
            if (!view.visible(berth.centerX + 0.5f, berth.centerY + 0.5f,
                    hullExtentCells(berth.airframe))) continue;
            emitWreck(out, cam, berth, berth.centerX + 0.5f, berth.centerY + 0.5f, berth.facingDegrees,
                    berth.airframe, berth.centerX, berth.centerY, cellPx, alphaMult);
        }
        for (GroundWreck wreck : airfield.groundWrecks()) {
            if (wreck.crashImpact != null) {
                HullWreckRenderer.emit(out, cam, sprites.airframeSprite(wreck.airframe), wreck.airframe,
                        wreck.crashImpact, ctx.sim.getSimTickIndex() * BattleSimulation.TICK_DT, alphaMult);
                continue;
            }
            if (!view.visible(wreck.x, wreck.y, hullExtentCells(wreck.airframe))) continue;
            emitWreck(out, cam, wreck, wreck.x, wreck.y, wreck.facingDegrees,
                    wreck.airframe, wreck.cellX(), wreck.cellY(), cellPx, alphaMult);
        }
    }

    /**
     * Draws one wreck: the hull torn into three pieces that have shifted where
     * they lie, every one of them charred.
     *
     * <p>The tear is {@link HullBreakup}'s and the pieces are drawn as source
     * sub-rectangles of the aircraft's own sprite, so this needs no wreck art
     * and no runtime image editing — neither of which is available against a
     * texture the game owns. A cache that never recorded the sprite's pixel
     * size cannot be addressed that way, so it falls back to the whole charred
     * hull rather than drawing nothing.
     *
     * <p>{@code wreckKey} identifies the wreck for {@link #wreckFor} — the
     * {@link AirfieldService.Berth} or {@link GroundWreck} carrying this
     * position, which does not move: the wreck at a given place is torn the
     * same way on every frame of the battle and again in a replay of it, and
     * two wrecks on one field are torn differently.
     */
    private void emitWreck(DrawList out, BattleCamera cam, Object wreckKey,
                           float padCellX, float padCellY, float facingDegrees, Airframe airframe,
                           int seedX, int seedY, float cellPx, float alphaMult) {
        ShuttleSpriteCache cache = sprites.airframeSprite(airframe);
        if (cache == null || cache.sprite == null) return;
        if (cache.pxW <= 0 || cache.pxH <= 0) {
            emitHull(out, cam, airframe, facingDegrees, padCellX, padCellY, cellPx,
                    BURNT_HULL_R, BURNT_HULL_G, BURNT_HULL_B, alphaMult);
            return;
        }

        String hullId = airframe.renderHullId();
        // Ground scale, matching the intact hull emitHull draws: the wreck is
        // that same hull's own pieces, and a wreck a third smaller than the
        // aircraft that stood there a second earlier would read as a second,
        // smaller object rather than the same one broken.
        float alongPx = HullFootprintResolver.visualLengthCells(hullId) * cellPx
                * AirAppearance.GROUND_SCALE;
        float acrossPx = alongPx * cache.aspect;
        float[] pivot = HullPivotResolver.pivotOffset(hullId);
        float pvx = pivot[0] * AirAppearance.GROUND_SCALE;
        float pvy = pivot[1] * AirAppearance.GROUND_SCALE;
        float rad = (float) Math.toRadians(facingDegrees);
        float faceCos = (float) Math.cos(rad);
        float faceSin = (float) Math.sin(rad);
        float baseX = cam.cellToScreenX(padCellX + pvx * faceCos - pvy * faceSin);
        float baseY = cam.cellToScreenY(padCellY + pvx * faceSin + pvy * faceCos);

        HullBreakup breakup = wreckFor(wreckKey, seedX, seedY, hullId);
        for (HullBreakup.Piece piece : breakup.pieces()) {
            float spinRad = (float) Math.toRadians(piece.spinDegrees());
            float spinCos = (float) Math.cos(spinRad);
            float spinSin = (float) Math.sin(spinRad);
            // The piece turns about its own centre, then slides; the whole
            // wreck is swung to the pad's bearing afterwards, so a hull parked
            // facing any direction comes apart the same way relative to itself.
            float centreX = (piece.centreAcross() - 0.5f) * acrossPx;
            float centreY = (0.5f - piece.centreAlong()) * alongPx;
            float slideX = piece.slideAcross() * alongPx;
            float slideY = -piece.slideAlong() * alongPx;
            for (HullBreakup.Run run : piece.runs()) {
                emitWreckRun(out, cache, run, piece.spinDegrees(), acrossPx, alongPx,
                        centreX, centreY, slideX, slideY, spinCos, spinSin,
                        baseX, baseY, faceCos, faceSin, facingDegrees, alphaMult);
            }
        }
    }

    /** One run of the tear's lattice: a source strip of the hull sprite, placed where its piece now lies. */
    private static void emitWreckRun(DrawList out, ShuttleSpriteCache cache,
                                     HullBreakup.Run run, float spinDegrees,
                                     float acrossPx, float alongPx,
                                     float centreX, float centreY, float slideX, float slideY,
                                     float spinCos, float spinSin,
                                     float baseX, float baseY, float faceCos, float faceSin,
                                     float facing, float alphaMult) {
        int srcX = Math.round(run.firstCol() * cache.pxW / (float) HullBreakup.GRID);
        int srcRight = Math.round((run.firstCol() + run.colCount()) * cache.pxW / (float) HullBreakup.GRID);
        int srcY = Math.round(run.row() * cache.pxH / (float) HullBreakup.GRID);
        int srcBottom = Math.round((run.row() + 1) * cache.pxH / (float) HullBreakup.GRID);
        if (srcRight <= srcX || srcBottom <= srcY) return;

        // Destination extents come from the rounded source rect, not from the
        // ideal lattice, so a sprite whose pixels do not divide evenly by the
        // grid is still drawn at its own scale rather than stretched to fit.
        float u0 = srcX / (float) cache.pxW;
        float u1 = srcRight / (float) cache.pxW;
        float v0 = srcY / (float) cache.pxH;
        float v1 = srcBottom / (float) cache.pxH;
        float localX = ((u0 + u1) * 0.5f - 0.5f) * acrossPx;
        float localY = (0.5f - (v0 + v1) * 0.5f) * alongPx;

        // Turn about the piece's own centre, shift the piece, then swing the
        // whole wreck round to the pad's bearing.
        float aboutX = localX - centreX;
        float aboutY = localY - centreY;
        float spunX = aboutX * spinCos - aboutY * spinSin + centreX + slideX;
        float spunY = aboutX * spinSin + aboutY * spinCos + centreY + slideY;
        float screenX = baseX + spunX * faceCos - spunY * faceSin;
        float screenY = baseY + spunX * faceSin + spunY * faceCos;

        float stripAcross = (u1 - u0) * acrossPx;
        float stripAlong = (v1 - v0) * alongPx;
        out.addSheetQuad(RenderLayer.UNITS, cache.sprite, srcX, srcY, srcRight - srcX, srcBottom - srcY,
                screenX, screenY,
                stripAcross + bleed(stripAcross), stripAlong + bleed(stripAlong),
                facing + spinDegrees,
                BURNT_HULL_R, BURNT_HULL_G, BURNT_HULL_B, alphaMult);
    }

    /**
     * How far past its own cell each drawn strip reaches, in screen pixels.
     *
     * <p>Adjacent strips of one piece are meant to be continuous metal, but
     * their corners are computed independently and land a hair apart, which
     * under rotation shows as a hairline of background through the middle of a
     * panel. Stated in pixels because that is what the fault is: a seam is
     * about a pixel wide whether the wreck is drawn at twenty pixels or two
     * hundred, so a percentage closes it at one zoom and not the other.
     */
    private static final float WRECK_SEAM_BLEED_PX = 0.75f;

    /**
     * The most of its own size a strip may add, whatever
     * {@link #WRECK_SEAM_BLEED_PX} asks for.
     *
     * <p>A hull drawn small enough puts the whole tear inside a couple of
     * pixels, and there a fixed pixel of bleed is not a hairline fix — it is
     * several times the strip. Left unbounded it tripled every piece and fused
     * the tears shut, so a review frame of a burnt airfield showed three dark
     * blobs where three broken aircraft should have been. Whichever bound bites
     * is the right one: at a readable zoom the pixel closes the seam, and when
     * the wreck is a smudge on the map there was never a seam to see.
     */
    private static final float WRECK_SEAM_BLEED_LIMIT = 0.08f;

    /** Total growth for a strip of {@code extent} screen pixels — half of it at each end. */
    private static float bleed(float extent) {
        return 2f * Math.min(WRECK_SEAM_BLEED_PX, extent * WRECK_SEAM_BLEED_LIMIT);
    }

    /**
     * The tear for one wreck, worked out once and kept.
     *
     * <p>Small enough a map to walk rather than index: a field has a handful of
     * wrecks on it at most.
     */
    private HullBreakup wreckFor(Object wreckKey, int seedX, int seedY, String hullId) {
        HullBreakup cached = wrecks.get(wreckKey);
        if (cached != null) return cached;
        // Keyed on the hull's own name rather than an enum position, so
        // reordering a list of aircraft does not silently re-tear every wreck
        // on every map. String.hashCode is specified, so a replay tears the
        // same way.
        HullBreakup torn = HullBreakup.of(((long) seedX << 20) ^ seedY ^ hullId.hashCode());
        wrecks.put(wreckKey, torn);
        return torn;
    }

    /**
     * Charred-hull tint, multiplied over the aircraft's own sprite.
     *
     * <p>Skewed warm rather than evenly dark. A flat grey multiply reads as the
     * aircraft standing in shadow, which is the wrong thing entirely; leaving
     * more of the red channel than the blue reads as scorched metal. Dark
     * enough to be unmistakable beside a live airframe, light enough that the
     * panel lines survive — taken to about a fifth the hull turns into a
     * silhouette, and a hole in the apron is not a wreck.
     */
    private static final float BURNT_HULL_R = 0.32f;
    private static final float BURNT_HULL_G = 0.25f;
    private static final float BURNT_HULL_B = 0.20f;

    /**
     * Draws one hull centred on {@code (centerCellX, centerCellY)} at
     * {@code facingDegrees}, tinted by {@code (r, g, b)}.
     *
     * <p>Shared by the live airframe and the wreck so the two can never drift
     * apart in size, pivot or bearing: a hulk that sat a foot off where the
     * aircraft had been standing would read as a second object. Taking the
     * airframe and facing as plain values rather than a berth is what lets a
     * {@link GroundWreck} — which has no berth under it — draw through the
     * same code as a parked or pad-wrecked one.
     *
     * <p>Drawn at {@link AirAppearance#GROUND_SCALE} — the same size an air
     * entity draws at {@code altitudeT == 0} — so a launch or a recovery hands
     * off between this hull and {@code ShuttleRenderSystem}'s without a pop.
     * The pivot offset is scaled the same way {@code ShuttleRenderSystem}
     * scales its own: it is a vector in the hull's own drawn frame, so an
     * unscaled pivot on a scaled hull anchors the sprite off its pad.
     */
    /**
     * A parked hull's drawn span in cells, for {@link ViewCull}.
     *
     * <p>The long axis, from the same resolver {@link #emitHull} sizes with, at
     * the same ground scale, and taken as the extent on both axes because the
     * hull turns and a facing is not known to be cheap to reason about here. A
     * pivot offset moves the drawn centre off the unit's position by a fraction
     * of that span, which the slack covers.
     */
    private static float hullExtentCells(Airframe airframe) {
        return HullFootprintResolver.visualLengthCells(airframe.renderHullId())
                * AirAppearance.GROUND_SCALE * AUTHORED_EXTENT_SLACK;
    }

    private void emitHull(DrawList out, BattleCamera cam, Airframe airframe, float facingDegrees,
                          float centerCellX, float centerCellY, float cellPx,
                          float r, float g, float b, float alphaMult) {
        ShuttleSpriteCache cache = sprites.airframeSprite(airframe);
        if (cache == null || cache.sprite == null) return;
        float hullLenCells = HullFootprintResolver.visualLengthCells(airframe.renderHullId());
        float[] pivot = HullPivotResolver.pivotOffset(airframe.renderHullId());
        float rad = (float) Math.toRadians(facingDegrees);
        float c = (float) Math.cos(rad);
        float sn = (float) Math.sin(rad);
        float pvx = pivot[0] * AirAppearance.GROUND_SCALE;
        float pvy = pivot[1] * AirAppearance.GROUND_SCALE;
        float cx = cam.cellToScreenX(centerCellX + pvx * c - pvy * sn);
        float cy = cam.cellToScreenY(centerCellY + pvx * sn + pvy * c);
        emitWholeSprite(out, cache, facingDegrees,
                hullLenCells * cellPx * AirAppearance.GROUND_SCALE,
                cx, cy, r, g, b, alphaMult);
    }

    /** Untinted {@link #emitWholeSprite} — a body drawn in its own colours. */
    private static void emitWholeSprite(DrawList out, ShuttleSpriteCache cache, float facingDegrees,
                                        float spriteHeightPx, float cx, float cy, float alphaMult) {
        emitWholeSprite(out, cache, facingDegrees, spriteHeightPx, cx, cy, 1f, 1f, 1f, alphaMult);
    }

    /**
     * Emits one whole-texture rotated body sprite, sized {@code spriteHeightPx} tall
     * (× the sprite's natural aspect wide). Mirrors
     * {@code ShuttleRenderSystem.emitTurretLayer} — the {@code SPRITE} drain owns
     * size/angle/alpha/blend/color and resets angle afterward.
     */
    private static void emitWholeSprite(DrawList out, ShuttleSpriteCache cache, float facingDegrees,
                                        float spriteHeightPx, float cx, float cy,
                                        float r, float g, float b, float alphaMult) {
        float pxW = spriteHeightPx * cache.aspect;
        out.addSprite(RenderLayer.UNITS, cache.sprite, cx, cy, pxW, spriteHeightPx, facingDegrees,
                r, g, b, alphaMult);
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
     * over the matched tables, no released {@code Entity} handles anywhere.
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
    /** Whether a corpse lies as a layered body, and so has a pool under it. */
    private boolean bleeds(int bodyFamily, int poseIndex) {
        if (bodyFamily < 0) return false;
        LayeredUnitAssets outfit = sprites.layeredUnitSprites().get(LayeredArmorFamily.fromOrdinal(bodyFamily));
        return outfit != null && outfit.downed(poseIndex) != null;
    }

    /** Heaves a second a carrier hauls its load with, and how far each heave jolts and rocks the body. */
    private static final float DRAG_HEAVE_HZ = 1.8f;
    private static final float DRAG_JOLT_CELLS = 0.05f;
    private static final float DRAG_ROCK_DEGREES = 7f;

    /**
     * A body being dragged off by a Runner, alive or dead: lying, its head toward the carrier
     * along the line it is hauled on, jolting forward and rocking with each heave. The jitter is
     * keyed to the sim tick, so a review frame stays a function of the battle.
     */
    private static void emitDragged(RenderContext ctx, DrawList out, LayeredSpriteCache lying,
                                    long body, long carrier, float x, float y, float scale, float alpha) {
        BattleCamera cam = ctx.camera;
        float dx = 0f, dy = 1f;
        if (ctx.sim.world().hasPosition(carrier)) {
            dx = ctx.sim.world().x(carrier) - x;
            dy = ctx.sim.world().y(carrier) - y;
            float d = (float) Math.hypot(dx, dy);
            if (d > 1e-4f) {
                dx /= d;
                dy /= d;
            } else {
                dx = 0f;
                dy = 1f;
            }
        }
        double phase = 2 * Math.PI * (ctx.sim.getSimTickIndex() * BattleSimulation.TICK_DT * DRAG_HEAVE_HZ
                + (body & 15L) / 16.0);
        float heave = (float) Math.max(0.0, Math.sin(phase));
        float jolt = DRAG_JOLT_CELLS * heave * heave;
        float angle = (float) Math.toDegrees(Math.atan2(-dx, dy))
                + DRAG_ROCK_DEGREES * (float) Math.sin(phase + 0.6);
        out.addSprite(RenderLayer.UNITS, lying.sprite,
                cam.cellToScreenX(x + dx * jolt), cam.cellToScreenY(y + dy * jolt),
                lying.pxWidth * scale, lying.pxHeight * scale, angle, 1f, 1f, 1f, alpha);
    }

    private void sweepDeadSprites(RenderContext ctx, DrawList out) {
        BattleComponents c = ctx.sim.getBattleComponents();
        BattleCamera cam = ctx.camera;
        ViewCull view = ViewCull.of(cam);
        float cullMinX = view.cullMinX(BODY_SILHOUETTE_CELLS);
        float cullMaxX = view.cullMaxX(BODY_SILHOUETTE_CELLS);
        float cullMinY = view.cullMinY(BODY_SILHOUETTE_CELLS);
        float cullMaxY = view.cullMaxY(BODY_SILHOUETTE_CELLS);
        // Base cell-sprite size shared across UNITS strata; renderScale applied below.
        float unitSize = cam.cellPxSize() * BattleRenderer.UNIT_FRAC;
        float alphaMult = ctx.alphaMult;

        // Pools first, every one of them, so a pool never paints over a neighbouring body.
        List<LayeredSpriteCache> pools = sprites.bloodPools();
        if (!pools.isEmpty()) {
            int now = ctx.sim.getSimTickIndex();
            for (ArchetypeTable t : ctx.sim.getEntityWorld().matched(c.corpses)) {
                Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
                int[] poseIdx = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
                float[] rx = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
                float[] ry = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();
                float[] facing = t.floats(c.CORPSE, BattleComponents.CORPSE_FACING_DEGREES).array();
                int[] family = t.ints(c.CORPSE, BattleComponents.CORPSE_BODY_FAMILY).array();
                int[] deathTick = t.ints(c.CORPSE, BattleComponents.CORPSE_DEATH_TICK).array();
                for (int r = 0, n = t.rowCount(); r < n; r++) {
                    if (poseIdx[r] < 0 || !bleeds(family[r], poseIdx[r])) continue;
                    if (rx[r] < cullMinX || rx[r] > cullMaxX
                            || ry[r] < cullMinY || ry[r] > cullMaxY) continue;
                    LayeredSpriteCache pool = pools.get(Math.floorMod(poseIdx[r], pools.size()));
                    float size = cam.cellPxSize() * BLOOD_POOL_CELLS * ((UnitType) types[r]).renderScale
                            * bloodSpread((now - deathTick[r]) * BattleSimulation.TICK_DT);
                    out.addSprite(RenderLayer.UNITS, pool.sprite,
                            cam.cellToScreenX(rx[r]), cam.cellToScreenY(ry[r]),
                            size, size * pool.pxHeight / pool.pxWidth, facing[r],
                            1f, 1f, 1f, alphaMult);
                }
            }
        }

        for (ArchetypeTable t : ctx.sim.getEntityWorld().matched(c.corpses)) {
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            int[] poseIdx = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            float[] rx = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
            float[] ry = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();
            float[] corpseFacing = t.floats(c.CORPSE, BattleComponents.CORPSE_FACING_DEGREES).array();
            int[] corpseFamily = t.ints(c.CORPSE, BattleComponents.CORPSE_BODY_FAMILY).array();
            RavRunnerAssets ravArt = sprites.ravRunnerSprites();
            RavBruteAssets deadBruteArt = sprites.ravBruteSprites();
            Object[] ravRigs = (ravArt != null || deadBruteArt != null) && t.has(c.RAV_RIG)
                    ? t.objects(c.RAV_RIG, BattleComponents.RAV_RIG_STATE).array() : null;
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (poseIdx[r] < 0) continue;
                if (rx[r] < cullMinX || rx[r] > cullMaxX
                        || ry[r] < cullMinY || ry[r] > cullMaxY) continue;
                UnitType type = (UnitType) types[r];
                if (deadBruteArt != null && ravRigs != null && ravRigs[r] instanceof RavBruteRig deadBrute
                        && deadBrute.dead()) {
                    // A dead Brute has burst as a Runner does, over the same ichor.
                    RavBruteComposer.emitDead(out, deadBruteArt, ravArt != null ? ravArt.splat : null,
                            ravArt != null ? ravArt.drop : null, deadBrute,
                            cam.cellToScreenX(deadBrute.bodyX()), cam.cellToScreenY(deadBrute.bodyY()),
                            cam.cellPxSize(), alphaMult);
                    continue;
                }
                RavRunnerRig ravRig = ravArt != null && ravRigs != null && ravRigs[r] instanceof RavRunnerRig dead
                        ? dead : null;
                if (ravRig != null && ravRig.dead()) {
                    // A dead Runner has burst: its pieces where they were flung, over its ichor.
                    RavRunnerComposer.emit(out, ravArt, ravRig,
                            cam.cellToScreenX(ravRig.bodyX()), cam.cellToScreenY(ravRig.bodyY()),
                            cam.cellPxSize() * type.renderScale, 1f, alphaMult);
                    continue;
                }
                // A body that wore layered art lies as itself: its outfit's own pose,
                // at the live shoulder scale, turned to the facing it fell with.
                LayeredUnitAssets outfit = corpseFamily[r] >= 0
                        ? sprites.layeredUnitSprites().get(
                                LayeredArmorFamily.fromOrdinal(corpseFamily[r])) : null;
                LayeredSpriteCache lying = outfit != null ? outfit.downed(poseIdx[r]) : null;
                long carrier = lying != null ? ctx.sim.swarmHarvest().carrierOf(t.entityAt(r)) : 0L;
                if (carrier != 0L) {
                    float shoulderPx = layeredInfantryShoulderWidth(cam.cellPxSize(), type.renderScale);
                    float scale = shoulderPx * DOWNED_BODY_SCALE / LayeredUnitComposer.SOURCE_SHOULDER_PX;
                    emitDragged(ctx, out, lying, t.entityAt(r), carrier, rx[r], ry[r], scale, alphaMult);
                    continue;
                }
                if (lying != null) {
                    float shoulderPx = layeredInfantryShoulderWidth(cam.cellPxSize(), type.renderScale);
                    float scale = shoulderPx * DOWNED_BODY_SCALE / LayeredUnitComposer.SOURCE_SHOULDER_PX;
                    out.addSprite(RenderLayer.UNITS, lying.sprite,
                            cam.cellToScreenX(rx[r]), cam.cellToScreenY(ry[r]),
                            lying.pxWidth * scale, lying.pxHeight * scale, corpseFacing[r],
                            1f, 1f, 1f, alphaMult);
                    continue;
                }
                RenderAppearance app = RenderAppearance.of(type);
                if (!app.hasDeathPose) continue;
                UnitSpriteCache cache = sprites.unitDeadSprites().get(type);
                if (cache == null || cache.sheet == null || cache.frames == null
                        || cache.frames.frames.length == 0) continue;

                SpriteSheetFrames frames = cache.frames;
                int frameIdx = ((poseIdx[r] % frames.frames.length) + frames.frames.length)
                        % frames.frames.length;
                SpriteSheetFrames.Frame f = frames.frames[frameIdx];

                float scaledSize = unitSize * ctx.sim.getRoster().renderScale(t.entityAt(r));
                float targetW, targetH;
                if (f.w >= f.h) {
                    targetW = scaledSize;
                    targetH = scaledSize * f.h / (float) f.w;
                } else {
                    targetH = scaledSize;
                    targetW = scaledSize * f.w / (float) f.h;
                }
                float cx = cam.cellToScreenX(rx[r]);
                float cy = cam.cellToScreenY(ry[r]);
                out.addSheetQuad(RenderLayer.UNITS, cache.sheet,
                        f.x, f.y, f.w, f.h,
                        cx, cy, targetW, targetH,
                        1f, 1f, 1f, alphaMult);
            }
        }
    }

    /**
     * Live infantry/civilians/mechs: the authored {@code SPRITE} frame as a batched
     * {@code SHEET_QUAD} (the SOUTH-weapon-up pose flipped vertically via the
     * engine's {@link DrawList#addSheetQuadFlippedV} mirror). A pure {@code Query}
     * column walk over {@link BattleComponents#liveSprites} — the facing/frame
     * derivation ({@code computeFacing}/{@code pickFrame}/weapon-up) is gone from
     * this class; {@code battle.appearance.FacingSystem} authors
     * {@code SPRITE_INDEX}/{@code SPRITE_FLIP_V}/{@code SPRITE_SHEET} once per tick
     * and this sweep just reads them, the {@link #sweepDeadSprites} pattern
     * extended to the live side. Membership itself is the sheet-drawn gate — every
     * matched row already is a
     * {@link com.dillon.starsectormarines.battle.unit.UnitType#drawnAsSheet()} type
     * (no {@code RenderAppearance.spriteKind} check needed), and requiring
     * {@code HEALTH} excludes corpses without a separate check.
     *
     * <p>Two gates:
     * <ol>
     *   <li><b>{@code hp <= 0} — this gate must EXIST; its position is just the
     *   cheap-first ordering.</b> A unit killed <em>after</em> this tick's
     *   death-dispatcher drain (air-strafe damage, a convoy turret, a shot arrival)
     *   keeps its {@code HEALTH} row — hp &le; 0 — until the <em>next</em> tick's
     *   drain transmutes it to a corpse, so a released-but-not-yet-transmuted row
     *   still matches {@code liveSprites} for one frame (the old dense-roster walk
     *   got this filter for free — release already emptied the roster slot — so a
     *   {@code Query} walk must state it). The visibility gate below can <em>never</em>
     *   filter such a row: its {@link UnitRosterService#indexOf} resolves to
     *   {@code INVALID_INDEX} and {@link FogOfWarService#getUnitVisibility}
     *   tolerantly returns {@code VIS_VISIBLE} for an out-of-range index — so
     *   without this gate (in either position) the corpse-to-be draws its stale
     *   last live frame.</li>
     *   <li><b>Visibility</b>, unchanged: {@code VIS_HIDDEN} skips, {@code VIS_FADING}
     *   multiplies in the fade alpha.</li>
     * </ol>
     *
     * <p>Cache resolution: the base cache is {@code sprites.unitSprites().get(type)}.
     * When the authored selector is {@code SPRITE_SHEET ==
     * LiveAppearance.SHEET_SECONDARY_AIM} and the row carries a
     * {@code SECONDARY_WEAPON}, the aim cache is looked up by <em>that weapon's own
     * equipment id</em> — {@code sprites.specialEquipmentAimSheets().get(id)},
     * joined off the row's {@code SECONDARY_WEAPON_SPEC} definition, not the unit's type (aim
     * sheets are keyed by weapon kind, so resolving off {@code IDENTITY_TYPE} would
     * draw the wrong — or no — aim sheet the moment a second secondary with aim art
     * exists) — and used only if it's non-null with loaded frames; otherwise the
     * base cache stands in (the fall-back-to-base-on-missing-aim-art behavior,
     * preserved from the old {@code emitLiveSprite}). The colored-quad fallback
     * covers a missing/unloaded base sheet.
     *
     * <p><b>One accepted seam.</b> A unit spawned during battle setup (before the
     * first sim tick) draws its seeded south-idle frame
     * ({@code UnitRosterService.allocate}'s {@code SPRITE_INDEX} seed) for its
     * first render(s), until the first {@code FacingSystem} pass authors real
     * facing — the old per-frame derivation would have shown path-facing one frame
     * earlier. Imperceptible; accepted in
     * {@code ecs-nouns.md}.
     */
    private void sweepLiveSprites(RenderContext ctx, DrawList out) {
        BattleComponents c = ctx.sim.getBattleComponents();
        BattleCamera cam = ctx.camera;
        ViewCull view = ViewCull.of(cam);
        // Hoisted into locals rather than asked per row: see ViewCull.cullMinX.
        float cullMinX = view.cullMinX(BODY_SILHOUETTE_CELLS);
        float cullMaxX = view.cullMaxX(BODY_SILHOUETTE_CELLS);
        float cullMinY = view.cullMinY(BODY_SILHOUETTE_CELLS);
        float cullMaxY = view.cullMaxY(BODY_SILHOUETTE_CELLS);
        UnitRosterService roster = ctx.sim.getRoster();
        SystemFxService systemFx = roster.systemFx();
        FogOfWarService vis = ctx.sim.getFogOfWar();
        float unitSize = cam.cellPxSize() * BattleRenderer.UNIT_FRAC;
        float half = unitSize / 2f;
        float alphaMult = ctx.alphaMult;

        for (ArchetypeTable t : ctx.sim.getEntityWorld().matched(c.liveSprites)) {
            Object[] types = t.objects(c.IDENTITY, BattleComponents.IDENTITY_TYPE).array();
            Object[] factions = t.objects(c.IDENTITY, BattleComponents.IDENTITY_FACTION).array();
            float[] hp = t.floats(c.HEALTH, BattleComponents.HEALTH_HP).array();
            float[] rx = t.floats(c.POSITION, BattleComponents.POSITION_X).array();
            float[] ry = t.floats(c.POSITION, BattleComponents.POSITION_Y).array();
            int[] sheetSel = t.ints(c.SPRITE, BattleComponents.SPRITE_SHEET).array();
            int[] frameIdx = t.ints(c.SPRITE, BattleComponents.SPRITE_INDEX).array();
            int[] flipV = t.ints(c.SPRITE, BattleComponents.SPRITE_FLIP_V).array();
            boolean hasSecondary = t.has(c.SECONDARY_WEAPON);
            boolean hasLayered = t.has(c.LAYERED_ANIMATION);
            boolean hasMechLayered = t.has(c.MECH_LAYERED_ANIMATION);
            boolean hasMechGait = t.has(c.MECH_GAIT_STATE);
            RavRunnerAssets ravArt = sprites.ravRunnerSprites();
            RavBruteAssets bruteArt = sprites.ravBruteSprites();
            RavMatriarchAssets matriarchArt = sprites.ravMatriarchSprites();
            Object[] ravRigs = (ravArt != null || bruteArt != null || matriarchArt != null) && t.has(c.RAV_RIG)
                    ? t.objects(c.RAV_RIG, BattleComponents.RAV_RIG_STATE).array() : null;
            Object[] secSpec = hasSecondary
                    ? t.objects(c.SECONDARY_WEAPON, BattleComponents.SECONDARY_WEAPON_SPEC).array() : null;
            Object[] primaryWeapon = t.has(c.COMBAT)
                    ? t.objects(c.COMBAT, BattleComponents.COMBAT_PRIMARY_WEAPON).array() : null;
            Object[] equipmentGrade = t.has(c.COMBAT)
                    ? t.objects(c.COMBAT, BattleComponents.COMBAT_EQUIPMENT_GRADE).array() : null;
            float[] layeredFacing = hasLayered
                    ? t.floats(c.LAYERED_ANIMATION, BattleComponents.LAYERED_FACING_DEGREES).array() : null;
            float[] layeredLocomotion = hasLayered
                    ? t.floats(c.LAYERED_ANIMATION, BattleComponents.LAYERED_LOCOMOTION_PHASE).array() : null;
            float[] layeredWeaponPhase = hasLayered
                    ? t.floats(c.LAYERED_ANIMATION, BattleComponents.LAYERED_WEAPON_PHASE).array() : null;
            float[] layeredHeadLook = hasLayered
                    ? t.floats(c.LAYERED_ANIMATION, BattleComponents.LAYERED_HEAD_LOOK_DEGREES).array() : null;
            int[] layeredPose = hasLayered
                    ? t.ints(c.LAYERED_ANIMATION, BattleComponents.LAYERED_WEAPON_POSE).array() : null;
            int[] layeredFlags = hasLayered
                    ? t.ints(c.LAYERED_ANIMATION, BattleComponents.LAYERED_FLAGS).array() : null;
            int[] layeredBodyFamily = hasLayered
                    ? t.ints(c.LAYERED_ANIMATION, BattleComponents.LAYERED_BODY_FAMILY).array() : null;
            int[] layeredHeadFamily = hasLayered
                    ? t.ints(c.LAYERED_ANIMATION, BattleComponents.LAYERED_HEAD_FAMILY).array() : null;
            float[] mechFacing = hasMechLayered
                    ? t.floats(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FACING_DEGREES).array() : null;
            float[] mechHipFacing = hasMechLayered
                    ? t.floats(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_HIP_FACING_DEGREES).array() : null;
            float[] mechLocomotion = hasMechLayered
                    ? t.floats(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_LOCOMOTION_PHASE).array() : null;
            float[] mechChaingunPhase = hasMechLayered
                    ? t.floats(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_CHAINGUN_PHASE).array() : null;
            float[] mechSrmPhase = hasMechLayered
                    ? t.floats(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_SRM_PHASE).array() : null;
            float[] mechLrmPhase = hasMechLayered
                    ? t.floats(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_LRM_PHASE).array() : null;
            int[] mechFlags = hasMechLayered
                    ? t.ints(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FLAGS).array() : null;
            int[] mechArms = hasMechLayered
                    ? t.ints(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_ARMS).array() : null;
            int[] mechChassis = hasMechLayered
                    ? t.ints(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_CHASSIS).array() : null;
            int[] mechLeftShoulder = hasMechLayered
                    ? t.ints(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_LEFT_SHOULDER).array() : null;
            int[] mechRightShoulder = hasMechLayered
                    ? t.ints(c.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_RIGHT_SHOULDER).array() : null;
            Object[] mechGait = hasMechGait
                    ? t.objects(c.MECH_GAIT_STATE,
                    BattleComponents.MECH_GAIT_STATE_STATE).array() : null;

            for (int r = 0, n = t.rowCount(); r < n; r++) {
                long entityId = t.entityAt(r);
                // Gate 1: a released-but-not-yet-transmuted row (see the method doc
                // above) — the visibility gate can never filter these, so this
                // check is load-bearing wherever it sits.
                if (hp[r] <= 0f) continue;

                // Off screen: four float compares against hoisted locals,
                // ahead of the roster lookup the visibility gate needs and of
                // the six or seven authored images a layered body composes
                // from. This is where most of the layer's collection cost was
                // going at a close framing.
                if (rx[r] < cullMinX || rx[r] > cullMaxX
                        || ry[r] < cullMinY || ry[r] > cullMaxY) continue;

                // Gate 2: visibility, keyed by this row's dense roster slot.
                int denseIdx = roster.indexOf(entityId);
                byte uv = vis.getUnitVisibility(denseIdx);
                if (uv == FogOfWarService.VIS_HIDDEN) continue;
                float unitAlpha = alphaMult;
                if (uv == FogOfWarService.VIS_FADING) unitAlpha *= vis.getFadeAlpha(denseIdx);

                UnitType type = (UnitType) types[r];
                LayeredMechAssets mechAssets = hasMechLayered
                        ? sprites.layeredMechSprites(ctx.sim.identity().faction(entityId))
                        : null;
                if (mechAssets != null) {
                    float cx = cam.cellToScreenX(rx[r]);
                    float cy = cam.cellToScreenY(ry[r]);
                    // Chassis width is the single sizing unit. Total appendage
                    // overhang remains close to the legacy 1.6-cell silhouette.
                    float hullWidth = layeredMechHullWidth(
                            cam.cellPxSize(), roster.renderScale(entityId));
                    LayerPose authoredPose = mechPose(mechChassis[r], mechLocomotion[r],
                            mechFlags[r]);
                    MechGaitState gait = mechGait != null
                            ? (MechGaitState) mechGait[r] : null;
                    LayeredMechComposer.GaitPose gaitPose = gait != null
                            ? new LayeredMechComposer.GaitPose(
                            cam.cellToScreenX(gait.leftFootX()),
                            cam.cellToScreenY(gait.leftFootY()), gait.leftFootFacing(),
                            cam.cellToScreenX(gait.rightFootX()),
                            cam.cellToScreenY(gait.rightFootY()), gait.rightFootFacing(),
                            cam.cellToScreenX(rx[r] + gait.waistOffsetX()),
                            cam.cellToScreenY(ry[r] + gait.waistOffsetY()),
                            gait.leftFootLift(), gait.rightFootLift()) : null;
                    LayeredMechComposer.emit(out, mechAssets, cx, cy, hullWidth,
                            mechHipFacing[r], mechFacing[r], mechLocomotion[r], mechChaingunPhase[r],
                            mechSrmPhase[r], mechLrmPhase[r], mechFlags[r], mechChassis[r],
                            mechArms[r], mechLeftShoulder[r], mechRightShoulder[r], unitAlpha,
                            authoredPose, gaitPose);
                    continue;
                }
                Object rig = ravRigs != null ? ravRigs[r] : null;
                if (bruteArt != null && rig instanceof RavBruteRig bruteRig && bruteRig.posed()) {
                    // The Brute too; its rig is sized in cells, so no render scale.
                    RavBruteComposer.emit(out, bruteArt, bruteRig,
                            cam.cellToScreenX(bruteRig.bodyX()), cam.cellToScreenY(bruteRig.bodyY()),
                            cam.cellPxSize(), unitAlpha);
                    continue;
                }
                if (matriarchArt != null && rig instanceof RavMatriarchRig matriarchRig && matriarchRig.posed()) {
                    RavMatriarchComposer.emit(out, matriarchArt, matriarchRig,
                            cam.cellToScreenX(matriarchRig.bodyX()), cam.cellToScreenY(matriarchRig.bodyY()),
                            cam.cellPxSize(), unitAlpha);
                    continue;
                }
                RavRunnerRig ravRig = ravArt != null && rig instanceof RavRunnerRig runnerRig ? runnerRig : null;
                if (ravRig != null && ravRig.posed()) {
                    // The Runner is drawn on its own skeleton, solved at the position it was posed at.
                    RavRunnerComposer.emit(out, ravArt, ravRig,
                            cam.cellToScreenX(ravRig.bodyX()), cam.cellToScreenY(ravRig.bodyY()),
                            cam.cellPxSize() * type.renderScale, 1f, unitAlpha);
                    continue;
                }
                LayeredArmorFamily bodyFamily = hasLayered
                        ? LayeredArmorFamily.fromOrdinal(layeredBodyFamily[r]) : null;
                LayeredArmorFamily headFamily = hasLayered
                        ? LayeredArmorFamily.fromOrdinal(layeredHeadFamily[r]) : null;
                LayeredUnitAssets layeredAssets = hasLayered
                        ? sprites.layeredUnitSprites().get(bodyFamily) : null;
                LayeredUnitAssets layeredHeadAssets = hasLayered
                        ? sprites.layeredUnitSprites().get(headFamily) : null;
                if (layeredAssets != null && layeredHeadAssets != null) {
                    // A runner is drawn on the treadmill's belt, not on the cell beside it.
                    float onMachine = (layeredFlags[r] & LayeredAppearance.FLAG_AT_STATION) != 0
                            ? LayeredAppearance.workMotion(layeredFlags[r]).mountCells()
                                    * cam.cellPxSize() : 0f;
                    double mountFacing = Math.toRadians(layeredFacing[r]);
                    float cx = cam.cellToScreenX(rx[r]) - (float) Math.sin(mountFacing) * onMachine;
                    float cy = cam.cellToScreenY(ry[r]) + (float) Math.cos(mountFacing) * onMachine;
                    SpecialEquipmentDef secondary = secSpec != null
                            ? (SpecialEquipmentDef) secSpec[r] : null;
                    LayerPose authoredPose = infantryPoseDef(sprites.unitLayerLayouts(),
                            type.drawsLayeredWeapon(),
                            secondary, layeredPose[r],
                            layeredLocomotion[r], layeredWeaponPhase[r], layeredFlags[r]);
                    authoredPose = sprites.unitLayerLayouts().applyArmorMastering(
                            authoredPose, bodyFamily, headFamily);
                    WeaponDef primary = primaryDefinition(
                            primaryWeapon != null ? primaryWeapon[r] : null);
                    EquipmentGrade grade = equipmentGrade != null
                            ? (EquipmentGrade) equipmentGrade[r] : EquipmentGrade.SERVICE;
                    IntegralSystemDef integralSystem = roster.integralSystems().spec(entityId);
                    PodLaunch podLaunch = roster.integralSystems().launch(entityId);
                    LayeredUnitComposer.IntegralLaunch launch = null;
                    if (podLaunch != null && integralSystem != null
                            && integralSystem.missilePod() != null) {
                        MissilePodMountGeometry.Pose mount = MissilePodMountGeometry.resolve(
                                rx[r], ry[r], podLaunch.bodyBearingDegrees(),
                                podLaunch.bearingDegrees(), integralSystem.missilePod().mount());
                        launch = new LayeredUnitComposer.IntegralLaunch(
                                cam.cellToScreenX(mount.muzzleX()), cam.cellToScreenY(mount.muzzleY()),
                                mount.bearingDegrees(), podLaunch.phase());
                    }
                    float shoulderPx = layeredInfantryShoulderWidth(
                            cam.cellPxSize(), type.renderScale);
                    // Asleep in a bunk: the straight lying pose, along the bunk, head on the pillow.
                    LayeredSpriteCache sleeper = onMachine > 0f && LayeredAppearance.workMotion(layeredFlags[r])
                            == WorkMotion.SLEEP ? layeredAssets.downed(0) : null;
                    if (sleeper != null) {
                        float scale = shoulderPx * SLEEPER_BODY_SCALE / LayeredUnitComposer.SOURCE_SHOULDER_PX;
                        out.addSprite(RenderLayer.UNITS, sleeper.sprite, cx, cy,
                                sleeper.pxWidth * scale, sleeper.pxHeight * scale, layeredFacing[r],
                                1f, 1f, 1f, unitAlpha);
                        continue;
                    }
                    // Knocked down by a charge: the picture lies as a fallen body does for that
                    // second, though the sim keeps the body upright (colony-breach-nouns.md, Brute).
                    LayeredSpriteCache lying = ctx.sim.stagger().isStaggered(entityId)
                            && ctx.sim.stagger().cause(entityId) == StaggerService.Cause.KNOCKED_DOWN
                            ? layeredAssets.downed((int) (entityId & 0x7fffffff)) : null;
                    // On a Runner's spike: the body lies as a fallen one does, hauled along behind the jaws.
                    long carrier = ctx.sim.swarmHarvest().carrierOf(entityId);
                    LayeredSpriteCache hauled = carrier != 0L
                            ? layeredAssets.downed((int) (entityId & 0x7fffffff)) : null;
                    if (hauled != null) {
                        float scale = shoulderPx * DOWNED_BODY_SCALE / LayeredUnitComposer.SOURCE_SHOULDER_PX;
                        emitDragged(ctx, out, hauled, entityId, carrier, rx[r], ry[r], scale, unitAlpha);
                        continue;
                    }
                    if (lying != null) {
                        float scale = shoulderPx * DOWNED_BODY_SCALE / LayeredUnitComposer.SOURCE_SHOULDER_PX;
                        out.addSprite(RenderLayer.UNITS, lying.sprite, cx, cy,
                                lying.pxWidth * scale, lying.pxHeight * scale, layeredFacing[r],
                                1f, 1f, 1f, unitAlpha);
                        continue;
                    }
                    // Lifted out of the column arrays so the halo's replay of
                    // this same composition can close over them.
                    LayerPose pose = authoredPose;
                    float facingDeg = layeredFacing[r];
                    float headLookDeg = layeredHeadLook[r];
                    float locomotion = layeredLocomotion[r];
                    float weaponPhase = layeredWeaponPhase[r];
                    int weaponPose = layeredPose[r];
                    int animationFlags = layeredFlags[r];
                    // The running-system halo is this actor's own head and body
                    // drawn again underneath, so it is emitted here rather than
                    // by a separate pass: it has to sit immediately behind the
                    // layers it is a copy of, and it is composed from them.
                    if (systemFx.isRunning(entityId) || systemFx.breakFlash(entityId) > 0f) {
                        halo.emit(out, systemFx, entityId,
                                layeredAssets.body, layeredHeadAssets.head,
                                cx, cy, shoulderPx, unitAlpha,
                                emitter -> LayeredUnitComposer.emit(emitter, layeredAssets,
                                        layeredHeadAssets.head, primary,
                                        type.drawsLayeredWeapon(), secondary, grade,
                                        cx, cy, shoulderPx, facingDeg, headLookDeg,
                                        locomotion, weaponPhase, weaponPose,
                                        animationFlags, 1f, pose, integralSystem));
                    }
                    LayeredUnitComposer.emit(out, layeredAssets, layeredHeadAssets.head,
                            primary, type.drawsLayeredWeapon(), secondary, grade,
                            cx, cy, shoulderPx, facingDeg, headLookDeg, locomotion,
                            weaponPhase, weaponPose, animationFlags, unitAlpha,
                            authoredPose, integralSystem, launch);
                    continue;
                }
                UnitSpriteCache cache = sprites.unitSprites().get(type);
                if (sheetSel[r] == LiveAppearance.SHEET_SECONDARY_AIM && secSpec != null) {
                    SpecialEquipmentDef equipment = (SpecialEquipmentDef) secSpec[r];
                    UnitSpriteCache aim = sprites.specialEquipmentAimSheets().get(equipment.id());
                    if (aim != null && aim.sheet != null && aim.frames != null
                            && aim.frames.frames.length > 0) {
                        cache = aim;
                    }
                }
                if (cache == null || cache.sheet == null || cache.frames == null
                        || cache.frames.frames.length == 0) {
                    Allegiance allegiance = Allegiance.of((Faction) factions[r]);
                    Color col = allegiance.friendly() ? MARINE_COLOR
                            : allegiance == Allegiance.NEUTRAL
                            ? CIVILIAN_COLOR : DEFENDER_COLOR;
                    float cx = cam.cellToScreenX(rx[r]);
                    float cy = cam.cellToScreenY(ry[r]);
                    emitSolidQuad(out, cx, cy, half, col, unitAlpha);
                    continue;
                }
                emitLiveSprite(out, cam, type, cache, frameIdx[r], flipV[r] != 0,
                        rx[r], ry[r], unitSize, unitAlpha);
            }
        }
    }

    /** Resolve the catalog definition stored in the combat component. */
    private static WeaponDef primaryDefinition(Object primary) {
        return primary instanceof WeaponDef definition ? definition : null;
    }

    static LayerPose infantryPoseDef(boolean drawsLayeredWeapon, SpecialEquipmentDef secondary,
                                     int pose, float locomotionPhase,
                                     float actionPhase, int flags) {
        return infantryPoseDef(UnitLayerLayouts.get(), drawsLayeredWeapon, secondary,
                pose, locomotionPhase, actionPhase, flags);
    }

    static LayerPose infantryPoseDef(UnitLayerLayouts layouts, boolean drawsLayeredWeapon,
                                     SpecialEquipmentDef secondary, int pose,
                                     float locomotionPhase, float actionPhase, int flags) {
        // A ship's medic kneels at a bed with no weapon to draw; every other
        // authored clip is a weapon pose.
        if (!drawsLayeredWeapon && (flags & LayeredAppearance.FLAG_TREATING) == 0) return null;
        boolean moving = (flags & LayeredAppearance.FLAG_MOVING) != 0;
        String variantId = "rifle";
        String animationId;
        float phase;
        switch (pose) {
            case LayeredAppearance.POSE_IDLE -> {
                animationId = moving ? "walking" : "idle";
                phase = moving ? locomotionPhase : 0f;
            }
            case LayeredAppearance.POSE_AIMED -> {
                animationId = "aiming";
                phase = actionPhase;
            }
            case LayeredAppearance.POSE_FIRING -> {
                animationId = "firing";
                phase = actionPhase;
            }
            case LayeredAppearance.POSE_ROCKET_AIM, LayeredAppearance.POSE_ROCKET_FIRE,
                 LayeredAppearance.POSE_AMR_AIM, LayeredAppearance.POSE_AMR_FIRE,
                 LayeredAppearance.POSE_SMOKE_THROW,
                 LayeredAppearance.POSE_SATCHEL_PLANT -> {
                if ((flags & LayeredAppearance.FLAG_TREATING) != 0) {
                    // A medic kneels the way a satchel is planted, whatever
                    // special they actually carry.
                    AnimationClip kneel = layouts.clip("marine-line", "satchel", "planting");
                    return kneel != null ? kneel.sample(actionPhase) : null;
                }
                if (secondary == null
                        || secondary.presentation().layerClips() == null) {
                    return null;
                }
                LayerClips layerClips = secondary.presentation().layerClips();
                boolean firing = pose == LayeredAppearance.POSE_ROCKET_FIRE
                        || pose == LayeredAppearance.POSE_AMR_FIRE;
                variantId = layerClips.variant();
                animationId = firing && layerClips.firing() != null
                        ? layerClips.firing() : layerClips.using();
                phase = actionPhase;
            }
            default -> {
                return null;
            }
        }
        AnimationClip clip = layouts.clip("marine-line", variantId, animationId);
        if (clip == null) return null;
        LayerPose sampled = clip.sample(phase);
        if (moving && (pose == LayeredAppearance.POSE_AIMED
                || pose == LayeredAppearance.POSE_FIRING)) {
            // A primary-weapon action owns the upper body, but it must not
            // erase locomotion while the simulation is still translating the
            // marine. Keep the action body/head/weapon and take both feet from
            // the current distance-driven stride sample.
            AnimationClip walking = layouts.clip("marine-line", "rifle", "walking");
            if (walking != null) {
                sampled = sampled.withLayersFrom(walking.sample(locomotionPhase),
                        "left-foot", "right-foot");
            }
        }
        boolean enteringSecondary = pose == LayeredAppearance.POSE_ROCKET_AIM
                || pose == LayeredAppearance.POSE_AMR_AIM
                || pose == LayeredAppearance.POSE_SMOKE_THROW
                || pose == LayeredAppearance.POSE_SATCHEL_PLANT;
        if (!enteringSecondary
                || phase >= LayeredAppearance.ACTION_ENTRY_BLEND_PHASE) {
            return sampled;
        }
        boolean fromMoving = (flags & LayeredAppearance.FLAG_ACTION_FROM_MOVING) != 0;
        AnimationClip sourceClip = layouts.clip("marine-line", "rifle",
                fromMoving ? "walking" : "idle");
        if (sourceClip == null) return sampled;
        LayerPose source = sourceClip.sample(fromMoving ? locomotionPhase : 0f);
        return LayerPose.blendMatching(source, sampled,
                phase / LayeredAppearance.ACTION_ENTRY_BLEND_PHASE);
    }

    private static LayerPose mechPose(int chassis, float locomotionPhase, int flags) {
        String unitId = switch (chassis) {
            case LayeredMechAppearance.CHASSIS_HOUND -> "mech-hound";
            case LayeredMechAppearance.CHASSIS_SIROCCO -> "mech-sirocco";
            default -> "mech-bulwark";
        };
        boolean stepping = (flags & (LayeredMechAppearance.FLAG_MOVING
                | LayeredMechAppearance.FLAG_TURNING)) != 0;
        AnimationClip clip = UnitLayerLayouts.get().clip(unitId, "field-loadout",
                stepping ? "walking" : "idle");
        return clip != null ? clip.sample(stepping ? locomotionPhase : 0f) : null;
    }

    /**
     * Emits the authored {@code SPRITE} frame — {@code frameIdx}/{@code flipV} are
     * read straight off the {@link BattleComponents#SPRITE} columns
     * {@code battle.appearance.FacingSystem} wrote last tick; no facing/weapon-up
     * derivation happens here anymore. Sizing: {@code renderScale}d cell height,
     * width by the frame's aspect — unchanged from the old {@code renderUnitSprite}.
     */
    private static void emitLiveSprite(DrawList out, BattleCamera cam, UnitType type,
                                       UnitSpriteCache cache, int frameIdx, boolean flipV,
                                       float rx, float ry, float unitSize, float alphaMult) {
        SpriteSheetFrames frames = cache.frames;
        // Sheet-cache-dependent clamp stays render-side — FacingSystem deliberately
        // authors the unclamped logical frame (the render tier owns defending
        // against whatever the currently-loaded cache's frame count is).
        if (frameIdx >= frames.frames.length) frameIdx = 0;
        SpriteSheetFrames.Frame f = frames.frames[frameIdx];

        float targetH = unitSize * type.renderScale;
        float targetW = targetH * f.w / (float) f.h;
        float cx = cam.cellToScreenX(rx);
        float cy = cam.cellToScreenY(ry);
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
     * Durability bars for combatants (drones excluded — they bar themselves in the
     * DRONES layer). Runs <b>last</b> so bars paint over every body in the layer —
     * the per-stratum sweep that dissolves the old per-entity decorator ordering
     * trap. Same combatant/visibility gating + fade alpha as the bodies, same
     * per-kind {@code barY} (turret/hub sit higher by their visual extent), via the
     * shared {@link DurabilityBarDecor}. The {@code drawsDurabilityBar} tag is the
     * combatant-and-not-drone check.
     *
     * <p>Armor is an optional live-only capacity, so the armored row is emitted only for
     * an entity that actually carries one. Ownership styling comes from
     * {@link Allegiance}, resolved per entity from its simulation faction.
     *
     * <p>The bar spans the body it belongs to rather than one cell: an emplacement
     * or hub is measured by its structure's visual extent and a mech by its render
     * scale, which is also what gives a big, tough thing the room its segment
     * dividers need. A type tagged {@code barsOnlyWhenUnderFire} stays bare until
     * combat telemetry records damage against it, so an untouched turret line reads
     * as scenery until the moment it starts taking hits.
     */
    private void sweepDurabilityBars(RenderContext ctx, DrawList out) {
        // The host profile is read in classifyRoster, which leaves this empty
        // when decorations are suppressed rather than checking it twice.
        BattleCamera cam = ctx.camera;
        World world = ctx.sim.world();
        TurretStateService turretState = ctx.sim.turretState();
        ViewCull view = ViewCull.of(cam);
        float barMinX = view.cullMinX(BODY_SILHOUETTE_CELLS);
        float barMaxX = view.cullMaxX(BODY_SILHOUETTE_CELLS);
        float barMinY = view.cullMinY(BODY_SILHOUETTE_CELLS);
        float barMaxY = view.cullMaxY(BODY_SILHOUETTE_CELLS);
        float cellPx = cam.cellPxSize();
        float unitSize = cellPx * BattleRenderer.UNIT_FRAC;
        float alphaMult = ctx.alphaMult;
        FogOfWarService vis = ctx.sim.getFogOfWar();

        CombatTelemetryService telemetry = ctx.sim.telemetry();
        for (int i = 0, n = bars.size(); i < n; i++) {
            long u = bars.id(i);
            int slot = bars.slot(i);
            UnitType type = bars.type(i);
            RenderAppearance appearance = RenderAppearance.of(type);
            if (appearance.barsOnlyWhenUnderFire && !hasTakenFire(telemetry, u)) continue;
            byte uv = vis.getUnitVisibility(slot);
            if (uv == FogOfWarService.VIS_HIDDEN) continue;
            // After the gates rather than before them, and on positions this
            // loop was going to read anyway. Ahead of it, the cull was two
            // extra component probes for every body the gates were about to
            // drop, and on a map where it rejects almost nothing that cost more
            // than it saved -- measured, at the 280x168 control's mid framing.
            float bodyX = world.renderX(u);
            float bodyY = world.renderY(u);
            // The hoisted body bounds cover every ordinary body; a structure or
            // a parked hull is bigger than one and asks for its own, which is a
            // handful of bodies rather than every one of them.
            if (bodyX < barMinX || bodyX > barMaxX || bodyY < barMinY || bodyY > barMaxY) {
                if (!view.visible(bodyX, bodyY, barExtentCells(ctx, u, type))) continue;
            }
            float barAlpha = alphaMult;
            if (uv == FogOfWarService.VIS_FADING) barAlpha *= vis.getFadeAlpha(slot);

            float cx = cam.cellToScreenX(bodyX);
            float cy = cam.cellToScreenY(bodyY);
            // The bar spans the drawn body, so its extent doubles as the gap offset.
            float bodyPx;
            if (type.isTurret()) {
                bodyPx = turretState.mount(u).visualCells * cellPx;
            } else if (type.isDroneHub()) {
                bodyPx = DroneHub.VISUAL_CELLS * cellPx;
            } else if (type.isBasedAircraft()) {
                AirfieldService.Berth berth = ctx.sim.getAirfieldService().berthOf(u);
                bodyPx = berth != null
                        ? HullFootprintResolver.visualLengthCells(berth.airframe.renderHullId())
                                * AirAppearance.GROUND_SCALE * cellPx
                        : cellPx;
            } else {
                bodyPx = unitSize * appearance.renderScale;
            }
            float barY = cy + bodyPx / 2f + BattleRenderer.HP_BAR_GAP;
            Allegiance owner = Allegiance.of(ctx.sim.identity().faction(u));
            if (world.hasArmor(u)) {
                DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner, cx, barY, bodyPx,
                        world.hp(u), world.maxHp(u),
                        world.armor(u), world.maxArmor(u), barAlpha);
            } else {
                DurabilityBarDecor.emit(out, RenderLayer.UNITS, owner, cx, barY, bodyPx,
                        world.hp(u), world.maxHp(u), barAlpha);
            }
        }
    }

    /**
     * The span a durability bar and the body under it occupy, in cells, for
     * {@link ViewCull}.
     *
     * <p>The same four cases the bar itself is sized by, in the same order, so
     * the cull cannot disagree with the emit about how big the thing is.
     */
    private static float barExtentCells(RenderContext ctx, long u, UnitType type) {
        if (type.isTurret()) {
            return ctx.sim.turretState().mount(u).visualCells * AUTHORED_EXTENT_SLACK;
        }
        if (type.isDroneHub()) {
            return DroneHub.VISUAL_CELLS * AUTHORED_EXTENT_SLACK;
        }
        if (type.isBasedAircraft()) {
            AirfieldService.Berth berth = ctx.sim.getAirfieldService().berthOf(u);
            return berth != null ? hullExtentCells(berth.airframe) : BODY_SILHOUETTE_CELLS;
        }
        return BODY_SILHOUETTE_CELLS;
    }

    /**
     * True once combat telemetry has recorded damage against {@code id}. Telemetry
     * is the exact first-hit record — a capacity comparison would also read as "hit"
     * for anything spawned below full, and it is already kept for every combatant.
     */
    private static boolean hasTakenFire(CombatTelemetryService telemetry, long id) {
        return telemetry.isRecorded(id) && telemetry.damageTaken(id) > 0f;
    }
}
