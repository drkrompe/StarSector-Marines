package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.smoke.SmokeObscuration;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.BodyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.world.model.DoodadService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.LongPredicate;

/**
 * Resolves one round's full flight in a single space-time raycast at fire
 * time — the ballistics swap's core: a ray from the shooter toward a
 * lead-and-target-plane-sampled aim point, hard-capped at the first wall, walked
 * against height-gated doodad block rolls and time-domain unit-radius contacts
 * in time order. Damage/FX application is scheduled by the caller on the returned
 * {@link Resolution#flightTime()}; this class only decides <em>where the
 * round physically ends up</em>.
 *
 * <p>See {@code ballistics-nouns.md} for the enduring model: the time-domain
 * contact solve, target-plane aim, vertical silhouettes, obstacle catch bands,
 * and explicit fire-source boundary.
 *
 * <p><b>Pure and stateless.</b> Every constructor dependency is read-only
 * from this class's perspective (grid, doodad cover, the spatial index
 * snapshot, roster by-id reads); all randomness flows through the caller's
 * {@link Random}, mirroring the combat samplers' testability contract. No
 * mutable instance state, so a single shared resolver instance is safe to
 * call concurrently from the parallel UPDATE_UNITS dispatch — every local
 * (event list, gather buffer) is call-scoped. The one intentionally
 * unsynchronized read is a candidate's velocity ({@code MOVEMENT_VEL_X/Y}
 * via {@link MovementService}), written by the same tick's movement pass —
 * see {@link #resolve} for why a mid-tick-stale sample is fine.
 */
public final class BallisticResolver {

    /**
     * A missed round remains physically live for this multiple of its
     * weapon's maximum targeting range. An accurate round needs collision
     * work only through its predicted contact with the intended body.
     */
    public static final float FLIGHT_RANGE_MULTIPLIER = 1.5f;
    /**
     * A manually thrown area round may be aimed out to this multiple of its weapon range; a point
     * further out bursts at this distance along the same bearing. Past nominal range the round pays
     * in scatter ({@link GroundBurstAim}), not in refusal.
     */
    public static final float MAX_GROUND_BURST_RANGE_MULTIPLIER = 2f;
    /** A physical ray crossing another hostile always transfers the missed shot into that body. */
    public static final float HOSTILE_INCIDENTAL_HIT_CHANCE = 1f;
    /** Friendly bodies retain a probabilistic graze so formation fire is not guaranteed self-harm. */
    public static final float FRIENDLY_INCIDENTAL_HIT_CHANCE = 0.35f;
    /** Damage multiplier applied by the caller (queue time) when a round's victim shares the shooter's faction. Declared here as the tuning surface; not consumed inside {@link #resolve}. */
    public static final float FRIENDLY_FIRE_DAMAGE_MULT = 0.5f;
    /** Probabilistic cover and friendly contacts at or inside this muzzle distance have zero catch chance. */
    public static final float PROXIMITY_CATCH_ZERO_DISTANCE = 2.0f;
    /** Probabilistic cover and friendly contacts at or beyond this muzzle distance use their full authored chance. */
    public static final float PROXIMITY_CATCH_FULL_DISTANCE = 8.0f;
    /**
     * A shooter at or inside this distance from a man in a trench meets none of its cover: close
     * enough to fire down into it. Trench cover is strongest against fire from a distance, and an
     * attacker who closes has beaten the position.
     */
    public static final float OCCUPANT_COVER_ZERO_DISTANCE = 3.0f;
    /** A shooter at or beyond this distance from a man in a trench meets its full cover. */
    public static final float OCCUPANT_COVER_FULL_DISTANCE = 15.0f;
    /** Round speed (cells/sec) used when the firing weapon carries no velocity of its own. Declared here as the tuning surface; the per-shot velocity is resolved by the caller before calling {@link #resolve}. */
    public static final float DEFAULT_ROUND_VELOCITY = 60f;
    /** Block-roll chance by cover level, index = level (capped at 3 = {@link NavigationGrid#MAX_COVER}). Tuning-neutral anchor: today's 0.85/0.70/0.55 accuracy multipliers for levels 1/2/3. */
    public static final float[] BLOCK_CHANCE_BY_LEVEL = {0f, 0.15f, 0.30f, 0.45f};
    /**
     * Slack (cells) added on top of the largest body radius when gathering
     * candidates along the corridor, for a stationary candidate. Movers add
     * {@link #MAX_MOVER_SPEED_CELLS} scaled by flight exposure on top of that
     * (see the margin computation in {@link #resolve}), since a mover can walk
     * into the corridor mid-flight even when its fire-tick position sits
     * outside the base margin.
     *
     * <p>This used to <em>be</em> the whole base margin, documented as "max
     * collision radius plus slack" and fixed at 1.0. It stopped being true
     * when a 1.2-cell APC became an ordinary index body: a point-blank shot
     * down a lane the hull overhung passed through it, because the speed term
     * that topped the margin up shrinks to nothing over a two-cell shot. The
     * body-size half is no longer this constant's job, or any caller's —
     * {@link UnitSpatialIndex#gatherAlongSegment} adds it, because the index
     * is the only party that knows how big the bodies it holds are. What is
     * left here is the clearance it was always meant to be.
     */
    public static final float GATHER_SLACK_CELLS = 1.0f;
    /**
     * Fastest mover speed (cells/sec) assumed when scaling the gather margin
     * by flight exposure time — a tuning-surface constant per
     * {@link #GATHER_SLACK_CELLS}'s precedent, not a per-call roster scan.
     * The fastest live mover today is ALIEN at 2.2 cells/s; 4 leaves
     * headroom for vehicles.
     */
    public static final float MAX_MOVER_SPEED_CELLS = 4f;

    /** Where a round's flight ended and what stopped it. */
    /** {@code GROUND_BURST} is a thrown rocket that met nothing on its way and burst where it was aimed. */
    public enum StopKind { UNIT_HIT, COVER_CLIP, WALL, DOODAD_BLOCK, OVERSHOOT, GROUND_BURST }

    /**
     * General fire origin for entity and non-entity mounts. {@code entityId}
     * is zero when the source does not live in the ground-unit roster; a
     * non-zero id excludes the shooter's own body from the contact gather.
     * {@code z} is the lightweight target-plane origin used by future
     * airborne adopters; every S4 ground source supplies zero.
     */
    public record Source(long entityId, float x, float y, float z, Faction faction) {
        public Source {
            Objects.requireNonNull(faction, "faction");
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("Ballistic source coordinates must be finite");
            }
        }
    }

    /**
     * The outcome of one resolved round. {@code endX}/{@code endY}/{@code endZ}
     * is where the round physically stopped (visual endpoint); {@code flightTime} =
     * {@code stopDist / roundVelocity}, the delay before damage/FX apply;
     * {@code victimId} is 0 unless {@code kind == UNIT_HIT}; {@code
     * hitIntended} is true only when the recorded victim is the locked
     * target (drives {@code ShotEvent.hit}); {@code friendlyHit} is true
     * when the recorded victim shares the shooter's faction.
     */
    /**
     * One accepted actor contact retained even when authored penetration lets the round continue.
     * {@code part} is where on a person the round landed, or null for a body without parts.
     */
    public record BodyHit(long victimId, float x, float y, float z, float flightTime,
                          boolean intended, boolean friendly, BodyPart part) { }

    /**
     * {@code struck} names the piece of the environment the round stopped in, or is null: a body hit,
     * an overshoot, a catch by the ground a man stands in (a trench), or edge cover lent by the map's
     * boundary. It is recorded here and worn only at arrival ({@link EnvironmentWear}); the resolver
     * never writes the world.
     */
    public record Resolution(float endX, float endY, float endZ, float flightTime,
                              long victimId, boolean hitIntended,
                              boolean friendlyHit, StopKind kind,
                              List<BodyHit> bodyHits, StruckFeature struck) {
        public Resolution {
            bodyHits = List.copyOf(bodyHits);
        }

        public Resolution(float endX, float endY, float endZ, float flightTime,
                          long victimId, boolean hitIntended,
                          boolean friendlyHit, StopKind kind,
                          List<BodyHit> bodyHits) {
            this(endX, endY, endZ, flightTime, victimId, hitIntended, friendlyHit, kind, bodyHits, null);
        }
        /** True when the round reached a physical contact rather than leaving the modeled segment. */
        public boolean impacts() {
            return kind != StopKind.OVERSHOOT;
        }

        /** The body part the terminal victim was struck on, or null (no victim, or a body without parts). */
        public BodyPart victimPart() {
            for (int i = bodyHits.size() - 1; i >= 0; i--) {
                if (bodyHits.get(i).victimId() == victimId) return bodyHits.get(i).part();
            }
            return null;
        }
    }

    /**
     * One contact candidate along the ray, in time order. Doodad crossings
     * roll their level chance, unit contacts roll cover-clip then incidental
     * graze; proximity attenuation is applied while walking these events.
     * Intended accuracy was already committed when target-plane aim was sampled.
     * {@code victimCellX}/{@code victimCellY} (unit events only) is the
     * victim's OWN extrapolated cell at contact time, floor(U(t)) — the
     * cover edge-clip lookup's cell, distinct from {@code x}/{@code y} (the
     * round's own FX endpoint, P(t)). See the "Contact-position split" note
     * in {@code ballistics-nouns.md}.
     */
    private static final class Event {
        final float t;
        final boolean doodad;
        final float x;
        final float y;
        final float z;
        final int doodadLevel;
        final long unitId;
        final boolean friendly;
        final boolean hull;
        final int victimCellX;
        final int victimCellY;

        private Event(float t, boolean doodad, float x, float y, float z, int doodadLevel,
                       long unitId, boolean friendly, boolean hull,
                       int victimCellX, int victimCellY) {
            this.t = t;
            this.doodad = doodad;
            this.x = x;
            this.y = y;
            this.z = z;
            this.doodadLevel = doodadLevel;
            this.unitId = unitId;
            this.friendly = friendly;
            this.hull = hull;
            this.victimCellX = victimCellX;
            this.victimCellY = victimCellY;
        }

        static Event doodad(float t, float x, float y, float z, int level) {
            return new Event(t, true, x, y, z, level, 0L, false, false, 0, 0);
        }

        static Event unit(float t, float x, float y, float z, long unitId,
                           boolean friendly, boolean hull,
                           int victimCellX, int victimCellY) {
            return new Event(t, false, x, y, z, 0, unitId, friendly, hull,
                    victimCellX, victimCellY);
        }
    }

    private final NavigationGrid grid;
    private final DoodadService doodads;
    private final UnitSpatialIndex unitIndex;
    private final UnitRosterService roster;
    private final BodyService bodies;
    /** Bodies held on a swarm carrier's blades: direct fire never touches them (`colony-breach-nouns.md`, Harvest). */
    private LongPredicate held = id -> false;

    public BallisticResolver(NavigationGrid grid, DoodadService doodads,
                              UnitSpatialIndex unitIndex, UnitRosterService roster) {
        this.grid = grid;
        this.doodads = doodads;
        this.unitIndex = unitIndex;
        this.roster = roster;
        this.bodies = roster.bodies();
    }

    /**
     * Names the bodies no round may touch: a body held on a carrier is passed
     * by the ray, whether it was the round's target, a stray's bystander or the
     * body behind a penetration. It stays in the index and the roster, because
     * everything else still has to see it.
     */
    public void spareHeld(LongPredicate held) {
        this.held = held == null ? id -> false : held;
    }

    /**
     * How far, in cells beyond a friend's own body radius, the lane a shooter checks before firing reaches either
     * side of the line to the target. A round's lateral error is the reason it is wider than the body: a friend
     * whose shoulder is a hand's breadth off the line is still in it.
     */
    public static final float FRIENDLY_LANE_CLEARANCE = 0.25f;

    /**
     * How far past the target, in cells, the checked lane runs. A round that misses flies on, and a friend
     * standing just behind the man being shot at is where it goes first.
     */
    public static final float FRIENDLY_LANE_OVERSHOOT = 1.5f;

    private static final ThreadLocal<LongBucket> LANE_SCRATCH = ThreadLocal.withInitial(LongBucket::new);

    /**
     * Whether a friend of {@code shooter} stands in the lane from it to {@code target}: the check a shooter makes
     * before pulling the trigger, as against the discipline roll it makes after the round is committed. Reads only
     * and draws nothing from any random stream. The lane starts where the resolver starts to let a round strike a
     * friend ({@link #PROXIMITY_CATCH_ZERO_DISTANCE}), so a man at his squadmate's shoulder is not blocked by him.
     */
    public boolean friendInLane(long shooter, long target) {
        World world = roster.world();
        return friendInLane(shooter, world.renderX(shooter), world.renderY(shooter),
                roster.identity().faction(shooter), world.renderX(target), world.renderY(target), target);
    }

    /**
     * {@link #friendInLane(long, long)} from an explicit muzzle toward a point; {@code shooter} and {@code target}
     * are never counted as friends in the way (zero for none).
     */
    public boolean friendInLane(long shooter, float fromX, float fromY, Faction side,
                                float toX, float toY, long target) {
        return friendInLane(shooter, fromX, fromY, 0f, side, toX, toY, target);
    }

    /**
     * {@link #friendInLane(long, float, float, Faction, float, float, long)} from a muzzle standing {@code fromZ}
     * cells above the target plane, as a gun on a plinth does: a friend under the line the round descends along
     * ({@link #overhead}) is not in its way.
     */
    public boolean friendInLane(long shooter, float fromX, float fromY, float fromZ, Faction side,
                                float toX, float toY, long target) {
        LongBucket candidates = LANE_SCRATCH.get();
        candidates.clear();
        unitIndex.gatherAlongSegment(fromX, fromY, toX, toY, FRIENDLY_LANE_CLEARANCE + FRIENDLY_LANE_OVERSHOOT,
                candidates);
        World world = roster.world();
        for (int i = 0; i < candidates.size; i++) {
            long id = candidates.ids[i];
            if (id == shooter || id == target || held.test(id)) continue;
            BodyCarrier carrier = bodies.carrierOf(id);
            if (carrier != null ? !carrier.isTargetable(id) : !roster.isAliveById(id)) continue;
            Faction faction = carrier != null ? carrier.faction(id) : roster.identity().faction(id);
            if (faction == Faction.CIVILIAN || !side.friendlyTo(faction)) continue;
            if (!inLane(fromX, fromY, toX, toY, world.x(id), world.y(id), roster.radius(id))) continue;
            if (fromZ > 0f && overhead(fromX, fromY, fromZ, toX, toY, world.x(id), world.y(id),
                    roster.hitHalfHeight(id))) continue;
            return true;
        }
        return false;
    }

    /**
     * Whether a round leaving {@code fromZ} cells above the target plane at ({@code fromX}, {@code fromY}) and
     * descending in a straight line to the plane at ({@code toX}, {@code toY}) passes over the top of a body of
     * {@code halfHeight} at ({@code bx}, {@code by}): the round's height where it crosses the body is above it.
     * Zero height overflies nothing.
     */
    public static boolean overhead(float fromX, float fromY, float fromZ, float toX, float toY,
                                   float bx, float by, float halfHeight) {
        if (!(fromZ > 0f)) return false;
        float dx = toX - fromX;
        float dy = toY - fromY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-6f) return false;
        float along = ((bx - fromX) * dx + (by - fromY) * dy) / length;
        float zThere = fromZ * (1f - Math.max(0f, Math.min(1f, along / length)));
        return zThere > halfHeight;
    }

    /**
     * Whether a burst of {@code radius} at ({@code x}, {@code y}) would reach a body on {@code side}, the shooter's
     * own included: the danger-close check a gunner makes on where the round is going to land. A body's footprint
     * counts, as it does in {@code Detonations}. Reads only.
     */
    public boolean friendInBlast(long shooter, Faction side, float x, float y, float radius) {
        if (shooter != 0L && bodyInBlast(shooter, x, y, radius)) return true;
        LongBucket candidates = LANE_SCRATCH.get();
        candidates.clear();
        unitIndex.gather(x, y, radius + BLAST_FOOTPRINT_PAD, candidates);
        for (int i = 0; i < candidates.size; i++) {
            long id = candidates.ids[i];
            if (id == shooter || held.test(id)) continue;
            BodyCarrier carrier = bodies.carrierOf(id);
            if (carrier != null ? !carrier.isTargetable(id) : !roster.isAliveById(id)) continue;
            if (roster.isRiding(id)) continue;
            Faction faction = carrier != null ? carrier.faction(id) : roster.identity().faction(id);
            if (side.friendlyTo(faction) && bodyInBlast(id, x, y, radius)) return true;
        }
        return false;
    }

    /** Whether body {@code id}'s footprint reaches into a burst of {@code radius} at ({@code x}, {@code y}). */
    public boolean bodyInBlast(long id, float x, float y, float radius) {
        World world = roster.world();
        float dx = world.x(id) - x;
        float dy = world.y(id) - y;
        float reach = radius + roster.radius(id);
        return dx * dx + dy * dy <= reach * reach;
    }

    /** How far past a blast's radius {@link #friendInBlast} gathers for a body whose footprint reaches into it. */
    private static final float BLAST_FOOTPRINT_PAD = 2f;

    /**
     * The lane geometry on its own: whether a body of {@code radius} at ({@code bx}, {@code by}) stands in the lane
     * from ({@code fromX}, {@code fromY}) to ({@code toX}, {@code toY}) -- no nearer the muzzle than
     * {@link #PROXIMITY_CATCH_ZERO_DISTANCE}, no further along than {@link #FRIENDLY_LANE_OVERSHOOT} past the target,
     * and within its radius plus {@link #FRIENDLY_LANE_CLEARANCE} of the line.
     */
    public static boolean inLane(float fromX, float fromY, float toX, float toY,
                                 float bx, float by, float radius) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 1e-6f) return false;
        float ux = dx / length;
        float uy = dy / length;
        float rx = bx - fromX;
        float ry = by - fromY;
        float along = rx * ux + ry * uy;
        if (along < PROXIMITY_CATCH_ZERO_DISTANCE || along > length + FRIENDLY_LANE_OVERSHOOT) return false;
        float lateral = Math.abs(rx * uy - ry * ux);
        return lateral <= radius + FRIENDLY_LANE_CLEARANCE;
    }

    /** {@code target}'s ground velocity in cells/sec, as this resolver's own lead reads it. */
    public float targetVelocityX(long target) {
        return TargetMotion.velocityX(bodies, roster.movement(), target);
    }

    /** {@code target}'s ground velocity in cells/sec, as this resolver's own lead reads it. */
    public float targetVelocityY(long target) {
        return TargetMotion.velocityY(bodies, roster.movement(), target);
    }

    /**
     * Resolves the shooter's round against {@code target}, walking every
     * wall/doodad/unit contact along the ray in time order and returning
     * where it stopped. The aim point leads the target's one-step predicted
     * position (perfect lead — tuning-neutral, a led shot against a
     * constant-velocity target contacts exactly as a stationary shot does),
     * and every unit contact is solved in the TIME domain against the
     * candidate's own extrapolated motion; a non-mover's implicit zero
     * velocity collapses both the lead and the contact solve to S1's
     * static-world math exactly. {@code finalAccuracy} is the full accuracy
     * stack with cover already removed (cover is re-expressed here as
     * physical interception); {@code effectiveSpread} broadens the sampled
     * lateral/elevation miss clearance in the target plane; {@code
     * roundVelocity} is cells/sec, already resolved by the caller (see
     * {@link #DEFAULT_ROUND_VELOCITY}); {@code maximumTargetingRange} is the
     * firing weapon's effective acquisition range and bounds a missed free
     * flight at {@link #FLIGHT_RANGE_MULTIPLIER} times that distance. Reads
     * only — safe to call from a parallel dispatch.
     */
    public Resolution resolve(long shooter, long target,
                               float finalAccuracy, float effectiveSpread,
                               float roundVelocity, float maximumTargetingRange,
                               Random rng) {
        return resolve(shooter, target, finalAccuracy, effectiveSpread, roundVelocity,
                maximumTargetingRange, 0, rng);
    }

    public Resolution resolve(long shooter, long target,
                               float finalAccuracy, float effectiveSpread,
                               float roundVelocity, float maximumTargetingRange,
                               int bodyPenetrations, Random rng) {
        World world = roster.world();
        return resolve(new Source(shooter, world.renderX(shooter), world.renderY(shooter),
                        0f, roster.identity().faction(shooter)),
                target, finalAccuracy, effectiveSpread, roundVelocity,
                maximumTargetingRange, bodyPenetrations, rng);
    }

    /**
     * Resolves a round from an explicit source. This is the shared entry point
     * for static mounts and other callers that already own a float muzzle
     * position rather than needing it read from a roster entity.
     */
    public Resolution resolve(Source source, long target,
                               float finalAccuracy, float effectiveSpread,
                               float roundVelocity, float maximumTargetingRange,
                               Random rng) {
        return resolve(source, target, finalAccuracy, effectiveSpread, roundVelocity,
                maximumTargetingRange, 0, rng);
    }

    public Resolution resolve(Source source, long target,
                               float finalAccuracy, float effectiveSpread,
                               float roundVelocity, float maximumTargetingRange,
                               int bodyPenetrations, Random rng) {
        if (!(roundVelocity > 0f) || !Float.isFinite(roundVelocity)) {
            throw new IllegalArgumentException("roundVelocity must be finite and positive");
        }
        if (!(maximumTargetingRange > 0f) || !Float.isFinite(maximumTargetingRange)) {
            throw new IllegalArgumentException("maximumTargetingRange must be finite and positive");
        }
        if (bodyPenetrations < 0) {
            throw new IllegalArgumentException("bodyPenetrations cannot be negative");
        }
        World world = roster.world();
        MovementService movement = roster.movement();

        float fromX = source.x();
        float fromY = source.y();
        float fromZ = source.z();
        float targetX = world.renderX(target);
        float targetY = world.renderY(target);

        // Step 1: shooter lead. The aim point is the target's one-step
        // predicted position at estimated intercept time (dist / velocity),
        // not its fire-tick position — a lead without extrapolation and an
        // extrapolation without lead each systematically miss a lateral
        // mover; they only balance as a pair (see ballistics-nouns.md). wTarget
        // = 0 for a non-mover (turret/hub target), which zeroes tLead's
        // contribution and reproduces S1's aim point exactly.
        float wTargetX = TargetMotion.velocityX(bodies, movement, target);
        float wTargetY = TargetMotion.velocityY(bodies, movement, target);
        float tLead = dist(fromX, fromY, targetX, targetY) / roundVelocity;
        float leadX = targetX + wTargetX * tLead;
        float leadY = targetY + wTargetY * tLead;

        // Step 2: commit accuracy once in the target plane. Lateral error
        // rotates the real ground ray; elevation error becomes a linear Z
        // slope. An authored miss therefore visibly clears the intended
        // silhouette instead of crossing it and failing a hidden second roll.
        //
        // Smoke joins the stack here rather than upstream because obscuration
        // is a property of the lane this round actually flies, not of the
        // shooter: it is measured on the same source-to-target segment the
        // structural trace uses, and it degrades the sight picture instead of
        // stopping anything. The round itself crosses the cloud untouched.
        float obscuredAccuracy = finalAccuracy * SmokeObscuration.onLine(grid, fromX, fromY, targetX, targetY);
        TargetPlaneAim.Sample aim = TargetPlaneAim.sample(
                obscuredAccuracy, world.incomingAccuracyMult(target), effectiveSpread,
                targetRadius(target), targetHitHalfHeight(target), rng);
        float baseDx = leadX - fromX;
        float baseDy = leadY - fromY;
        float baseDist = (float) Math.sqrt(baseDx * baseDx + baseDy * baseDy);
        float baseDirX = baseDist > 1e-6f ? baseDx / baseDist : 1f;
        float baseDirY = baseDist > 1e-6f ? baseDy / baseDist : 0f;
        float aimX = leadX - baseDirY * aim.lateral();
        float aimY = leadY + baseDirX * aim.lateral();
        float zSlope = (aim.elevation() - fromZ) / Math.max(baseDist, 1e-6f);

        float aimDx = aimX - fromX;
        float aimDy = aimY - fromY;
        float aimDist = (float) Math.sqrt(aimDx * aimDx + aimDy * aimDy);
        float dirX, dirY;
        if (aimDist > 1e-6f) {
            dirX = aimDx / aimDist;
            dirY = aimDy / aimDist;
        } else {
            // Degenerate: shooter and jittered aim point coincide. Any fixed
            // direction is as good as another — nothing meaningful to aim at.
            dirX = 1f;
            dirY = 0f;
        }
        float maximumFlightDistance = maximumTargetingRange * FLIGHT_RANGE_MULTIPLIER;
        float rawLen = maximumFlightDistance;
        if (aim.onTarget() && bodyPenetrations == 0) {
            float targetContactTime = firstCircleContactTime(
                    fromX, fromY, dirX, dirY, roundVelocity,
                    targetX, targetY, wTargetX, wTargetY, targetRadius(target));
            // Search only through the intended body's predicted entry point:
            // walls, cover, and interposers before it still win, while
            // irrelevant space behind a successful shot is never gathered or
            // sorted. Math.nextUp keeps the independently repeated candidate
            // solve inside the closed trace despite float roundoff. A
            // pathological lead with no ground-circle intersection falls
            // back to the sampled target plane rather than becoming a miss
            // with a full free-flight tail.
            float accurateFlightDistance = targetContactTime >= 0f
                    ? Math.nextUp(roundVelocity * targetContactTime)
                    : aimDist;
            rawLen = Math.min(maximumFlightDistance, accurateFlightDistance);
        }
        return trace(source, target, aim.onTarget(), aim.heightFraction(), dirX, dirY, zSlope,
                rawLen, roundVelocity, bodyPenetrations, rng);
    }

    /**
     * Commits a manually aimed primary from the body's current float position.
     * There is no target identity, target lead, or silhouette-based hit roll.
     */
    public Resolution resolvePoint(long shooter, float aimX, float aimY,
                                   float finalAccuracy, float effectiveSpread,
                                   float roundVelocity, float maximumTargetingRange,
                                   Random rng) {
        return resolvePoint(shooter, aimX, aimY, finalAccuracy, effectiveSpread, roundVelocity,
                maximumTargetingRange, PointAimDispersion.WEAPON, rng);
    }

    /** As above, with the dispersion profile chosen by the caller (a player's cursor is {@code CONTROLLED}). */
    public Resolution resolvePoint(long shooter, float aimX, float aimY,
                                   float finalAccuracy, float effectiveSpread,
                                   float roundVelocity, float maximumTargetingRange,
                                   PointAimDispersion dispersion, Random rng) {
        World world = roster.world();
        return resolvePoint(new Source(shooter, world.renderX(shooter), world.renderY(shooter),
                        0f, roster.identity().faction(shooter)),
                aimX, aimY, finalAccuracy, effectiveSpread, roundVelocity,
                maximumTargetingRange, 0, dispersion, rng);
    }

    public Resolution resolvePoint(Source source, float aimX, float aimY,
                                   float finalAccuracy, float effectiveSpread,
                                   float roundVelocity, float maximumTargetingRange,
                                   Random rng) {
        return resolvePoint(source, aimX, aimY, finalAccuracy, effectiveSpread,
                roundVelocity, maximumTargetingRange, 0, rng);
    }

    /**
     * Resolves one point shot from an explicit muzzle. The aim point supplies
     * only a bearing; it is not a lifetime or a locked
     * body. The round remains live to the weapon's full modeled reach, including
     * beyond the cursor, and every body it meets is an incidental contact.
     * Callers retain the ordinary weapon/profile/range/stance accuracy stack.
     * Smoke further widens aim error before the shared contact walk.
     *
     * @throws IllegalArgumentException for a non-finite input or coincident aim;
     *     a trigger without a meaningful bearing must be held by the caller
     */
    public Resolution resolvePoint(Source source, float aimX, float aimY,
                                   float finalAccuracy, float effectiveSpread,
                                   float roundVelocity, float maximumTargetingRange,
                                   int bodyPenetrations, Random rng) {
        return resolvePoint(source, aimX, aimY, finalAccuracy, effectiveSpread, roundVelocity,
                maximumTargetingRange, bodyPenetrations, PointAimDispersion.WEAPON, rng);
    }

    private Resolution resolvePoint(Source source, float aimX, float aimY,
                                    float finalAccuracy, float effectiveSpread,
                                    float roundVelocity, float maximumTargetingRange,
                                    int bodyPenetrations, PointAimDispersion dispersion, Random rng) {
        if (!(roundVelocity > 0f) || !Float.isFinite(roundVelocity)) {
            throw new IllegalArgumentException("roundVelocity must be finite and positive");
        }
        if (!(maximumTargetingRange > 0f) || !Float.isFinite(maximumTargetingRange)) {
            throw new IllegalArgumentException("maximumTargetingRange must be finite and positive");
        }
        if (bodyPenetrations < 0) {
            throw new IllegalArgumentException("bodyPenetrations cannot be negative");
        }
        float dx = aimX - source.x();
        float dy = aimY - source.y();
        float aimDistance = (float) Math.hypot(dx, dy);
        if (!Float.isFinite(aimDistance) || aimDistance <= 1e-6f) {
            throw new IllegalArgumentException("Point aim must be finite and distinct from its source");
        }
        float baseDirX = dx / aimDistance;
        float baseDirY = dy / aimDistance;
        // A nearby cursor must not evade smoke further down the same firing
        // lane. Manual aim is calibrated at weapon range, including its sight
        // picture, rather than at an arbitrary cursor distance.
        float sightDistance = maximumTargetingRange;
        float obscuredAccuracy = finalAccuracy * SmokeObscuration.onLine(grid, source.x(), source.y(),
                source.x() + baseDirX * sightDistance, source.y() + baseDirY * sightDistance);
        PointAim.Sample aim = PointAim.sample(obscuredAccuracy, effectiveSpread,
                maximumTargetingRange, dispersion, rng);
        float dirX = baseDirX - baseDirY * aim.lateralSlope();
        float dirY = baseDirY + baseDirX * aim.lateralSlope();
        float directionLength = (float) Math.hypot(dirX, dirY);
        dirX /= directionLength;
        dirY /= directionLength;
        // Source Z is the same lightweight ground silhouette coordinate used
        // by entity aim; at the sight plane the centered round reaches Z=0.
        float zSlope = (aim.elevationSlope() - source.z() / sightDistance)
                / directionLength;
        return trace(source, 0L, false, 0.5f, dirX, dirY, zSlope,
                maximumTargetingRange * FLIGHT_RANGE_MULTIPLIER,
                roundVelocity, bodyPenetrations, rng);
    }

    /**
     * Resolves one manually thrown rocket. Unlike {@link #resolvePoint}, the aim point is a
     * destination: the round flies toward the aim point plus its scatter, stops at the first body,
     * wall or doodad on the way as any round does, and otherwise bursts on the ground there
     * ({@link StopKind#GROUND_BURST}) instead of overshooting into free flight. An aim point beyond
     * {@link #MAX_GROUND_BURST_RANGE_MULTIPLIER} times the weapon range is pulled in along its bearing.
     *
     * @throws IllegalArgumentException for a non-finite input or coincident aim
     */
    public Resolution resolveGroundBurst(Source source, float aimX, float aimY,
                                         float finalAccuracy, float effectiveSpread,
                                         float roundVelocity, float weaponRange,
                                         int bodyPenetrations, Random rng) {
        if (!(roundVelocity > 0f) || !Float.isFinite(roundVelocity)) {
            throw new IllegalArgumentException("roundVelocity must be finite and positive");
        }
        if (!(weaponRange > 0f) || !Float.isFinite(weaponRange)) {
            throw new IllegalArgumentException("weaponRange must be finite and positive");
        }
        if (bodyPenetrations < 0) {
            throw new IllegalArgumentException("bodyPenetrations cannot be negative");
        }
        float dx = aimX - source.x();
        float dy = aimY - source.y();
        float aimDistance = (float) Math.hypot(dx, dy);
        if (!Float.isFinite(aimDistance) || aimDistance <= 1e-6f) {
            throw new IllegalArgumentException("Point aim must be finite and distinct from its source");
        }
        float baseDirX = dx / aimDistance;
        float baseDirY = dy / aimDistance;
        float throwDistance = Math.min(aimDistance, weaponRange * MAX_GROUND_BURST_RANGE_MULTIPLIER);
        float obscuredAccuracy = finalAccuracy * SmokeObscuration.onLine(grid, source.x(), source.y(),
                source.x() + baseDirX * throwDistance, source.y() + baseDirY * throwDistance);
        GroundBurstAim.Offset scatter = GroundBurstAim.sample(obscuredAccuracy, effectiveSpread,
                weaponRange, throwDistance, rng);
        float burstDx = baseDirX * throwDistance + scatter.dx();
        float burstDy = baseDirY * throwDistance + scatter.dy();
        float burstDistance = (float) Math.hypot(burstDx, burstDy);
        if (burstDistance <= 1e-6f) {
            // Scatter landed the burst on the muzzle itself; keep the bearing rather than invent one.
            burstDx = baseDirX * 1e-3f;
            burstDy = baseDirY * 1e-3f;
            burstDistance = 1e-3f;
        }
        float dirX = burstDx / burstDistance;
        float dirY = burstDy / burstDistance;
        // The round comes down to the ground at the burst point.
        float zSlope = -source.z() / burstDistance;
        Resolution res = trace(source, 0L, false, 0.5f, dirX, dirY, zSlope,
                burstDistance, roundVelocity, bodyPenetrations, rng);
        if (res.kind() != StopKind.OVERSHOOT) return res;
        return new Resolution(res.endX(), res.endY(), res.endZ(), res.flightTime(),
                0L, res.hitIntended(), false, StopKind.GROUND_BURST, res.bodyHits(), null);
    }

    /** Shared physical contact walk; aim policy is fully committed before entry. */
    private Resolution trace(Source source, long target, boolean onTarget, float targetHeightFraction,
                             float dirX, float dirY, float zSlope, float rawLen,
                             float roundVelocity, int bodyPenetrations, Random rng) {
        World world = roster.world();
        MovementService movement = roster.movement();
        Faction shooterFaction = source.faction();
        float fromX = source.x();
        float fromY = source.y();
        float fromZ = source.z();
        float rawEndX = fromX + dirX * rawLen;
        float rawEndY = fromY + dirY * rawLen;

        long wallPacked = grid.firstWallOnLine(fromX, fromY, rawEndX, rawEndY);
        int wallCellX = (int) wallPacked;
        int wallCellY = (int) (wallPacked >>> 32);
        boolean wallFound = !(wallCellX == -1 && wallCellY == -1);

        float wallEntry = wallFound
                ? segmentCellEntryFraction(fromX, fromY, rawEndX, rawEndY,
                wallCellX, wallCellY)
                : 1f;
        float wallBoundaryX = fromX + (rawEndX - fromX) * wallEntry;
        float wallBoundaryY = fromY + (rawEndY - fromY) * wallEntry;
        // Keep the committed endpoint one representable float inside the
        // blocker. Detonation and wall-damage payloads project the endpoint
        // with floor(); an exact east/south boundary would otherwise name the
        // adjacent open cell when the round approached from that side.
        float rayEndX = wallFound
                ? Math.nextAfter(wallBoundaryX, rawEndX) : rawEndX;
        float rayEndY = wallFound
                ? Math.nextAfter(wallBoundaryY, rawEndY) : rawEndY;
        // rawLen is the authoritative distance for an unobstructed ray. Do
        // not reconstruct it from rounded endpoint coordinates: an accurate
        // trace ends one representable float beyond the intended contact,
        // and a second sqrt can round that protection back below the contact.
        float rayLen = wallFound
                ? dist(fromX, fromY, rayEndX, rayEndY)
                : rawLen;
        int shooterCellX = (int) Math.floor(fromX);
        int shooterCellY = (int) Math.floor(fromY);
        int rayEndCellX = (int) Math.floor(rayEndX);
        int rayEndCellY = (int) Math.floor(rayEndY);

        List<Event> events = new ArrayList<>();

        // Step 3: doodad crossings — Bresenham the (wall-capped) ray's cells,
        // skipping the shooter's own cell. Doodads are static, so this stays
        // the S1 distance-domain math verbatim.
        walkDoodadCrossings(shooterCellX, shooterCellY, rayEndCellX, rayEndCellY,
                fromX, fromY, fromZ, roundVelocity, zSlope, events);

        // Step 4: unit contacts over the (wall-capped) ray, solved in the
        // TIME domain against each candidate's own extrapolated motion. The
        // gather margin grows with flight exposure — a slow round's corridor
        // can be entered mid-flight by a candidate whose fire-tick position
        // sits outside the base margin.
        float margin = GATHER_SLACK_CELLS + MAX_MOVER_SPEED_CELLS * (rayLen / roundVelocity);
        LongBucket candidates = new LongBucket();
        unitIndex.gatherAlongSegment(fromX, fromY, rayEndX, rayEndY, margin, candidates);
        for (int i = 0; i < candidates.size; i++) {
            long candidateId = candidates.ids[i];
            if (candidateId == source.entityId()) continue;
            if (held.test(candidateId)) continue;
            // One question, one answer, whatever is carrying the body. The
            // branch this replaces asked it twice and had to be widened for
            // every carrier added.
            BodyCarrier carrier = bodies.carrierOf(candidateId);
            if (carrier != null ? !carrier.isTargetable(candidateId)
                    : !roster.isAliveById(candidateId)) continue;

            Faction candidateFaction = roster.identity().faction(candidateId);
            // Civilians are neutral rather than faction-allied, but fire from
            // the player's side — the company and whoever is friendly to it —
            // must treat them as protected non-targets. Defender fire retains
            // ordinary target and incidental-contact behavior.
            if (candidateFaction == Faction.CIVILIAN
                    && shooterFaction.friendlyTo(Faction.MARINE)) {
                continue;
            }

            // The same physical body circle SeparationSystem shoves apart and
            // Detonations/WorldPicker size against — one radius concept per body.
            float r = roster.radius(candidateId);
            float ux = world.x(candidateId);
            float uy = world.y(candidateId);

            // Velocity actually applied by this tick's movement pass; 0 for
            // a non-mover (no MOVEMENT component). MOVEMENT_VEL_X/Y are
            // zeroed+rewritten by MovementService and nudged by
            // SeparationSystem during the same tick this resolver runs
            // (parallel UPDATE_UNITS) — a mid-tick-stale read moves the
            // contact point by at most moveSpeed * TICK_DT cells. Velocity
            // is an extrapolation hint here, not an invariant, so this read
            // is intentionally unsynchronized.
            float wx = 0f;
            float wy = 0f;
            if (carrier != null) {
                wx = carrier.velocityX(candidateId);
                wy = carrier.velocityY(candidateId);
            } else if (movement.has(candidateId)) {
                wx = movement.velX(candidateId);
                wy = movement.velY(candidateId);
            }

            // Round: P(s) = O + d*v*s (s in seconds). Candidate:
            // U(s) = U0 + w*s. Relative: R(s) = (U0 - O) + (w - v*d)*s;
            // contact when |R(s)| <= r. Quadratic a*s^2 + b*s + c = 0 in s.
            // w = 0 collapses this to S1's distance-domain ray-circle solve
            // exactly (proven by the stationary-regression test).
            float sEntry = firstCircleContactTime(
                    fromX, fromY, dirX, dirY, roundVelocity,
                    ux, uy, wx, wy, r);
            if (sEntry < 0f) continue;

            // Wall cap and friendly proximity stay DISTANCE tests — the wall
            // is static, and distance-along-ray is also the round's actual
            // traveled distance at this moving contact's predicted time.
            float rayDistAtEntry = roundVelocity * sEntry;
            if (rayDistAtEntry > rayLen) continue; // beyond the wall-capped ray

            // The ground ray may cross a body circle while the round passes
            // over its head or below its feet. Doodads and target-edge cover
            // apply their own Z gates at their respective event sites;
            // structural walls alone remain full-height.
            float contactZ = fromZ + zSlope * rayDistAtEntry;
            if (Math.abs(contactZ) > roster.hitHalfHeight(candidateId)) continue;

            boolean friendly = shooterFaction.friendlyTo(candidateFaction);
            if (friendly && rayDistAtEntry < PROXIMITY_CATCH_ZERO_DISTANCE) continue;

            // Contact-position split: the FX endpoint is where the ROUND is,
            // P(sEntry); the cover edge-clip lookup (step 5) uses the
            // VICTIM's own extrapolated cell, floor(U(sEntry)) — a target
            // sprinting out from behind a parapet has left the covered cell
            // by contact time, and vice versa.
            // A hull's circle decided the contact; its footprint says where on the hull the round lands, so a
            // round into a long hull side-on ends on its side rather than out at the circle's rim.
            float onHull = carrier != null ? hullContactDistance(fromX + dirX * rayDistAtEntry,
                    fromY + dirY * rayDistAtEntry, dirX, dirY, ux + wx * sEntry, uy + wy * sEntry,
                    carrier.hullFacingDegrees(candidateId), carrier.hullHalfLength(candidateId),
                    carrier.hullHalfWidth(candidateId), 2f * r) : 0f;
            onHull = Math.max(0f, Math.min(onHull, rayLen - rayDistAtEntry));
            float contactDist = rayDistAtEntry + onHull;
            float contactX = fromX + dirX * contactDist;
            float contactY = fromY + dirY * contactDist;
            if (onHull > 0f) contactZ = fromZ + zSlope * contactDist;
            float sContact = sEntry + onHull / roundVelocity;
            int victimCellX = (int) Math.floor(ux + wx * sEntry);
            int victimCellY = (int) Math.floor(uy + wy * sEntry);
            events.add(Event.unit(sContact, contactX, contactY, contactZ,
                    candidateId, friendly, carrier != null, victimCellX, victimCellY));
        }

        // Step 5: walk events sorted by t; first non-penetrated stop wins. A wall (when
        // present) is never a member of this list — it necessarily sits at
        // the ray's own endpoint, so every doodad/unit event's t is <= the
        // wall's t by construction; reaching the end of the list without a
        // stop is exactly "nothing stopped it before the wall/ray end".
        events.sort((a, b) -> Float.compare(a.t, b.t));
        List<BodyHit> bodyHits = new ArrayList<>();
        int penetrationsRemaining = bodyPenetrations;
        // The part the committed aim landed on, when the round is headed for a person. A prop
        // crossed in the lane short of him catches by that part; no roll is spent deciding it.
        BodyPlan targetPlan = onTarget && target != 0L && bodies.carrierOf(target) == null
                ? bodyPlanOf(target) : null;
        BodyPart intendedPart = targetPlan != null ? targetPlan.atHeight(targetHeightFraction) : null;
        // A part with no cover rows of its own meets a crossed prop as its body does.
        if (intendedPart != null && !intendedPart.hasCoverRows()) intendedPart = null;
        for (Event e : events) {
            float proximityScale = proximityCatchScale(e.t * roundVelocity);
            if (e.doodad) {
                int level = Math.min(e.doodadLevel, BLOCK_CHANCE_BY_LEVEL.length - 1);
                // A part-bound round meets the prop by that part (a low crate passes head shots);
                // anything else — a miss, point aim, a partless target, or a round already through
                // its man — keeps the flat chance.
                float blockChance = (intendedPart != null && !anyIntended(bodyHits)
                        ? CoverProfile.crossed(level, intendedPart)
                        : BLOCK_CHANCE_BY_LEVEL[level])
                        * proximityScale;
                if (blockChance > 0f && rng.nextFloat() < blockChance) {
                    return new Resolution(e.x, e.y, e.z, e.t, 0L,
                            anyIntended(bodyHits), false, StopKind.DOODAD_BLOCK, bodyHits,
                            StruckFeature.cell((int) Math.floor(e.x), (int) Math.floor(e.y)));
                }
                continue; // fly on
            }

            long victim = e.unitId;
            int fromDx = shooterCellX - e.victimCellX;
            int fromDy = shooterCellY - e.victimCellY;
            // A hull takes no terrain cover. Low cover is a thing a person
            // gets behind, and a chassis or an airframe is neither the size nor
            // the shape to do it — a rule that predates air and, before the
            // collapse, was spelled "is it a vehicle". A gun on a plinth is a
            // storey above the cover at its foot, so that cover hides none of it.
            boolean coverless = e.hull || roster.standsAboveLowCover(victim);
            int coverLevel = coverless ? 0
                    : grid.getCoverAgainst(e.victimCellX, e.victimCellY, shooterCellX, shooterCellY);
            BodyPlan victimPlan = e.hull ? null : bodyPlanOf(victim);
            // A body whose rows read cover from the ground up (a mech) is not gated by the catch
            // band: that band is centred on the target plane and sized for a man, so on a body
            // twice his height it would hide the torso and bare the legs. Its rows say what reaches.
            if (victimPlan == null || !victimPlan.coverFromGround()) {
                float coverCatchHalfHeight = e.hull ? 0f : grid.getCoverCatchHalfHeight(
                        e.victimCellX, e.victimCellY, fromDx, fromDy);
                if (!intersectsCatchBand(e.z, coverCatchHalfHeight)) coverLevel = 0;
            }
            // The ground the victim stands in (a trench) covers him from every direction,
            // more the further off the shooter is.
            int occupantLevel = coverless ? 0
                    : doodads.occupantCoverAt(e.victimCellX, e.victimCellY, e.z);
            float occupantScale = occupantLevel > 0 ? occupantCoverScale(e.t * roundVelocity) : 0f;
            BodyPart part = null;
            float edgeBlockChance;
            float occupantBlockChance;
            if (victimPlan != null) {
                // A person: the round lands on the part at the height it meets him, and cover
                // hides parts. One roll against the better of the two covers for that part. The
                // intended target's height is where the committed aim landed on him; anyone else
                // is struck wherever the round happens to be crossing.
                part = victimPlan.atHeight(victim == target && onTarget
                        ? targetHeightFraction
                        : heightFraction(e.z, roster.hitHalfHeight(victim)));
            }
            if (part != null && part.hasCoverRows()) {
                edgeBlockChance = CoverProfile.edge(coverLevel, part) * proximityScale;
                occupantBlockChance = CoverProfile.occupant(occupantLevel, part) * occupantScale;
            } else {
                // No part, or a part without cover rows of its own: the flat whole-body chance.
                edgeBlockChance = BLOCK_CHANCE_BY_LEVEL[Math.min(coverLevel, BLOCK_CHANCE_BY_LEVEL.length - 1)]
                        * proximityScale;
                occupantBlockChance = BLOCK_CHANCE_BY_LEVEL[Math.min(occupantLevel, BLOCK_CHANCE_BY_LEVEL.length - 1)]
                        * occupantScale;
            }
            float coverBlockChance = Math.max(edgeBlockChance, occupantBlockChance);
            if (coverBlockChance > 0f) {
                float roll = rng.nextFloat();
                if (roll < coverBlockChance) {
                    // The one catch roll also says which cover caught it: a roll the edge would have
                    // stopped is the edge's; the remainder is the ground he stands in, which wears nothing.
                    StruckFeature struck = roll < edgeBlockChance
                            ? edgeCoverFeature(e.victimCellX, e.victimCellY, fromDx, fromDy)
                            : null;
                    return new Resolution(e.x, e.y, e.z, e.t, 0L,
                            anyIntended(bodyHits), false, StopKind.COVER_CLIP, bodyHits, struck);
                }
            }

            boolean isLockedTarget = victim == target;
            if (isLockedTarget) {
                if (!onTarget) continue;
                BodyHit hit = new BodyHit(victim, e.x, e.y, e.z, e.t, true, e.friendly, part);
                bodyHits.add(hit);
                if (penetrationsRemaining-- > 0) continue;
                return new Resolution(e.x, e.y, e.z, e.t, victim, true,
                        e.friendly, StopKind.UNIT_HIT, bodyHits);
            }
            float hitChance = (e.friendly
                    ? FRIENDLY_INCIDENTAL_HIT_CHANCE * proximityScale
                    : HOSTILE_INCIDENTAL_HIT_CHANCE)
                    * world.incomingAccuracyMult(victim);
            if (hitChance > 0f && rng.nextFloat() < hitChance) {
                BodyHit hit = new BodyHit(victim, e.x, e.y, e.z, e.t, false, e.friendly, part);
                bodyHits.add(hit);
                if (penetrationsRemaining-- > 0) continue;
                return new Resolution(e.x, e.y, e.z, e.t, victim,
                        anyIntended(bodyHits), e.friendly, StopKind.UNIT_HIT, bodyHits);
            }
            // else fly on
        }

        StopKind finalKind = wallFound ? StopKind.WALL : StopKind.OVERSHOOT;
        return new Resolution(rayEndX, rayEndY, fromZ + zSlope * rayLen, rayLen / roundVelocity,
                0L, anyIntended(bodyHits), false, finalKind, bodyHits,
                wallFound ? StruckFeature.cell(wallCellX, wallCellY) : null);
    }

    /**
     * The feature lending a body on ({@code x}, {@code y}) its edge cover against a shooter in the
     * direction ({@code fromDx}, {@code fromDy}): the shared-edge feature on that side when it is what
     * supplies the level, otherwise the blocking neighbour cell on that side. Null where the cover is
     * the map's own boundary.
     */
    private StruckFeature edgeCoverFeature(int x, int y, int fromDx, int fromDy) {
        int facing = NavigationGrid.facingFor(fromDx, fromDy);
        Direction side = NavigationGrid.directionForFacing(facing);
        int cellLevel = grid.getCellCoverAtFacing(x, y, facing);
        SharedEdgeBarrier barrier = grid.inBounds(x, y) ? grid.getEdgeBarrier(x, y, side) : null;
        if (barrier != null && barrier.kind().coverLevel() > cellLevel) {
            return StruckFeature.edge(x, y, side);
        }
        int nx = x + side.dx;
        int ny = y + side.dy;
        return grid.inBounds(nx, ny) ? StruckFeature.cell(nx, ny) : null;
    }

    private static boolean anyIntended(List<BodyHit> hits) {
        for (BodyHit hit : hits) if (hit.intended()) return true;
        return false;
    }

    private float targetRadius(long id) {
        return roster.radius(id);
    }

    private float targetHitHalfHeight(long id) {
        return roster.hitHalfHeight(id);
    }

    /**
     * How much further along the round's line, from where it enters a hull's contact circle, it meets the hull's
     * footprint: a {@code halfLength} by {@code halfWidth} rectangle about {@code (bodyX, bodyY)} along
     * {@code facingDeg} (sprite convention). Zero for a body with no footprint or a round already inside it; a line
     * that misses the footprint lands where it passes nearest the hull's centre. Never more than {@code chord}.
     */
    static float hullContactDistance(float px, float py, float dirX, float dirY, float bodyX, float bodyY,
                                     float facingDeg, float halfLength, float halfWidth, float chord) {
        if (!(halfLength > 0f) || !(halfWidth > 0f)) return 0f;
        double rad = Math.toRadians(facingDeg);
        float fwdX = (float) -Math.sin(rad);
        float fwdY = (float) Math.cos(rad);
        float sideX = (float) Math.cos(rad);
        float sideY = (float) Math.sin(rad);
        float relX = px - bodyX;
        float relY = py - bodyY;
        float lf = relX * fwdX + relY * fwdY;
        float ls = relX * sideX + relY * sideY;
        float df = dirX * fwdX + dirY * fwdY;
        float ds = dirX * sideX + dirY * sideY;
        float enter = 0f;
        float exit = chord;
        float[] slabF = slab(lf, df, halfLength);
        float[] slabS = slab(ls, ds, halfWidth);
        if (slabF != null && slabS != null) {
            enter = Math.max(enter, Math.max(slabF[0], slabS[0]));
            exit = Math.min(exit, Math.min(slabF[1], slabS[1]));
            if (enter <= exit) return enter;
        }
        float nearest = -(relX * dirX + relY * dirY);
        return Math.max(0f, Math.min(chord, nearest));
    }

    /** The interval of ray distance inside one slab {@code |l + d*u| <= half}, or null when the ray never is. */
    private static float[] slab(float l, float d, float half) {
        if (Math.abs(d) < 1e-6f) {
            return Math.abs(l) <= half ? new float[]{Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY} : null;
        }
        float a = (-half - l) / d;
        float b = (half - l) / d;
        return a <= b ? new float[]{a, b} : new float[]{b, a};
    }

    /**
     * First non-negative contact time between the round ray and one moving
     * body circle, or {@code -1} when their timelines never intersect.
     */
    private static float firstCircleContactTime(float fromX, float fromY,
                                                float dirX, float dirY,
                                                float roundVelocity,
                                                float bodyX, float bodyY,
                                                float bodyVelocityX, float bodyVelocityY,
                                                float radius) {
        float relVelX = bodyVelocityX - roundVelocity * dirX;
        float relVelY = bodyVelocityY - roundVelocity * dirY;
        float r0x = bodyX - fromX;
        float r0y = bodyY - fromY;
        float a = relVelX * relVelX + relVelY * relVelY;
        float b = 2f * (r0x * relVelX + r0y * relVelY);
        float c = r0x * r0x + r0y * r0y - radius * radius;

        if (a < 1e-6f) {
            // The body paces the round exactly. It contacts only when already
            // overlapping at fire time; avoiding sqrt also prevents NaN.
            return c <= 0f ? 0f : -1f;
        }
        float disc = b * b - 4f * a * c;
        if (disc < 0f) return -1f;
        float sqrtDisc = (float) Math.sqrt(disc);
        float sExit = (-b + sqrtDisc) / (2f * a);
        if (sExit < 0f) return -1f;
        return Math.max(0f, (-b - sqrtDisc) / (2f * a));
    }

    /** Entry fraction of segment {@code a -> b} into one closed unit cell. */
    public static float segmentCellEntryFraction(float ax, float ay, float bx, float by,
                                                  int cellX, int cellY) {
        float dx = bx - ax;
        float dy = by - ay;
        float xEntry = Float.NEGATIVE_INFINITY;
        float yEntry = Float.NEGATIVE_INFINITY;
        if (dx > 0f) xEntry = (cellX - ax) / dx;
        else if (dx < 0f) xEntry = (cellX + 1f - ax) / dx;
        if (dy > 0f) yEntry = (cellY - ay) / dy;
        else if (dy < 0f) yEntry = (cellY + 1f - ay) / dy;
        return Math.max(0f, Math.min(1f, Math.max(xEntry, yEntry)));
    }

    /**
     * Smooth muzzle-distance attenuation shared by probabilistic cover and
     * friendly incidental contacts. Package-visible for exact curve tests.
     */
    static float proximityCatchScale(float distanceFromMuzzle) {
        if (distanceFromMuzzle <= PROXIMITY_CATCH_ZERO_DISTANCE) return 0f;
        if (distanceFromMuzzle >= PROXIMITY_CATCH_FULL_DISTANCE) return 1f;
        float x = (distanceFromMuzzle - PROXIMITY_CATCH_ZERO_DISTANCE)
                / (PROXIMITY_CATCH_FULL_DISTANCE - PROXIMITY_CATCH_ZERO_DISTANCE);
        return x * x * (3f - 2f * x);
    }

    /**
     * How much of a trench's cover a shooter {@code distance} cells from the man in it meets: none
     * at {@link #OCCUPANT_COVER_ZERO_DISTANCE}, all of it at {@link #OCCUPANT_COVER_FULL_DISTANCE},
     * smoothstepped between.
     */
    public static float occupantCoverScale(float distance) {
        if (distance <= OCCUPANT_COVER_ZERO_DISTANCE) return 0f;
        if (distance >= OCCUPANT_COVER_FULL_DISTANCE) return 1f;
        float x = (distance - OCCUPANT_COVER_ZERO_DISTANCE)
                / (OCCUPANT_COVER_FULL_DISTANCE - OCCUPANT_COVER_ZERO_DISTANCE);
        return x * x * (3f - 2f * x);
    }

    /** Bresenham cell walk from the shooter's cell to the ray-end cell, emitting a doodad-crossing {@link Event} for every non-start cell whose physical doodad silhouette contains the round's Z. */
    private void walkDoodadCrossings(int x0, int y0, int x1, int y1,
                                      float fromX, float fromY, float fromZ,
                                      float roundVelocity, float zSlope,
                                      List<Event> out) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        boolean first = true;
        while (true) {
            if (!first) {
                float cx = x + 0.5f;
                float cy = y + 0.5f;
                float t = dist(fromX, fromY, cx, cy) / roundVelocity;
                float z = fromZ + zSlope * roundVelocity * t;
                int level = doodads.getDoodadLevelOnCell(x, y, z);
                if (level > 0) {
                    out.add(Event.doodad(t, cx, cy, z, level));
                }
            }
            first = false;
            if (x == x1 && y == y1) break;
            int e2 = err << 1;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx)  { err += dx; y += sy; }
        }
    }

    private static float dist(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * The parts a round landing on {@code unit} is struck on: a mech's chassis plan (its sections'
     * height bands), otherwise its type's plan, or null for one whole-body contact.
     */
    private BodyPlan bodyPlanOf(long unit) {
        World world = roster.world();
        if (MechSections.ENABLED && world.hasMechLoadout(unit)) return world.mechLoadout(unit).variant.bodyPlan().bodyPlan();
        return roster.identity().type(unit).bodyPlan();
    }

    /** Where {@code z} sits up a body of {@code halfHeight}: 0 at its feet, 1 at its top. */
    private static float heightFraction(float z, float halfHeight) {
        if (!(halfHeight > 0f)) return 0.5f;
        return Math.max(0f, Math.min(0.999999f, (z / halfHeight + 1f) * 0.5f));
    }

    private static boolean intersectsCatchBand(float z, float catchHalfHeight) {
        return catchHalfHeight > 0f && Math.abs(z) <= catchHalfHeight;
    }
}
