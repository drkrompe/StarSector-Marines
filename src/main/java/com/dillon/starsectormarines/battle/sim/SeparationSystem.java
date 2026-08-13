package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;

import java.util.Arrays;

/**
 * Post-movement soft-collision relaxation pass — pushes overlapping ground
 * units apart over a few ticks instead of letting them stack on one point.
 * Design: {@code roadmap/continuous-positions/stories/separation-steering.md}.
 * Stateless consumer (Services/Systems shape): every field below is a
 * reusable scratch buffer, never battle state.
 *
 * <p><b>Participants.</b> A unit takes part iff it is a live grid occupant
 * with a positive footprint radius and is <em>not</em> steered by its own
 * continuous-flight body. Concretely: {@link UnitRosterService#isAliveById}
 * (mirrors {@code NavigationService.rebuildOccupancyMap}'s {@code gridOccupants}
 * query — POSITION present, not yet corpse-transmuted; every roster entity
 * already carries POSITION), {@code radius > 0} ({@link
 * com.dillon.starsectormarines.battle.unit.UnitType#radius}, true for every
 * type today), and {@code !}{@link World#hasKinematics}. The last clause is
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
 * <p><b>S1 scope.</b> Every participant is currently treated as equal mass
 * (a flat 50/50 overlap split via {@link #weightOf}) — the inverse-mass
 * weighting and immovable structures/turrets are S2 (see the story doc's
 * slice list). {@link #weightOf} already takes both ids so that slice is a
 * one-method change; nothing else in the accumulate pass needs to move.
 *
 * <p><b>Algorithm.</b> Two-phase, order-independent, single-threaded:
 * <ol>
 *   <li><b>Accumulate</b> — for each participant {@code a}, gather nearby
 *       participants via {@link UnitSpatialIndex#gather} (a tick-start
 *       snapshot used only to prune candidates; live positions are re-read
 *       for the actual overlap test), and for each overlapping {@code b}
 *       accumulate a push vector into {@code a}'s scratch impulse slot.
 *       Every pair is naturally evaluated from both sides (once as {@code a}
 *       gathers {@code b}, once as {@code b} gathers {@code a}), so there is
 *       no half-pair bookkeeping.</li>
 *   <li><b>Apply</b> — clamp each accumulated impulse to {@link
 *       #MAX_PUSH_SPEED}{@code × dt}, walkability-guard the resulting
 *       position (full move, else X-only slide, else Y-only slide, else drop
 *       the impulse), and write it back via {@link World#setPos}.</li>
 * </ol>
 *
 * <p>{@link #MOVEMENT_VEL_X}/{@code Y} fold-in (so {@code FacingSystem}
 * animates a shoved unit) is S2 — S1 only ever touches POSITION.
 */
public final class SeparationSystem {

    /**
     * Neighbor-query radius, in cells — 2 × the largest unit radius (mech,
     * 0.6) plus a per-tick motion margin, so no overlapping pair can be
     * outside the net even after this tick's movement.
     */
    public static final float QUERY_RADIUS = 1.5f;
    /** Fraction of a pair's overlap resolved per tick before the speed clamp — relaxation, not instant pop. */
    public static final float STIFFNESS = 0.5f;
    /** Cap on push distance per tick, in cells/sec — kept under walk speed (2.0) so separation never outruns intent. */
    public static final float MAX_PUSH_SPEED = 1.5f;

    /** Below this separation distance, two units are treated as coincident and steered apart by the deterministic id-hash tiebreak instead of a (division-by-zero) normalized delta. */
    private static final float COINCIDENT_EPS = 1e-4f;

    private final UnitRosterService roster;
    private final World world;
    private final UnitSpatialIndex unitIndex;
    private final NavigationGrid grid;

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

    public SeparationSystem(UnitRosterService roster, UnitSpatialIndex unitIndex, NavigationGrid grid) {
        this.roster = roster;
        this.world = roster.world();
        this.unitIndex = unitIndex;
        this.grid = grid;
    }

    /**
     * Runs one relaxation pass over the current roster. Sole caller is
     * {@code BattleSimulation.tick()}, right after the occupancy-delta drain
     * and before the spawn flush — see the story doc's "Tick slot" section
     * for why that slot is safe (all this-tick movement has landed; nothing
     * downstream reads POSITION before {@code APPEARANCE}).
     */
    public void tick(float dt) {
        int liveCount = roster.liveCount();
        ensureCapacity(liveCount);
        Arrays.fill(impulseX, 0, liveCount, 0f);
        Arrays.fill(impulseY, 0, liveCount, 0f);
        long[] dense = roster.denseArray();

        accumulate(dense, liveCount);
        apply(dense, liveCount, dt);
    }

    private void accumulate(long[] dense, int liveCount) {
        for (int i = 0; i < liveCount; i++) {
            long a = dense[i];
            if (!participates(a)) continue;
            float ax = world.x(a);
            float ay = world.y(a);
            float ra = radiusOf(a);
            unitIndex.gather(ax, ay, QUERY_RADIUS, scratch);
            for (int k = 0, n = scratch.size; k < n; k++) {
                long b = scratch.ids[k];
                if (b == a || !participates(b)) continue;
                float bx = world.x(b);
                float by = world.y(b);
                float rb = radiusOf(b);
                float sumR = ra + rb;
                float dx = ax - bx;
                float dy = ay - by;
                float dist2 = dx * dx + dy * dy;
                if (dist2 >= sumR * sumR) continue; // no overlap
                float dist = (float) Math.sqrt(dist2);
                float overlap = sumR - dist;
                if (overlap <= 0f) continue;

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

                float mag = weightOf(a, b) * overlap * STIFFNESS;
                impulseX[i] += dirX * mag;
                impulseY[i] += dirY * mag;
            }
        }
    }

    private void apply(long[] dense, int liveCount, float dt) {
        float maxMag = MAX_PUSH_SPEED * dt;
        for (int i = 0; i < liveCount; i++) {
            float ix = impulseX[i];
            float iy = impulseY[i];
            if (ix == 0f && iy == 0f) continue;
            float mag = (float) Math.sqrt(ix * ix + iy * iy);
            if (mag > maxMag) {
                float scale = maxMag / mag;
                ix *= scale;
                iy *= scale;
            }
            long a = dense[i];
            float ax = world.x(a);
            float ay = world.y(a);
            float nx = ax + ix;
            float ny = ay + iy;
            if (grid.isWalkable((int) Math.floor(nx), (int) Math.floor(ny))) {
                world.setPos(a, nx, ny);
            } else if (grid.isWalkable((int) Math.floor(nx), (int) Math.floor(ay))) {
                // X-only slide: the full move clips a wall, but sliding along it does not.
                world.setPos(a, nx, ay);
            } else if (grid.isWalkable((int) Math.floor(ax), (int) Math.floor(ny))) {
                // Y-only slide, the perpendicular case.
                world.setPos(a, ax, ny);
            }
            // Else every candidate cell is non-walkable — drop the impulse this tick.
        }
    }

    /** Participant gate: alive, has a footprint, and isn't steered by its own continuous-flight body. See the class doc for why each clause is there. */
    private boolean participates(long id) {
        return roster.isAliveById(id)
                && !world.hasKinematics(id)
                && roster.identity().type(id).radius > 0f;
    }

    private float radiusOf(long id) {
        return roster.identity().type(id).radius;
    }

    /**
     * Fraction of a pair's overlap that {@code a} yields toward {@code b}.
     * S1 placeholder: a flat 50/50 split (equal-mass). S2 replaces this with
     * inverse-mass weighting ({@code m = radius²}) plus the immovable-structure
     * special cases from the story doc's Algorithm section; every accumulate
     * call already routes through this method, so that slice touches nothing
     * else.
     */
    private static float weightOf(long a, long b) {
        return 0.5f;
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
    }
}
