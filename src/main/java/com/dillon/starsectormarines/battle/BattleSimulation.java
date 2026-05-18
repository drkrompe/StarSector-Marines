package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.air.AirSimContext;
import com.dillon.starsectormarines.battle.air.AirSystem;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.ai.CombatantBehavior;
import com.dillon.starsectormarines.battle.ai.FallbackBehavior;
import com.dillon.starsectormarines.battle.ai.FleeBehavior;
import com.dillon.starsectormarines.battle.ai.GarrisonBehavior;
import com.dillon.starsectormarines.battle.ai.KitRetrieverBehavior;
import com.dillon.starsectormarines.battle.ai.PatrolBehavior;
import com.dillon.starsectormarines.battle.ai.SquadAlertLevel;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.TurretBehavior;
import com.dillon.starsectormarines.battle.ai.UnitBehavior;
import com.dillon.starsectormarines.battle.ai.goap.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.objective.Objective;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.weapons.Detonations;
import com.dillon.starsectormarines.battle.weapons.HeavyWeapons;
import com.dillon.starsectormarines.battle.weapons.InfantryWeapons;
import com.dillon.starsectormarines.battle.weapons.WeaponSimContext;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Headless auto-battler simulation. Owns the {@link NavigationGrid}, the unit
 * roster, and the per-tick logic that drives target acquisition, pathfinding,
 * movement interpolation, and combat resolution.
 *
 * <p>The caller drives time via {@link #advance(float)}, which accumulates real
 * time into fixed 30Hz ticks. That keeps simulation determinism independent of
 * the render framerate and lets speed/pause controls work by scaling the input
 * dt (or feeding zero) — the sim itself doesn't care.
 *
 * <p>v1 behavior, each tick:
 * <ul>
 *   <li>Each alive unit refreshes its target to the nearest alive enemy.</li>
 *   <li>If a target is in {@link Unit#attackRange}, the unit stops moving and
 *       fires on {@link Unit#attackCooldown}, dealing {@link Unit#attackDamage}.</li>
 *   <li>Otherwise the unit re-pathfinds (only when between cells, to avoid a
 *       visual jump mid-step) and advances {@code moveProgress} along the path
 *       at {@link Unit#moveSpeed} cells/sec.</li>
 *   <li>When one faction runs out of alive units, the sim flags
 *       {@link #isComplete()} and records the winner.</li>
 * </ul>
 *
 * <p>Pathfinding runs every tick a unit is moving — at 16 units × 30Hz on a
 * 24×16 grid it's a few thousand cell expansions per second, well inside the
 * budget. Spatial indexing for target search can come later if we scale up.
 */
public class BattleSimulation implements AirSimContext, WeaponSimContext {

    /** Fixed simulation timestep — 30Hz. */
    public static final float TICK_DT = 1f / 30f;

    /** Damage reduction per cover level (0..MAX_COVER). Open ground = 0%; 1 wall = 15%; 2 = 30%; 3+ = 45%. Applied multiplicatively in {@link #fireShot}. */
    private static final float[] COVER_DAMAGE_REDUCTION = { 0f, 0.15f, 0.30f, 0.45f };

    /** Sim seconds a tracer stays visible after being fired. */
    private static final float SHOT_LIFETIME = 0.15f;
    /** Min/max near-miss offset (cells) from target cell-center on a missed shot. */
    private static final float MISS_OFFSET_MIN = 0.5f;
    private static final float MISS_OFFSET_MAX = 2.0f;

    /** Probability a hit puts the target into fall-back. Rolled once per hit; ignored if already falling back. */
    private static final float FALLBACK_CHANCE   = 0.25f;
    /** Sim seconds a unit stays in fall-back state once entered. After this, normal engagement resumes. */
    private static final float FALLBACK_DURATION = 3.5f;

    /** Squad morale drained per non-fatal hit on a squadmate. Tuned to feel — a 4-man squad eats 12 hits before drain alone could break them at 1.0 baseline. */
    public static final float MORALE_DROP_ON_HIT   = 0.05f;
    /** Additional morale drop when a hit kills the squadmate. Combined with {@link #MORALE_DROP_ON_HIT}, two quick kills in a 4-man squad take morale from 1.0 to 0.3 — right at the broken threshold. */
    public static final float MORALE_DROP_ON_DEATH = 0.30f;
    /** Morale recovered per sim-second while the squad is out of contact ({@code !_engagedThisTick}). Recovers from broken (0.3) to cleared (0.5) in 1s at default. */
    public static final float MORALE_RECOVERY_RATE = 0.20f;
    /** Hysteresis: squad flips to broken when morale dips below this. */
    public static final float MORALE_BROKEN_THRESHOLD = 0.30f;
    /** Hysteresis: broken squad reverts to normal posture once morale climbs above this. The gap (>0.2 over the broken threshold) prevents flickering. */
    public static final float MORALE_CLEAR_THRESHOLD  = 0.50f;

    private final NavigationGrid grid;
    private final CellTopology topology;
    private final List<Unit> units = new ArrayList<>();
    private final AirSystem airSystem = new AirSystem();
    /** Handheld squad weapons (rifle / SMG / DMR / rocket launcher). Owns fireShot, fireSecondary, and the per-tick burst continuation pass. Pumped each tick via {@code infantry.tick(this)}; behavior call sites still go through the delegating {@link #fireShot} / {@link #fireSecondary} wrappers on this class. */
    private final InfantryWeapons infantry = new InfantryWeapons();
    /** Chassis-mounted weapons on motorized / heavy units (mech today, future tanks/hovercraft). Owns fireMechWeapon and the per-tick mech continuation + wreck-spawn passes. */
    private final HeavyWeapons heavy = new HeavyWeapons();
    /** Physics-based AoE pipeline — owns the in-flight rocket queue and drains expired entries into splash + wall damage. Both infantry rockets and mech HE rockets queue here through {@link #queueDetonation}. */
    private final Detonations detonations = new Detonations();
    private final List<Objective> objectives = new ArrayList<>();
    private final List<EquipmentDrop> equipmentDrops = new ArrayList<>();
    private final List<Doodad> doodads = new ArrayList<>();
    /**
     * Per-cell, per-facing doodad cover. Indexed as
     * {@code (y * gridWidth + x) * FACING_COUNT + facing}. Updated on
     * {@link #addDoodad}; never decreases during a battle (doodads aren't
     * removed mid-fight). Lazy-initialized — the array is allocated on first
     * {@link #addDoodad} call.
     *
     * <p>A doodad at (cx, cy) contributes cover two ways:
     * <ol>
     *   <li><b>Isotropic on its own cell.</b> All four facings of (cx, cy)
     *       gain the doodad's cover level — a marine standing on the crate
     *       cell is "co-located with the cover," counted as covered from any
     *       angle. Matches the pre-Story-G semantic.</li>
     *   <li><b>Per-facing on each cardinal neighbor.</b> The doodad at (cx, cy)
     *       sits between the neighbor at (cx, cy-1) and threats further north,
     *       so that neighbor gains S-facing cover (threat south = doodad is
     *       between). Same for the other three cardinals — the doodad blocks
     *       LOS toward itself, so the neighbor reads cover from the facing
     *       <em>toward</em> the doodad.</li>
     * </ol>
     *
     * <p>Multiple doodads stacking on the same cell+facing take the max,
     * matching the pre-Story-G doodad-only rule. Combined with cell-grid wall
     * cover ({@link NavigationGrid#getCoverAtFacing}) at the consumer site —
     * {@link com.dillon.starsectormarines.battle.ai.TacticalScoring} sums the
     * two when scoring candidate firing positions.
     */
    private byte[] doodadCoverByFacing;
    private final List<MapVehicle> vehicles = new ArrayList<>();
    /** Persistent visual decals (bullet holes, craters, rubble) accumulated over the battle. Bounded by {@link #DECAL_CAP} with FIFO eviction via {@link java.util.ArrayDeque#pollFirst} — O(1) head removal, unlike {@code ArrayList.remove(0)} which would shift the whole tail per overflow. */
    private final java.util.ArrayDeque<Decal> decals = new java.util.ArrayDeque<>();
    /** Soft cap on decal count — older decals get dropped from the head when this fills, so a long battle doesn't accumulate thousands of bullet holes. */
    private static final int DECAL_CAP = 600;
    /** Active smoking wrecks parked at destroyed turret cells. Each emits a periodic smoke-puff event the renderer drains into the impact FX engine. */
    private final List<SmokingWreck> smokingWrecks = new ArrayList<>();
    /** Smoke-puff events queued this advance — each entry is {x, y, radiusCells}. Drained by the renderer per frame and cleared at the start of each advance. */
    private final List<float[]> smokePuffsThisFrame = new ArrayList<>();
    /** Total seconds a wreck keeps smoking after destruction. Long enough that the player notices "this turret is dead and smoldering" between glances. */
    private static final float WRECK_LIFETIME = 18f;
    /** Min/max sim-seconds between puffs on a single wreck. Jittered per emission so wrecks don't sync up. */
    private static final float WRECK_PUFF_MIN_GAP = 0.45f;
    private static final float WRECK_PUFF_MAX_GAP = 0.85f;
    /** Dense, primitive-keyed squad lookup. fastutil's Int2ObjectOpenHashMap avoids the per-call Integer autobox that {@link #getSquad} would do on a {@code HashMap<Integer, Squad>} — and getSquad is hit per-unit per-tick from the behavior dispatch. */
    private final Int2ObjectMap<Squad> squads = new Int2ObjectOpenHashMap<>();

    /**
     * Per-target attacker index: for each unit currently targeted by at least
     * one alive attacker, the bucket holds that attacker list. Rebuilt at the
     * top of each tick by {@link #rebuildAttackersByTarget()}. Unit doesn't
     * override equals/hashCode, so {@code Object2ObjectOpenHashMap} gives
     * identity-key semantics for free.
     *
     * <p>Drives O(1)-lookup crowding scoring in {@link com.dillon.starsectormarines.battle.ai.TacticalScoring}:
     * instead of scanning every unit per candidate enemy, the scorer walks the
     * (typically &lt; 6 entry) attacker list for that enemy. Buckets are
     * recycled from {@link #attackerListPool} so steady-state allocation is
     * zero.
     */
    private final Object2ObjectMap<Unit, ArrayList<Unit>> attackersByTarget = new Object2ObjectOpenHashMap<>();
    /** Recycled {@code ArrayList<Unit>} buckets. {@link #rebuildAttackersByTarget()} clears + returns every bucket here before re-populating, so the steady-state allocation is zero. */
    private final ArrayList<ArrayList<Unit>> attackerListPool = new ArrayList<>();
    /** Next squad id to assign on shuttle deboard. Monotonically increasing across the battle's lifetime. */
    private int nextSquadId = 0;
    private final List<ShotEvent> activeShots = new ArrayList<>();
    /** Shots fired during the last {@link #advance(float)} call. Cleared on each advance, populated per tick. Drives one-shot audio in the renderer. */
    private final List<ShotEvent> shotsThisFrame = new ArrayList<>();
    /** Shots whose lifetime ran out during the last {@link #advance(float)} call — the "arrival" event for projectile-style shots. The renderer reads this to spawn impact FX at the endpoint when the projectile sprite actually reaches its target, rather than at launch time. */
    private final List<ShotEvent> shotsExpiredThisFrame = new ArrayList<>();
    /** Units that transitioned from alive to dead during the last {@link #advance(float)} call. Same lifecycle as {@link #shotsThisFrame}. */
    private final List<Unit> deathsThisFrame = new ArrayList<>();
    private final Random rng = new Random();

    /** Counter for IDs of marines deboarded from shuttles. Bumped via {@link #nextMarineId()} when {@link AirSystem} deboards. Format: "m0", "m1", ... matches the pre-shuttle setup convention. */
    private int deboardedMarineCount = 0;

    /** Per-cell unit count, rebuilt at the start of each tick. Passed to the pathfinder so units route around ally-held cells. */
    private final byte[] occupancyMap;

    private float tickAccumulator = 0f;
    private boolean complete = false;
    private Faction winner;

    private final ZoneGraph zoneGraph;
    /**
     * Set whenever the walkability layout changes during a tick (wall breach,
     * turret demolish). Drained to a single {@link ZoneGraph#rebuild()} at the
     * end of the tick so multiple breaches in the same tick collapse into one
     * full graph rebuild. AI queries that run mid-tick see the previous
     * tick's graph — fine in practice, since rubble stays walkable forever
     * (paths only ever gain shortcuts) and the new portal becomes visible
     * within 1/30s.
     */
    private boolean zoneGraphDirty = false;

    /** Fighter wings committed to this battle. Lives on the sim so the overlay can read it without coupling to the briefing screen. */
    private FlybyRoster flybyRoster = FlybyRoster.EMPTY;

    /**
     * Authored tactical node graph from the map generator. Read by the
     * patrol behavior for waypoint sampling. Default is an empty map so
     * legacy callers that don't wire one through still work — patrols on
     * empty maps degrade gracefully to random-cell wander.
     */
    private TacticalMap tacticalMap = new TacticalMap(java.util.Collections.emptyList());

    public BattleSimulation(NavigationGrid grid, CellTopology topology) {
        this.grid = grid;
        this.topology = topology;
        this.occupancyMap = new byte[grid.getWidth() * grid.getHeight()];
        this.zoneGraph = new ZoneGraph(grid);
        this.zoneGraph.rebuild();
    }

    @Override public NavigationGrid getGrid() { return grid; }
    /** Categorization tags (street / rubble / wall / vehicle / etc.) for renderer + placement filters. Sibling to {@link #grid}; the pathfinder doesn't touch this. */
    public CellTopology getTopology()      { return topology; }
    /** Zone+portal graph layered on the {@link NavigationGrid}. Rebuilt on wall destruction so AI queries reflect the current map. */
    public ZoneGraph getZoneGraph()        { return zoneGraph; }

    /**
     * Wall-damage entry point that callers should prefer over
     * {@link NavigationGrid#damageCell} directly — it pipes through to the
     * grid and triggers a {@link ZoneGraph#rebuild()} when a wall actually
     * collapses, so the AI's zone vocabulary stays in sync with reality.
     * Returns true the call that knocks the wall down.
     */
    @Override
    public boolean damageCell(int x, int y, int amount) {
        if (!grid.damageCell(x, y, amount)) return false;
        // A wall that just collapsed is now walkable + a zone-graph portal
        // (handled inside grid.damageCell). Topology needs the visual swap:
        // clear WALL so the wall pass stops drawing tile art, set the ground
        // kind to RUBBLE so the floor pass picks the damaged-floor autotile.
        topology.setWall(x, y, false);
        topology.setGroundKind(x, y, CellTopology.GroundKind.RUBBLE);
        zoneGraphDirty = true;
        return true;
    }
    public List<Unit> getUnits()           { return units; }
    public List<Shuttle> getShuttles()     { return airSystem.getShuttles(); }
    public List<Objective> getObjectives() { return objectives; }
    public List<EquipmentDrop> getEquipmentDrops() { return equipmentDrops; }
    public List<Doodad> getDoodads()       { return doodads; }
    public void addDoodad(Doodad d) {
        doodads.add(d);
        if (d.cover <= 0) return;
        if (!grid.inBounds(d.cellX, d.cellY)) return;
        if (doodadCoverByFacing == null) {
            doodadCoverByFacing = new byte[grid.getWidth() * grid.getHeight() * NavigationGrid.FACING_COUNT];
        }
        // Isotropic on own cell — a marine standing on the crate cell counts
        // as covered from any angle. Max-merge with existing so stacked
        // doodads use the heaviest cover.
        maxMergeDoodadFacing(d.cellX, d.cellY, NavigationGrid.FACING_N, d.cover);
        maxMergeDoodadFacing(d.cellX, d.cellY, NavigationGrid.FACING_E, d.cover);
        maxMergeDoodadFacing(d.cellX, d.cellY, NavigationGrid.FACING_S, d.cover);
        maxMergeDoodadFacing(d.cellX, d.cellY, NavigationGrid.FACING_W, d.cover);
        // Cardinal neighbors gain cover toward the doodad — the marine on
        // (cx, cy-1) reads S-facing cover because the doodad sits between them
        // and any southward threat. Same logic in the other three cardinals.
        maxMergeDoodadFacing(d.cellX, d.cellY - 1, NavigationGrid.FACING_S, d.cover);
        maxMergeDoodadFacing(d.cellX, d.cellY + 1, NavigationGrid.FACING_N, d.cover);
        maxMergeDoodadFacing(d.cellX - 1, d.cellY, NavigationGrid.FACING_E, d.cover);
        maxMergeDoodadFacing(d.cellX + 1, d.cellY, NavigationGrid.FACING_W, d.cover);
    }

    /**
     * Internal helper — writes {@code level} to a cell+facing slot if higher
     * than the current value. Out-of-bounds calls are no-ops so callers don't
     * need to bounds-check the four neighbor writes around an edge doodad.
     */
    private void maxMergeDoodadFacing(int x, int y, int facing, int level) {
        if (!grid.inBounds(x, y)) return;
        int slot = (grid.index(x, y) * NavigationGrid.FACING_COUNT) + facing;
        int existing = doodadCoverByFacing[slot] & 0xFF;
        if (level > existing) doodadCoverByFacing[slot] = (byte) level;
    }

    /**
     * Directional doodad cover at (x, y) against a threat in direction
     * {@code (fromDx, fromDy)} (offset from this cell to the threat). 0 if
     * no doodad covers that facing.
     */
    public int getDoodadCoverAt(int x, int y, int fromDx, int fromDy) {
        return getDoodadCoverAtFacing(x, y, NavigationGrid.facingFor(fromDx, fromDy));
    }

    public int getDoodadCoverAtFacing(int x, int y, int facing) {
        if (doodadCoverByFacing == null) return 0;
        if (!grid.inBounds(x, y)) return 0;
        if (facing < 0 || facing >= NavigationGrid.FACING_COUNT) return 0;
        return doodadCoverByFacing[(grid.index(x, y) * NavigationGrid.FACING_COUNT) + facing] & 0xFF;
    }

    /**
     * Direction-agnostic doodad cover at (x, y) — max across all 4 facings.
     * Back-compat accessor for {@link com.dillon.starsectormarines.battle.ai.TacticalScoring#findFallbackPosition}
     * and other callers that don't carry a threat direction.
     */
    public int getDoodadCoverAt(int x, int y) {
        if (doodadCoverByFacing == null) return 0;
        if (!grid.inBounds(x, y)) return 0;
        int base = grid.index(x, y) * NavigationGrid.FACING_COUNT;
        int n = doodadCoverByFacing[base    ] & 0xFF;
        int e = doodadCoverByFacing[base + 1] & 0xFF;
        int s = doodadCoverByFacing[base + 2] & 0xFF;
        int w = doodadCoverByFacing[base + 3] & 0xFF;
        return Math.max(Math.max(n, e), Math.max(s, w));
    }
    /** Parked vehicles that occupy multi-cell footprints. Cells were flagged non-walkable at setup time, so the sim doesn't need to consult this list for pathing/LOS — only the renderer does. */
    public List<MapVehicle> getVehicles()  { return vehicles; }
    public void addVehicle(MapVehicle v)   { vehicles.add(v); }
    /** Persistent visual decals — bullet holes, craters, rubble. Pure render data; combat ignores them. Returned as {@code Iterable} because the renderer only iterates; head-eviction needs the {@code ArrayDeque} surface internally. */
    public Iterable<Decal> getDecals()     { return decals; }
    public void addDecal(Decal d) {
        decals.addLast(d);
        if (decals.size() > DECAL_CAP) decals.pollFirst();
    }
    /** Smoke-puff events emitted by smoking wrecks during the last advance. Each entry is {x, y, radiusCells}. Drained by the renderer per frame. */
    public List<float[]> getSmokePuffsThisFrame() { return smokePuffsThisFrame; }
    /** Fighter wings committed to this battle. {@code FlybyOverlay} reads this on first tick and drives spawns from the per-wing schedules. Defaults to {@link FlybyRoster#EMPTY}; missions assign via {@link #setFlybyRoster}. */
    public FlybyRoster getFlybyRoster()    { return flybyRoster; }
    public void setFlybyRoster(FlybyRoster roster) { this.flybyRoster = roster != null ? roster : FlybyRoster.EMPTY; }
    public List<ShotEvent> getActiveShots(){ return activeShots; }
    public List<ShotEvent> getShotsThisFrame() { return shotsThisFrame; }
    /** Shots whose lifetime ended this advance — the "projectile arrived" event. Renderer reads this to spawn impact FX + arrival sounds at the moment a turret-shot sprite reaches its endpoint. */
    public List<ShotEvent> getShotsExpiredThisFrame() { return shotsExpiredThisFrame; }
    public List<Unit> getDeathsThisFrame()     { return deathsThisFrame; }
    public boolean isComplete()            { return complete; }
    public Faction getWinner()             { return winner; }
    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. Exposed for AI scoring; do not mutate directly — go through {@link #setPath}. */
    public byte[] getOccupancyMap()        { return occupancyMap; }
    @Override public Random getRng()       { return rng; }
    /** Returns the squad with the given id, or {@code null} if {@code id == Unit.NO_SQUAD} or the squad was never registered. */
    public Squad getSquad(int id)          { return id == Unit.NO_SQUAD ? null : squads.get(id); }
    /** All squads currently registered. Used by the per-tick alert update; behaviors should read individual squads via {@link #getSquad(int)} keyed off {@link Unit#squadId}. */
    public Collection<Squad> getSquads()   { return squads.values(); }
    /** Tactical hint graph produced by the map generator. Never null; an empty graph for legacy maps. */
    public TacticalMap getTacticalMap()    { return tacticalMap; }
    /** Set the tactical map for this battle. Called once by {@code BattleSetup} right after construction, before the first {@link #advance} call. */
    public void setTacticalMap(TacticalMap map) {
        this.tacticalMap = map != null ? map : new TacticalMap(java.util.Collections.emptyList());
    }

    @Override
    public void addUnit(Unit u) {
        units.add(u);
    }

    public void addShuttle(Shuttle s) {
        airSystem.add(s);
    }

    // ---- WeaponSimContext: services the weapon subsystems reach back for ----

    @Override
    public void applyDamage(Unit target, float damage, float vsTurretMult) {
        boolean wasAlive = target.isAlive();
        int targetCover = grid.getCoverAt(target.cellX, target.cellY);
        float dr = COVER_DAMAGE_REDUCTION[Math.min(targetCover, COVER_DAMAGE_REDUCTION.length - 1)];
        float effectiveMult = (target instanceof MapTurret) ? vsTurretMult : 1f;
        target.hp -= damage * effectiveMult * (1f - dr);
        boolean died = wasAlive && !target.isAlive();
        if (died) {
            target.deathPoseIdx = rng.nextInt(4);
            deathsThisFrame.add(target);
            emitEquipmentDropIfApplicable(target);
        }
        // Morale drain — fires only for squad members. Solo units (turrets,
        // civilians) have no squad morale to bleed; their behaviors don't
        // consult MORALE_BROKEN. Hit drain always; death adds the extra drop.
        // Clamped to [0, 1] each step so a death-on-a-frame doesn't underflow.
        if (wasAlive && target.squadId != Unit.NO_SQUAD) {
            Squad sq = squads.get(target.squadId);
            if (sq != null) {
                float drop = MORALE_DROP_ON_HIT + (died ? MORALE_DROP_ON_DEATH : 0f);
                sq.morale = Math.max(0f, sq.morale - drop);
            }
        }
    }

    @Override
    public void postShot(ShotEvent shot) {
        activeShots.add(shot);
        shotsThisFrame.add(shot);
    }

    @Override
    public void queueDetonation(PendingDetonation det) {
        detonations.queue(det);
    }

    @Override
    public void spawnSmokingWreck(int x, int y) {
        smokingWrecks.add(new SmokingWreck(x, y, WRECK_LIFETIME,
                0.05f + rng.nextFloat() * 0.10f));
    }

    @Override
    public void rollFallbackOnHit(Unit target) {
        if (!target.isAlive()) return;
        if (target.fallbackTimer > 0f) return;
        if (target instanceof MapTurret) return;
        // GOAP-driven infantry (squad members without a mech loadout) own their
        // retreat through SurviveContact / BreakContact — the squad-level
        // morale system decides when to pull back and re-engage. The legacy
        // per-unit fall-back roll conflicts with that (it can yank a planter
        // off the charge site or a cordon holder off their doorway), so we
        // skip it here. Civilians (NO_SQUAD) and mechs ({@code mech != null},
        // no GOAP tree yet) keep the legacy roll until their own substitutes
        // land. Note: shares {@link Unit#fallbackCellX}/{@code fallbackCellY}
        // with BreakContact — fine since both paths can't fire on the same
        // unit at the same time given this gate.
        if (target.squadId != Unit.NO_SQUAD && target.mech == null) return;
        if (rng.nextFloat() >= FALLBACK_CHANCE) return;
        int[] fallback = TacticalScoring.findFallbackPosition(target, this);
        if (fallback[0] == target.cellX && fallback[1] == target.cellY) return;
        target.fallbackCellX = fallback[0];
        target.fallbackCellY = fallback[1];
        target.fallbackTimer = FALLBACK_DURATION;
        // Stale path no longer applies — target will re-path to the fall-back
        // cell on its next updateUnit pass.
        clearPath(target);
    }

    // ---- AirSimContext: services the AirSystem reaches back for during a tick ----

    @Override
    public boolean isCellOccupied(int x, int y) {
        if (!grid.inBounds(x, y)) return false;
        // Read the precomputed occupancy map instead of scanning units. The map
        // counts each unit's current cell + path destination, so a value > 0
        // means at least one unit physically stands on (x, y) right now.
        // Destination-only contributions count too, but a shuttle picking a
        // deboard cell should still avoid those — a marine en route to (x, y)
        // is about to be there.
        return (occupancyMap[y * grid.getWidth() + x] & 0xFF) > 0;
    }

    @Override
    public String nextMarineId() {
        return "m" + deboardedMarineCount++;
    }

    @Override
    public int mintSquad(Faction faction, Unit leader) {
        Squad squad = new Squad(nextSquadId++, faction);
        squad.leader = leader;
        squads.put(squad.id, squad);
        return squad.id;
    }

    public void addObjective(Objective o) {
        objectives.add(o);
    }

    /**
     * Drives the simulation forward. Accepts any real-time delta; internally
     * runs zero or more fixed 30Hz ticks until the accumulator is drained.
     * Returns immediately once the battle is complete.
     */
    public void advance(float dt) {
        // Clear unconditionally so a paused caller doesn't keep replaying the previous frame's events.
        shotsThisFrame.clear();
        shotsExpiredThisFrame.clear();
        deathsThisFrame.clear();
        smokePuffsThisFrame.clear();
        if (complete) return;
        tickAccumulator += dt;
        while (tickAccumulator >= TICK_DT) {
            tick();
            tickAccumulator -= TICK_DT;
            if (complete) break;
        }
    }

    private void tick() {
        // Backstop: if a caller (currently BattleSetup) hasn't registered
        // objectives, install the default eliminate-each-other pair so the
        // old behavior keeps working untouched. Run-once on first tick.
        if (objectives.isEmpty()) {
            objectives.add(new EliminateFactionObjective(Faction.MARINE, Faction.DEFENDER));
            objectives.add(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        }
        rebuildOccupancyMap();
        // Rebuild the attacker index BEFORE per-unit updates so target-
        // selection's crowding scoring (TacticalScoring.findBestTarget) sees a
        // consistent snapshot of last-tick's targets. We deliberately don't
        // re-rebuild mid-tick as units pick new targets — the snapshot model
        // means a squad's crowding cost reflects the previous frame, which
        // matches the prior O(U²) behavior's semantics anyway.
        rebuildAttackersByTarget();
        // Refresh squad-level awareness BEFORE individual unit updates so the
        // garrison/patrol behavior dispatch this tick sees fresh ENGAGED /
        // SUSPICIOUS / UNAWARE state. Solo units (squadId == NO_SQUAD) skip
        // the squad path entirely.
        updateSquadAlertLevels();
        // Morale recovery + hysteresis. Reads the freshly-set _engagedThisTick
        // flag from updateSquadAlertLevels: a squad out of contact this tick
        // recovers; a squad in contact holds. Runs before the GOAP replan so
        // SurviveContact relevance sees the up-to-date moraleBroken flag.
        updateSquadMorale();
        // Evaluate fallback chains after alert state is current: an engaged
        // garrison that's lost half its members reassigns to its FALLBACK_TO
        // link, and a squad whose members have all arrived at their new post
        // clears the in-progress flag. Runs after alerts (so we see fresh
        // aliveMembers) and before updateUnit (so the new home cells are
        // visible to garrison dispatch this same tick).
        updateSquadFallback();
        // Squad-level GOAP replan pass. Piggybacks the alert-update phase so
        // plans reflect THIS tick's fresh aliveMembers + centroid + alert
        // level before any unit executes. Serial today; the planner +
        // WorldStateBuilder + actions are designed for parallel execution
        // across squads (see roadmap/ai/README.md parallelism section) and
        // we'll fork-join here once we feel the cost.
        for (Squad squad : squads.values()) {
            GoapInfantryBehavior.replanIfNeeded(squad, this);
        }
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            updateUnit(u);
        }
        // Burst-fire rounds queued after a primary shot — fire them now so
        // they emit at the right per-weapon spacing without piling onto the
        // AI's single-decision-per-tick model. Lives on the InfantryWeapons
        // subsystem; this call drains every unit's burst state.
        infantry.tick(this);
        // Mech chassis weapons run on their own state bag (MechLoadoutState).
        // Continuation handling for chaingun bursts + SRM salvos, plus cooldown
        // tick-down for all three tracks. New triggers (start a burst / salvo /
        // LRM) come from CombatantBehavior; the subsystem pass also runs the
        // mech-wreck spawn for any chassis units that died this tick.
        heavy.tick(this);
        // Physics-based rocket/missile damage — each pending detonation ticks
        // down its arrival timer and applies splash + wall damage when it
        // expires. Pairs with the visual ShotEvent flight; the visual and the
        // damage are queued together and arrive together.
        detonations.tick(this);
        // Convert any turrets that just died into walkable rubble so the next
        // tick's pathfinding + zone graph sees the hole, and the floor pass
        // picks the cell up as rubble.
        demolishDeadTurrets();
        // Age smoking wrecks + emit any puff events that came due this tick.
        tickSmokingWrecks();
        // Air vehicles tick AFTER units so new deboarded marines aren't iterated
        // mid-loop. They'll be picked up by next tick's occupancy + target pass.
        airSystem.tick(this, TICK_DT);
        advanceShots();
        processEquipmentDrops();
        for (Objective o : objectives) o.tick(this);
        // Single zone-graph rebuild for the whole tick — drains any wall
        // breaches or turret demolishes that happened this tick. Multiple
        // breaches in one tick (e.g., a rocket shredding a wall section)
        // collapse into one rebuild.
        if (zoneGraphDirty) {
            zoneGraph.rebuild();
            zoneGraphDirty = false;
        }
        checkWinCondition();
    }

    /**
     * Returns the alive attackers currently aiming at {@code target}, or null
     * if no one is targeting it. The list is mutated in-place each tick by
     * {@link #rebuildAttackersByTarget()} — callers must not retain it across
     * tick boundaries.
     */
    public ArrayList<Unit> getAttackersOf(Unit target) {
        return attackersByTarget.get(target);
    }

    /**
     * Rebuilds {@link #attackersByTarget} from the current {@code Unit.target}
     * pointers. Recycles bucket lists via {@link #attackerListPool} so the
     * steady-state allocation is zero — buckets grow once, then live forever.
     *
     * <p>Skips dead attackers and dead targets so a unit holding a stale
     * pointer at its dying enemy doesn't pollute the next tick's lookup.
     */
    private void rebuildAttackersByTarget() {
        for (ArrayList<Unit> bucket : attackersByTarget.values()) {
            bucket.clear();
            attackerListPool.add(bucket);
        }
        attackersByTarget.clear();
        for (Unit u : units) {
            if (!u.isAlive() || u.target == null || !u.target.isAlive()) continue;
            ArrayList<Unit> bucket = attackersByTarget.get(u.target);
            if (bucket == null) {
                bucket = attackerListPool.isEmpty()
                        ? new ArrayList<>(4)
                        : attackerListPool.remove(attackerListPool.size() - 1);
                attackersByTarget.put(u.target, bucket);
            }
            bucket.add(u);
        }
    }

    /**
     * Counts alive units per cell into {@link #occupancyMap}, including each
     * unit's path destination cell (if different from its current cell). This
     * makes destination cells visible to firing-position and fall-back scoring,
     * so units don't all converge on the same goal. Saturates at 255.
     *
     * <p>The map is also incrementally updated within a tick — when a unit
     * re-paths in {@link #updateUnit}, the old destination is decremented and
     * the new one incremented — so units picking positions later in the same
     * tick see the freshest information.
     */
    private void rebuildOccupancyMap() {
        Arrays.fill(occupancyMap, (byte) 0);
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            incrementOccupancy(u.cellX, u.cellY);
            int destX = pathDestX(u);
            if (destX != Integer.MIN_VALUE) {
                int destY = pathDestY(u);
                if (destX != u.cellX || destY != u.cellY) {
                    incrementOccupancy(destX, destY);
                }
            }
        }
    }

    private void incrementOccupancy(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        int idx = y * grid.getWidth() + x;
        int cur = occupancyMap[idx] & 0xFF;
        if (cur < 255) occupancyMap[idx] = (byte) (cur + 1);
    }

    private void decrementOccupancy(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        int idx = y * grid.getWidth() + x;
        int cur = occupancyMap[idx] & 0xFF;
        if (cur > 0) occupancyMap[idx] = (byte) (cur - 1);
    }

    /** X coordinate of the unit's final path cell, or {@code Integer.MIN_VALUE} if the path is empty. Internal helper for occupancy bookkeeping. */
    private static int pathDestX(Unit u) {
        return u.path.length == 0 ? Integer.MIN_VALUE : u.path[u.path.length - 2];
    }
    /** Y coordinate of the unit's final path cell, or {@code Integer.MIN_VALUE} if the path is empty. */
    private static int pathDestY(Unit u) {
        return u.path.length == 0 ? Integer.MIN_VALUE : u.path[u.path.length - 1];
    }

    /**
     * Replaces a unit's path and keeps {@link #occupancyMap} in sync — the old
     * destination loses its occupancy contribution and the new destination
     * gains one (subject to start-cell guards). Public so AI behaviors in
     * {@code battle.ai} can route their movement through this method instead
     * of touching {@code u.path} directly. Pass {@link GridPathfinder#EMPTY_PATH}
     * (or call {@link #clearPath(Unit)}) to drop the current path.
     */
    public void setPath(Unit u, int[] newPath) {
        int oldDestX = pathDestX(u);
        int oldDestY = pathDestY(u);
        if (oldDestX != Integer.MIN_VALUE && (oldDestX != u.cellX || oldDestY != u.cellY)) {
            decrementOccupancy(oldDestX, oldDestY);
        }
        u.path = newPath;
        u.pathIdx = newPath.length == 0 ? 0 : 1;
        if (newPath.length > 0) {
            int newDestX = newPath[newPath.length - 2];
            int newDestY = newPath[newPath.length - 1];
            if (newDestX != u.cellX || newDestY != u.cellY) {
                incrementOccupancy(newDestX, newDestY);
            }
        }
    }

    /** Convenience: drop the unit's path. Equivalent to {@code setPath(u, GridPathfinder.EMPTY_PATH)}. */
    public void clearPath(Unit u) {
        setPath(u, GridPathfinder.EMPTY_PATH);
    }

    // advanceBursts moved to InfantryWeapons.tick — pumped from the tick loop
    // via `infantry.tick(this)`.

    /**
     * Flips destroyed-turret mount cells from non-walkable obstacle to walkable
     * rubble. Mirrors the wall-collapse half of {@link NavigationGrid#damageCell}:
     * opens the cell + edges, recomputes cover on the cell and its 4 cardinal
     * neighbors, tags topology rubble for the renderer, and rebuilds the zone
     * graph once if at least one turret went down this tick. Guarded by
     * {@link MapTurret#demolished} so successive ticks don't re-process a wreck.
     */
    private void demolishDeadTurrets() {
        for (Unit u : units) {
            if (!(u instanceof MapTurret)) continue;
            MapTurret t = (MapTurret) u;
            if (t.isAlive() || t.demolished) continue;
            grid.setWalkable(t.cellX, t.cellY, true);
            grid.openAllEdges(t.cellX, t.cellY);
            topology.setGroundKind(t.cellX, t.cellY, CellTopology.GroundKind.RUBBLE);
            grid.recomputeCoverAt(t.cellX, t.cellY);
            grid.recomputeCoverAt(t.cellX + 1, t.cellY);
            grid.recomputeCoverAt(t.cellX - 1, t.cellY);
            grid.recomputeCoverAt(t.cellX, t.cellY + 1);
            grid.recomputeCoverAt(t.cellX, t.cellY - 1);
            t.demolished = true;
            zoneGraphDirty = true;
            // Mount cell keeps smoking for a while so the player can see the
            // wreck is dead-and-cooling rather than just "gone".
            smokingWrecks.add(new SmokingWreck(t.cellX, t.cellY, WRECK_LIFETIME,
                    0.05f + rng.nextFloat() * 0.10f));
        }
    }

    /**
     * Ticks every queued {@link PendingDetonation}; when one's timer drains,
     * applies its splash damage at the endpoint and removes it from the list.
     * Reverse iteration for in-place removal.
     *
     * <p>This is the physics-based damage path — rockets / missiles fire,
     * fly visibly for {@code flightSec}, and damage whatever's at the endpoint
     * when they arrive (not when they launched). Friendly fire is ON: every
     * unit in radius takes damage regardless of faction.
     */
    // advancePendingDetonations + detonate moved to weapons/Detonations.
    // spawnMechWrecks moved to weapons/HeavyWeapons (pumped from heavy.tick).
    // Both subsystems own their own state; the sim just calls their tick()
    // from the tick loop and exposes spawnSmokingWreck + damageCell as
    // context primitives.

    /**
     * Ages each smoking wreck and emits a smoke-puff event when its per-wreck
     * timer expires. Puff radius tapers with remaining lifetime so a fresh
     * wreck billows and an old one wisps. Wrecks are removed from the list
     * when their lifetime hits zero.
     */
    private void tickSmokingWrecks() {
        for (int i = smokingWrecks.size() - 1; i >= 0; i--) {
            SmokingWreck w = smokingWrecks.get(i);
            w.remainingLifetime -= TICK_DT;
            if (w.remainingLifetime <= 0f) {
                smokingWrecks.remove(i);
                continue;
            }
            w.nextPuffTimer -= TICK_DT;
            if (w.nextPuffTimer > 0f) continue;
            float cooledFrac = Math.max(0.15f, w.remainingLifetime / w.totalLifetime);
            float radius = 0.40f + cooledFrac * 0.45f;
            smokePuffsThisFrame.add(new float[]{w.cellX + 0.5f, w.cellY + 0.5f, radius});
            w.nextPuffTimer = WRECK_PUFF_MIN_GAP
                    + rng.nextFloat() * (WRECK_PUFF_MAX_GAP - WRECK_PUFF_MIN_GAP);
        }
    }

    /** Ages every active shot by one tick and drops expired ones. Reverse iteration for in-place removal. */
    private void advanceShots() {
        for (int i = activeShots.size() - 1; i >= 0; i--) {
            ShotEvent s = activeShots.get(i);
            s.lifetime -= TICK_DT;
            if (s.lifetime <= 0f) {
                shotsExpiredThisFrame.add(s);
                activeShots.remove(i);
            }
        }
    }

    /**
     * Dispatch entry point — pulls fall-back out so it applies to every role,
     * then routes to the role-specific behavior. PLANTER / OBJECTIVE_CAMPER /
     * VIP currently fall through to combatant behavior; mission-specific
     * subsystems (SABOTAGE first) override these branches as they land.
     */
    /**
     * Routes the per-tick update for one unit. Fall-back is a pre-dispatch
     * override so it applies regardless of role; otherwise the per-role
     * behavior instance handles the unit. Behavior classes live in
     * {@code battle.ai}; this method holds no per-role logic.
     */
    private void updateUnit(Unit u) {
        if (u.fallbackTimer > 0f) {
            FallbackBehavior.INSTANCE.update(u, this);
            return;
        }
        behaviorFor(u.role).update(u, this);
    }

    private UnitBehavior behaviorFor(UnitRole role) {
        switch (role) {
            // PLANTER routes through CombatantBehavior → GoapInfantryBehavior.
            // The plant action is a squad-plan slot inside HoldPortalCordon
            // (Story J); the unit keeps role=PLANTER so ChargeSiteObjective.tick
            // still finds it on-site, but no longer has its own per-unit dispatch.
            case KIT_RETRIEVER:  return KitRetrieverBehavior.INSTANCE;
            case FLEE:           return FleeBehavior.INSTANCE;
            case TURRET:         return TurretBehavior.INSTANCE;
            case GARRISON:       return GarrisonBehavior.INSTANCE;
            case PATROL:         return PatrolBehavior.INSTANCE;
            case OBJECTIVE_CAMPER:
            case VIP:
            case COMBATANT:
            default:             return CombatantBehavior.INSTANCE;
        }
    }

    /**
     * Refreshes {@link SquadAlertLevel} on every registered squad. Promotion
     * rules:
     * <ul>
     *   <li><b>ENGAGED</b> — any living squadmate has LOS to an alive enemy
     *       combatant. {@code timeSinceContact} resets to zero and
     *       {@code lastSeenEnemy} captures that enemy's cell.</li>
     *   <li><b>SUSPICIOUS</b> — no current LOS, but a squadmate is in a
     *       fall-back (recently hit). The squad converges on the last known
     *       enemy cell so a patrol that gets sniped doesn't keep walking
     *       its route obliviously.</li>
     *   <li><b>UNAWARE</b> — neither of the above. After
     *       {@link Squad#ENGAGED_DECAY_SECONDS} of no contact an ENGAGED
     *       squad drops to SUSPICIOUS, and after another
     *       {@link Squad#SUSPICIOUS_DECAY_SECONDS} a SUSPICIOUS squad drops
     *       to UNAWARE. The decay lets garrisons hold their state across
     *       brief duck-behind-cover moments and patrols commit to
     *       investigation before giving up.</li>
     * </ul>
     *
     * <p>Empty squads (all members dead) are left in their last state — the
     * GC cleans them up on save; the next tick's behaviors won't dispatch
     * because no member is alive.
     */
    /** Cell radius around a squadmate inside which an enemy shot's origin counts as "audible gunfire" and promotes the squad to SUSPICIOUS. Bigger than weapon ranges so a distant firefight pulls patrols in to investigate — that's the whole point. */
    public static final float GUNFIRE_ALERT_RADIUS = 18f;

    /** Ratio of {@link Squad#aliveMembers} to {@link Squad#originalSize} at or below which a garrison triggers its FALLBACK_TO retreat. 0.5 = "lose half, fall back." */
    private static final float FALLBACK_TRIGGER_RATIO = 0.5f;
    /** Cell-radius around a unit's home cell at which they count as "arrived" for clearing the squad's fallbackInProgress flag. Generous so members don't stutter at the last step waiting on stragglers. */
    public static final float HOME_ARRIVAL_RADIUS = 2.0f;

    /**
     * Story A: cell range within which an enemy is considered "in the kill
     * zone" for a garrison squad's ambush trigger. The kill zone is intentionally
     * close — garrisons want the first shot to land, not to give away their
     * post at extreme range. {@code 8} cells matches typical rifle effective
     * range while staying short of the renderer's visibility horizon.
     */
    public static final int KILL_ZONE_RANGE_CELLS = 8;
    /**
     * Story A: consecutive sim ticks of LOS to a close enemy required before
     * the kill-zone gate trips. At {@code TICK_DT = 1/30 sec}, 6 ticks ≈ 0.2s
     * of stable sight — short enough to feel reflexive, long enough that a
     * single transient LoS frame (target dancing through a doorway) doesn't
     * spring the ambush prematurely.
     */
    public static final int KILL_ZONE_LOS_TICKS_THRESHOLD = 6;

    /**
     * Single per-tick pass that fills in every squad's derived aggregates
     * (alive member count, centroid, alert level) and drives the
     * ENGAGED/SUSPICIOUS/UNAWARE state machine.
     *
     * <p>Restructured from the prior squads-outer/units-inner form into a
     * units-outer pass that posts each alive unit's contribution to its
     * squad in one walk: increments the alive count, accumulates centroid,
     * notes if any member is in fall-back, and (only if the squad isn't
     * already tagged ENGAGED this tick) runs a LoS scan for visible enemies.
     * Engaged squads short-circuit subsequent LoS scans for the same squad
     * within this tick.
     *
     * <p>The audible-gunfire promotion only runs for squads that finished the
     * first pass still un-engaged. Final state transitions are applied once
     * per squad at the end.
     */
    private void updateSquadAlertLevels() {
        // Per-tick transient flags. Boxed onto Squad to keep allocation out of
        // the hot path; reset at the top so a dead squad's leftover flags
        // don't leak into next tick.
        for (Squad squad : squads.values()) {
            squad.aliveMembers = 0;
            squad.centroidX = 0f;
            squad.centroidY = 0f;
            squad._engagedThisTick = false;
            squad._suspiciousThisTick = false;
            squad._killZoneSightedThisTick = false;
        }

        // Pass 1: accumulate squad aggregates + per-squad engagement LoS.
        // For garrison squads (holdsFireUntilKillZone), also drive the kill-zone
        // LOS-stability counter — incremented when any squadmate has LOS to an
        // enemy within KILL_ZONE_RANGE_CELLS this tick, reset to 0 otherwise.
        // We track this independently of the early-exit ENGAGED flag because
        // the ENGAGED scan stops at the first sighted enemy, and we need the
        // tighter "close + visible" predicate for the kill-zone gate.
        for (Unit u : units) {
            if (!u.isAlive() || u.squadId == Unit.NO_SQUAD) continue;
            Squad squad = squads.get(u.squadId);
            if (squad == null) continue;
            squad.aliveMembers++;
            squad.centroidX += u.cellX;
            squad.centroidY += u.cellY;
            if (u.fallbackTimer > 0f) squad._suspiciousThisTick = true;

            // Kill-zone LOS scan for garrison squads only. Looks for ANY
            // squadmate with LOS to a close enemy combatant — a single
            // qualifying sighting per tick increments the counter for the
            // squad. The scan is keyed on holdsFireUntilKillZone so non-
            // garrison squads pay nothing.
            if (squad.holdsFireUntilKillZone && !squad._killZoneSightedThisTick) {
                for (Unit other : units) {
                    if (!other.isAlive() || other.faction == squad.faction) continue;
                    if (!other.type.combatant) continue;
                    int dx = other.cellX - u.cellX;
                    int dy = other.cellY - u.cellY;
                    if (dx * dx + dy * dy > KILL_ZONE_RANGE_CELLS * KILL_ZONE_RANGE_CELLS) continue;
                    if (!grid.hasLineOfSight(u.cellX, u.cellY, other.cellX, other.cellY)) continue;
                    squad._killZoneSightedThisTick = true;
                    break;
                }
            }

            // LoS scan only if no squadmate has tripped ENGAGED yet this tick —
            // one engaged squadmate is enough to commit the whole squad.
            if (squad._engagedThisTick) continue;
            for (Unit other : units) {
                if (!other.isAlive() || other.faction == squad.faction) continue;
                if (!other.type.combatant) continue;
                if (!grid.hasLineOfSight(u.cellX, u.cellY, other.cellX, other.cellY)) continue;
                squad._engagedThisTick = true;
                squad.lastSeenEnemyX = other.cellX;
                squad.lastSeenEnemyY = other.cellY;
                break;
            }
        }

        // Audible-gunfire promotion runs only for not-yet-engaged squads.
        // Iterate units once more, but only do the shot scan for squads that
        // still need promoting — the early-skip means engaged squads pay
        // nothing here.
        if (!activeShots.isEmpty()) {
            for (Unit u : units) {
                if (!u.isAlive() || u.squadId == Unit.NO_SQUAD) continue;
                Squad squad = squads.get(u.squadId);
                if (squad == null || squad._engagedThisTick || squad._suspiciousThisTick) continue;
                for (ShotEvent shot : activeShots) {
                    if (shot.shooterFaction == squad.faction) continue;
                    float dx = shot.fromX - (u.cellX + 0.5f);
                    float dy = shot.fromY - (u.cellY + 0.5f);
                    if (dx * dx + dy * dy <= GUNFIRE_ALERT_RADIUS * GUNFIRE_ALERT_RADIUS) {
                        squad._suspiciousThisTick = true;
                        squad.lastSeenEnemyX = Math.round(shot.fromX);
                        squad.lastSeenEnemyY = Math.round(shot.fromY);
                        break;
                    }
                }
            }
        }

        // Finalize: divide centroids, apply alert-state transitions.
        for (Squad squad : squads.values()) {
            if (squad.aliveMembers > 0) {
                squad.centroidX /= squad.aliveMembers;
                squad.centroidY /= squad.aliveMembers;
            }
            // Story A: garrison kill-zone LOS hysteresis. Increments when any
            // squadmate sighted a close enemy this tick; resets to 0 when no
            // close-LOS sighting was recorded. Only garrison squads keep this
            // counter — non-garrison squads have holdsFireUntilKillZone=false
            // and never get a sighting flagged (skipped in pass 1).
            if (squad.holdsFireUntilKillZone) {
                if (squad._killZoneSightedThisTick) {
                    if (squad.killZoneLosTicks < KILL_ZONE_LOS_TICKS_THRESHOLD) {
                        squad.killZoneLosTicks++;
                    }
                } else {
                    squad.killZoneLosTicks = 0;
                }
            }
            if (squad._engagedThisTick) {
                squad.alertLevel = SquadAlertLevel.ENGAGED;
                squad.timeSinceContact = 0f;
            } else if (squad._suspiciousThisTick) {
                if (squad.alertLevel != SquadAlertLevel.ENGAGED) {
                    squad.alertLevel = SquadAlertLevel.SUSPICIOUS;
                }
                squad.timeSinceContact = 0f;
            } else {
                squad.timeSinceContact += TICK_DT;
                if (squad.alertLevel == SquadAlertLevel.ENGAGED
                        && squad.timeSinceContact >= Squad.ENGAGED_DECAY_SECONDS) {
                    squad.alertLevel = SquadAlertLevel.SUSPICIOUS;
                } else if (squad.alertLevel == SquadAlertLevel.SUSPICIOUS
                        && squad.timeSinceContact >= Squad.ENGAGED_DECAY_SECONDS + Squad.SUSPICIOUS_DECAY_SECONDS) {
                    squad.alertLevel = SquadAlertLevel.UNAWARE;
                    squad.lastSeenEnemyX = -1;
                    squad.lastSeenEnemyY = -1;
                }
            }
        }
    }

    /**
     * Drives the FALLBACK_TO retreat chain for garrison squads. Runs once per
     * tick after {@link #updateSquadAlertLevels} so the fresh
     * {@link Squad#aliveMembers} is visible.
     *
     * <h2>Trigger pass</h2>
     * For each squad with an {@link Squad#assignedNode} that hasn't already
     * fired its one-shot fallback: if alive-strength drops to or below
     * {@link #FALLBACK_TRIGGER_RATIO} of {@link Squad#originalSize}, the squad
     * reassigns to the first {@link TacticalNode.LinkKind#FALLBACK_TO} target.
     * Each surviving member gets a new {@link Unit#homeCellX home cell} near
     * the new anchor (picked via {@link BattleSetup#pickCellsNear} so cover is
     * preserved at the new post). {@link Squad#fallbackInProgress} is set so
     * {@link com.dillon.starsectormarines.battle.ai.GarrisonBehavior} routes
     * members to their new homes regardless of alert level.
     *
     * <h2>Arrival pass</h2>
     * For each squad already mid-retreat: when every surviving member is
     * within {@link #HOME_ARRIVAL_RADIUS} of their new home, the in-progress
     * flag clears and the squad resumes normal garrison engagement at the
     * new post. The squad's alert level isn't reset — if there's still an
     * enemy in LoS, the next tick's promotion will pick it up.
     *
     * <p>Fallback is one-shot per squad to prevent cascading: a battered
     * garrison falls back once and then holds, even if the new post is also
     * overrun. Chained retreats would need explicit gating that we don't
     * have yet.
     */
    /**
     * Per-tick morale recovery + hysteresis flag update. Drain hooks live in
     * {@link #applyDamage} (per-hit + per-death); this pass only handles the
     * passive recovery side and the broken/cleared transitions.
     *
     * <p>Recovery gates on {@code !squad._engagedThisTick} — a squad with any
     * member who has LoS to an enemy this tick reads as still in contact and
     * doesn't recover. Capped by {@code aliveMembers / originalSize} so a
     * squad that's lost half its members can't climb back above 0.5 no matter
     * how long they hide; a third casualty drops the cap to 0.25 and they
     * stay broken indefinitely. Squads with {@code originalSize == 0} (an
     * unstamped sentinel — shouldn't happen post-Story-B but guarded for
     * safety) treat the cap as 1.0.
     *
     * <p>Hysteresis: {@link Squad#moraleBroken} flips true when morale dips
     * below {@link #MORALE_BROKEN_THRESHOLD}; flips false again once morale
     * climbs above the (higher) {@link #MORALE_CLEAR_THRESHOLD}. The gap
     * prevents a squad hovering near 0.3 from flickering between
     * SurviveContact and EliminateEnemies on every replan.
     */
    private void updateSquadMorale() {
        for (Squad squad : squads.values()) {
            if (squad.aliveMembers <= 0) continue;

            float cap = (squad.originalSize > 0)
                    ? (float) squad.aliveMembers / squad.originalSize
                    : 1f;
            if (!squad._engagedThisTick) {
                squad.morale = Math.min(cap, squad.morale + MORALE_RECOVERY_RATE * TICK_DT);
            } else {
                // In contact — no recovery; also re-clamp in case the cap
                // dropped (member died this tick) below current morale.
                squad.morale = Math.min(cap, squad.morale);
            }

            if (squad.moraleBroken) {
                if (squad.morale > MORALE_CLEAR_THRESHOLD) squad.moraleBroken = false;
            } else {
                if (squad.morale < MORALE_BROKEN_THRESHOLD) squad.moraleBroken = true;
            }
        }
    }

    private void updateSquadFallback() {
        for (Squad squad : squads.values()) {
            if (squad.assignedNode == null) continue;
            if (squad.aliveMembers == 0) continue;

            // Arrival pass for in-progress retreats.
            if (squad.fallbackInProgress) {
                if (allMembersHome(squad)) squad.fallbackInProgress = false;
                continue;
            }

            // Trigger pass — only fire once per squad.
            if (squad.fallbackTriggered) continue;
            if (squad.originalSize <= 0) continue;
            if ((float) squad.aliveMembers / squad.originalSize > FALLBACK_TRIGGER_RATIO) continue;
            List<TacticalNode> targets = squad.assignedNode.linkedTo(TacticalNode.LinkKind.FALLBACK_TO);
            if (targets.isEmpty()) continue;

            TacticalNode newNode = targets.get(0);
            assignFallbackHomes(squad, newNode);
            squad.assignedNode = newNode;
            squad.fallbackTriggered = true;
            squad.fallbackInProgress = true;
        }
    }

    /** True when every alive squad member is within {@link #HOME_ARRIVAL_RADIUS} of their home cell — caller treats that as "the retreat is finished." */
    private boolean allMembersHome(Squad squad) {
        for (Unit u : units) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (u.homeCellX < 0) continue;
            float dx = u.homeCellX - u.cellX;
            float dy = u.homeCellY - u.cellY;
            if (dx * dx + dy * dy > HOME_ARRIVAL_RADIUS * HOME_ARRIVAL_RADIUS) return false;
        }
        return true;
    }

    /**
     * Distributes new home cells around {@code newNode}'s anchor to every
     * surviving member of {@code squad}. Reuses
     * {@link BattleSetup#pickCellsNear} so the cover-sorted ordering is the
     * same one the original spawn used — the highest-rank survivors (iterated
     * in unit list order, which preserves spawn priority) take the best new
     * cover stacks.
     */
    private void assignFallbackHomes(Squad squad, TacticalNode newNode) {
        List<int[]> cells = BattleSetup.pickCellsNear(grid, newNode.anchorX, newNode.anchorY, 5, squad.aliveMembers);
        int idx = 0;
        for (Unit u : units) {
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (idx >= cells.size()) {
                // Out of cells — keep the survivor's current home so they
                // don't end up homeless. They'll just hold where they are.
                continue;
            }
            int[] cell = cells.get(idx++);
            u.homeCellX = cell[0];
            u.homeCellY = cell[1];
            // Wipe stale path — next garrison tick re-paths to the new home.
            clearPath(u);
        }
    }

    /**
     * Emits an {@link EquipmentDrop} at the dying unit's cell if they were
     * carrying a kit — i.e., a PLANTER (or a KIT_RETRIEVER whose pointer maps
     * to an objective). Skips drops that would point at a completed objective.
     * The drop is placed at the unit's current cell; mission code should
     * ensure the cell is walkable for normal combat, so retrieval is reachable.
     */
    private void emitEquipmentDropIfApplicable(Unit dead) {
        Objective carried = null;
        if (dead.role == UnitRole.PLANTER) {
            carried = dead.assignedObjective;
        } else if (dead.role == UnitRole.KIT_RETRIEVER && dead.equipmentDropTarget != null
                && !dead.equipmentDropTarget.consumed) {
            // Retriever was carrying nothing in-hand, but their target kit
            // is still on the ground. We don't emit a new drop — the existing
            // one remains in the world for someone else to grab.
            return;
        }
        if (carried == null || carried.isComplete()) return;
        equipmentDrops.add(new EquipmentDrop(dead.cellX, dead.cellY, carried));
    }

    /**
     * Per-tick sweep over active equipment drops:
     * <ol>
     *   <li>Any alive marine on a drop cell consumes it and is promoted to
     *       {@link UnitRole#PLANTER} with the drop's objective. Their old role
     *       is wiped — including any other kit they were currently chasing.</li>
     *   <li>Unconsumed drops without an assigned retriever recruit the nearest
     *       alive {@link UnitRole#COMBATANT} marine, promoting them to
     *       {@link UnitRole#KIT_RETRIEVER}. Existing planters and other
     *       retrievers are skipped so they keep their current task.</li>
     *   <li>Consumed drops fall off the list.</li>
     * </ol>
     */
    private void processEquipmentDrops() {
        if (equipmentDrops.isEmpty()) return;

        // Pickup pass — any marine standing on a drop cell takes the kit.
        for (EquipmentDrop drop : equipmentDrops) {
            if (drop.consumed) continue;
            if (drop.objective.isComplete()) { drop.consumed = true; continue; }
            for (Unit u : units) {
                if (!u.isAlive() || u.faction != Faction.MARINE) continue;
                if (u.cellX != drop.cellX || u.cellY != drop.cellY) continue;
                u.role = UnitRole.PLANTER;
                u.assignedObjective = drop.objective;
                u.equipmentDropTarget = null;
                drop.consumed = true;
                break;
            }
        }

        // Assignment pass — make sure each unconsumed drop has a retriever.
        for (EquipmentDrop drop : equipmentDrops) {
            if (drop.consumed) continue;
            if (hasLivingRetriever(drop)) continue;
            Unit nearest = nearestAvailableMarine(drop.cellX, drop.cellY);
            if (nearest != null) {
                nearest.role = UnitRole.KIT_RETRIEVER;
                nearest.equipmentDropTarget = drop;
                // Wipe any stale path so the retriever re-pathfinds to the drop
                // next tick instead of continuing toward their old target.
                clearPath(nearest);
            }
        }

        // Cleanup.
        for (int i = equipmentDrops.size() - 1; i >= 0; i--) {
            if (equipmentDrops.get(i).consumed) equipmentDrops.remove(i);
        }
    }

    private boolean hasLivingRetriever(EquipmentDrop drop) {
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            if (u.role == UnitRole.KIT_RETRIEVER && u.equipmentDropTarget == drop) return true;
        }
        return false;
    }

    /**
     * Nearest alive marine that isn't actively occupied with an incomplete
     * objective. Skip-by-state instead of skip-by-role so stale role labels
     * (e.g., a PLANTER whose site already blew but didn't tick through their
     * own update yet) don't strand drops with no retriever. Anyone idle —
     * combatant, finished planter, retriever whose kit got picked up — is
     * eligible. Returns null only when every alive marine is genuinely busy.
     */
    private Unit nearestAvailableMarine(int cx, int cy) {
        Unit best = null;
        float bestDist = Float.MAX_VALUE;
        for (Unit u : units) {
            if (!u.isAlive() || u.faction != Faction.MARINE) continue;
            if (u.role == UnitRole.PLANTER
                    && u.assignedObjective != null
                    && !u.assignedObjective.isComplete()) continue;
            if (u.role == UnitRole.KIT_RETRIEVER
                    && u.equipmentDropTarget != null
                    && !u.equipmentDropTarget.consumed) continue;
            float d = TacticalScoring.cellDistance(u.cellX, u.cellY, cx, cy);
            if (d < bestDist) {
                bestDist = d;
                best = u;
            }
        }
        return best;
    }

    /**
     * Advances a unit one tick along its current path. Public so behaviors
     * call this after re-pathing or as the last step of their per-tick
     * update.
     */
    public void advanceMovement(Unit u) {
        if (u.pathIdx >= u.pathCellCount()) return;

        int nextX = u.pathCellX(u.pathIdx);
        int nextY = u.pathCellY(u.pathIdx);
        float dx = nextX - u.cellX;
        float dy = nextY - u.cellY;
        float cellDist = (float) Math.sqrt(dx * dx + dy * dy);
        if (cellDist < 0.0001f) {
            u.pathIdx++;
            return;
        }

        float stepLength = u.moveSpeed * TICK_DT; // cell-units this tick
        u.moveProgress += stepLength / cellDist;

        if (u.moveProgress >= 1f) {
            u.cellX = nextX;
            u.cellY = nextY;
            u.renderX = u.cellX;
            u.renderY = u.cellY;
            u.moveProgress = 0f;
            u.pathIdx++;
        } else {
            u.renderX = u.cellX + dx * u.moveProgress;
            u.renderY = u.cellY + dy * u.moveProgress;
        }
    }

    /**
     * Stanced-fire convenience: most callers fire from a stationary position
     * (engage loops, garrisons, turrets, mech chassis) and don't need to
     * think about stance. Routes to {@link #fireShot(Unit, Unit,
     * com.dillon.starsectormarines.battle.weapons.FireStance)} with
     * {@link com.dillon.starsectormarines.battle.weapons.FireStance#STANCED}.
     * Callers firing while walking should call the stance-aware overload
     * with {@code MOVING} so the accuracy penalty applies.
     */
    public void fireShot(Unit shooter, Unit target) {
        fireShot(shooter, target, com.dillon.starsectormarines.battle.weapons.FireStance.STANCED);
    }

    /**
     * Stance-aware fire. {@link com.dillon.starsectormarines.battle.weapons.FireStance#STANCED}
     * preserves the base accuracy roll;
     * {@link com.dillon.starsectormarines.battle.weapons.FireStance#MOVING}
     * halves it. Implementation lives in
     * {@code battle/weapons/InfantryWeapons.java}; this method exists so AI
     * behaviors can call {@code sim.fireShot(...)} without reaching into the
     * subsystem accessor.
     */
    public void fireShot(Unit shooter, Unit target,
                         com.dillon.starsectormarines.battle.weapons.FireStance stance) {
        infantry.fireShot(this, shooter, target, stance);
    }

    /**
     * Delegates to {@link InfantryWeapons#fireSecondary}. Same delegation
     * rationale as {@link #fireShot}.
     */
    public void fireSecondary(Unit shooter, Unit target) {
        infantry.fireSecondary(this, shooter, target);
    }

    /**
     * Float-origin counterpart to {@link #fireShot(Unit, Unit)} for shooters
     * that aren't on the unit list — today, shuttle-mounted turrets fired by
     * {@link com.dillon.starsectormarines.battle.air.AirSystem}. Stats come
     * from {@code kind}; origin is a world-space float pair so a hovering
     * shuttle's mount fires from its actual rendered position rather than the
     * floored cell center.
     *
     * <p>Damage / cover / fall-back / death pipeline is identical to
     * {@link #fireShot} — implemented in terms of the same {@link WeaponSimContext}
     * methods so a single change to applyDamage or rollFallbackOnHit picks up
     * both fire paths. The vsTurret bonus is fixed at 1.0 because
     * {@link TurretKind} doesn't carry a per-kind multiplier yet; if mounted
     * heavy mortars ever want extra punch vs static defenses, this is the
     * lever.
     */
    public void fireShotFrom(float fromX, float fromY, Faction shooterFaction,
                             TurretKind kind, Unit target) {
        boolean hit = rng.nextFloat() < kind.accuracy;
        if (hit) {
            applyDamage(target, kind.damage, 1f);
            rollFallbackOnHit(target);
        }
        float toX, toY;
        if (hit) {
            toX = target.cellX + 0.5f;
            toY = target.cellY + 0.5f;
        } else {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            toX = target.cellX + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.cellY + 0.5f + (float) Math.sin(angle) * spread;
        }
        postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooterFaction,
                SHOT_LIFETIME, kind, null, null));
    }

    /**
     * Delegates to {@link HeavyWeapons#fireMechWeapon}. Kept on the sim's
     * surface because AI behaviors call {@code sim.fireMechWeapon(...)}
     * directly. Implementation lives in {@code battle/weapons/HeavyWeapons.java}.
     */
    public void fireMechWeapon(Unit shooter, Unit target, MechWeapon weapon) {
        heavy.fireMechWeapon(this, shooter, target, weapon);
    }

    /**
     * Delegates to {@link HeavyWeapons#fireMechWeapon} with explicit accuracy
     * multiplier. Used by the LRM indirect-fire path (no LOS = reduced acc).
     */
    public void fireMechWeapon(Unit shooter, Unit target, MechWeapon weapon, float accuracyMult) {
        heavy.fireMechWeapon(this, shooter, target, weapon, accuracyMult);
    }

    // advanceMechWeapons + spawnMechWrecks moved to HeavyWeapons.tick.

    /**
     * Applies damage from an external source (flyby strafing run) to a unit
     * already tracked by the sim. Mirrors the post-hit half of {@link #fireShot}:
     * cover-reduces, applies HP, emits death + equipment drop if applicable.
     * Skips accuracy roll (the overlay already decided the round connected) and
     * fall-back (strafes pin you down rather than break contact). No
     * {@link ShotEvent} is emitted — flyby tracers are drawn by the overlay
     * itself, not the ground combat tracer pass.
     */
    public void applyExternalDamage(Unit target, float damage) {
        if (target == null || !target.isAlive() || damage <= 0f) return;
        int cover = grid.getCoverAt(target.cellX, target.cellY);
        float dr = COVER_DAMAGE_REDUCTION[Math.min(cover, COVER_DAMAGE_REDUCTION.length - 1)];
        target.hp -= damage * (1f - dr);
        if (!target.isAlive()) {
            target.deathPoseIdx = rng.nextInt(4);
            deathsThisFrame.add(target);
            emitEquipmentDropIfApplicable(target);
        }
    }

    /**
     * Battle ends when one faction's objectives are all complete, or when any
     * of their objectives is failed (which immediately hands the win to the
     * opposing faction). Mutual-failure ties resolve to no winner.
     *
     * <p>The "complete" check is conjunctive per side ({@link Objective#isComplete()}
     * across all marine objectives, then all defender ones); "failed" is
     * disjunctive (any single failure flips the side to lost). With only the
     * default {@link EliminateFactionObjective} pair, this reduces to the old
     * "last faction standing" behavior — but mission-specific objectives
     * (charge sites, extraction, raid crates) layer in without changing this
     * code.
     */
    private void checkWinCondition() {
        boolean marineFailed = false, marineAllComplete = true, marineHasObjective = false;
        boolean defenderFailed = false, defenderAllComplete = true, defenderHasObjective = false;
        for (Objective o : objectives) {
            if (o.owningFaction() == Faction.MARINE) {
                marineHasObjective = true;
                if (o.isFailed()) marineFailed = true;
                if (!o.isComplete()) marineAllComplete = false;
            } else if (o.owningFaction() == Faction.DEFENDER) {
                defenderHasObjective = true;
                if (o.isFailed()) defenderFailed = true;
                if (!o.isComplete()) defenderAllComplete = false;
            }
        }
        boolean marineWin = marineHasObjective && marineAllComplete && !marineFailed;
        boolean defenderWin = defenderHasObjective && defenderAllComplete && !defenderFailed;
        if (!marineWin && !defenderWin && !marineFailed && !defenderFailed) return;
        complete = true;
        if (marineWin && !defenderWin)       winner = Faction.MARINE;
        else if (defenderWin && !marineWin)  winner = Faction.DEFENDER;
        else if (marineFailed && !defenderFailed) winner = Faction.DEFENDER;
        else if (defenderFailed && !marineFailed) winner = Faction.MARINE;
        else                                 winner = null;
    }

}
