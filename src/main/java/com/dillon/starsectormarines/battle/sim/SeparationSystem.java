package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.Arrays;

/**
 * Post-movement separation pass. It pushes overlapping ground units apart
 * over a few ticks instead of letting them stack on one point, and gives
 * moving squads a terrain-scaled, role-aware formation steer. The shared
 * slot engine spreads a mech lance into a useful weapons-platform footprint
 * and a marine fireteam into a smaller tactical interval on open ground,
 * compresses either through constrained terrain, and expands it afterward.
 * Design: {@code separation-steering.md}.
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
     * Ordinary neighbor-query radius, in cells — 2 × the largest unit radius
     * (mech, 0.6) plus a per-tick motion margin, so no overlapping pair can be
     * outside the net even after this tick's movement. Mechs use
     * {@link #MECH_FORMATION_QUERY_RADIUS} instead.
     */
    public static final float QUERY_RADIUS = 1.5f;
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
    /** Fully walkable square radius that qualifies the local footprint as open ground. */
    static final int MECH_FORMATION_OPEN_CLEARANCE = 3;
    /** Infantry needs less clear ground before it can resume normal interval. */
    static final int INFANTRY_FORMATION_OPEN_CLEARANCE = 2;
    /** Below this mean path-heading agreement, members keep their individual orders. */
    static final float FORMATION_MIN_HEADING_COHERENCE = 0.65f;
    /** Compression-floor radius plus enough slack for one tick of ordinary mech movement. */
    private static final float MECH_FORMATION_QUERY_RADIUS = 2.75f;

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
    /** Reused member buffer for one squad's active-path formation participants. */
    private long[] formationMembers = new long[0];
    /** Two-float reusable return buffer for path-heading calculation. */
    private final float[] headingScratch = new float[2];

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
        accumulateSquadFormations(dense, liveCount, dt);
        apply(dense, liveCount, dt);
    }

    private void accumulate(long[] dense, int liveCount) {
        for (int i = 0; i < liveCount; i++) {
            long a = dense[i];
            if (!participates(a) || isImmovable(a)) continue;
            float ax = world.x(a);
            float ay = world.y(a);
            float ra = radiusOf(a);
            unitIndex.gather(ax, ay, neighborQueryRadius(a), scratch);
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
                float dist = (float) Math.sqrt(dist2);
                float physicalOverlap = Math.max(0f, sumR - dist);
                float formationGap = movingAlliedMechPair(a, b)
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

                float correction = physicalOverlap * STIFFNESS
                        + formationGap * FORMATION_STIFFNESS;
                float mag = weightOf(a, b) * correction;
                impulseX[i] += dirX * mag;
                impulseY[i] += dirY * mag;
            }
        }
    }

    private void accumulateSquadFormations(long[] dense, int liveCount, float dt) {
        ensureFormationCapacity(liveCount);
        for (Squad squad : roster.getSquads()) {
            FormationProfile profile = formationProfile(squad);
            if (profile == null) continue;
            int count = gatherMovingFormationMembers(squad.id, dense, liveCount);
            if (count < 2) continue;
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
            centerX /= count;
            centerY /= count;
            float forwardLength = (float) Math.sqrt(
                    forwardX * forwardX + forwardY * forwardY);
            if (forwardLength / count < FORMATION_MIN_HEADING_COHERENCE) continue;
            forwardX /= forwardLength;
            forwardY /= forwardLength;
            float lateralX = -forwardY;
            float lateralY = forwardX;
            float spacing = preferredFormationSpacing(
                    formationMembers, count, profile);
            float openness = (spacing - profile.minimumDistance)
                    / (profile.openDistance - profile.minimumDistance);
            if (openness <= 0f) continue;
            boolean depthPair = count == 2 && compareFormationRoles(
                    formationMembers[0], formationMembers[1], profile) != 0;

            for (int slot = 0; slot < count; slot++) {
                float slotForward = slotForward(
                        slot, count, spacing, depthPair);
                float slotLateral = slotLateral(
                        slot, count, spacing, depthPair);
                long member = formationMembers[slot];
                float targetX = centerX + forwardX * slotForward
                        + lateralX * slotLateral;
                float targetY = centerY + forwardY * slotForward
                        + lateralY * slotLateral;
                float dx = targetX - world.x(member);
                float dy = targetY - world.y(member);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance < COINCIDENT_EPS) continue;
                float correction = Math.min(
                        distance * FORMATION_STIFFNESS * openness,
                        FORMATION_MAX_SPEED * dt);
                int denseIndex = roster.indexOf(member);
                if (denseIndex == UnitRosterService.INVALID_INDEX) continue;
                impulseX[denseIndex] += dx / distance * correction;
                impulseY[denseIndex] += dy / distance * correction;
            }
        }
    }

    private int gatherMovingFormationMembers(int squadId, long[] dense,
                                             int liveCount) {
        int count = 0;
        for (int i = 0; i < liveCount; i++) {
            long member = dense[i];
            if (!roster.combat().has(member)
                    || !entityWorld.has(member, components.MOVEMENT)
                    || !hasActivePath(member)
                    || !roster.squad().hasSquad(member)
                    || roster.squad().squadId(member) != squadId
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
        MechRole role = world.mechLoadout(member).role;
        if (role == MechRole.ASSAULT) return 0;
        if (role == MechRole.LR_SUPPORT) return 2;
        return 1;
    }

    private void pathHeading(long member, float[] output) {
        int[] path = world.path(member);
        int count = Paths.cellCount(path);
        float x = world.x(member);
        float y = world.y(member);
        for (int i = world.pathIdx(member); i < count; i++) {
            float dx = Paths.cellX(path, i) + 0.5f - x;
            float dy = Paths.cellY(path, i) + 0.5f - y;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 0.25f) {
                output[0] = dx / length;
                output[1] = dy / length;
                return;
            }
        }
        output[0] = 0f;
        output[1] = 0f;
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
            clearance = Math.min(clearance, walkableClearance(
                    world.cellX(member), world.cellY(member), profile));
        }
        float openness = clearance / (float) profile.openClearance;
        return profile.minimumDistance
                + (profile.openDistance - profile.minimumDistance) * openness;
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
                                     boolean depthPair) {
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
        float radius = spacing / (2f * (float) Math.sin(Math.PI / count));
        return (float) Math.cos(2f * Math.PI * slot / count) * radius;
    }

    private static float slotLateral(int slot, int count, float spacing,
                                     boolean depthPair) {
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

    private float neighborQueryRadius(long id) {
        return isMech(id) ? MECH_FORMATION_QUERY_RADIUS : QUERY_RADIUS;
    }

    private boolean movingAlliedMechPair(long a, long b) {
        return isMech(a) && isMech(b)
                && roster.identity().faction(a) == roster.identity().faction(b)
                && (hasActivePath(a) || hasActivePath(b));
    }

    private boolean isMech(long id) {
        return roster.identity().type(id).isMech();
    }

    private boolean hasActivePath(long id) {
        return world.pathIdx(id) < Paths.cellCount(world.path(id));
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
                && roster.radius(id) > 0f;
    }

    private float radiusOf(long id) {
        return roster.radius(id);
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

    private void ensureFormationCapacity(int required) {
        if (formationMembers.length >= required) return;
        int newCap = Math.max(required, Math.max(16, formationMembers.length * 2));
        formationMembers = new long[newCap];
    }
}
