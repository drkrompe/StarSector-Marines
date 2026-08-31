package com.dillon.starsectormarines.battle.unit;

import java.util.function.LongConsumer;

/**
 * One kind of body that is not a row in the dense roster — a convoy chassis, an
 * aircraft, whatever comes next.
 *
 * <p>The battle has exactly one concept of <b>a body that can be perceived,
 * scored, targeted, hit, attributed and killed</b>, and for a while it had three
 * implementations of it. The spatial index admitted each kind on its own path,
 * ballistics branched twice on which kind it was holding, the splash sweep ran
 * three loops, and the damage service dispatched into two near-identical
 * resolvers. Every one of those sites had to be edited again for the third kind,
 * and three of them were not: a mech could not target a taxiing aircraft, a held
 * burst reference to one did not resolve, and a squad's memory of one evaporated
 * on the tick it was recorded. None of those failures were about aircraft. They
 * were about the carrier being visible to consumers at all.
 *
 * <p>So the carrier registers and the consumers ask. What is behind these methods
 * — a {@code GroundBody}, an {@code AirBody}, a mission bag, an archetype query —
 * is the carrier's business and nobody else's.
 *
 * <p><b>This is not a storage collapse.</b> A chassis and an aircraft carry
 * {@code IDENTITY}, {@code HEALTH} and {@code ARMOR} and deliberately no
 * {@code POSITION}, {@code COMBAT}, {@code MOVEMENT} or {@code ROLE}, which is
 * what makes occupancy, separation, the fire system, the mover and the planner
 * skip them for free. Membership-narrowing is load-bearing and stays exactly as
 * it is; the duplication being removed is at the consumer seam.
 *
 * <p><b>Threading.</b> Every method here is a read against state the serial
 * phases own, called from the parallel {@code UPDATE_UNITS} dispatch as well as
 * from serial passes. Implementations must not mutate anything on themselves;
 * {@link #destroy} is the one exception and is called only from the serial
 * damage drain.
 *
 * <p>See {@code air-nouns.md} and {@code convoy-nouns.md}.
 */
public interface BodyCarrier {

    /** Whether this carrier is the one that owns {@code id}. */
    boolean owns(long id);

    /**
     * Whether {@code id} can be reached at all right now — present on the map,
     * structurally alive, and not in one of the states this carrier exempts.
     * A wreck, a chassis still off-map, and a craft in the air are each a body
     * that exists and cannot be shot.
     */
    boolean isTargetable(long id);

    /** Which side the body belongs to. */
    Faction faction(long id);

    /** Velocity along X in cells/sec, for a shooter's lead. */
    float velocityX(long id);

    /** Velocity along Y in cells/sec. */
    float velocityY(long id);

    /** Circular contact radius used by the ballistic and blast broad phases. */
    float targetRadius(long id);

    /** Target-plane half-height a round has to arrive inside to touch the body. */
    float hitHalfHeight(long id);

    /**
     * How far this body can shoot, in cells, or {@code 0} for one that cannot.
     * Answered here rather than through {@code COMBAT} because a carried body
     * does not have that component: its weapon, if it has one, runs its own
     * aim loop.
     */
    float weaponRange(long id);

    /**
     * What happens when the body runs out of structure — the per-carrier sink
     * on the shared damage route.
     *
     * <p>This is the part that genuinely differs and stays split. A roster
     * unit's death cascade (corpse pose, equipment drop, squad-leader
     * promotion, the death mailbox) has nothing to do for a chassis; an
     * aircraft's has to light the cook-off, leave the wreck and give the runway
     * back. Common route, per-carrier sink.
     */
    void destroy(long id);

    /**
     * Visits every body this carrier holds, targetable or not, so the shared
     * snapshot can be rebuilt from what actually exists rather than from a
     * second list that could drift. Populations here are a handful per battle.
     */
    void forEachBody(LongConsumer visitor);
}
