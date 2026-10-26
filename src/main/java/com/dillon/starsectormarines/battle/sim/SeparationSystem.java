package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.Airframe;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechLanceOrder;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.FireTeamGroups;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.BodyCapsule;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.vehicle.HullRam;
import com.dillon.starsectormarines.battle.unit.BodyRadius;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.Arrays;
import java.util.function.LongPredicate;

/**
 * Post-movement separation pass. It pushes overlapping ground units apart
 * over a few ticks instead of letting them stack on one point, and gives
 * moving squads a terrain-scaled, role-aware formation steer. The shared
 * slot engine spreads a mech lance into a useful weapons-platform footprint
 * and a marine fireteam into a smaller tactical interval on open ground,
 * compresses either through constrained terrain, and expands it afterward.
 * Design: {@code continuous-positions-nouns.md}.
 * Stateless consumer (Services/Systems shape): every field below is a
 * reusable scratch buffer, never battle state.
 *
 * <p><b>Participants.</b> A unit takes part iff it is a live grid occupant
 * with a positive footprint radius and is <em>not</em> steered by its own
 * continuous-flight body. Concretely: {@link UnitRosterService#isAliveById}
 * (mirrors {@code NavigationService.rebuildOccupancyMap}'s {@code gridOccupants}
 * query — POSITION present, not yet corpse-transmuted; every roster entity
 * already carries POSITION), {@code radius > 0} ({@link
 * com.dillon.starsectormarines.battle.unit.BodyRadius}, positive for every
 * body today), and {@code !}{@link World#hasKinematics}. The last clause is
 * what excludes drones: a drone is a dense-roster ground unit like any other
 * ({@code UnitRosterService.adopt} gives it POSITION/MOVEMENT same as
 * infantry), but its position is slaved to its {@code KINEMATICS} {@link
 * com.dillon.starsectormarines.battle.air.AirBody} every tick
 * ({@code DroneSwarmAction} writes {@code world.setPos} from the body) — a
 * separation nudge on top would just get overwritten next tick, so drones
 * opt out via the same "has a continuous-flight body" test that already
 * distinguishes them from ground movers elsewhere. Air craft and convoy
 * vehicles need no explicit exclusion: they're world-resident only (minted
 * via {@code allocateAir}/{@code allocateVehicle}), never enter the dense
 * roster this system walks, and carry no POSITION component at all.
 *
 * <p><b>Mass model.</b> A pair with a mech in it is weighed in tonnes, the one
 * mass model hulls and mechs share ({@link HullRam#bodyTonnes}): a Hound shoves a
 * marine aside almost entirely and a Bulwark outweighs a Hound, so the lighter
 * machine yields. Any other pair weighs each participant's {@code radius²}; a heavier
 * {@code b} yields less, so a mech (radius 0.6, mass
 * 0.36) shoves a marine (radius 0.3, mass 0.09) roughly 4× as far as the
 * marine shoves back. Static emplacements ({@link
 * com.dillon.starsectormarines.battle.unit.UnitType#isStatic()} — turrets
 * and drone hubs) are immovable — infinite mass: a mover overlapping one
 * yields the full overlap (weight 1), and an immovable unit never accumulates
 * an impulse of its own ({@link #accumulate} skips it as the outer
 * participant entirely).
 *
 * <p><b>Algorithm.</b> Two-phase, order-independent, single-threaded:
 * <ol>
 *   <li><b>Accumulate</b> — for each participant {@code a}, gather nearby
 *       participants via {@link UnitSpatialIndex#gather} (a tick-start
 *       snapshot used only to prune candidates; live positions are re-read
 *       for the actual distance test), and for each overlapping {@code b}
 *       accumulate a push vector into {@code a}'s scratch impulse slot.
 *       Moving squads with a coherent direction of travel then accumulate
 *       a weaker correction toward stable role-aware formation slots.
 *       Local terrain clearance blends each formation profile from its
 *       open-ground interval down to its compression floor. Every moving
 *       allied mech pair also receives the mech compression-floor spacing,
 *       while only members of one squad receive formation-slot steering.
 *       Every pair is naturally evaluated from both sides (once as {@code a}
 *       gathers {@code b}, once as {@code b} gathers {@code a}), so there is
 *       no half-pair bookkeeping.</li>
 *   <li><b>Apply</b> — clamp each accumulated impulse to {@link
 *       #MAX_PUSH_SPEED}{@code × dt}, topology-guard the resulting position
 *       (full move, else X-only slide, else Y-only slide, else drop the
 *       impulse), and write it back through the authoritative POSITION and
 *       MOVEMENT columns. The guard applies the same shared-edge and diagonal
 *       rules as A*, not merely destination-cell walkability.</li>
 * </ol>
 *
 * <p><b>Shape.</b> A body is a {@link BodyCapsule}: a segment along its
 * layered facing, swept by a radius. Every round body is a capsule of zero
 * length, and a pair of those takes exactly the circle path it always took.
 * When either body of a pair is elongated (the Rav Bloom Runner), the pair is
 * pushed apart along the line between the closest points of the two segments,
 * by the capsules' radii less that distance, with the same mass split. The
 * push moves the body's centre only; it never turns it. Facing belongs to
 * {@code FacingSystem}, which turns a body toward its target or the velocity it
 * applied, and a shove is folded into that velocity, so a body shoved hard
 * enough already turns with it at the combatant turn rate; a second writer of
 * facing here would be overwritten within the tick anyway.
 * {@code -Dbattle.separation.capsules=false} is the control: every body is the
 * circle of its {@link BodyRadius}.
 *
 * <p>Every applied displacement is also folded additively into the
 * {@code MOVEMENT} component's {@code MOVEMENT_VEL_X}/{@code Y} fields (the
 * same fields {@link MovementService#setVelocity} writes) so {@code
 * FacingSystem}, which derives pose from "velocity applied this tick",
 * animates a shoved unit instead of ghost-sliding it. Read via {@link
 * UnitRosterService#entityWorld()}/{@link UnitRosterService#components()}
 * rather than through {@code MovementService} because {@code setVelocity}
 * is private to that class and this is an additive fold-in, not a plain
 * overwrite.
 */
public final class SeparationSystem {

    /**
     * Ordinary neighbor-query radius, in cells — 2 × a mech's 0.6 plus a
     * per-tick motion margin, which covered every overlapping pair back when a
     * mech was the largest thing on the grid. Mechs use
     * {@link #MECH_FORMATION_QUERY_RADIUS} instead.
     *
     * <p><b>It no longer covers every pair.</b> A parked aircraft is sized by
     * its airframe — around four and a half cells for a Valkyrie — so a marine
     * overlapping one is found only inside this net, and settles just outside
     * it rather than clear of the hull. Widening the net is not free: it is the
     * per-unit per-tick candidate gather, and paying a five-cell query on every
     * marine to clear a handful of hardstands is the wrong trade. What actually
     * keeps people off a hull is a navigation footprint — which a garrison
     * berth does not stamp today, unlike a civilian scenery hull or a wreck.
     * Separation was never going to be that: its push is deliberately clamped
     * below walk speed, so a unit walking straight at a hull grinds through it
     * however wide the net is.
     */
    public static final float QUERY_RADIUS = 1.5f;
    /** {@code -Dbattle.separation.capsules=false} shoves every body as its circle. */
    public static final String CAPSULES_PROPERTY = "battle.separation.capsules";
    /** Covers one tick of motion beyond the spatial index's tick-start snapshot for a capsule's wider gather. */
    private static final float CAPSULE_QUERY_MARGIN = 0.25f;
    /** Fraction of a pair's overlap resolved per tick before the speed clamp — relaxation, not instant pop. */
    public static final float STIFFNESS = 0.5f;
    /** Cap on push distance per tick, in cells/sec — kept under walk speed (2.0) so separation never outruns intent. */
    public static final float MAX_PUSH_SPEED = 1.5f;
    /** Compression floor for moving allied mechs in streets and chokepoints. */
    public static final float MECH_FORMATION_MIN_DISTANCE = 2.5f;
    /** Normal nearest-neighbor spacing for one mech squad on open ground. */
    public static final float MECH_FORMATION_OPEN_DISTANCE = 6f;
    /** Compression floor for an infantry fireteam in a tight passage. */
    public static final float INFANTRY_FORMATION_MIN_DISTANCE = 0.75f;
    /** Normal nearest-neighbor spacing for an infantry fireteam on open ground. */
    public static final float INFANTRY_FORMATION_OPEN_DISTANCE = 1.75f;
    /** Gentle correction toward any squad member's terrain-scaled formation slot. */
    public static final float FORMATION_STIFFNESS = 0.08f;
    /** Formation correction cap, below the slowest formed unit's authored move speed. */
    public static final float FORMATION_MAX_SPEED = 0.75f;
    /**
     * The largest share of a walker's own step this tick that formation
     * steering may take back. Slotting needs to slow a member down (that is how
     * depth forms among men walking at one speed), but a pull that cancels the
     * whole step holds him still: at a bend in his route, where the follower's
     * step shrinks, a straggler pulled toward a slot well ahead once stood for
     * a quarter of a minute.
     */
    static final float FORMATION_MAX_SLOWDOWN = 0.5f;
    /** Fully walkable square radius that qualifies the local footprint as open ground. */
    static final int MECH_FORMATION_OPEN_CLEARANCE = 3;
    /** Infantry needs less clear ground before it can resume normal interval. */
    static final int INFANTRY_FORMATION_OPEN_CLEARANCE = 2;
    /** Below this mean path-heading agreement, members keep their individual orders. */
    static final float FORMATION_MIN_HEADING_COHERENCE = 0.65f;
    /** Open-ground interval between neighboring fire-team anchors. */
    static final float FIRE_TEAM_LATERAL_INTERVAL = 4f;
    /** Wing-team setback that turns three anchors into a shallow advance arc. */
    static final float FIRE_TEAM_ARC_DEPTH = 1.25f;
    /** Upcoming path cells inspected before formation steering yields to a portal or narrow run. */
    static final int INFANTRY_CLEARANCE_LOOKAHEAD = 3;
    /** Compression-floor radius plus enough slack for one tick of ordinary mech movement. */
    private static final float MECH_FORMATION_QUERY_RADIUS = 2.75f;
    /** A mech faster than this is walking: what stands ahead of it is shoved off its line (cells a second). */
    private static final float MECH_WALKING_SPEED = 0.15f;
    /** Nearer a walking mech's line than this, a body is dead ahead of it, and goes to the mech's left. */
    private static final float MECH_LINE_TIE = 0.05f;

    /** Below this ratio of sideways to inward walking speed, a mover meets an immovable body head-on and steps aside. */
    private static final float HEAD_ON_TANGENT = 0.25f;

    /** Below this separation distance, two units are treated as coincident and steered apart by the deterministic id-hash tiebreak instead of a (division-by-zero) normalized delta. */
    private static final float COINCIDENT_EPS = 1e-4f;

    /**
     * Ordinary battle ids use direct addressing for the per-pass collision
     * slot lookup. The roster still owns the authoritative sparse fallback
     * for an unusually large externally adopted id.
     */
    private static final int MAX_DIRECT_COLLISION_ID = 1 << 20;

    private static final byte PARTICIPATES = 1;
    private static final byte IMMOVABLE = 1 << 1;
    private static final byte MECH = 1 << 2;
    private static final byte HAS_MECH_LOADOUT = 1 << 3;
    private static final byte ESCAPE_ACTIVE = 1 << 4;
    private static final byte ACTIVE_PATH = 1 << 5;
    /** Scratch-integrity marker: this dense slot was populated by the current table walk. */
    private static final byte POPULATED = 1 << 6;

    private final UnitRosterService roster;
    private final World world;
    private final UnitSpatialIndex unitIndex;
    private final NavigationGrid grid;
    private final EntityWorld entityWorld;
    private final BattleComponents components;
    private final MovementService movement;

    /** Reused neighbor-query output buffer — cleared and repopulated by every {@link UnitSpatialIndex#gather} call inside {@link #tick}. */
    private final LongBucket scratch = new LongBucket();
    /**
     * Per-dense-slot accumulated impulse, X and Y — grown (never shrunk) to
     * roster capacity by {@link #ensureCapacity}, zeroed over {@code [0,
     * liveCount)} at the top of every {@link #tick}. Indexed by the same
     * dense roster index used for the accumulate and apply passes within one
     * call — safe because neither phase spawns or releases units, so the
     * roster's dense order can't shift mid-call.
     */
    private float[] impulseX = new float[0];
    private float[] impulseY = new float[0];
    /**
     * Per-tick collision view keyed by dense roster slot. Separation evaluates
     * the same nearby unit many times, so its immutable-for-this-pass position,
     * footprint and classifications are read once instead of re-probing ECS
     * components for every candidate pair.
     */
    private float[] collisionX = new float[0];
    private float[] collisionY = new float[0];
    private float[] collisionRadius = new float[0];
    private float[] collisionMass = new float[0];
    /** Mass in tonnes ({@link HullRam#bodyTonnes}), read for any pair a mech is in. */
    private float[] collisionTonnes = new float[0];
    private byte[] collisionFlags = new byte[0];
    private byte[] collisionFaction = new byte[0];
    /**
     * The capsule each slot is shoved as: segment midpoint, half-extent along
     * the facing, swept radius, and how far it reaches from the position. A
     * round body has a zero half-extent, its midpoint at its position and its
     * footprint radius.
     */
    private float[] shapeX = new float[0];
    private float[] shapeY = new float[0];
    private float[] shapeHalfX = new float[0];
    private float[] shapeHalfY = new float[0];
    private float[] shapeRadius = new float[0];
    private float[] shapeReach = new float[0];
    /** The velocity each slot's mover applied this tick, before this pass: what it was walking at. */
    private float[] collisionVelX = new float[0];
    private float[] collisionVelY = new float[0];
    private boolean[] elongated = new boolean[0];
    /** Farthest any elongated participant reaches from its position this pass, or 0. */
    private float maxCapsuleReach;
    /** Closest-points return buffer. */
    private final float[] closest = new float[4];
    private final boolean capsules;
    /** Bodies whose position another body owns this tick (a Runner's load); they take no part. */
    private LongPredicate held = id -> false;
    /**
     * Entity id to collision-slot-plus-one for the current pass. Entries are
     * grow-and-stay and need no full-array clear: {@link #collisionSlot}
     * verifies the encoded slot still holds the requested id in the current
     * dense roster before using it.
     */
    private int[] collisionSlotById = new int[64];
    /** Reused member buffer for one squad's active-path formation participants. */
    private long[] formationMembers = new long[0];
    /** Two-float reusable return buffer for path-heading calculation. */
    private final float[] headingScratch = new float[2];
    /** Squad id of each dense slot this pass groups, or -1 for a slot it skips. */
    private int[] groupedSquadOfSlot = new int[0];
    /** Squadded combatants of the last grouping pass, one contiguous slice per squad. */
    private long[] groupedMembers = new long[0];
    /** Per squad id: first index of its slice, its member count and a fill cursor. */
    private int[] groupedStart = new int[0];
    private int[] groupedCount = new int[0];
    private int[] groupedCursor = new int[0];
    /** Squad ids below this have a valid entry in the grouped arrays. */
    private int groupedSquadSlots;
    private final FireTeamGroups.Teams fireTeams = new FireTeamGroups.Teams();
    /** The moving members of each fire team, flattened; the second pair slices it per team. */
    private long[] movingMembers = new long[0];
    private int[] movingTeamStart = new int[0];
    private int[] movingTeamLength = new int[0];

    public SeparationSystem(UnitRosterService roster, UnitSpatialIndex unitIndex, NavigationGrid grid) {
        this(roster, unitIndex, grid,
                Boolean.parseBoolean(System.getProperty(CAPSULES_PROPERTY, "true")));
    }

    SeparationSystem(UnitRosterService roster, UnitSpatialIndex unitIndex, NavigationGrid grid,
                     boolean capsules) {
        this.capsules = capsules;
        this.roster = roster;
        this.world = roster.world();
        this.unitIndex = unitIndex;
        this.grid = grid;
        this.entityWorld = roster.entityWorld();
        this.components = roster.components();
        this.movement = roster.movement();
    }

    /**
     * Bodies held by another body sit where their holder puts them every tick,
     * so they take no part: a shove would be overwritten, and the holder would
     * be shoved off its course by its own load.
     */
    public void spareHeld(LongPredicate held) {
        this.held = held;
    }

    /**
     * Runs one relaxation pass over the current roster. Sole caller is
     * {@code BattleSimulation.tick()}, right after the occupancy-delta drain
     * and before the spawn flush — see the story doc's "Tick slot" section
     * for why that slot is safe: all this-tick {@code UPDATE_UNITS} position
     * writes have landed (serial slot), and every phase that reads POSITION
     * afterward this tick — {@code FIRING} through {@code APPEARANCE} — is
     * supposed to see post-separation positions; no later phase writes
     * ground-unit POSITION except spawn placement.
     */
    public void tick(float dt) {
        tick(dt, 0L);
    }

    /** Physical relaxation remains active for the controlled body; formation steering does not. */
    public void tick(float dt, long controlledUnitId) {
        if (dt <= 0f) return;
        int liveCount = roster.liveCount();
        ensureCapacity(liveCount);
        Arrays.fill(impulseX, 0, liveCount, 0f);
        Arrays.fill(impulseY, 0, liveCount, 0f);
        long[] dense = roster.denseArray();
        cacheCollisionSlots(dense, liveCount);
        cacheCollisionState(dense, liveCount);

        accumulate(dense, liveCount, dt, controlledUnitId);
        accumulateSquadFormations(dense, liveCount, dt, controlledUnitId);
        apply(dense, liveCount, dt, controlledUnitId);
    }

    private void accumulate(long[] dense, int liveCount, float dt, long controlledUnitId) {
        for (int i = 0; i < liveCount; i++) {
            long a = dense[i];
            byte aFlags = collisionFlags[i];
            if (!hasFlag(aFlags, PARTICIPATES)
                    || hasFlag(aFlags, IMMOVABLE)) continue;
            float ax = collisionX[i];
            float ay = collisionY[i];
            float ra = collisionRadius[i];
            boolean aLong = elongated[i];
            float queryRadius = hasFlag(aFlags, MECH)
                    ? MECH_FORMATION_QUERY_RADIUS : QUERY_RADIUS;
            if (maxCapsuleReach > 0f) {
                // Two long bodies can touch nose to tail from farther apart
                // than the ordinary net reaches.
                queryRadius = Math.max(queryRadius,
                        shapeReach[i] + maxCapsuleReach + CAPSULE_QUERY_MARGIN);
            }
            unitIndex.gather(ax, ay, queryRadius, scratch);
            for (int k = 0, n = scratch.size; k < n; k++) {
                long b = scratch.ids[k];
                if (b == a) continue;
                int j = collisionSlot(b, dense, liveCount);
                if (j == UnitRosterService.INVALID_INDEX) continue;
                byte bFlags = collisionFlags[j];
                if (!hasFlag(bFlags, PARTICIPATES)) continue;
                if (hasFlag(aFlags, HAS_MECH_LOADOUT)
                        && hasFlag(bFlags, HAS_MECH_LOADOUT)
                        && (hasFlag(aFlags, ESCAPE_ACTIVE)
                        || hasFlag(bFlags, ESCAPE_ACTIVE))) continue;
                float bx = collisionX[j];
                float by = collisionY[j];
                float rb = collisionRadius[j];
                float sumR;
                float dx;
                float dy;
                boolean crossing = false;
                if (aLong || elongated[j]) {
                    // Measured lower id first, so both sides of a pair see the
                    // same two points and push along one line.
                    boolean aFirst = a < b;
                    int p = aFirst ? i : j;
                    int q = aFirst ? j : i;
                    BodyCapsule.closestPoints(shapeX[p], shapeY[p], shapeHalfX[p], shapeHalfY[p],
                            shapeX[q], shapeY[q], shapeHalfX[q], shapeHalfY[q], closest);
                    dx = closest[0] - closest[2];
                    dy = closest[1] - closest[3];
                    if (!aFirst) {
                        dx = -dx;
                        dy = -dy;
                    }
                    if (dx * dx + dy * dy < COINCIDENT_EPS * COINCIDENT_EPS) {
                        // The segments cross: the whole of both radii is
                        // overlap, and the centres say which way is out.
                        dx = ax - bx;
                        dy = ay - by;
                        crossing = true;
                    }
                    sumR = shapeRadius[i] + shapeRadius[j];
                } else {
                    sumR = ra + rb;
                    dx = ax - bx;
                    dy = ay - by;
                }
                float dist2 = dx * dx + dy * dy;
                float dist = (float) Math.sqrt(dist2);
                float physicalOverlap = crossing ? sumR : Math.max(0f, sumR - dist);
                float formationGap = a != controlledUnitId && b != controlledUnitId
                        && hasFlag(aFlags, MECH)
                        && hasFlag(bFlags, MECH)
                        && collisionFaction[i] == collisionFaction[j]
                        && (hasFlag(aFlags, ACTIVE_PATH)
                        || hasFlag(bFlags, ACTIVE_PATH))
                        ? Math.max(0f, MECH_FORMATION_MIN_DISTANCE - Math.max(dist, sumR))
                        : 0f;
                if (physicalOverlap <= 0f && formationGap <= 0f) continue;

                float dirX, dirY;
                if (dist < COINCIDENT_EPS) {
                    float theta = coincidentAngle(a, b);
                    dirX = (float) Math.cos(theta);
                    dirY = (float) Math.sin(theta);
                    if (a > b) {
                        dirX = -dirX;
                        dirY = -dirY;
                    }
                } else {
                    dirX = dx / dist;
                    dirY = dy / dist;
                }
                if (physicalOverlap > 0f && hasFlag(bFlags, MECH)) {
                    // Ahead of a walking mech, a body is shoved off its line, not carried down it: to the side it
                    // already stands on, or dead ahead to the mech's left, which for two mechs head-on is each one's own right.
                    float bvx = collisionVelX[j], bvy = collisionVelY[j];
                    float bv = (float) Math.sqrt(bvx * bvx + bvy * bvy);
                    if (bv > MECH_WALKING_SPEED) {
                        float ux = bvx / bv, uy = bvy / bv;
                        float relX = ax - bx, relY = ay - by;
                        if (relX * ux + relY * uy > 0f) {
                            float left = -relX * uy + relY * ux;
                            float s = Math.abs(left) > MECH_LINE_TIE ? Math.signum(left) : 1f;
                            dirX += -uy * s;
                            dirY += ux * s;
                            float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
                            dirX /= len;
                            dirY /= len;
                        }
                    }
                }

                float correction = physicalOverlap * STIFFNESS
                        + formationGap * FORMATION_STIFFNESS;
                float weight = hasFlag(bFlags, IMMOVABLE)
                        ? 1f
                        : hasFlag(aFlags, MECH) || hasFlag(bFlags, MECH)
                        ? collisionTonnes[j] / (collisionTonnes[i] + collisionTonnes[j])
                        : collisionMass[j]
                        / (collisionMass[i] + collisionMass[j]);
                float mag = weight * correction;
                impulseX[i] += dirX * mag;
                impulseY[i] += dirY * mag;
                if (physicalOverlap > 0f && hasFlag(bFlags, IMMOVABLE)) {
                    slideRound(i, a, b, dirX, dirY, physicalOverlap, dt);
                }
            }
        }
    }

    /**
     * A mover walking nearly head-on into an immovable body is pushed straight
     * back along its own line, so its step and the push cancel and it stands
     * there for good: a route laid along cell centres meets a body on a cell
     * centre exactly head-on. Such a mover also steps aside, as fast as it was
     * walking in, up to the overlap; once off the line the ordinary push has a
     * sideways part of its own and carries it round. Which side is a hash of
     * the pair, never a random draw.
     */
    private void slideRound(int i, long a, long b, float dirX, float dirY,
                            float overlap, float dt) {
        float vx = collisionVelX[i];
        float vy = collisionVelY[i];
        float inward = -(vx * dirX + vy * dirY);
        if (inward <= 0f) return;
        float tangentX = vx + inward * dirX;
        float tangentY = vy + inward * dirY;
        if (tangentX * tangentX + tangentY * tangentY
                > HEAD_ON_TANGENT * HEAD_ON_TANGENT * inward * inward) return;
        float side = (coincidentAngle(a, b) < (float) Math.PI) ? 1f : -1f;
        float slide = Math.min(inward * dt, overlap);
        impulseX[i] += -dirY * side * slide;
        impulseY[i] += dirX * side * slide;
    }

    private void cacheCollisionSlots(long[] dense, int liveCount) {
        int largestDirectId = 0;
        for (int i = 0; i < liveCount; i++) {
            long id = dense[i];
            if (id > 0L && id <= MAX_DIRECT_COLLISION_ID) {
                largestDirectId = Math.max(largestDirectId, (int) id);
            }
        }
        int required = largestDirectId + 1;
        if (required > collisionSlotById.length) {
            int capacity = Math.min(MAX_DIRECT_COLLISION_ID + 1,
                    Math.max(required, collisionSlotById.length << 1));
            collisionSlotById = Arrays.copyOf(collisionSlotById, capacity);
        }
        for (int i = 0; i < liveCount; i++) {
            long id = dense[i];
            if (id > 0L && id < collisionSlotById.length) {
                collisionSlotById[(int) id] = i + 1;
            }
        }
    }

    private int collisionSlot(long id, long[] dense, int liveCount) {
        if (id > 0L && id < collisionSlotById.length) {
            int slot = collisionSlotById[(int) id] - 1;
            return slot >= 0 && slot < liveCount && dense[slot] == id
                    ? slot : UnitRosterService.INVALID_INDEX;
        }
        if (id <= MAX_DIRECT_COLLISION_ID) {
            return UnitRosterService.INVALID_INDEX;
        }
        return roster.indexOf(id);
    }

    private void cacheCollisionState(long[] dense, int liveCount) {
        Arrays.fill(collisionFlags, 0, liveCount, (byte) 0);
        maxCapsuleReach = 0f;
        for (ArchetypeTable table : entityWorld.matched(components.gridOccupants)) {
            // POSITION-minus-CORPSE is deliberately broader than the ground
            // roster. Ignore any future position-only family before asking for
            // the universal ground-unit columns.
            if (!table.has(components.IDENTITY) || !table.has(components.ROLE)) continue;

            float[] posX = table.floats(components.POSITION,
                    BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION,
                    BattleComponents.POSITION_Y).array();
            Object[] types = table.objects(components.IDENTITY,
                    BattleComponents.IDENTITY_TYPE).array();
            Object[] factions = table.objects(components.IDENTITY,
                    BattleComponents.IDENTITY_FACTION).array();
            Object[] variants = table.objects(components.IDENTITY,
                    BattleComponents.IDENTITY_MECH_VARIANT).array();
            Object[] airframes = table.objects(components.IDENTITY,
                    BattleComponents.IDENTITY_AIRFRAME).array();
            Object[] stages = table.objects(components.IDENTITY,
                    BattleComponents.IDENTITY_SWARM_STAGE).array();
            int[] roles = table.ints(components.ROLE,
                    BattleComponents.ROLE_ORDINAL).array();

            boolean hasHealth = table.has(components.HEALTH);
            float[] hp = hasHealth
                    ? table.floats(components.HEALTH,
                    BattleComponents.HEALTH_HP).array() : null;
            boolean hasKinematics = table.has(components.KINEMATICS);
            boolean hasMovement = table.has(components.MOVEMENT);
            Object[] paths = hasMovement
                    ? table.objects(components.MOVEMENT,
                    BattleComponents.MOVEMENT_PATH).array() : null;
            int[] pathIndices = hasMovement
                    ? table.ints(components.MOVEMENT,
                    BattleComponents.MOVEMENT_PATH_IDX).array() : null;
            boolean hasMechLoadout = table.has(components.MECH_LOADOUT);
            Object[] mechLoadouts = hasMechLoadout
                    ? table.objects(components.MECH_LOADOUT,
                    BattleComponents.MECH_LOADOUT_STATE).array() : null;
            float[] velXs = hasMovement
                    ? table.floats(components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X).array() : null;
            float[] velYs = hasMovement
                    ? table.floats(components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y).array() : null;
            float[] facings = table.has(components.LAYERED_ANIMATION)
                    ? table.floats(components.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_FACING_DEGREES).array() : null;
            boolean hasTurretState = table.has(components.TURRET_STATE);
            Object[] turretStructureIds = hasTurretState
                    ? table.objects(components.TURRET_STATE,
                    BattleComponents.TURRET_STATE_STRUCTURE_ID).array() : null;

            for (int row = 0, rows = table.rowCount(); row < rows; row++) {
                long id = table.entityAt(row);
                int slot = collisionSlot(id, dense, liveCount);
                if (slot == UnitRosterService.INVALID_INDEX) continue;

                UnitType type = (UnitType) types[row];
                String turretStructureId = hasTurretState
                        ? (String) turretStructureIds[row] : null;
                StructureDef turretStructure = turretStructureId != null
                        ? TurretCatalogRegistry.requireStructure(turretStructureId) : null;
                // The same precedence UnitRosterService.radius answers by id,
                // fed off columns instead of lookups — a body that is one size
                // to a round and another to a shove is the defect this shares
                // its way out of. A carried body needs no branch here: it holds
                // no POSITION and so is never a row in this query.
                float radius = BodyRadius.resolve(turretStructure,
                        (Airframe) airframes[row],
                        (MechVariant) variants[row],
                        type,
                        (SwarmStage) stages[row]);
                byte flags = POPULATED;
                if (hasHealth && hp[row] > 0f
                        && !hasKinematics && radius > 0f && !held.test(id)) {
                    flags |= PARTICIPATES;
                }
                if (type.isStatic()
                        || roles[row] == UnitRole.STRUCTURE.ordinal()) {
                    flags |= IMMOVABLE;
                }
                if (type.isMech()) {
                    flags |= MECH;
                    if (hasMovement
                            && pathIndices[row] < Paths.cellCount((int[]) paths[row])) {
                        flags |= ACTIVE_PATH;
                    }
                }
                if (hasMechLoadout) {
                    flags |= HAS_MECH_LOADOUT;
                    if (((MechLoadoutComponent) mechLoadouts[row]).collisionEscapeActive) {
                        flags |= ESCAPE_ACTIVE;
                    }
                }
                collisionX[slot] = posX[row];
                collisionY[slot] = posY[row];
                collisionRadius[slot] = radius;
                collisionMass[slot] = radius * radius;
                collisionTonnes[slot] = HullRam.bodyTonnes(type, (SwarmStage) stages[row],
                        hasMechLoadout ? ((MechLoadoutComponent) mechLoadouts[row]).variant.relativeMass : 1f);
                collisionFlags[slot] = flags;
                collisionFaction[slot] =
                        (byte) ((Faction) factions[row]).ordinal();
                collisionVelX[slot] = hasMovement ? velXs[row] : 0f;
                collisionVelY[slot] = hasMovement ? velYs[row] : 0f;
                cacheShape(slot, type, (SwarmStage) stages[row], posX[row], posY[row], radius,
                        facings != null ? facings[row] : Float.NaN,
                        hasFlag(flags, PARTICIPATES));
            }
        }
        for (int i = 0; i < liveCount; i++) {
            if (!hasFlag(collisionFlags[i], POPULATED)) {
                // A passenger has no position and so cannot crowd anybody. It
                // is legitimately absent from the query; anything else missing
                // is the corruption this check is here for.
                if (roster.isRiding(dense[i])) continue;
                throw new IllegalStateException(
                        "live unit missing from gridOccupants query: " + dense[i]);
            }
        }
    }

    private void cacheShape(int slot, UnitType type, SwarmStage stage, float x, float y, float radius,
                            float facingDegrees, boolean participates) {
        float halfLength = BodyCapsule.halfLength(type, stage);
        if (!capsules || halfLength <= 0f || Float.isNaN(facingDegrees)) {
            float shove = capsules ? BodyCapsule.radius(type, radius, stage) : radius;
            shapeX[slot] = x;
            shapeY[slot] = y;
            shapeHalfX[slot] = 0f;
            shapeHalfY[slot] = 0f;
            shapeRadius[slot] = shove;
            shapeReach[slot] = shove;
            // A body shoved bigger than its sim radius (the elder Brute, capped
            // under half a cell for the grid) is measured as a capsule of no
            // length, so the pair test reads its shove radius.
            elongated[slot] = shove > radius;
            if (participates && shove > radius) maxCapsuleReach = Math.max(maxCapsuleReach, shove);
            return;
        }
        double rad = Math.toRadians(facingDegrees);
        float fx = (float) -Math.sin(rad);
        float fy = (float) Math.cos(rad);
        float offset = BodyCapsule.forwardOffset(type, stage);
        shapeX[slot] = x + fx * offset;
        shapeY[slot] = y + fy * offset;
        shapeHalfX[slot] = fx * halfLength;
        shapeHalfY[slot] = fy * halfLength;
        shapeRadius[slot] = BodyCapsule.radius(type, radius, stage);
        shapeReach[slot] = BodyCapsule.reach(type, radius, stage);
        elongated[slot] = true;
        if (participates) maxCapsuleReach = Math.max(maxCapsuleReach, shapeReach[slot]);
    }

    /**
     * The footprint radius the last pass cached for {@code id}, or {@code -1}
     * if it held no slot.
     *
     * <p>Package-private for {@code OneBodyIsOneSizeTest}. The columnar
     * derivation drifting from the by-id one is silent by nature — both
     * answers are plausible floats and nothing crashes — so the only thing
     * that can catch it is reading back what this pass actually used.
     */
    float cachedRadius(long id) {
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        int slot = collisionSlot(id, dense, liveCount);
        return slot == UnitRosterService.INVALID_INDEX ? -1f : collisionRadius[slot];
    }

    private static boolean hasFlag(byte flags, byte flag) {
        return (flags & flag) != 0;
    }

    private void accumulateSquadFormations(long[] dense, int liveCount, float dt,
                                           long controlledUnitId) {
        ensureFormationCapacity(liveCount);
        // One grouping pass over the roster, not one full scan per squad. A
        // late-battle Conquest fields a few hundred squads against a few
        // hundred units, so the old per-squad rescan was the single most
        // expensive thing this phase did.
        groupCombatantsBySquad(dense, liveCount, controlledUnitId);
        for (Squad squad : roster.getSquads()) {
            FormationProfile profile = formationProfile(squad);
            if (profile == null) continue;
            if (squad.id < 0 || squad.id >= groupedSquadSlots) continue;
            int memberCount = groupedCount[squad.id];
            if (memberCount == 0) continue;
            int first = groupedStart[squad.id];
            if (profile == FormationProfile.MECH) {
                if (squad.lanceOrder() != MechLanceOrder.FORM_ON_LEAD) continue;
                int count = gatherMovingFormationMembers(groupedMembers, first, memberCount);
                accumulateFormation(count, profile, dt,
                        Float.NaN, Float.NaN, Float.NaN, Float.NaN);
            } else {
                accumulateInfantryFireTeams(groupedMembers, first, memberCount, dt);
            }
        }
    }

    /**
     * Squadded combatants grouped by squad id into {@link #groupedMembers}, each
     * squad's slice in dense-roster order — the order a per-squad scan of the
     * dense array produced, which formation slotting and its tie-breaks depend
     * on. A squad with no live squadded combatant has a count of zero, so a
     * caller skipping it is skipping exactly what the old empty-scan path skipped.
     * Squad ids are minted sequentially, so they index the per-squad arrays.
     */
    private void groupCombatantsBySquad(long[] dense, int liveCount, long controlledUnitId) {
        if (groupedSquadOfSlot.length < liveCount) {
            int capacity = Math.max(liveCount, groupedSquadOfSlot.length * 2);
            groupedSquadOfSlot = new int[capacity];
            groupedMembers = new long[capacity];
        }
        int highest = -1;
        for (int i = 0; i < liveCount; i++) {
            long member = dense[i];
            int squadId = -1;
            if (member != controlledUnitId
                    && roster.combat().has(member) && roster.squad().hasSquad(member)) {
                squadId = roster.squad().squadId(member);
            }
            groupedSquadOfSlot[i] = squadId;
            if (squadId > highest) highest = squadId;
        }
        groupedSquadSlots = highest + 1;
        if (groupedCount.length < groupedSquadSlots) {
            int capacity = Math.max(groupedSquadSlots, groupedCount.length * 2);
            groupedStart = new int[capacity];
            groupedCount = new int[capacity];
            groupedCursor = new int[capacity];
        }
        Arrays.fill(groupedCount, 0, groupedSquadSlots, 0);
        for (int i = 0; i < liveCount; i++) {
            int squadId = groupedSquadOfSlot[i];
            if (squadId >= 0) groupedCount[squadId]++;
        }
        int offset = 0;
        for (int squadId = 0; squadId < groupedSquadSlots; squadId++) {
            groupedStart[squadId] = offset;
            groupedCursor[squadId] = offset;
            offset += groupedCount[squadId];
        }
        for (int i = 0; i < liveCount; i++) {
            int squadId = groupedSquadOfSlot[i];
            if (squadId >= 0) groupedMembers[groupedCursor[squadId]++] = dense[i];
        }
    }

    private void accumulateInfantryFireTeams(long[] allMembers, int first, int memberCount,
                                             float dt) {
        if (movingMembers.length < memberCount) {
            int capacity = Math.max(memberCount, movingMembers.length * 2);
            movingMembers = new long[capacity];
            movingTeamStart = new int[capacity];
            movingTeamLength = new int[capacity];
        }
        FireTeamGroups.organizeInto(allMembers, first, memberCount, roster.squad(), fireTeams);
        int movingTeamCount = 0;
        int movingCount = 0;
        for (int t = 0; t < fireTeams.count(); t++) {
            int teamStart = movingCount;
            for (int k = 0; k < fireTeams.length(t); k++) {
                long member = fireTeams.member(t, k);
                // A member at zero speed is holding, path or no: steered to a slot it would slide while it fires.
                if (entityWorld.has(member, components.MOVEMENT) && world.moveSpeed(member) > 0f
                        && (hasActivePath(member)
                        || (movement.formationMemoryTimer(member) > 0f
                        && sharesFormationDestination(member, allMembers, first, memberCount)))) {
                    movingMembers[movingCount++] = member;
                }
            }
            if (movingCount > teamStart) {
                movingTeamStart[movingTeamCount] = teamStart;
                movingTeamLength[movingTeamCount] = movingCount - teamStart;
                movingTeamCount++;
            }
        }
        if (movingTeamCount == 0) return;

        float centerX = 0f;
        float centerY = 0f;
        float forwardX = 0f;
        float forwardY = 0f;
        for (int i = 0; i < movingCount; i++) {
            long member = movingMembers[i];
            centerX += world.x(member);
            centerY += world.y(member);
            pathHeading(member, headingScratch);
            forwardX += headingScratch[0];
            forwardY += headingScratch[1];
        }
        centerX /= movingCount;
        centerY /= movingCount;
        float forwardLength = (float) Math.sqrt(forwardX * forwardX + forwardY * forwardY);
        boolean sharedArc = movingTeamCount > 1
                && forwardLength / movingCount >= FORMATION_MIN_HEADING_COHERENCE;
        if (sharedArc) {
            forwardX /= forwardLength;
            forwardY /= forwardLength;
        }

        for (int teamIndex = 0; teamIndex < movingTeamCount; teamIndex++) {
            int teamSize = movingTeamLength[teamIndex];
            int teamFirst = movingTeamStart[teamIndex];
            ensureFormationCapacity(teamSize);
            for (int i = 0; i < teamSize; i++) formationMembers[i] = movingMembers[teamFirst + i];
            if (!sharedArc) {
                accumulateFormation(teamSize, FormationProfile.INFANTRY, dt,
                        Float.NaN, Float.NaN, Float.NaN, Float.NaN);
                continue;
            }
            float ordinal = teamIndex - (movingTeamCount - 1) * 0.5f;
            float lateralX = -forwardY;
            float lateralY = forwardX;
            float openness = formationOpenness(
                    formationMembers, teamSize, FormationProfile.INFANTRY);
            float anchorX = centerX
                    + lateralX * ordinal * FIRE_TEAM_LATERAL_INTERVAL * openness
                    - forwardX * Math.abs(ordinal) * FIRE_TEAM_ARC_DEPTH * openness;
            float anchorY = centerY
                    + lateralY * ordinal * FIRE_TEAM_LATERAL_INTERVAL * openness
                    - forwardY * Math.abs(ordinal) * FIRE_TEAM_ARC_DEPTH * openness;
            accumulateFormation(teamSize, FormationProfile.INFANTRY, dt,
                    anchorX, anchorY, forwardX, forwardY);
        }
    }

    private void accumulateFormation(int count, FormationProfile profile, float dt,
                                     float anchorX, float anchorY,
                                     float sharedForwardX, float sharedForwardY) {
        if (count < 2) return;
        sortFormationMembers(count, profile);
        float centerX = 0f;
        float centerY = 0f;
        float forwardX = 0f;
        float forwardY = 0f;
        for (int i = 0; i < count; i++) {
            long member = formationMembers[i];
            centerX += world.x(member);
            centerY += world.y(member);
            pathHeading(member, headingScratch);
            forwardX += headingScratch[0];
            forwardY += headingScratch[1];
        }
        centerX = Float.isNaN(anchorX) ? centerX / count : anchorX;
        centerY = Float.isNaN(anchorY) ? centerY / count : anchorY;
        float forwardLength = (float) Math.sqrt(forwardX * forwardX + forwardY * forwardY);
        if (forwardLength / count < FORMATION_MIN_HEADING_COHERENCE) return;
        if (!Float.isNaN(sharedForwardX)) {
            forwardX = sharedForwardX;
            forwardY = sharedForwardY;
        } else {
            forwardX /= forwardLength;
            forwardY /= forwardLength;
        }
        float lateralX = -forwardY;
        float lateralY = forwardX;
        float spacing = preferredFormationSpacing(formationMembers, count, profile);
        float openness = spacingOpenness(spacing, profile);
        if (openness <= 0f) return;
        boolean depthPair = count == 2 && compareFormationRoles(
                formationMembers[0], formationMembers[1], profile) != 0;
        for (int slot = 0; slot < count; slot++) {
            float slotForward = slotForward(slot, count, spacing, depthPair, profile);
            float slotLateral = slotLateral(slot, count, spacing, depthPair, profile);
            long member = formationMembers[slot];
            float targetX = centerX + forwardX * slotForward + lateralX * slotLateral;
            float targetY = centerY + forwardY * slotForward + lateralY * slotLateral;
            float dx = targetX - world.x(member);
            float dy = targetY - world.y(member);
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < COINCIDENT_EPS) continue;
            float correction = Math.min(distance * FORMATION_STIFFNESS * openness,
                    FORMATION_MAX_SPEED * dt);
            int denseIndex = roster.indexOf(member);
            if (denseIndex == UnitRosterService.INVALID_INDEX) continue;
            float pullX = dx / distance * correction;
            float pullY = dy / distance * correction;
            // This tick's own step, as the follower applied it before separation runs.
            float stepX = movement.velX(member) * dt;
            float stepY = movement.velY(member) * dt;
            float step = (float) Math.sqrt(stepX * stepX + stepY * stepY);
            if (step > 0f) {
                float back = -(pullX * stepX + pullY * stepY) / step;
                float limit = FORMATION_MAX_SLOWDOWN * step;
                if (back > limit) {
                    float restore = (back - limit) / step;
                    pullX += stepX * restore;
                    pullY += stepY * restore;
                }
            }
            impulseX[denseIndex] += pullX;
            impulseY[denseIndex] += pullY;
        }
    }

    private int gatherMovingFormationMembers(long[] squadMembers, int first, int memberCount) {
        int count = 0;
        for (int i = first; i < first + memberCount; i++) {
            long member = squadMembers[i];
            if (!entityWorld.has(member, components.MOVEMENT)
                    || !hasActivePath(member)
                    || (world.hasMechLoadout(member)
                    && world.mechLoadout(member).collisionEscapeActive)) continue;
            formationMembers[count++] = member;
        }
        return count;
    }

    private void sortFormationMembers(int count, FormationProfile profile) {
        for (int i = 1; i < count; i++) {
            long member = formationMembers[i];
            int j = i - 1;
            while (j >= 0 && compareFormationMembers(member,
                    formationMembers[j], profile) < 0) {
                formationMembers[j + 1] = formationMembers[j];
                j--;
            }
            formationMembers[j + 1] = member;
        }
    }

    private int compareFormationMembers(long first, long second,
                                        FormationProfile profile) {
        int byRole = compareFormationRoles(first, second, profile);
        return byRole != 0 ? byRole : Long.compare(first, second);
    }

    private int compareFormationRoles(long first, long second,
                                      FormationProfile profile) {
        if (profile == FormationProfile.MECH) {
            return Integer.compare(mechFormationRoleRank(first),
                    mechFormationRoleRank(second));
        }
        return Float.compare(roster.combat().attackRange(first),
                roster.combat().attackRange(second));
    }

    private int mechFormationRoleRank(long member) {
        if (!world.hasMechLoadout(member)) return 1;
        MechRole role = world.mechLoadout(member).effectiveRole();
        if (role == MechRole.ASSAULT || role == MechRole.ARMORED_SUPPORT) return 0;
        if (role == MechRole.LR_SUPPORT) return 2;
        return 1;
    }

    private void pathHeading(long member, float[] output) {
        int count = movement.waypointCount(member);
        float x = world.x(member);
        float y = world.y(member);
        for (int i = world.pathIdx(member); i < count; i++) {
            float dx = movement.waypointX(member, i) - x;
            float dy = movement.waypointY(member, i) - y;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 0.25f) {
                output[0] = dx / length;
                output[1] = dy / length;
                return;
            }
        }
        // A just-settled mover retains the final authored path segment. It is
        // sufficient to finish the formation correction without inventing a
        // new movement direction or keeping a permanent formation state.
        if (count >= 2 && movement.formationMemoryTimer(member) > 0f) {
            float dx = movement.waypointX(member, count - 1) - movement.waypointX(member, count - 2);
            float dy = movement.waypointY(member, count - 1) - movement.waypointY(member, count - 2);
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 1e-4f) {
                output[0] = dx / length;
                output[1] = dy / length;
                return;
            }
        }
        output[0] = 0f;
        output[1] = 0f;
    }

    private boolean sharesFormationDestination(long member, long[] squadMembers,
                                               int first, int memberCount) {
        int[] path = world.path(member);
        if (Paths.cellCount(path) < 2) return false;
        int destX = Paths.destX(path);
        int destY = Paths.destY(path);
        int ownTeam = roster.squad().fireTeamIndex(member);
        for (int i = first; i < first + memberCount; i++) {
            long other = squadMembers[i];
            if (other == member || !entityWorld.has(other, components.MOVEMENT)
                    || roster.squad().fireTeamIndex(other) == ownTeam) continue;
            int[] otherPath = world.path(other);
            if (Paths.cellCount(otherPath) >= 2
                    && Paths.destX(otherPath) == destX
                    && Paths.destY(otherPath) == destY) return true;
        }
        return false;
    }

    float preferredMechFormationSpacing(long[] members, int count) {
        return preferredFormationSpacing(
                members, count, FormationProfile.MECH);
    }

    float preferredInfantryFormationSpacing(long[] members, int count) {
        return preferredFormationSpacing(
                members, count, FormationProfile.INFANTRY);
    }

    private float preferredFormationSpacing(long[] members, int count,
                                            FormationProfile profile) {
        int clearance = profile.openClearance;
        for (int i = 0; i < count; i++) {
            long member = members[i];
            clearance = Math.min(clearance,
                    formationClearance(member, profile));
        }
        float openness = clearance / (float) profile.openClearance;
        return profile.minimumDistance
                + (profile.openDistance - profile.minimumDistance) * openness;
    }

    private float formationOpenness(long[] members, int count,
                                    FormationProfile profile) {
        return spacingOpenness(
                preferredFormationSpacing(members, count, profile), profile);
    }

    private static float spacingOpenness(float spacing,
                                         FormationProfile profile) {
        return (spacing - profile.minimumDistance)
                / (profile.openDistance - profile.minimumDistance);
    }

    /**
     * Formation authority anticipates infantry portals instead of discovering
     * them only once the leading marine is already on the threshold. A zero
     * clearance anywhere in the next few authored path cells releases both
     * the fire-team interval and its squad-arc anchor; ordinary path following
     * and collision separation then form the doorway queue. As path indices
     * advance beyond the constraint this same query rises again, so the teams
     * reform without a separate state machine.
     */
    private int formationClearance(long member, FormationProfile profile) {
        int clearance = walkableClearance(
                world.cellX(member), world.cellY(member), profile);
        if (profile != FormationProfile.INFANTRY || clearance == 0) {
            return clearance;
        }
        int[] path = world.path(member);
        int pathCount = Paths.cellCount(path);
        int first = Math.max(0, world.pathIdx(member));
        int end = Math.min(pathCount, first + INFANTRY_CLEARANCE_LOOKAHEAD);
        for (int i = first; i < end && clearance > 0; i++) {
            clearance = Math.min(clearance, walkableClearance(
                    Paths.cellX(path, i), Paths.cellY(path, i), profile));
        }
        return clearance;
    }

    private int walkableClearance(int centerX, int centerY,
                                  FormationProfile profile) {
        if (grid.isDoorway(centerX, centerY)) return 0;
        for (int radius = 1; radius <= profile.openClearance; radius++) {
            for (int offset = -radius; offset <= radius; offset++) {
                if (!formationCellOpen(centerX + offset, centerY - radius)
                        || !formationCellOpen(centerX + offset, centerY + radius)
                        || !formationCellOpen(centerX - radius, centerY + offset)
                        || !formationCellOpen(centerX + radius, centerY + offset)) {
                    return radius - 1;
                }
            }
        }
        return profile.openClearance;
    }

    private boolean formationCellOpen(int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y)
                && !grid.isDoorway(x, y);
    }

    private static float slotForward(int slot, int count, float spacing,
                                     boolean depthPair, FormationProfile profile) {
        if (count == 2) {
            if (!depthPair) return 0f;
            return slot == 0 ? spacing * 0.5f : -spacing * 0.5f;
        }
        if (count == 3) return slot == 0
                ? spacing * 0.57735026f : -spacing * 0.28867513f;
        if (count == 4) {
            if (slot == 0) return spacing * 0.70710677f;
            if (slot == 3) return -spacing * 0.70710677f;
            return 0f;
        }
        if (profile == FormationProfile.INFANTRY) {
            float ordinal = slot - (count - 1) * 0.5f;
            return -Math.abs(ordinal) * spacing * 0.35f;
        }
        float radius = spacing / (2f * (float) Math.sin(Math.PI / count));
        return (float) Math.cos(2f * Math.PI * slot / count) * radius;
    }

    private static float slotLateral(int slot, int count, float spacing,
                                     boolean depthPair, FormationProfile profile) {
        if (count == 2) {
            if (depthPair) return 0f;
            return slot == 0 ? spacing * 0.5f : -spacing * 0.5f;
        }
        if (count == 3) {
            if (slot == 1) return spacing * 0.5f;
            if (slot == 2) return -spacing * 0.5f;
            return 0f;
        }
        if (count == 4) {
            if (slot == 1) return spacing * 0.70710677f;
            if (slot == 2) return -spacing * 0.70710677f;
            return 0f;
        }
        if (profile == FormationProfile.INFANTRY) {
            float ordinal = slot - (count - 1) * 0.5f;
            return ordinal * spacing * 0.7f;
        }
        float radius = spacing / (2f * (float) Math.sin(Math.PI / count));
        return (float) Math.sin(2f * Math.PI * slot / count) * radius;
    }

    private static FormationProfile formationProfile(Squad squad) {
        if (squad.isDroneSquad()) return null;
        return squad.isMechSquad()
                ? FormationProfile.MECH : FormationProfile.INFANTRY;
    }

    private enum FormationProfile {
        INFANTRY(INFANTRY_FORMATION_MIN_DISTANCE,
                INFANTRY_FORMATION_OPEN_DISTANCE,
                INFANTRY_FORMATION_OPEN_CLEARANCE),
        MECH(MECH_FORMATION_MIN_DISTANCE,
                MECH_FORMATION_OPEN_DISTANCE,
                MECH_FORMATION_OPEN_CLEARANCE);

        private final float minimumDistance;
        private final float openDistance;
        private final int openClearance;

        FormationProfile(float minimumDistance, float openDistance,
                         int openClearance) {
            this.minimumDistance = minimumDistance;
            this.openDistance = openDistance;
            this.openClearance = openClearance;
        }
    }

    private boolean isMech(long id) {
        return roster.identity().type(id).isMech();
    }

    private boolean hasActivePath(long id) {
        return world.pathIdx(id) < Paths.cellCount(world.path(id));
    }

    private void apply(long[] dense, int liveCount, float dt, long controlledUnitId) {
        float maxMag = MAX_PUSH_SPEED * dt;
        for (ArchetypeTable table : entityWorld.matched(components.gridOccupants)) {
            if (!table.has(components.MOVEMENT)) continue;
            float[] posX = table.floats(components.POSITION,
                    BattleComponents.POSITION_X).array();
            float[] posY = table.floats(components.POSITION,
                    BattleComponents.POSITION_Y).array();
            float[] velX = table.floats(components.MOVEMENT,
                    BattleComponents.MOVEMENT_VEL_X).array();
            float[] velY = table.floats(components.MOVEMENT,
                    BattleComponents.MOVEMENT_VEL_Y).array();
            float[] gaitPhase = table.floats(components.MOVEMENT,
                    BattleComponents.MOVEMENT_GAIT_PHASE).array();
            for (int row = 0, rows = table.rowCount(); row < rows; row++) {
                int i = collisionSlot(table.entityAt(row), dense, liveCount);
                if (i == UnitRosterService.INVALID_INDEX) continue;
                float ix = impulseX[i];
                float iy = impulseY[i];
                if (ix == 0f && iy == 0f) continue;
                float mag = (float) Math.sqrt(ix * ix + iy * iy);
                if (mag > maxMag) {
                    float scale = maxMag / mag;
                    ix *= scale;
                    iy *= scale;
                }
                float ax = collisionX[i];
                float ay = collisionY[i];
                float nx = ax + ix;
                float ny = ay + iy;
                float appliedX, appliedY;
                boolean sweptBody = table.entityAt(row) == controlledUnitId || isMech(table.entityAt(row));
                if (sweptBody) {
                    // Mechs and manual movers share body clearance with their
                    // route/drive step; relaxation cannot undo terrain contact.
                    ManualTerrainMotion.Result result = ManualTerrainMotion.move(
                            grid, ax, ay, ix, iy, collisionRadius[i]);
                    posX[row] = result.x();
                    posY[row] = result.y();
                    appliedX = result.dx();
                    appliedY = result.dy();
                } else if (canApplyDisplacement(ax, ay, nx, ny)) {
                    posX[row] = nx;
                    posY[row] = ny;
                    appliedX = ix;
                    appliedY = iy;
                } else if (canApplyDisplacement(ax, ay, nx, ay)) {
                    // X-only slide: the full move clips a wall, but sliding along it does not.
                    posX[row] = nx;
                    posY[row] = ay;
                    appliedX = ix;
                    appliedY = 0f;
                } else if (canApplyDisplacement(ax, ay, ax, ny)) {
                    // Y-only slide, the perpendicular case.
                    posX[row] = ax;
                    posY[row] = ny;
                    appliedX = 0f;
                    appliedY = iy;
                } else {
                    // Every candidate cell is non-walkable — drop the impulse this tick.
                    continue;
                }
                velX[row] = velX[row] + appliedX / dt;
                velY[row] = velY[row] + appliedY / dt;
                if (sweptBody) {
                    gaitPhase[row] = (gaitPhase[row]
                            + (float) Math.hypot(appliedX, appliedY)) % 1f;
                }
            }
        }
    }

    private boolean canApplyDisplacement(float fromX, float fromY,
                                         float toX, float toY) {
        return grid.canTraverseCellStep(
                (int) Math.floor(fromX), (int) Math.floor(fromY),
                (int) Math.floor(toX), (int) Math.floor(toY));
    }

    /**
     * Deterministic push-apart angle (radians) for a coincident pair, derived
     * from a hash of the pair's entity ids — never RNG, so stacked spawns fan
     * out identically every run. Order-independent (hashes the {@code
     * (min, max)} pair) so both sides of the pair compute the same base
     * angle; callers flip the sign for the higher id so the two participants
     * push in opposite directions.
     */
    private static float coincidentAngle(long a, long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        long h = lo * 0x9E3779B97F4A7C15L + hi * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        float t = (h & 0xFFFFFFFFL) / (float) 0x100000000L;
        return t * (float) (Math.PI * 2.0);
    }

    private void ensureCapacity(int required) {
        if (impulseX.length >= required) return;
        int newCap = Math.max(required, Math.max(64, impulseX.length * 2));
        impulseX = new float[newCap];
        impulseY = new float[newCap];
        collisionX = new float[newCap];
        collisionY = new float[newCap];
        collisionRadius = new float[newCap];
        collisionMass = new float[newCap];
        collisionTonnes = new float[newCap];
        collisionFlags = new byte[newCap];
        collisionFaction = new byte[newCap];
        shapeX = new float[newCap];
        shapeY = new float[newCap];
        shapeHalfX = new float[newCap];
        shapeHalfY = new float[newCap];
        shapeRadius = new float[newCap];
        shapeReach = new float[newCap];
        collisionVelX = new float[newCap];
        collisionVelY = new float[newCap];
        elongated = new boolean[newCap];
    }

    private void ensureFormationCapacity(int required) {
        if (formationMembers.length >= required) return;
        int newCap = Math.max(required, Math.max(16, formationMembers.length * 2));
        formationMembers = new long[newCap];
    }
}
