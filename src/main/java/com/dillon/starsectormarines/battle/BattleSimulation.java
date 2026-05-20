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
import com.dillon.starsectormarines.battle.ai.StructureBehavior;
import com.dillon.starsectormarines.battle.ai.TacticalScoring;
import com.dillon.starsectormarines.battle.ai.TurretBehavior;
import com.dillon.starsectormarines.battle.ai.UnitBehavior;
import com.dillon.starsectormarines.battle.ai.goap.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.ai.goap.GoapMechBehavior;
import com.dillon.starsectormarines.battle.command.MissionCommand;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.map.CellTopology;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.objective.EliminateFactionObjective;
import com.dillon.starsectormarines.battle.objective.Objective;
import com.dillon.starsectormarines.battle.tactical.TacticalMap;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretKind;
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
import java.util.EnumMap;
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

    /** Base squad morale drained per non-fatal hit on a squadmate, at full strength (cap = 1.0). At lower caps the per-hit drain is scaled by {@code 1 / cap} — a heavily mauled squad is more brittle per shot, so a lone survivor (cap = 0.25) takes a 0.20 drain per hit and folds on the first incoming. */
    public static final float MORALE_DROP_ON_HIT   = 0.05f;
    /** Additional morale drop when a hit kills the squadmate. Kept absolute (not cap-scaled) — it's a one-off event correlated with the cap reduction that the death itself triggers. */
    public static final float MORALE_DROP_ON_DEATH = 0.30f;
    /** Base morale recovered per sim-second while the squad isn't being shot at ({@link Squad#timeSinceUnderFire} {@code >=} {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}), at full strength. Effective rate is scaled by cap so a mauled squad takes proportionally longer to reach their (lower) ceiling — at base 0.20 this gives a constant ~2.5s recovery to the clear threshold and ~5s to full cap across all squad sizes. */
    public static final float MORALE_RECOVERY_RATE = 0.20f;
    /** Cooldown between morale-drain events on a single squad (sim seconds). A burst of incoming bullets in one tick still counts as one drain — prevents a hail of fire from insta-breaking a full squad. At 0.2s a full squad endures sustained fire for ~2.8s before breaking (5 hits/sec × 0.05/hit = 0.25/sec, 0.7 margin). Doesn't shield mauled squads: their per-hit drain (0.05/cap) is large enough that a single hit folds them on the first cooldown window. */
    public static final float MORALE_DRAIN_COOLDOWN = 0.2f;
    /** Near-miss morale drain — applied when a hostile shot's endpoint lands near a squad member but no damage is taken. Cap-scaled like hit drain. Suppressing fire that doesn't connect still rattles them, just less than a landed hit. */
    public static final float MORALE_DROP_ON_NEAR_MISS = 0.01f;
    /** Squared cell-distance from a shot's endpoint to a squad member that counts as a "near miss." 2.25 = 1.5 cells radius. */
    public static final float NEAR_MISS_RADIUS_SQ = 2.25f;
    /** Hysteresis broken threshold, as a <em>fraction of cap</em>. Squad flips to broken when {@code morale < MORALE_BROKEN_THRESHOLD * cap}. Scaling by cap keeps the model coherent for mauled squads: a lone survivor (cap = 0.25) breaks below 0.075 absolute morale, a fresh squad (cap = 1.0) breaks below 0.30. */
    public static final float MORALE_BROKEN_THRESHOLD = 0.30f;
    /** Hysteresis clear threshold, as a <em>fraction of cap</em>. Broken squad reverts once {@code morale > MORALE_CLEAR_THRESHOLD * cap}. Fixes the pre-scaling pathology where a solo survivor's cap (0.25) was below the absolute clear threshold (0.5) and they could never recover. */
    public static final float MORALE_CLEAR_THRESHOLD  = 0.50f;
    /** Sim seconds since the last hit/near-miss on a squadmate before morale recovery resumes. Decouples recovery from raw LoS — a broken squad in cover that can see distant enemies (and is firing back) but isn't actually being shot at composes itself; a pinned-down squad still taking incoming stays locked. Without this gate, a fallback that lands on a still-exposed cell (BreakContact's picker minimizes exposure but doesn't guarantee a true hide) keeps {@code _engagedThisTick=true} every tick via STANCED return fire and the squad never recovers. */
    public static final float MORALE_RECOVER_AFTER_FIRE_SECONDS = 2.0f;

    /**
     * Sim-seconds between commander-tier slow ticks. The squad-GOAP replan
     * loop runs every {@link GoapInfantryBehavior#REPLAN_PERIOD} (2s today);
     * the commander runs at a slower cadence so strategic assignments don't
     * thrash. Set so each commander tick is roughly bracketed by one full
     * GOAP replan cycle — gives squads a chance to act on a fresh assignment
     * before the commander considers reassigning. Tune in playtest.
     */
    public static final float COMMANDER_TICK_PERIOD = 2.5f;

    private final NavigationGrid grid;
    private final CellTopology topology;
    private final List<Unit> units = new ArrayList<>();
    private final AirSystem airSystem = new AirSystem();
    /** Fog-of-war contributor set + per-building roof alpha targets. Constructed empty; {@link com.dillon.starsectormarines.battle.BattleSetup} swaps in the real {@link com.dillon.starsectormarines.battle.map.Buildings} when it hands the sim a map. */
    private com.dillon.starsectormarines.battle.map.Buildings buildings = com.dillon.starsectormarines.battle.map.Buildings.EMPTY;
    private final com.dillon.starsectormarines.battle.vision.PlayerVisionState visionState = new com.dillon.starsectormarines.battle.vision.PlayerVisionState();
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
    /** Persistent visual decals (bullet holes, craters, rubble) accumulated over the battle. Bounded by {@link com.dillon.starsectormarines.DevConfig#DECAL_SOURCE_CAP} with FIFO eviction via {@link java.util.ArrayDeque#pollFirst} — O(1) head removal, unlike {@code ArrayList.remove(0)} which would shift the whole tail per overflow. */
    private final java.util.ArrayDeque<Decal> decals = new java.util.ArrayDeque<>();
    /**
     * Monotonic count of decals ever added to {@link #decals}. Lets the render
     * layer's accumulator know how many new decals were spawned since it last
     * stamped, even when {@link #decals} has saturated at the cap and FIFO
     * eviction keeps {@code decals.size()} pinned — without this counter the
     * accumulator can't distinguish "no new decals" from "new decals arrived
     * but the head was evicted to make room." See {@code DecalAccumulator}.
     */
    private long decalsEverAdded = 0L;
    /** Active smoking wrecks parked at destroyed turret cells. Each emits a periodic smoke-puff event the renderer drains into the impact FX engine. */
    private final List<SmokingWreck> smokingWrecks = new ArrayList<>();
    /** Smoke-puff events queued this advance — each entry is {x, y, radiusCells}. Drained by the renderer per frame and cleared at the start of each advance. */
    private final List<float[]> smokePuffsThisFrame = new ArrayList<>();
    /** Wall-collapse dust-burst events queued this advance — each entry is {x, y}. Spawned by Detonations on wall collapses + by flyby tracer collapses. Drained by FlybyOverlay each frame and cleared at the start of each advance. */
    private final List<float[]> wallDustsThisFrame = new ArrayList<>();
    /** Fire-burst events queued this advance — each entry is {x, y, radiusCells}. Burn phase only; drained by the renderer per frame and cleared at the start of each advance. */
    private final List<float[]> fireBurstsThisFrame = new ArrayList<>();
    /** Total seconds a wreck stays alive after destruction. Burn phase up front, then a longer smoke-only tail so the player can still read "dead turret" minutes later. */
    private static final float WRECK_LIFETIME = 30f;
    /** Seconds at the start of the wreck's life during which it emits fire bursts in addition to smoke. After this, fire stops and only smoke continues for the remainder. Public so the screen-side lightmap pump can mirror this window for persistent wreck-fire lights. */
    public static final float WRECK_BURN_DURATION = 12f;
    /** Tail of the burn phase over which fire-burst emit probability tapers from 1 to 0. RNG-gated so the taper actually drops emissions. Public for the lightmap pump's intensity ramp. */
    public static final float WRECK_FIRE_FADE_DURATION = 2f;
    /** Min/max sim-seconds between smoke puffs on a single wreck. Jittered per emission so wrecks don't sync up. */
    private static final float WRECK_PUFF_MIN_GAP = 0.45f;
    private static final float WRECK_PUFF_MAX_GAP = 0.85f;
    /** Min/max sim-seconds between fire bursts on a single wreck. Tighter than smoke — fire is the more active, frequent emission during the burn phase. */
    private static final float WRECK_FIRE_MIN_GAP = 0.25f;
    private static final float WRECK_FIRE_MAX_GAP = 0.50f;
    /** Active impact smoke plumes parked at HE detonation sites. Lighter cousin of {@link SmokingWreck} — shorter lifetime, no fire phase, fractional cell positions. Pipes through the shared {@link #smokePuffsThisFrame} drain. */
    private final List<SmokePlume> smokePlumes = new ArrayList<>();
    /** Total sim-seconds an HE impact plume keeps emitting. Long enough to read as a lingering column rising off the impact site, short enough that overlapping rocket salvos don't pile into permanent smoke. */
    private static final float PLUME_LIFETIME = 5.0f;
    /** Min/max sim-seconds between puff emissions on a single plume. Tighter than wreck cadence — impact smoke is denser per-second during its brief life. */
    private static final float PLUME_PUFF_MIN_GAP = 0.18f;
    private static final float PLUME_PUFF_MAX_GAP = 0.32f;
    /** Dense, primitive-keyed squad lookup. fastutil's Int2ObjectOpenHashMap avoids the per-call Integer autobox that {@link #getSquad} would do on a {@code HashMap<Integer, Squad>} — and getSquad is hit per-unit per-tick from the behavior dispatch. */
    private final Int2ObjectMap<Squad> squads = new Int2ObjectOpenHashMap<>();

    /**
     * Per-faction strategic commanders. A faction with no entry here has no
     * commander tier active — its squads run on ambient ENGAGEMENT goals
     * with {@link Squad#assignedObjective} left null. Missions that want
     * commander-driven coordination (Conquest spreads marine squads across
     * charge sites via {@code ConquestCommand}) install one via
     * {@link #setCommander(Faction, MissionCommand)} during {@code BattleSetup}.
     *
     * <p>Ticked at {@link #COMMANDER_TICK_PERIOD} cadence — slower than the
     * per-squad GOAP replan — so strategic assignments don't thrash.
     */
    private final Map<Faction, MissionCommand> commanders = new EnumMap<>(Faction.class);

    /**
     * Sim-seconds accumulated since the last commander slow-tick. When this
     * crosses {@link #COMMANDER_TICK_PERIOD}, every registered commander
     * gets a {@link MissionCommand#tick(BattleSimulation)} call before the
     * per-squad GOAP replan pass, so squads that replan this tick see the
     * freshest assignment.
     */
    private float commanderTickAccumulator = 0f;

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
    /** In-flight {@link Projectile}s — slow-velocity AoE kinds ({@link com.dillon.starsectormarines.battle.turret.TurretKind#cellsPerSec} &gt; 0). Advanced + detonated by {@link #advanceProjectiles(float)} each tick. */
    private final List<Projectile> activeProjectiles = new ArrayList<>();
    /** Projectiles that arrived this tick — parallel to {@link #shotsExpiredThisFrame} for the impact-FX dispatch in the renderer. Cleared each tick. */
    private final List<Projectile> projectilesArrivedThisFrame = new ArrayList<>();
    /** Units that transitioned from alive to dead during the last {@link #advance(float)} call. Same lifecycle as {@link #shotsThisFrame}. */
    private final List<Unit> deathsThisFrame = new ArrayList<>();
    private final Random rng = new Random();

    /** Counter for IDs of marines deboarded from shuttles. Bumped via {@link #nextMarineId()} when {@link AirSystem} deboards. Format: "m0", "m1", ... matches the pre-shuttle setup convention. */
    private int deboardedMarineCount = 0;

    /** Per-cell unit count, rebuilt at the start of each tick. Passed to the pathfinder so units route around ally-held cells. */
    private final byte[] occupancyMap;

    /**
     * Bucketed spatial index over alive units, rebuilt once per tick. AI
     * scoring (exposure, threat density, allies-near-for-spread) queries
     * this instead of scanning the full unit list — keeps proximity work
     * O(units within radius) instead of O(total units). See
     * {@link UnitSpatialIndex} for sizing.
     */
    private final UnitSpatialIndex unitIndex;

    private float tickAccumulator = 0f;
    /** Monotonic sim-tick counter incremented at the top of every {@link #tick}. Read by per-hit gates that want to fire at most once per tick (e.g. {@link #rollReprioritizeOnHit}). */
    public int simTickIndex = 0;
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

    /**
     * Defense posts placed by {@link com.dillon.starsectormarines.battle.mapgen.bsp.DefensePostStamper}
     * (conquest only). Walked by {@link #demolishDeadTurrets} when a turret
     * dies to detect post-wide annihilation and release the garrison squad's
     * tight patrol radius. Empty for missions that don't stamp posts.
     */
    private List<DefensePost> defensePosts = java.util.Collections.emptyList();

    public BattleSimulation(NavigationGrid grid, CellTopology topology) {
        this.grid = grid;
        this.topology = topology;
        this.occupancyMap = new byte[grid.getWidth() * grid.getHeight()];
        this.unitIndex = new UnitSpatialIndex(grid.getWidth(), grid.getHeight());
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
        // Roof cave-in: a wall just collapsed, so any building cell adjacent
        // to this wall loses its roof (and drops a rubble decal). Without
        // this the roof stays intact while the wall under it is gone, which
        // reads jarringly. The four-neighbor reach is intentional — a single
        // wall hit peels at most two cells (one on each side for interior
        // partitions, just one for a perimeter wall).
        peelRoofAround(x, y);
        zoneGraphDirty = true;
        return true;
    }

    private void peelRoofAround(int wallX, int wallY) {
        destroyRoofCell(wallX - 1, wallY);
        destroyRoofCell(wallX + 1, wallY);
        destroyRoofCell(wallX, wallY - 1);
        destroyRoofCell(wallX, wallY + 1);
    }

    /**
     * True iff {@code target} is standing on a cell that's part of a building
     * and still has its roof intact — the discriminator for the aerial /
     * indirect-fire shield rule. Used by both the AoE pipeline (see
     * {@link com.dillon.starsectormarines.battle.weapons.Detonations}) and
     * the direct-fire aerial path (turret + flyby) so all elevated weapons
     * are intercepted by intact ceilings consistently.
     */
    public boolean isRoofShielded(Unit target) {
        if (target == null) return false;
        return topology.getBuildingId(target.cellX, target.cellY) != 0
                && !topology.isRoofDestroyed(target.cellX, target.cellY);
    }

    @Override
    public void destroyRoofCell(int x, int y) {
        if (!grid.inBounds(x, y)) return;
        if (topology.getBuildingId(x, y) == 0) return;
        if (topology.isRoofDestroyed(x, y)) return;
        topology.setRoofDestroyed(x, y, true);
        // Rubble decal at the cell center with a small jitter + random pose,
        // matching the ImpactDecals.PRESET HE-rubble look — sells the cave-in
        // as "ceiling collapsed onto this tile" rather than a clean wipe.
        float jx = x + 0.5f + (rng.nextFloat() * 2f - 1f) * 0.25f;
        float jy = y + 0.5f + (rng.nextFloat() * 2f - 1f) * 0.25f;
        int rubbleIdx = rng.nextFloat() < 0.5f
                ? com.dillon.starsectormarines.battle.DecalKind.RUBBLE.index
                : com.dillon.starsectormarines.battle.DecalKind.RUBBLE_ALT.index;
        addDecal(new com.dillon.starsectormarines.battle.Decal(
                jx, jy, rubbleIdx, rng.nextFloat() * 360f, 1.10f));
    }
    public List<Unit> getUnits()           { return units; }
    public List<Shuttle> getShuttles()     { return airSystem.getShuttles(); }
    public List<Objective> getObjectives() { return objectives; }
    public List<EquipmentDrop> getEquipmentDrops() { return equipmentDrops; }
    public List<Doodad> getDoodads()       { return doodads; }
    /** Building registry for the roof-render + fog-of-war passes. Never null. */
    public com.dillon.starsectormarines.battle.map.Buildings getBuildings() { return buildings; }
    /** Faction-contributor set for the fog-of-war reveal. */
    public com.dillon.starsectormarines.battle.vision.PlayerVisionState getVisionState() { return visionState; }
    /** Hands the sim the map's building registry. Called by BattleSetup after generation. Subsequent visibility passes will reveal/hide these buildings as contributor units move. */
    public void setBuildings(com.dillon.starsectormarines.battle.map.Buildings buildings) {
        this.buildings = buildings != null ? buildings : com.dillon.starsectormarines.battle.map.Buildings.EMPTY;
    }
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
    public java.util.Collection<Decal> getDecals() { return decals; }
    /** Monotonic count of decals ever added — see field doc on {@link #decalsEverAdded}. Read by the render layer's accumulator to drive incremental stamping that survives FIFO eviction at the cap. */
    public long getDecalsEverAdded() { return decalsEverAdded; }
    public void addDecal(Decal d) {
        decals.addLast(d);
        if (decals.size() > com.dillon.starsectormarines.DevConfig.DECAL_SOURCE_CAP) decals.pollFirst();
        decalsEverAdded++;
    }
    /** Smoke-puff events emitted by smoking wrecks during the last advance. Each entry is {x, y, radiusCells}. Drained by the renderer per frame. */
    public List<float[]> getSmokePuffsThisFrame() { return smokePuffsThisFrame; }
    /** Fire-burst events emitted by smoking wrecks during the last advance (burn phase only). Each entry is {x, y, radiusCells}. Drained by the renderer per frame. */
    public List<float[]> getFireBurstsThisFrame() { return fireBurstsThisFrame; }
    /** Wall-collapse dust-burst events queued this advance. Each entry is {x, y} at the collapsed cell's center. Drained by {@code FlybyOverlay} which owns the dust-particle pool. */
    public List<float[]> getWallDustsThisFrame() { return wallDustsThisFrame; }

    /**
     * {@link com.dillon.starsectormarines.battle.weapons.WeaponSimContext}
     * implementation — records a dust-burst event for the renderer to drain.
     * Cleared at the start of each advance with the other per-frame event lists.
     */
    @Override
    public void spawnDustBurst(float cellX, float cellY) {
        wallDustsThisFrame.add(new float[]{cellX, cellY});
    }
    /** Live smoking wrecks. Read-only view — the lightmap pump iterates this each frame to assert persistent wreck-fire lights during the burn phase. */
    public List<SmokingWreck> getSmokingWrecks() { return java.util.Collections.unmodifiableList(smokingWrecks); }
    /** Fighter wings committed to this battle. {@code FlybyOverlay} reads this on first tick and drives spawns from the per-wing schedules. Defaults to {@link FlybyRoster#EMPTY}; missions assign via {@link #setFlybyRoster}. */
    public FlybyRoster getFlybyRoster()    { return flybyRoster; }
    public void setFlybyRoster(FlybyRoster roster) { this.flybyRoster = roster != null ? roster : FlybyRoster.EMPTY; }
    public List<ShotEvent> getActiveShots(){ return activeShots; }
    public List<ShotEvent> getShotsThisFrame() { return shotsThisFrame; }
    /** Shots whose lifetime ended this advance — the "projectile arrived" event. Renderer reads this to spawn impact FX + arrival sounds at the moment a turret-shot sprite reaches its endpoint. */
    public List<ShotEvent> getShotsExpiredThisFrame() { return shotsExpiredThisFrame; }
    /** In-flight {@link Projectile}s — slow-velocity AoE kinds. Renderer reads positions for sprite + contrail drawing. */
    public List<Projectile> getActiveProjectiles() { return activeProjectiles; }
    /** Projectiles that arrived this tick — parallel to {@link #getShotsExpiredThisFrame} for the renderer's impact-FX dispatch. */
    public List<Projectile> getProjectilesArrivedThisFrame() { return projectilesArrivedThisFrame; }
    public List<Unit> getDeathsThisFrame()     { return deathsThisFrame; }
    public boolean isComplete()            { return complete; }
    public Faction getWinner()             { return winner; }
    /** Per-cell unit count, indexed by {@link NavigationGrid#index(int, int)}. Exposed for AI scoring; do not mutate directly — go through {@link #setPath}. */
    public byte[] getOccupancyMap()        { return occupancyMap; }
    /** Bucketed spatial index over alive units. Rebuilt at the top of each tick by {@link #tick()}. */
    public UnitSpatialIndex getUnitIndex() { return unitIndex; }
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
    /** Stamped defense posts (conquest only). Called once by {@code BattleSetup} right after construction; safe to pass null/empty for missions without posts. */
    public void setDefensePosts(List<DefensePost> posts) {
        this.defensePosts = (posts != null && !posts.isEmpty()) ? posts : java.util.Collections.emptyList();
    }

    @Override
    public void addUnit(Unit u) {
        units.add(u);
        // Mirror into the spatial index so callers running outside the
        // tick loop (test fixtures, AirSystem mid-tick deboard) see the
        // unit on the next AI query. tick() still does the full rebuild
        // each frame, so this is purely additive.
        unitIndex.add(u);
    }

    public void addShuttle(Shuttle s) {
        airSystem.add(s);
    }

    // ---- WeaponSimContext: services the weapon subsystems reach back for ----

    @Override
    public void applyDamage(Unit target, float damage, float vsTurretMult) {
        applyDamage(target, damage, vsTurretMult, 1.0f);
    }

    @Override
    public void applyDamage(Unit target, float damage, float vsTurretMult, float moraleImpact) {
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
            // Squad leader promotion — if the dead unit was leading a
            // squad, hand the badge to the closest still-alive member.
            // Preserves direction of travel: the new leader stands roughly
            // where the old one fell, so followers don't get yanked
            // sideways when the leader dies mid-maneuver. See
            // InfantryCohesion.cohesionOverride for how the leader cell
            // pulls cohesion. NO_SQUAD units (turrets, civilians, etc.)
            // skip — no leader to promote.
            if (target.squadId != Unit.NO_SQUAD) {
                Squad ls = squads.get(target.squadId);
                if (ls != null && ls.leader == target) {
                    ls.leader = pickPromotionCandidate(ls, target);
                }
            }
        }
        // Morale drain — fires only for squad members. Solo units (turrets,
        // civilians) have no squad morale to bleed; their behaviors don't
        // consult MORALE_BROKEN. Hit drain always; death adds the extra drop.
        // Clamped to [0, 1] each step so a death-on-a-frame doesn't underflow.
        if (wasAlive && target.squadId != Unit.NO_SQUAD) {
            Squad sq = squads.get(target.squadId);
            if (sq != null) {
                // Death drain stacks on top of hit drain and bypasses the
                // cooldown — a kill is a discrete event the model should
                // always reflect, even if the squad just took a hit a tick
                // ago. The hit-component of the drain only fires when the
                // cooldown is clear; otherwise a burst of multiple hits in
                // one tick would each stack their per-hit drain and break
                // a full squad in a single frame.
                float cap = (sq.originalSize > 0 && sq.aliveMembers > 0)
                        ? (float) sq.aliveMembers / sq.originalSize
                        : 1f;
                float drop = 0f;
                if (sq.moraleDrainCooldown <= 0f) {
                    float hit = (cap > 0f) ? MORALE_DROP_ON_HIT / cap : MORALE_DROP_ON_HIT;
                    drop += hit * moraleImpact;
                    sq.moraleDrainCooldown = MORALE_DRAIN_COOLDOWN;
                }
                if (died) drop += MORALE_DROP_ON_DEATH;
                if (drop > 0f) sq.morale = Math.max(0f, sq.morale - drop);
                // Recovery gate: a hit always counts as "under fire," even
                // when the drain cooldown blocked the drop. Otherwise a
                // sustained burst would only reset the timer on the first
                // bullet, letting recovery resume mid-volley.
                sq.timeSinceUnderFire = 0f;
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

    /** Read-only view of in-flight rocket / missile detonations. Used by squad-coordination scorers (avoid rocket volleys against an already-doomed turret). */
    public List<PendingDetonation> getInflightDetonations() {
        return detonations.getPending();
    }

    /**
     * Detonates a {@link PendingDetonation} this tick instead of going through
     * the in-flight queue. Used by callers whose visible flight is already
     * resolved (today: {@code FlybyOverlay} fighter missile, detonating on
     * contact with its target's AoE radius). Same damage / wall / roof / dust
     * pipeline as a queued detonation — just without the timer delay.
     */
    public void detonateNow(PendingDetonation det) {
        detonations.detonateNow(det, this);
    }

    @Override
    public void spawnSmokingWreck(int x, int y) {
        smokingWrecks.add(new SmokingWreck(x, y, WRECK_LIFETIME,
                0.05f + rng.nextFloat() * 0.10f));
    }

    @Override
    public void spawnSmokePlume(float x, float y) {
        smokePlumes.add(new SmokePlume(x, y, PLUME_LIFETIME));
    }

    /**
     * Base chance a hit triggers a mech target re-evaluation when the mech
     * still has line-of-sight to its current target. Moderate — keeps the
     * mech from twitchy-switching every burst it takes but eventually
     * forces a look when flankers stack up hits.
     */
    private static final float REPRIORITIZE_BASE_CHANCE = 0.35f;
    /**
     * Bump when the mech's current target has no LoS. Compounds with
     * {@link #REPRIORITIZE_BASE_CHANCE} for a much higher reprio chance —
     * the "I'm chasing what I can't see while someone else shoots me"
     * failure mode that prompted this hook.
     */
    private static final float REPRIORITIZE_NO_LOS_CHANCE = 0.85f;

    @Override
    public void rollReprioritizeOnHit(Unit target, Unit shooter) {
        if (!target.isAlive()) return;
        // Only target-latching units reprio — mechs (lock until reset) and
        // turrets (lock once and slew). Infantry GOAP refreshes targets on
        // the squad replan, so the per-hit reprio would just step on the
        // planner. Both qualifying types share the "locked on someone I
        // can't see while flankers shoot me free" failure mode.
        boolean qualifies = target.mech != null || target instanceof MapTurret;
        if (!qualifies) return;
        // One roll per sim-tick. A 4-marine squad firing in the same tick
        // would otherwise compound a 0.35 base chance into ~0.82 cumulative
        // — near-constant target twitching. Latch the tick index on first
        // attempt regardless of success/failure so subsequent hits this
        // tick wait for the next one.
        if (target.lastReprioTickIndex == simTickIndex) return;
        target.lastReprioTickIndex = simTickIndex;
        // No current target → next behavior tick will pick fresh anyway.
        if (target.target == null || !target.target.isAlive()) return;
        // Already targeting the shooter → no point re-rolling.
        if (shooter != null && target.target == shooter) return;
        // Reprio chance bumps heavily when current target is out of LoS —
        // chasing a target you can't see while taking incoming is the
        // failure mode this hook exists to break.
        boolean hasLosToCurrentTarget = grid.hasLineOfSight(
                target.cellX, target.cellY,
                target.target.cellX, target.target.cellY);
        float chance = hasLosToCurrentTarget ? REPRIORITIZE_BASE_CHANCE : REPRIORITIZE_NO_LOS_CHANCE;
        if (rng.nextFloat() >= chance) return;
        // Clear the target — next behavior tick (EngageAtCurrentBand /
        // OverwatchKillZone / BackstopAssignedSquad for mechs, TurretAim
        // for turrets) calls its target-picker on the null check.
        // findBestTarget already weights distance + LOS + threat density,
        // so a closer-with-LOS flanker beats an out-of-LOS chase target
        // naturally.
        target.target = null;
    }

    @Override
    public void rollFallbackOnHit(Unit target) {
        if (!target.isAlive()) return;
        if (target.fallbackTimer > 0f) return;
        if (target instanceof MapTurret) return;
        // GOAP-driven squad members — both infantry and mechs — own their
        // retreat through their per-squad planner now. Infantry routes
        // through SurviveContact / BreakContact via squad morale; mech
        // squads run on GoapMechBehavior with no flinch (Stage 1 design:
        // mechs are implacable; morale-driven mech retreat queues for a
        // later slice, see roadmap/ai/14-mech-stage1.md "Mech survival").
        // The legacy per-unit fall-back roll conflicts with both planners
        // (it can yank a planter off the charge site or break a mech's
        // overwatch posture), so we skip every squad member here.
        // Civilians (NO_SQUAD) keep the legacy roll — FleeBehavior depends
        // on it.
        if (target.squadId != Unit.NO_SQUAD) return;
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
     * Install (or replace) the strategic commander for one faction. Pass
     * {@code null} to clear an existing commander — the faction's squads
     * fall back to ambient ENGAGEMENT goals. Typically called once during
     * {@code BattleSetup} per faction that wants the layer.
     */
    public void setCommander(Faction faction, MissionCommand commander) {
        if (commander == null) {
            commanders.remove(faction);
        } else {
            commanders.put(faction, commander);
        }
    }

    /**
     * The commander for {@code faction}, or {@code null} if none is wired.
     * Read by debug UI and by integration tests that want to poke at
     * commander state directly.
     */
    public MissionCommand getCommander(Faction faction) {
        return commanders.get(faction);
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
        projectilesArrivedThisFrame.clear();
        deathsThisFrame.clear();
        smokePuffsThisFrame.clear();
        fireBurstsThisFrame.clear();
        wallDustsThisFrame.clear();
        if (complete) return;
        tickAccumulator += dt;
        while (tickAccumulator >= TICK_DT) {
            tick();
            tickAccumulator -= TICK_DT;
            if (complete) break;
        }
    }

    private void tick() {
        simTickIndex++;
        // Backstop: if a caller (currently BattleSetup) hasn't registered
        // objectives, install the default eliminate-each-other pair so the
        // old behavior keeps working untouched. Run-once on first tick.
        if (objectives.isEmpty()) {
            objectives.add(new EliminateFactionObjective(Faction.MARINE, Faction.DEFENDER));
            objectives.add(new EliminateFactionObjective(Faction.DEFENDER, Faction.MARINE));
        }
        // Fog-of-war visibility pass — recomputed every 3rd tick (~10 Hz at
        // 30 Hz sim). The render path lerps current→target alpha per frame so
        // this cadence stays invisible.
        if (simTickIndex % 3 == 0 && !buildings.isEmpty()) {
            com.dillon.starsectormarines.battle.vision.BuildingVisibilityPass.update(
                    buildings, units, grid, visionState);
        }
        rebuildOccupancyMap();
        // Rebuild the spatial index BEFORE the AI passes so per-tick scoring
        // (exposure, threat density, allies-near) reads a consistent
        // snapshot. Same single-pass-per-tick semantics as the attacker
        // index below — mid-tick repath shifts aren't reflected until next
        // tick, matching the pre-spatial behavior.
        unitIndex.rebuild(units);
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
        // Commander-tier slow tick. Runs at COMMANDER_TICK_PERIOD cadence,
        // before per-squad replan so any assignment written this tick is
        // visible to the GOAP relevance pass below. Skips entirely when no
        // commanders are registered (the common case for non-Conquest /
        // non-Assault missions). Per-faction order is enum-declaration
        // order via the EnumMap — deterministic across runs.
        if (!commanders.isEmpty()) {
            commanderTickAccumulator += TICK_DT;
            if (commanderTickAccumulator >= COMMANDER_TICK_PERIOD) {
                commanderTickAccumulator -= COMMANDER_TICK_PERIOD;
                for (MissionCommand cmd : commanders.values()) {
                    cmd.tick(this);
                }
            }
        }
        // Squad-level GOAP replan pass. Piggybacks the alert-update phase so
        // plans reflect THIS tick's fresh aliveMembers + centroid + alert
        // level before any unit executes. Serial today; the planner +
        // WorldStateBuilder + actions are designed for parallel execution
        // across squads (see roadmap/ai/README.md parallelism section) and
        // we'll fork-join here once we feel the cost.
        for (Squad squad : squads.values()) {
            if (squad.isMechSquad()) {
                GoapMechBehavior.replanIfNeeded(squad, this);
            } else {
                GoapInfantryBehavior.replanIfNeeded(squad, this);
            }
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
        // Simulated-projectile path — advance each in-flight Projectile by dt,
        // detonate its onArrival payload when remainingTime hits zero, and
        // emit an arrival record for the renderer's impact-FX dispatch.
        // Runs BEFORE detonations.tick so a projectile arriving this tick
        // contributes its detonation to the same wave as legacy queue entries.
        advanceProjectiles(TICK_DT);
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
        // Lingering smoke plumes parked at HE impact sites — same per-frame
        // puff drain as the wrecks, just on a shorter, fire-less timer.
        tickSmokePlumes();
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
            releaseGuardpostIfAllTurretsDead(t);
        }
    }

    /**
     * If {@code deadTurret} was part of a {@link DefensePost} and every turret
     * on that post is now dead, find the squad linked to the post and revert
     * its patrol radius to the wide default — so the garrison stops orbiting
     * the wreckage and resumes normal search-and-destroy via the existing
     * SUSPICIOUS/ENGAGED transitions. No-op for stand-alone turrets (legacy
     * scatter, port defenses outside conquest).
     *
     * <p>Linear scan through posts + units is fine here: turret deaths cap at
     * ~10-15 per battle, posts at ~5-8, units at the few hundred peak — total
     * work bounded and infrequent.
     */
    private void releaseGuardpostIfAllTurretsDead(MapTurret deadTurret) {
        if (defensePosts.isEmpty()) return;
        DefensePost owner = null;
        for (DefensePost post : defensePosts) {
            for (DefensePost.TurretSpec spec : post.turrets) {
                if (spec.cellX == deadTurret.cellX && spec.cellY == deadTurret.cellY) {
                    owner = post;
                    break;
                }
            }
            if (owner != null) break;
        }
        if (owner == null) return;
        // Check whether every turret on the owning post is now dead. A spec
        // with no live MapTurret at its cell counts as dead — covers both the
        // already-demolished and the never-spawned edge cases.
        for (DefensePost.TurretSpec spec : owner.turrets) {
            boolean aliveAtSpec = false;
            for (Unit u : units) {
                if (!(u instanceof MapTurret)) continue;
                if (u.cellX != spec.cellX || u.cellY != spec.cellY) continue;
                if (u.isAlive()) { aliveAtSpec = true; break; }
            }
            if (aliveAtSpec) return;
        }
        for (Squad squad : squads.values()) {
            if (squad.defensePost != owner) continue;
            squad.defensePost = null;
            squad.patrolRadius = PatrolBehavior.PATROL_DISTRICT_RADIUS;
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
     * Ages each smoking wreck and emits smoke/fire events on independent
     * jittered timers. Two-phase lifecycle:
     * <ul>
     *   <li>Burn phase (first {@link #WRECK_BURN_DURATION}s): fire bursts on
     *       tight cadence in addition to smoke. Emit probability tapers to 0
     *       over the trailing {@link #WRECK_FIRE_FADE_DURATION}s so the fire
     *       crossfades out cleanly rather than cutting.</li>
     *   <li>Smoke phase (remainder): smoke continues at its full cadence.</li>
     * </ul>
     * Separate timers per emitter so plumes interleave naturally instead of
     * spawning as paired emissions on the same frame.
     */
    private void tickSmokingWrecks() {
        for (int i = smokingWrecks.size() - 1; i >= 0; i--) {
            SmokingWreck w = smokingWrecks.get(i);
            w.remainingLifetime -= TICK_DT;
            if (w.remainingLifetime <= 0f) {
                smokingWrecks.remove(i);
                continue;
            }
            float age = w.totalLifetime - w.remainingLifetime;

            w.nextPuffTimer -= TICK_DT;
            if (w.nextPuffTimer <= 0f) {
                float cooledFrac = Math.max(0.15f, w.remainingLifetime / w.totalLifetime);
                float radius = 0.40f + cooledFrac * 0.45f;
                smokePuffsThisFrame.add(new float[]{w.cellX + 0.5f, w.cellY + 0.5f, radius});
                w.nextPuffTimer = WRECK_PUFF_MIN_GAP
                        + rng.nextFloat() * (WRECK_PUFF_MAX_GAP - WRECK_PUFF_MIN_GAP);
            }

            if (age < WRECK_BURN_DURATION) {
                w.nextFireTimer -= TICK_DT;
                if (w.nextFireTimer <= 0f) {
                    float burnRemaining = WRECK_BURN_DURATION - age;
                    float intensity = (burnRemaining < WRECK_FIRE_FADE_DURATION)
                            ? burnRemaining / WRECK_FIRE_FADE_DURATION
                            : 1f;
                    if (rng.nextFloat() < intensity) {
                        float fireRadius = 0.40f + rng.nextFloat() * 0.30f;
                        fireBurstsThisFrame.add(new float[]{w.cellX + 0.5f, w.cellY + 0.5f, fireRadius});
                    }
                    w.nextFireTimer = WRECK_FIRE_MIN_GAP
                            + rng.nextFloat() * (WRECK_FIRE_MAX_GAP - WRECK_FIRE_MIN_GAP);
                }
            }
        }
    }

    /**
     * Ages each smoke plume and emits puff events on a jittered timer. Per-puff
     * radius scales with the remaining-lifetime fraction so the plume billows
     * hard at impact and thins as it rises. Reuses the shared
     * {@link #smokePuffsThisFrame} drain that wrecks emit into — the renderer
     * already pulls from that list each frame.
     */
    private void tickSmokePlumes() {
        for (int i = smokePlumes.size() - 1; i >= 0; i--) {
            SmokePlume p = smokePlumes.get(i);
            p.remainingLifetime -= TICK_DT;
            if (p.remainingLifetime <= 0f) {
                smokePlumes.remove(i);
                continue;
            }
            p.nextPuffTimer -= TICK_DT;
            if (p.nextPuffTimer <= 0f) {
                float lifeFrac = p.remainingLifetime / p.totalLifetime;
                // Bigger puffs early (impact bloom), tightening as the column
                // rises. Floor keeps the tail-end column readable rather than
                // shrinking to invisible.
                float radius = 0.45f + Math.max(0.20f, lifeFrac) * 0.55f;
                smokePuffsThisFrame.add(new float[]{p.x, p.y, radius});
                p.nextPuffTimer = PLUME_PUFF_MIN_GAP
                        + rng.nextFloat() * (PLUME_PUFF_MAX_GAP - PLUME_PUFF_MIN_GAP);
            }
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
     * Advances every in-flight {@link Projectile} by {@code dt}. Intercepted
     * projectiles (point-defense future hook) are removed without detonating;
     * expired ones fire their {@link Projectile#onArrival} payload immediately
     * via {@link com.dillon.starsectormarines.battle.weapons.Detonations#detonateNow}
     * and land in {@link #projectilesArrivedThisFrame} for the renderer's
     * impact-FX dispatch. Reverse iteration for in-place removal.
     */
    private void advanceProjectiles(float dt) {
        for (int i = activeProjectiles.size() - 1; i >= 0; i--) {
            Projectile p = activeProjectiles.get(i);
            if (p.intercepted) {
                // Future: spawn intercept FX here. For now, just remove.
                activeProjectiles.remove(i);
                continue;
            }
            p.remainingTime -= dt;
            if (p.remainingTime <= 0f) {
                detonations.detonateNow(p.onArrival, this);
                projectilesArrivedThisFrame.add(p);
                activeProjectiles.remove(i);
            }
        }
    }

    /** Queues a {@link Projectile} for the per-tick advance. Called from the AoE projectile fire path in {@link #fireShotFrom}. */
    public void queueProjectile(Projectile p) {
        activeProjectiles.add(p);
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
            case STRUCTURE:      return StructureBehavior.INSTANCE;
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
     * Story A backstop: cumulative sim-seconds a garrison squad must take
     * LoS-confirmed incoming fire before the kill-zone gate is forced open,
     * regardless of whether the shooter ever entered the 8-cell kill zone.
     * Prevents a long-LOS attacker (mech with 30-40-cell range, distant
     * sniper) from chipping a holdsFireUntilKillZone garrison that's
     * forbidden to fire back. {@code 3s} is the user-feel sweet spot:
     * long enough that a one-off pot shot doesn't blow a planned ambush,
     * short enough that the squad isn't a free target for the rest of the
     * engagement.
     */
    public static final float KILL_ZONE_AMBUSH_BLOWN_SECONDS = 3.0f;

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
    /**
     * Picks the closest still-alive squad member to {@code deadLeader}'s
     * last cell — the unit that takes over as squad leader. Returns null
     * if no member is alive (squad fully wiped this same tick). "Closest
     * to former leader" is the user-chosen promotion rule: it preserves
     * the squad's direction of travel through the promotion event, so
     * followers don't pivot when the badge changes hands mid-maneuver.
     */
    private Unit pickPromotionCandidate(Squad squad, Unit deadLeader) {
        Unit best = null;
        float bestDistSq = Float.MAX_VALUE;
        int lx = deadLeader.cellX;
        int ly = deadLeader.cellY;
        for (Unit u : units) {
            if (u == deadLeader || !u.isAlive()) continue;
            if (u.squadId != squad.id) continue;
            int dx = u.cellX - lx;
            int dy = u.cellY - ly;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = u;
            }
        }
        return best;
    }

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
            squad._underFireAtLosThisTick = false;
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

        // Story A backstop: per-tick under-fire-at-LoS scan for garrison
        // squads with the kill-zone gate set. Mirrors WorldStateBuilder's
        // evalUnderFireAtLos predicate but runs every tick (not per replan)
        // so timeUnderSustainedFire accumulates accurately across the
        // 2-second replan window. Scoped to holdsFireUntilKillZone garrisons
        // because the field is only consumed by their gate override.
        if (!activeShots.isEmpty()) {
            for (Unit u : units) {
                if (!u.isAlive() || u.squadId == Unit.NO_SQUAD) continue;
                Squad squad = squads.get(u.squadId);
                if (squad == null || !squad.holdsFireUntilKillZone) continue;
                if (squad._underFireAtLosThisTick) continue;
                for (ShotEvent shot : activeShots) {
                    if (shot.shooterFaction == squad.faction) continue;
                    float dx = shot.toX - (u.cellX + 0.5f);
                    float dy = shot.toY - (u.cellY + 0.5f);
                    // Same 2-cell-squared "shot landed near me" gate the
                    // predicate evaluator uses — keeps the two paths in sync.
                    if (dx * dx + dy * dy > 4f) continue;
                    int fromCellX = (int) Math.floor(shot.fromX);
                    int fromCellY = (int) Math.floor(shot.fromY);
                    if (grid.hasLineOfSight(u.cellX, u.cellY, fromCellX, fromCellY)) {
                        squad._underFireAtLosThisTick = true;
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
                // Story A backstop: accumulate sim-time under LoS-confirmed
                // incoming fire. Monotonic — never resets within a battle so
                // the ambush-blown threshold is a one-way door. Once it
                // crosses KILL_ZONE_AMBUSH_BLOWN_SECONDS, WorldStateBuilder
                // forces the kill-zone predicate true regardless of enemy
                // proximity.
                if (squad._underFireAtLosThisTick) {
                    squad.timeUnderSustainedFire += TICK_DT;
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
     * <p>Recovery gates on "haven't been shot at recently"
     * ({@link Squad#timeSinceUnderFire} {@code >=} {@link #MORALE_RECOVER_AFTER_FIRE_SECONDS}),
     * not on raw LoS. A broken squad behind imperfect cover can see — and
     * fire opportunistically at — distant enemies and still compose itself
     * once incoming hits/near-misses lull. Pre-fix, any LoS kept
     * {@code _engagedThisTick=true} and locked the squad broken indefinitely
     * once BreakContact's picker landed them on a least-exposed-but-not-
     * truly-hidden cell. Capped by {@code aliveMembers / originalSize} so a
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
        // Near-miss drain pass: hostile shots that landed near a squadmate
        // but didn't connect still rattle the squad. Same cooldown gate as
        // hits — a hail of misses can't insta-break either. One drain event
        // per squad per tick max (the cooldown bails on the second pass).
        if (!shotsThisFrame.isEmpty()) {
            for (ShotEvent shot : shotsThisFrame) {
                if (shot.hit) continue;
                Squad target = squadHitByMiss(shot);
                if (target == null) continue;
                // Recovery gate: a near-miss always resets the "under fire"
                // timer, even when the drain cooldown blocks the morale drop.
                target.timeSinceUnderFire = 0f;
                if (target.moraleDrainCooldown > 0f) continue;
                float cap = (target.originalSize > 0 && target.aliveMembers > 0)
                        ? (float) target.aliveMembers / target.originalSize
                        : 1f;
                float base = (cap > 0f) ? MORALE_DROP_ON_NEAR_MISS / cap : MORALE_DROP_ON_NEAR_MISS;
                float drop = base * shot.moraleImpact;
                target.morale = Math.max(0f, target.morale - drop);
                target.moraleDrainCooldown = MORALE_DRAIN_COOLDOWN;
            }
        }

        for (Squad squad : squads.values()) {
            if (squad.aliveMembers <= 0) continue;

            // Tick "time since under fire" before reading it. Saturate to
            // avoid overflow on long quiet stretches — the threshold check
            // only cares about >= MORALE_RECOVER_AFTER_FIRE_SECONDS.
            if (squad.timeSinceUnderFire < 1e9f) squad.timeSinceUnderFire += TICK_DT;

            float cap = (squad.originalSize > 0)
                    ? (float) squad.aliveMembers / squad.originalSize
                    : 1f;
            // Recovery gates on "haven't been shot at recently," not on raw
            // LoS. A broken squad that pulled back to cover with imperfect
            // hide (BreakContact's picker minimizes exposure but the
            // geometry doesn't always allow a true hide) can still see — and
            // fire back at — distant enemies. As long as no incoming
            // hits/near-misses arrive within MORALE_RECOVER_AFTER_FIRE_SECONDS,
            // they compose themselves. Pre-fix: any LoS kept _engagedThisTick
            // true forever, locking the squad in SurviveContact.
            if (squad.timeSinceUnderFire >= MORALE_RECOVER_AFTER_FIRE_SECONDS) {
                // Recovery rate scales with cap: a mauled squad recovers
                // proportionally slower toward their (lower) ceiling. With
                // base 0.20 this gives constant ~2.5s time-to-clear and
                // ~5s time-to-full-cap across all squad sizes. A solo
                // survivor's previous 1.25s full recovery was too fast to
                // read as "they're composing themselves."
                float rate = MORALE_RECOVERY_RATE * cap;
                squad.morale = Math.min(cap, squad.morale + rate * TICK_DT);
            } else {
                // Under fire — no recovery; also re-clamp in case the cap
                // dropped (member died this tick) below current morale.
                squad.morale = Math.min(cap, squad.morale);
            }

            // Tick down the drain cooldown so the next incoming hit /
            // near-miss can register.
            if (squad.moraleDrainCooldown > 0f) {
                squad.moraleDrainCooldown = Math.max(0f, squad.moraleDrainCooldown - TICK_DT);
            }

            // Thresholds scale with cap so the model stays coherent for
            // mauled squads. Solo cap = 0.25 → broken below 0.075, clears
            // above 0.125. Without scaling, the absolute clear threshold
            // (0.5) was above solo cap (0.25) and they could never recover.
            float brokenAt = MORALE_BROKEN_THRESHOLD * cap;
            float clearAt  = MORALE_CLEAR_THRESHOLD  * cap;
            if (squad.moraleBroken) {
                if (squad.morale > clearAt) squad.moraleBroken = false;
            } else {
                if (squad.morale < brokenAt) squad.moraleBroken = true;
            }
        }
    }

    /**
     * Returns the friendly squad most affected by {@code shot} as a near
     * miss — first squad whose member is within {@link #NEAR_MISS_RADIUS_SQ}
     * cells of the shot's endpoint. Returns null when the shot is a self-
     * faction shot, no squad member is in range, or the shooter's faction
     * matches the candidate. One assignment per shot — a stray that grazes
     * two squad members only rattles one of them (the first found), which
     * matches the "single drain event per shot" intent.
     */
    private Squad squadHitByMiss(ShotEvent shot) {
        for (Squad sq : squads.values()) {
            if (sq.aliveMembers <= 0) continue;
            if (sq.faction == shot.shooterFaction) continue;
            for (Unit member : units) {
                if (!member.isAlive() || member.squadId != sq.id) continue;
                float dx = shot.toX - (member.cellX + 0.5f);
                float dy = shot.toY - (member.cellY + 0.5f);
                if (dx * dx + dy * dy <= NEAR_MISS_RADIUS_SQ) return sq;
            }
        }
        return null;
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
                             TurretKind kind, Unit target, boolean aerialShooter) {
        // Default to hasLos=true — direct-fire callers (every existing path)
        // already gate LoS in the aim loop, so by the time the shot is taken,
        // the shooter sees the target. Indirect-fire callers use the explicit
        // overload to pass the actual LoS state.
        fireShotFrom(fromX, fromY, shooterFaction, kind, target, aerialShooter, /*hasLos*/ true);
    }

    /**
     * LoS-aware fire. For indirect-fire kinds ({@link TurretKind#indirectFire})
     * applies two accuracy modifiers on top of the kind's base accuracy:
     * <ul>
     *   <li><b>Quadratic distance falloff</b> — effective accuracy scales by
     *       {@code 1 - (d/range)²}. Preserves base accuracy near the battery,
     *       drops off fast at the edge of envelope. Direct-fire kinds skip
     *       this; their effective accuracy is the flat per-kind value.</li>
     *   <li><b>No-LoS multiplier</b> — when {@code hasLos} is false, accuracy
     *       is also multiplied by {@link TurretKind#noLosAccuracyMult}.
     *       Reads as "battery firing on a spotted target without optics on it
     *       — the salvo lands in the area but individual rockets fly wider."
     *       Mirrors the mech LRM path's
     *       {@link com.dillon.starsectormarines.battle.MechWeapon#LRM_NO_LOS_ACC_MULT}.</li>
     * </ul>
     */
    public void fireShotFrom(float fromX, float fromY, Faction shooterFaction,
                             TurretKind kind, Unit target, boolean aerialShooter, boolean hasLos) {
        float distToTarget = (float) Math.sqrt(
                (target.cellX + 0.5f - fromX) * (target.cellX + 0.5f - fromX) +
                (target.cellY + 0.5f - fromY) * (target.cellY + 0.5f - fromY));
        float effectiveAccuracy = kind.accuracy;
        if (kind.indirectFire) {
            float distNorm = Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
            float distFalloff = Math.max(0f, 1f - distNorm * distNorm);
            float losMult = hasLos ? 1f : kind.noLosAccuracyMult;
            effectiveAccuracy *= distFalloff * losMult;
        }

        // Simulated-projectile path: kinds with a real velocity (cellsPerSec
        // > 0) spawn a Projectile entity that travels at dist/cellsPerSec,
        // detonates AoE on arrival, and is queryable mid-flight (point
        // defense future). Per the simplified model, no hit/miss roll —
        // accuracy widens the scatter cone, the AoE radius decides what gets
        // hurt at detonation.
        if (kind.cellsPerSec() > 0f) {
            spawnProjectile(fromX, fromY, shooterFaction, kind, target, aerialShooter,
                    distToTarget, effectiveAccuracy);
            return;
        }

        // Legacy path — direct-fire tracers (VULCAN, ARBALEST, HEPHAESTUS,
        // HEAVY_MG, ...) still roll hit/miss and emit a ShotEvent + (for AoE
        // sub-kinds) a PendingDetonation. Velocity is implicit in flightSec.
        boolean hit = rng.nextFloat() < effectiveAccuracy;
        boolean isAoe = kind.aoeRadius > 0f;
        // Aerial delivery if the shooter is elevated (shuttle mount) or the
        // weapon kind inherently lobs (grenade launcher). Used to decide
        // whether intact roofs intercept this shot.
        boolean aerialDelivery = aerialShooter || kind.arcHeight > 0f;
        float effectiveSpread = kind.hitSpread * Math.min(1f, distToTarget / kind.range);

        // Compute the visual endpoint with hit-spread / miss-scatter first —
        // damage application is deferred until after the wall raycast so a
        // raycast-blocked shot doesn't telepathically land damage past the
        // wall it actually splattered on.
        float toX, toY;
        if (hit) {
            toX = target.cellX + 0.5f;
            toY = target.cellY + 0.5f;
            // Endpoint scatter on a hit — purely visual for direct-fire kinds,
            // but for AoE it also scatters the splash center so a 4-round
            // grenade burst sprays the cell cluster instead of stacking on one.
            if (effectiveSpread > 0f) {
                float angle = rng.nextFloat() * (float) (Math.PI * 2);
                float r = rng.nextFloat() * effectiveSpread;
                toX += (float) Math.cos(angle) * r;
                toY += (float) Math.sin(angle) * r;
            }
        } else {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float spread = MISS_OFFSET_MIN + rng.nextFloat() * (MISS_OFFSET_MAX - MISS_OFFSET_MIN);
            // Misses get the baseline near-miss scatter plus the kind's
            // distance-scaled spread — a stray salvo at close range scatters
            // less than a stray salvo at long range.
            spread += effectiveSpread;
            toX = target.cellX + 0.5f + (float) Math.cos(angle) * spread;
            toY = target.cellY + 0.5f + (float) Math.sin(angle) * spread;
        }
        // Wall raycast — for ground-deployed area-spread weapons, a scattered
        // round that would fly past a wall instead splatters on that wall.
        // Air-mounted variants leave kind.raycastShots = false.
        com.dillon.starsectormarines.battle.weapons.ShotRaycast.Result snapped =
                com.dillon.starsectormarines.battle.weapons.ShotRaycast.resolve(
                        grid, kind.raycastShots, fromX, fromY, toX, toY, hit);
        toX = snapped.toX();
        toY = snapped.toY();
        hit = snapped.hit();
        // Direct-fire damage — applied after raycast so a wall-blocked shot
        // can correctly count as a miss. AoE kinds skip this path; their
        // damage resolves at endpoint via the Detonations pipeline below.
        if (!isAoe && hit) {
            // Aerial direct-fire (shuttle ARBALEST / HEPHAESTUS strafing) is
            // intercepted by an intact roof — the round physically can't
            // reach the unit underneath. Ground direct-fire (turret shooting
            // through a doorway) is governed by the 2D LOS check inside the
            // raycast and doesn't need this shield.
            if (!aerialDelivery || !isRoofShielded(target)) {
                applyDamage(target, kind.damage, 1f);
                rollFallbackOnHit(target);
            }
        }
        // AoE path — register the detonation on the queue. Lifetime matches
        // the projectile's visible flight time so the explosion lines up with
        // the rendered round arriving at the endpoint.
        if (isAoe) {
            float flight = kind.flightSec > 0f ? kind.flightSec : SHOT_LIFETIME;
            queueDetonation(new com.dillon.starsectormarines.battle.PendingDetonation(
                    toX, toY, flight,
                    kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                    kind.wallDamage, shooterFaction, aerialDelivery));
        }
        float lifetime = kind.flightSec > 0f ? kind.flightSec : SHOT_LIFETIME;
        postShot(new ShotEvent(fromX, fromY, toX, toY, hit, shooterFaction,
                lifetime, kind, null, null));
    }

    /**
     * Simulated-projectile fire path. Used by {@link #fireShotFrom} when the
     * kind has {@code cellsPerSec > 0}.
     *
     * <p>No hit/miss roll — accuracy widens the scatter cone, the AoE radius
     * at detonation decides who gets hurt. Endpoint is sampled from a uniform
     * circle around the target cell with radius
     * {@code hitSpread × min(1, dist/range) × (2 - effectiveAccuracy)}: a
     * perfect-accuracy shot has scatter equal to the base spread (still
     * non-zero — even precision artillery has dispersion), a half-accuracy
     * shot doubles it, a no-LoS shot with quadratic falloff at max range
     * goes to maximum scatter. Combined with the kind's
     * {@link Projectile#onArrival} AoE radius, the spray pattern of a salvo
     * blankets a believably wide area instead of stacking all rockets on the
     * target cell.
     *
     * <p>Builds a {@link PendingDetonation} as the projectile's arrival
     * payload — same damage / AoE / wall / roof / friendly-fire logic as the
     * legacy queueDetonation path, just owned by the Projectile so a future
     * point-defense intercept can cancel it atomically.
     */
    private void spawnProjectile(float fromX, float fromY, Faction shooterFaction,
                                 TurretKind kind, Unit target, boolean aerialShooter,
                                 float distToTarget, float effectiveAccuracy) {
        boolean aerialDelivery = aerialShooter || kind.arcHeight > 0f;

        // Scatter scales with (1 - accuracy) — at acc=1.0 the multiplier is
        // 1.0 (still some dispersion via hitSpread), at acc=0.0 it doubles.
        // Tied to the distance-scaled effectiveSpread so close shots still
        // cluster tightly even at low accuracy.
        float distScale = Math.min(1f, distToTarget / Math.max(0.0001f, kind.range));
        float accScatterMult = 2f - Math.max(0f, Math.min(1f, effectiveAccuracy));
        float scatterRadius = kind.hitSpread * distScale * accScatterMult;
        float angle = rng.nextFloat() * (float) (Math.PI * 2);
        float r = rng.nextFloat() * scatterRadius;
        float toX = target.cellX + 0.5f + (float) Math.cos(angle) * r;
        float toY = target.cellY + 0.5f + (float) Math.sin(angle) * r;

        float flightTime = distToTarget / kind.cellsPerSec();

        PendingDetonation onArrival = new PendingDetonation(
                toX, toY, flightTime,
                kind.aoeRadius, kind.damage, /*vsTurretMult*/ 1f,
                kind.wallDamage, shooterFaction, aerialDelivery);
        queueProjectile(new Projectile(fromX, fromY, toX, toY,
                kind, shooterFaction, aerialDelivery, flightTime, onArrival));
        // Pair with a ShotEvent so the existing audio (shotsThisFrame) +
        // impact-FX (shotsExpiredThisFrame) dispatchers run unchanged. Same
        // flight time keeps the two in sync — they expire on the same tick,
        // so arrival FX line up with the projectile reaching its endpoint.
        // The renderer skips projectile-sprite drawing for ShotEvents whose
        // kind is on the simulated path (cellsPerSec > 0) and reads position
        // from the paired Projectile instead.
        postShot(new ShotEvent(fromX, fromY, toX, toY, /*hit*/ true, shooterFaction,
                flightTime, kind, null, null));
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
