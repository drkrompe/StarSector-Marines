package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

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
 * <p><b>Mass model.</b> Each participant's mass is {@code radius²} ({@link
 * #weightOf}); a heavier {@code b} yields less, so a mech (radius 0.6, mass
 * 0.36) shoves a marine (radius 0.3, mass 0.09) roughly 4× as far as the
 * marine shoves back. Static emplacements ({@link
 * com.dillon.starsectormarines.battle.unit.UnitType#isStatic()} — turrets
 * and drone hubs) are immovable — infinite mass: a mover overlapping one
 * yields the full overlap ({@code weightOf(mover, immovable) == 1}), and an
 * immovable unit never accumulates an impulse of its own ({@link
 * #accumulate} skips it as the outer participant entirely, via {@link
 * #isImmovable}).
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
    private final EntityWorld entityWorld;
    private final BattleComponents components;

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
        this.entityWorld = roster.entityWorld();
        this.components = roster.components();
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
            if (!participates(a) || isImmovable(a)) continue;
            float ax = world.x(a);
            float ay = world.y(a);
            float ra = radiusOf(a);
            unitIndex.gather(ax, ay, QUERY_RADIUS, scratch);
            for (int k = 0, n = scratch.size; k < n; k++) {
                long b = scratch.ids[k];
                if (b == a || !participates(b)) continue;
                if (allowsMechPassThrough(a, b)) continue;
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
            float appliedX, appliedY;
            if (grid.isWalkable((int) Math.floor(nx), (int) Math.floor(ny))) {
                world.setPos(a, nx, ny);
                appliedX = ix;
                appliedY = iy;
            } else if (grid.isWalkable((int) Math.floor(nx), (int) Math.floor(ay))) {
                // X-only slide: the full move clips a wall, but sliding along it does not.
                world.setPos(a, nx, ay);
                appliedX = ix;
                appliedY = 0f;
            } else if (grid.isWalkable((int) Math.floor(ax), (int) Math.floor(ny))) {
                // Y-only slide, the perpendicular case.
                world.setPos(a, ax, ny);
                appliedX = 0f;
                appliedY = iy;
            } else {
                // Every candidate cell is non-walkable — drop the impulse this tick.
                continue;
            }
            foldIntoVelocity(a, appliedX / dt, appliedY / dt);
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
     * A mech that has been unable to reduce its path distance for the escape
     * delay ignores only another mech's soft collision impulse. This prevents
     * face-to-face walker deadlocks without letting it clip a wall or erase
     * normal infantry spacing.
     */
    private boolean allowsMechPassThrough(long a, long b) {
        if (!world.hasMechLoadout(a) || !world.hasMechLoadout(b)) return false;
        return world.mechLoadout(a).collisionEscapeActive
                || world.mechLoadout(b).collisionEscapeActive;
    }

    /**
     * Fraction of a pair's overlap that {@code a} yields toward {@code b}:
     * inverse-mass weighting, {@code w = m(b) / (m(a) + m(b))} with
     * {@code m = radius²}. A heavier {@code b} yields less push onto itself,
     * so {@code a} absorbs more of the overlap — a mech barely moves for a
     * marine. {@code b} immovable ⇒ infinite mass ⇒ {@code w = 1} ({@code a}
     * yields the overlap in full); {@code a} immovable never reaches here
     * ({@link #accumulate} skips it via {@link #isImmovable} before calling
     * this).
     */
    private float weightOf(long a, long b) {
        if (isImmovable(b)) return 1f;
        float ma = massOf(a);
        float mb = massOf(b);
        return mb / (ma + mb);
    }

    private float massOf(long id) {
        float r = radiusOf(id);
        return r * r;
    }

    /**
     * Infinite-mass participants: emplacements ({@code UnitType.isStatic()} —
     * turrets and drone hubs, the two types spawned without a {@code MOVEMENT}
     * component; see {@link UnitRosterService#adopt}) push but are never
     * pushed — they hold their emplacement anchor. {@link UnitRole#STRUCTURE}
     * is checked too for forward compatibility with any future non-static
     * role that wants the same treatment, but every emplacement type today is
     * covered by {@code isStatic()} alone. Matches the immovable
     * classification the story doc's Algorithm section specifies.
     */
    private boolean isImmovable(long id) {
        return roster.identity().type(id).isStatic() || roster.role().role(id) == UnitRole.STRUCTURE;
    }

    /**
     * Additively folds this tick's separation displacement (as a velocity,
     * cells/sec) into the {@code MOVEMENT} component's velocity fields —
     * the same fields {@link MovementService#setVelocity} writes — so
     * {@code FacingSystem} (which reads "velocity applied this tick" to pick
     * a walk/idle pose) animates a shoved unit instead of ghost-sliding it.
     * {@code MovementService.beginTick} already zeroed these for every mover
     * before this system runs, so this is a set-from-zero, not a stomp.
     */
    private void foldIntoVelocity(long id, float dvx, float dvy) {
        float vx = entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X);
        float vy = entityWorld.getFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y);
        entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_X, vx + dvx);
        entityWorld.setFloat(id, components.MOVEMENT, BattleComponents.MOVEMENT_VEL_Y, vy + dvy);
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
