package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.air.AirSimContext;
import com.dillon.starsectormarines.battle.air.AirSystem;
import com.dillon.starsectormarines.battle.air.Shuttle;
import com.dillon.starsectormarines.battle.ai.CombatantBehavior;
import com.dillon.starsectormarines.battle.ai.FallbackBehavior;
import com.dillon.starsectormarines.battle.ai.FleeBehavior;
import com.dillon.starsectormarines.battle.ai.KitRetrieverBehavior;
import com.dillon.starsectormarines.battle.ai.PlanterBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.TurretBehavior;
import com.dillon.starsectormarines.battle.ai.UnitBehavior;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.objective.Objective;
import com.dillon.starsectormarines.battle.weapons.InfantryWeapons;
import com.dillon.starsectormarines.battle.weapons.WeaponSimContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final NavigationGrid grid;
    private final CellTopology topology;
    private final List<Unit> units = new ArrayList<>();
    private final AirSystem airSystem = new AirSystem();
    /** Handheld squad weapons (rifle / SMG / DMR / rocket launcher). Owns fireShot, fireSecondary, and the per-tick burst continuation pass. Pumped each tick via {@code infantry.tick(this)}; behavior call sites still go through the delegating {@link #fireShot} / {@link #fireSecondary} wrappers on this class. */
    private final InfantryWeapons infantry = new InfantryWeapons();
    private final List<Objective> objectives = new ArrayList<>();
    private final List<EquipmentDrop> equipmentDrops = new ArrayList<>();
    private final List<Doodad> doodads = new ArrayList<>();
    private final List<MapVehicle> vehicles = new ArrayList<>();
    /** Persistent visual decals (bullet holes, craters, rubble) accumulated over the battle. Bounded by {@link #DECAL_CAP} with FIFO eviction so a long firefight doesn't unbounded grow the render batch. */
    private final List<Decal> decals = new ArrayList<>();
    /** Soft cap on decal count — older decals get dropped from the head when this fills, so a long battle doesn't accumulate thousands of bullet holes. */
    private static final int DECAL_CAP = 600;
    /** Rockets / missiles in flight, scheduled to detonate at their endpoint when the timer drains. The physics-based damage path — paired with active {@link ShotEvent}s but separate (ShotEvent owns visuals; this owns damage). */
    private final List<PendingDetonation> pendingDetonations = new ArrayList<>();
    /** Active smoking wrecks parked at destroyed turret cells. Each emits a periodic smoke-puff event the renderer drains into the impact FX engine. */
    private final List<SmokingWreck> smokingWrecks = new ArrayList<>();
    /** Smoke-puff events queued this advance — each entry is {x, y, radiusCells}. Drained by the renderer per frame and cleared at the start of each advance. */
    private final List<float[]> smokePuffsThisFrame = new ArrayList<>();
    /** Total seconds a wreck keeps smoking after destruction. Long enough that the player notices "this turret is dead and smoldering" between glances. */
    private static final float WRECK_LIFETIME = 18f;
    /** Min/max sim-seconds between puffs on a single wreck. Jittered per emission so wrecks don't sync up. */
    private static final float WRECK_PUFF_MIN_GAP = 0.45f;
    private static final float WRECK_PUFF_MAX_GAP = 0.85f;
    private final Map<Integer, Squad> squads = new HashMap<>();
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

    /** Fighter wings committed to this battle. Lives on the sim so the overlay can read it without coupling to the briefing screen. */
    private FlybyRoster flybyRoster = FlybyRoster.EMPTY;

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
    public boolean damageCell(int x, int y, int amount) {
        if (!grid.damageCell(x, y, amount)) return false;
        // A wall that just collapsed is now walkable + a zone-graph portal
        // (handled inside grid.damageCell). Topology needs the visual swap:
        // clear WALL so the wall pass stops drawing tile art, set the ground
        // kind to RUBBLE so the floor pass picks the damaged-floor autotile.
        topology.setWall(x, y, false);
        topology.setGroundKind(x, y, CellTopology.GroundKind.RUBBLE);
        zoneGraph.rebuild();
        return true;
    }
    public List<Unit> getUnits()           { return units; }
    public List<Shuttle> getShuttles()     { return airSystem.getShuttles(); }
    public List<Objective> getObjectives() { return objectives; }
    public List<EquipmentDrop> getEquipmentDrops() { return equipmentDrops; }
    public List<Doodad> getDoodads()       { return doodads; }
    public void addDoodad(Doodad d)        { doodads.add(d); }
    /** Parked vehicles that occupy multi-cell footprints. Cells were flagged non-walkable at setup time, so the sim doesn't need to consult this list for pathing/LOS — only the renderer does. */
    public List<MapVehicle> getVehicles()  { return vehicles; }
    public void addVehicle(MapVehicle v)   { vehicles.add(v); }
    /** Persistent visual decals — bullet holes, craters, rubble. Pure render data; combat ignores them. */
    public List<Decal> getDecals()         { return decals; }
    public void addDecal(Decal d) {
        decals.add(d);
        if (decals.size() > DECAL_CAP) decals.remove(0);
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
        if (wasAlive && !target.isAlive()) {
            target.deathPoseIdx = rng.nextInt(4);
            deathsThisFrame.add(target);
            emitEquipmentDropIfApplicable(target);
        }
    }

    @Override
    public void postShot(ShotEvent shot) {
        activeShots.add(shot);
        shotsThisFrame.add(shot);
    }

    @Override
    public void queueDetonation(PendingDetonation det) {
        pendingDetonations.add(det);
    }

    @Override
    public void rollFallbackOnHit(Unit target) {
        if (!target.isAlive()) return;
        if (target.fallbackTimer > 0f) return;
        if (target instanceof MapTurret) return;
        if (rng.nextFloat() >= FALLBACK_CHANCE) return;
        int[] fallback = TacticalScoring.findFallbackPosition(target, this);
        if (fallback[0] == target.cellX && fallback[1] == target.cellY) return;
        target.fallbackCellX = fallback[0];
        target.fallbackCellY = fallback[1];
        target.fallbackTimer = FALLBACK_DURATION;
        // Stale path no longer applies — target will re-path to the fall-back
        // cell on its next updateUnit pass.
        setPath(target, Collections.emptyList());
    }

    // ---- AirSimContext: services the AirSystem reaches back for during a tick ----

    @Override
    public boolean isCellOccupied(int x, int y) {
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            if (u.cellX == x && u.cellY == y) return true;
        }
        return false;
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
        // LRM) come from CombatantBehavior; this pass just emits the queued rounds.
        advanceMechWeapons();
        // Physics-based rocket/missile damage — each pending detonation ticks
        // down its arrival timer and applies splash + wall damage when it
        // expires. Pairs with the visual ShotEvent flight; the visual and the
        // damage are queued together and arrive together.
        advancePendingDetonations();
        // Convert any turrets that just died into walkable rubble so the next
        // tick's pathfinding + zone graph sees the hole, and the floor pass
        // picks the cell up as rubble.
        demolishDeadTurrets();
        // Mech death → smoking wreck on the mech's cell. Idempotent via the
        // wreckSpawned latch on MechLoadoutState.
        spawnMechWrecks();
        // Age smoking wrecks + emit any puff events that came due this tick.
        tickSmokingWrecks();
        // Air vehicles tick AFTER units so new deboarded marines aren't iterated
        // mid-loop. They'll be picked up by next tick's occupancy + target pass.
        airSystem.tick(this, TICK_DT);
        advanceShots();
        processEquipmentDrops();
        for (Objective o : objectives) o.tick(this);
        checkWinCondition();
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
            int[] dest = pathDestination(u);
            if (dest != null && (dest[0] != u.cellX || dest[1] != u.cellY)) {
                incrementOccupancy(dest[0], dest[1]);
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

    /** Returns the unit's final path cell, or null if the path is empty. */
    private static int[] pathDestination(Unit u) {
        return u.path.isEmpty() ? null : u.path.get(u.path.size() - 1);
    }

    /**
     * Replaces a unit's path and keeps {@link #occupancyMap} in sync — the old
     * destination loses its occupancy contribution and the new destination
     * gains one (subject to start-cell guards). Public so AI behaviors in
     * {@code battle.ai} can route their movement through this method instead
     * of touching {@code u.path} directly.
     */
    public void setPath(Unit u, List<int[]> newPath) {
        int[] oldDest = pathDestination(u);
        if (oldDest != null && (oldDest[0] != u.cellX || oldDest[1] != u.cellY)) {
            decrementOccupancy(oldDest[0], oldDest[1]);
        }
        u.path = newPath;
        u.pathIdx = newPath.isEmpty() ? 0 : 1;
        int[] newDest = pathDestination(u);
        if (newDest != null && (newDest[0] != u.cellX || newDest[1] != u.cellY)) {
            incrementOccupancy(newDest[0], newDest[1]);
        }
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
        boolean anyDemolished = false;
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
            anyDemolished = true;
            // Mount cell keeps smoking for a while so the player can see the
            // wreck is dead-and-cooling rather than just "gone".
            smokingWrecks.add(new SmokingWreck(t.cellX, t.cellY, WRECK_LIFETIME,
                    0.05f + rng.nextFloat() * 0.10f));
        }
        if (anyDemolished) zoneGraph.rebuild();
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
    private void advancePendingDetonations() {
        for (int i = pendingDetonations.size() - 1; i >= 0; i--) {
            PendingDetonation det = pendingDetonations.get(i);
            det.remainingTime -= TICK_DT;
            if (det.remainingTime <= 0f) {
                detonate(det);
                pendingDetonations.remove(i);
            }
        }
    }

    /**
     * Applies a detonation: AoE damage to every unit within {@code aoeRadius}
     * with line of sight to the endpoint, plus wall HP damage at the endpoint
     * cell. Cover reduction applies per unit; vs-turret multiplier applies to
     * {@link MapTurret} targets. LOS-blocked units are spared (the wall
     * absorbed the splash for them), which keeps "duck behind cover" as a
     * real defensive option.
     */
    private void detonate(PendingDetonation det) {
        if (det.aoeRadius > 0f) {
            float r2 = det.aoeRadius * det.aoeRadius;
            int targetCx = (int) Math.floor(det.endpointX);
            int targetCy = (int) Math.floor(det.endpointY);
            for (Unit u : units) {
                if (!u.isAlive()) continue;
                float dx = (u.cellX + 0.5f) - det.endpointX;
                float dy = (u.cellY + 0.5f) - det.endpointY;
                if (dx * dx + dy * dy > r2) continue;
                // LOS from the detonation cell to the victim cell. Walls
                // between block the splash — gives marines hiding behind
                // walls a real reason to stay there.
                if (!grid.hasLineOfSight(targetCx, targetCy, u.cellX, u.cellY)) continue;
                int cover = grid.getCoverAt(u.cellX, u.cellY);
                float dr = COVER_DAMAGE_REDUCTION[Math.min(cover, COVER_DAMAGE_REDUCTION.length - 1)];
                float effectiveMult = (u instanceof MapTurret) ? det.vsTurretMult : 1f;
                boolean wasAlive = u.isAlive();
                u.hp -= det.damage * effectiveMult * (1f - dr);
                if (wasAlive && !u.isAlive()) {
                    u.deathPoseIdx = rng.nextInt(4);
                    deathsThisFrame.add(u);
                    emitEquipmentDropIfApplicable(u);
                }
            }
        }
        // Wall damage at the endpoint cell. damageCell silently no-ops on
        // walkable cells (the rocket hit ground, not a wall) — so this is
        // safe to call unconditionally for any HE round with wallDamage > 0.
        if (det.wallDamage > 0) {
            int cx = (int) Math.floor(det.endpointX);
            int cy = (int) Math.floor(det.endpointY);
            if (grid.inBounds(cx, cy)) {
                damageCell(cx, cy, det.wallDamage);
            }
        }
    }

    /**
     * Walks the unit list and emits a {@link SmokingWreck} on the cell of any
     * just-died mech ({@link MechLoadoutState#wreckSpawned} is the idempotency
     * latch). Runs once per tick after kill resolution. Catches mech deaths
     * from every code path — primary fire, mech crossfire, marine rockets,
     * flyby strafing — without duplicating spawn logic at each kill site.
     *
     * <p>Mech wrecks live the full {@link #WRECK_LIFETIME} window like turret
     * wrecks; the existing {@link #tickSmokingWrecks} pass drives the smoke
     * puff cadence. Unlike turrets, the mech's cell isn't flipped to walkable
     * rubble — the corpse sprite sits on the cell instead, which would be
     * inconsistent with letting marines path through it. (If playtest reads
     * "mech hulk should block path," easy follow-up.)
     */
    private void spawnMechWrecks() {
        for (Unit u : units) {
            if (u.isAlive()) continue;
            if (u.mech == null || u.mech.wreckSpawned) continue;
            smokingWrecks.add(new SmokingWreck(u.cellX, u.cellY, WRECK_LIFETIME,
                    0.05f + rng.nextFloat() * 0.10f));
            u.mech.wreckSpawned = true;
        }
    }

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
            case PLANTER:        return PlanterBehavior.INSTANCE;
            case KIT_RETRIEVER:  return KitRetrieverBehavior.INSTANCE;
            case FLEE:           return FleeBehavior.INSTANCE;
            case TURRET:         return TurretBehavior.INSTANCE;
            case OBJECTIVE_CAMPER:
            case VIP:
            case COMBATANT:
            default:             return CombatantBehavior.INSTANCE;
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
                setPath(nearest, Collections.emptyList());
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
        if (u.path.isEmpty() || u.pathIdx >= u.path.size()) return;

        int[] nextCell = u.path.get(u.pathIdx);
        float dx = nextCell[0] - u.cellX;
        float dy = nextCell[1] - u.cellY;
        float cellDist = (float) Math.sqrt(dx * dx + dy * dy);
        if (cellDist < 0.0001f) {
            u.pathIdx++;
            return;
        }

        float stepLength = u.moveSpeed * TICK_DT; // cell-units this tick
        u.moveProgress += stepLength / cellDist;

        if (u.moveProgress >= 1f) {
            u.cellX = nextCell[0];
            u.cellY = nextCell[1];
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
     * Delegates to {@link InfantryWeapons#fireShot}. Kept on the sim's
     * surface because AI behaviors call {@code sim.fireShot(...)} directly —
     * making them reach into a subsystem accessor would churn the call sites
     * for no real gain. The implementation now lives in
     * {@code battle/weapons/InfantryWeapons.java}.
     */
    public void fireShot(Unit shooter, Unit target) {
        infantry.fireShot(this, shooter, target);
    }

    /**
     * Delegates to {@link InfantryWeapons#fireSecondary}. Same delegation
     * rationale as {@link #fireShot}.
     */
    public void fireSecondary(Unit shooter, Unit target) {
        infantry.fireSecondary(this, shooter, target);
    }

    /**
     * Convenience overload — full accuracy. Used by all the precision-fire
     * code paths (chaingun, SRM, line-of-sight LRMs).
     */
    public void fireMechWeapon(Unit shooter, Unit target, MechWeapon weapon) {
        fireMechWeapon(shooter, target, weapon, 1.0f);
    }

    /**
     * Fires one round of a mech chassis weapon. Damage / accuracy / vsTurret
     * pull from the {@link MechWeapon} parameter rather than the shooter's
     * baked Unit stats — a single mech runs three concurrent weapon tracks
     * with very different numbers, so the weapon's profile drives the math.
     * Caller is responsible for cooldown / ammo / range gating before calling.
     *
     * <p>{@code accuracyMult} scales the weapon's base accuracy at the hit
     * roll. Set to 1.0 for line-of-sight fire; the LRM indirect-fire path
     * (no LOS to target) passes {@link MechWeapon#LRM_NO_LOS_ACC_MULT}.
     */
    public void fireMechWeapon(Unit shooter, Unit target, MechWeapon weapon, float accuracyMult) {
        // Accuracy roll determines endpoint placement (target cell on hit,
        // scattered on miss). For AoE weapons damage application is deferred
        // to detonation arrival; kinetic weapons (chaingun) apply damage at
        // fire time below since their flight is so short.
        boolean hit = rng.nextFloat() < weapon.accuracy * accuracyMult;
        boolean isAoe = weapon.aoeRadius > 0f;

        // KINETIC PATH — chainguns and any future no-AoE mech weapon. Damage
        // applies immediately to the locked target (same model fireShot uses
        // for marine primaries).
        if (!isAoe && hit) {
            boolean wasAlive = target.isAlive();
            int targetCover = grid.getCoverAt(target.cellX, target.cellY);
            float dr = COVER_DAMAGE_REDUCTION[Math.min(targetCover, COVER_DAMAGE_REDUCTION.length - 1)];
            float effectiveMult = (target instanceof MapTurret) ? weapon.vsTurretMult : 1f;
            target.hp -= weapon.damage * effectiveMult * (1f - dr);
            if (wasAlive && !target.isAlive()) {
                target.deathPoseIdx = rng.nextInt(4);
                deathsThisFrame.add(target);
                emitEquipmentDropIfApplicable(target);
            }
            // Roll fall-back on hit — same recipe as fireShot. Skipped for
            // turrets (they fight to the last HP) and already-broken units.
            if (target.isAlive() && target.fallbackTimer <= 0f
                    && !(target instanceof MapTurret)
                    && rng.nextFloat() < FALLBACK_CHANCE) {
                int[] fallback = TacticalScoring.findFallbackPosition(target, this);
                if (fallback[0] != target.cellX || fallback[1] != target.cellY) {
                    target.fallbackCellX = fallback[0];
                    target.fallbackCellY = fallback[1];
                    target.fallbackTimer = FALLBACK_DURATION;
                    setPath(target, Collections.emptyList());
                }
            }
        }

        float fromX = shooter.cellX + 0.5f;
        float fromY = shooter.cellY + 0.5f;
        float toX, toY;
        if (hit) {
            toX = target.cellX + 0.5f;
            toY = target.cellY + 0.5f;
            // Endpoint scatter — pure visual offset around the target cell.
            // For AoE weapons this also scatters the splash center, so a salvo
            // sprays the impact zone instead of stacking on one cell.
            if (weapon.hitSpread > 0f) {
                float angle = rng.nextFloat() * (float) (Math.PI * 2);
                float r = rng.nextFloat() * weapon.hitSpread;
                toX += (float) Math.cos(angle) * r;
                toY += (float) Math.sin(angle) * r;
            }
        } else {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            // Misses get the wider baseline scatter PLUS the weapon's hitSpread
            // — an indirect-fire weapon's misses scatter further than a rifle's.
            spread += weapon.hitSpread;
            toX = target.cellX + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.cellY + 0.5f + (float) Math.sin(angle) * spread;
        }

        // AOE PATH — queue a detonation at the endpoint. Damage resolves on
        // arrival via advancePendingDetonations / detonate. Hit-vs-miss only
        // affects WHERE the rocket lands; AoE math at impact decides who's
        // close enough to feel it.
        if (isAoe) {
            pendingDetonations.add(new PendingDetonation(
                    toX, toY, weapon.flightSec,
                    weapon.aoeRadius, weapon.damage, weapon.vsTurretMult,
                    weapon.wallDamage, shooter.faction));
        }

        float lifetime = weapon.flightSec > 0f ? weapon.flightSec : SHOT_LIFETIME;
        ShotEvent evt = new ShotEvent(fromX, fromY, toX, toY, hit, shooter.faction, lifetime,
                null, null, null, weapon);
        activeShots.add(evt);
        shotsThisFrame.add(evt);
    }

    /**
     * Per-tick mech-weapon pass — runs the three chassis tracks (chaingun
     * burst continuation, SRM salvo continuation, LRM cooldown drain) for every
     * unit with a {@link MechLoadoutState}. Mirrors the infantry burst pass for
     * the marine primary side; lives separate because the mech burst state is
     * on the loadout, not the unit.
     *
     * <p>The trigger decisions (start a burst / launch a salvo / lob an LRM)
     * happen inside the per-unit behavior. This pass only handles continuation
     * — emitting queued rounds at their proper spacing — and ticks down
     * the per-weapon cooldowns so the next trigger decision sees the right
     * gating.
     */
    private void advanceMechWeapons() {
        for (Unit u : units) {
            MechLoadoutState m = u.mech;
            if (m == null || !u.isAlive()) continue;

            if (m.chaingunCooldown > 0f) m.chaingunCooldown -= TICK_DT;
            if (m.srmCooldown      > 0f) m.srmCooldown      -= TICK_DT;
            if (m.lrmCooldown      > 0f) m.lrmCooldown      -= TICK_DT;

            // Chaingun burst continuation.
            if (m.chaingunBurstRemaining > 0) {
                m.chaingunBurstTimer -= TICK_DT;
                if (m.chaingunBurstTimer <= 0f) {
                    if (m.chaingunBurstTarget == null || !m.chaingunBurstTarget.isAlive()) {
                        m.chaingunBurstRemaining = 0;
                        m.chaingunBurstTarget = null;
                    } else {
                        fireMechWeapon(u, m.chaingunBurstTarget, m.chaingun);
                        m.chaingunBurstRemaining--;
                        m.chaingunBurstTimer = m.chaingun.burstSpacing;
                        if (m.chaingunBurstRemaining == 0) m.chaingunBurstTarget = null;
                    }
                }
            }

            // SRM salvo continuation.
            if (m.srmSalvoRemaining > 0) {
                m.srmSalvoTimer -= TICK_DT;
                if (m.srmSalvoTimer <= 0f) {
                    if (m.srmSalvoTarget == null || !m.srmSalvoTarget.isAlive()) {
                        m.srmSalvoRemaining = 0;
                        m.srmSalvoTarget = null;
                    } else {
                        fireMechWeapon(u, m.srmSalvoTarget, m.srmPod);
                        m.srmSalvoRemaining--;
                        m.srmSalvoTimer = m.srmPod.burstSpacing;
                        if (m.srmSalvoRemaining == 0) m.srmSalvoTarget = null;
                    }
                }
            }

            // LRM salvo continuation — same pattern as SRM. Locked target is
            // held across the whole 5-rocket wave so a single salvo reads as
            // one coordinated barrage instead of scatter fire across enemies.
            // LOS is recomputed per rocket: if marines pop into LOS mid-salvo,
            // the later rockets get full accuracy; if LOS drops mid-salvo, the
            // remaining rockets eat the indirect-fire penalty.
            if (m.lrmSalvoRemaining > 0) {
                m.lrmSalvoTimer -= TICK_DT;
                if (m.lrmSalvoTimer <= 0f) {
                    if (m.lrmSalvoTarget == null || !m.lrmSalvoTarget.isAlive()) {
                        m.lrmSalvoRemaining = 0;
                        m.lrmSalvoTarget = null;
                    } else {
                        boolean hasLos = grid.hasLineOfSight(
                                u.cellX, u.cellY,
                                m.lrmSalvoTarget.cellX, m.lrmSalvoTarget.cellY);
                        float accMult = hasLos ? 1.0f : MechWeapon.LRM_NO_LOS_ACC_MULT;
                        fireMechWeapon(u, m.lrmSalvoTarget, m.lrmArtillery, accMult);
                        m.lrmSalvoRemaining--;
                        m.lrmSalvoTimer = m.lrmArtillery.burstSpacing;
                        if (m.lrmSalvoRemaining == 0) m.lrmSalvoTarget = null;
                    }
                }
            }
        }
    }

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
