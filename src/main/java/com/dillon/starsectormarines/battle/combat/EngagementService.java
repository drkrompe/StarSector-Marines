package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.unit.BodyService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * Whether <em>this</em> shooter can engage <em>that</em> body, right now.
 *
 * <p>It replaces an absolute predicate — {@code isCombatTarget(id)} — that only
 * ever had a relational answer. An aircraft on final and a defence post are
 * engaged; the same aircraft and a rifle section are not; the aircraft has not
 * changed between the two sentences. Asked absolutely, the question had to be
 * answered for the most limited shooter on the map, so the answer for everybody
 * else was wrong and the one case that differed lived somewhere else entirely,
 * as a hardcoded "only a defence post can reach up" filter inside the anti-air
 * drain.
 *
 * <p>The relation has two halves and they are kept apart deliberately.
 * <b>Presence</b> is about the candidate alone — is it in the battle, is it
 * whole, is it alive — and is the carrier's to answer. <b>Reach</b> is about the
 * pair, and today the only question in it is altitude. Splitting them is what
 * lets the index and the blast sweep keep asking the absolute, ground-level
 * question they are entitled to ask, while a shooter that can do more than
 * ground level asks the whole thing.
 *
 * <p><b>Behaviour is held constant.</b> Infantry and mechs cannot engage a craft
 * in the air, a defence post can, and everybody can engage one on its wheels —
 * the same three answers as before, now derived from one place instead of three.
 * That was the goal: a structural change that shows up in no measurement is a
 * structural change somebody can review.
 *
 * <p>See {@code air-nouns.md} for the altitude model and {@code ecs-nouns.md}
 * for what a body is.
 */
public final class EngagementService {

    private final UnitRosterService roster;
    private final BodyService bodies;

    public EngagementService(UnitRosterService roster) {
        this.roster = roster;
        this.bodies = roster.bodies();
    }

    /**
     * Whether {@code shooterId} may put fire on {@code candidateId} this
     * instant — the candidate is in the battle and this shooter can reach where
     * it is.
     */
    public boolean canEngage(long shooterId, long candidateId) {
        if (candidateId == 0L || candidateId == shooterId) return false;
        BodyCarrier carrier = bodies.carrierOf(candidateId);
        if (carrier != null) {
            if (!carrier.isPresent(candidateId)) return false;
            return !carrier.isAirborne(candidateId) || reachesAltitude(shooterId);
        }
        return roster.isAliveById(candidateId) && roster.isLive(candidateId)
                && roster.identity().type(candidateId).combatant;
    }

    /**
     * Whether the body at {@code shooterId} can put fire into the air at all.
     *
     * <p>The one weapon capability the relation currently needs, and the place a
     * real per-weapon answer lands when anti-air is authored. Today it is
     * derived from the emplacement kind, because that is exactly what the
     * filter it replaces said and this commit is not the place to change what
     * can shoot at what. When a weapon gains an authored elevation, this method
     * reads it and every consumer of the relation inherits that without being
     * touched — which is the whole point of there being one of it.
     */
    public boolean reachesAltitude(long shooterId) {
        return roster.identity().has(shooterId)
                && roster.identity().type(shooterId).isTurret();
    }
}
