package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;

/**
 * Stateless tick consumer that puts a field's airframes on the map, notices
 * when one has been destroyed on its pad, settles the wreck onto the concrete,
 * and counts down a turnaround.
 *
 * <p>Split from {@link AirfieldService} on the project's usual line: the
 * service owns berth state, this owns the actions that state implies. Spawning
 * and removing units is tick-side work and does not belong to a state owner.
 *
 * <p>Placement is driven off the berth rather than commanded: a berth that is
 * {@code PARKED} and has no live airframe on it gets one. That covers the first
 * tick of the battle and the tick a refit completes with the same rule, and it
 * means no caller has to remember to put an aircraft back.
 */
public final class AirfieldSystem {

    private final Faction faction;
    /** Names each airframe as it is placed. Monotonic — an airframe put back after a sortie is a new unit. */
    private int nextAirframeId;

    public AirfieldSystem(Faction faction) {
        this.faction = faction;
    }

    /**
     * Advance every berth. Runs at full tick rate: the work is a walk of at
     * most a handful of berths doing nothing, and a destroyed airframe should
     * stop supplying sorties on the tick it dies rather than up to a cadence
     * period later.
     */
    public void tick(float dt, BattleControl sim, AirfieldService service) {
        if (service == null || service.berths().isEmpty()) return;
        for (AirfieldService.Berth berth : service.berths()) {
            releaseGroundIfNothingStandsThere(sim, berth);
            switch (berth.state) {
                // Standing on its concrete, either ready to go or being worked
                // back up to it. One rule for both, because what is on the pad
                // is the same aircraft either way and everything that can happen
                // to it there can happen in both states.
                case PARKED, REFITTING -> {
                    if (berth.airframeId != 0L && !sim.world().isAlive(berth.airframeId)) {
                        // Burned where it stood. The berth is written off for
                        // the battle, nothing replaces it, and the hulk stays
                        // on the concrete — this is the one death path that
                        // leaves wreckage on a pad, so it is the one that says
                        // so.
                        service.burnedOnPad(berth);
                        settleWreck(sim, berth);
                    } else if (berth.airframeId == 0L) {
                        place(sim, service, berth);
                    } else {
                        turnaround(sim, berth);
                    }
                }
                case AWAY -> {
                    // The aircraft is in the air now, so the unit that was
                    // standing in for it comes off the map entirely. Not
                    // killed — a death here would owe a wreck and a casualty
                    // nobody took — and not merely released, which leaves the
                    // hull's row in the world for the render pass to keep
                    // drawing on an empty pad.
                    if (berth.airframeId != 0L) {
                        sim.takeOffTheField(berth.airframeId);
                        berth.airframeId = 0L;
                    }
                }
                case DESTROYED -> {
                    // Terminal. Nothing to place, nothing to count down.
                }
            }
        }
    }

    /**
     * The one place a berth gives its ground back.
     *
     * <p><b>Every way an aircraft stops standing on its stand passes through
     * here</b>, which is deliberate and is the shape the runway claim was
     * eventually forced into for exactly this reason: a release written into
     * each ending is a release the next ending will not have, and what that
     * leaves behind is an invisible wall in the middle of the apron that
     * nothing can walk through and nothing explains. So this asks the only
     * question that matters — is a live airframe standing here? — rather than
     * enumerating launches, kills, refits and teardowns. A launch, a hull
     * burned on the concrete, one lost over the objective, and any ending
     * nobody has written yet are all the same answer.
     *
     * <p>Ordered before the berth's own branch on purpose. The kill path stamps
     * a wreck over this same ground, and a wreck settling onto the dead
     * aircraft's own footprint would read its marks instead of the concrete and
     * decide wrongly about who could be stepped where — and would leave the
     * intact hull's opaque cell underneath a wreck that is supposed to be
     * shootable across. Given back first, the wreck stamps a clean apron.
     *
     * <p>Tolerant of a berth that never stamped anything: an empty claim is a
     * long compare and a return.
     */
    private static void releaseGroundIfNothingStandsThere(BattleControl sim,
                                                          AirfieldService.Berth berth) {
        if (berth.closedGround == 0L) return;
        boolean standing = (berth.state == AirfieldService.BerthState.PARKED
                        || berth.state == AirfieldService.BerthState.REFITTING)
                && berth.airframeId != 0L
                && sim.world().isAlive(berth.airframeId);
        if (standing) return;
        AirframeFootprint.lift(sim.getGrid(), sim.getTopology(),
                berth.centerX, berth.centerY, berth.closedGround);
        berth.closedGround = 0L;
    }

    /**
     * Keep the berth's hull in step with the aircraft standing on it, and let it
     * off the turnaround when the crew have it back up.
     *
     * <p>The unit is the truth while it is standing there. A berth that kept its
     * own number would launch an aircraft at whatever hull it landed with,
     * however much of it had since been shot off on the pad or welded back on —
     * which is the same figure being kept in two places and drifting the moment
     * anything touches either.
     */
    private static void turnaround(BattleControl sim, AirfieldService.Berth berth) {
        berth.hullHp = sim.world().hp(berth.airframeId);
        if (berth.state != AirfieldService.BerthState.REFITTING) return;
        if (berth.refitWork > 0f) return;
        berth.refitTarget = 0f;
        berth.state = AirfieldService.BerthState.PARKED;
    }

    /**
     * Settles the burnt hull onto the hardstand: it is an obstacle from here
     * on, and whoever is still standing where it lands steps clear of it.
     *
     * <p>Sized to the same ground an aircraft hull covers anywhere else on the
     * map, since it is the same hull; it has simply stopped being able to fly.
     * The geometry — the footprint, the step-clear, the refusal to seal anybody
     * in — is {@link AirframeFootprint}'s, shared with the taxiway kill
     * {@link AirSystem} settles the same way. This gathers the candidate units
     * the cheap way already at hand here: every live unit on the field.
     *
     * <p>The intact hull's own ground has already been given back by
     * {@link #releaseGroundIfNothingStandsThere} on this tick, so what is
     * stamped here goes onto concrete rather than over the dead aircraft's own
     * marks.
     */
    private void settleWreck(BattleControl sim, AirfieldService.Berth berth) {
        AirframeFootprint.settleWreck(sim.getGrid(), sim.getTopology(), sim.world(),
                everyoneOnTheField(sim), berth.centerX, berth.centerY);
    }

    /**
     * Stands an airframe on its hardstand, carrying whatever hull the berth is
     * holding, and closes the ground under it.
     *
     * <p><b>An aircraft is a thing you walk round.</b> Without the footprint a
     * marine crossed clean through an intact fighter while its burnt-out wreck
     * stopped him, which is backwards, and it left the apron a raid is fought
     * over an open field with aircraft drawn on it. It blocks sight only where
     * the aircraft actually stands, for a reason {@link AirframeFootprint}
     * explains: a hull opaque across its whole footprint cannot be shot at all.
     *
     * <p>The hull covers the middle of the stand and no more. A pad is five
     * cells across, so a 3x3 leaves a cell of marked concrete all the way
     * round it — which is where the ground crew stand to reach the aircraft,
     * and why the size is a fact about the stand rather than about the hull.
     *
     * <p>Happens once per arrival rather than per tick — a berth reconciles
     * every tick but only places when it is holding no airframe, and the
     * release above guarantees the ground claim is empty by then, so nothing
     * is ever stamped twice.
     */
    private void place(BattleControl sim, AirfieldService service,
                       AirfieldService.Berth berth) {
        // The ground first, and deliberately: the stamp declines to close a cell
        // somebody is standing in, and an aircraft placed before its own
        // footprint is somebody standing in the middle of it — which would leave
        // a hole under the hull for people to walk into.
        berth.closedGround = AirframeFootprint.stand(sim.getGrid(), sim.getTopology(),
                sim.world(), everyoneOnTheField(sim), berth.centerX, berth.centerY);
        berth.airframeId = sim.spawn(BasedAircraft.create(
                "af" + (nextAirframeId++), faction, berth.airframe,
                berth.centerX, berth.centerY, berth.hullHp));
    }

    /**
     * Candidates for the occupancy reads and the wreck's step-clear: every live
     * unit, which is cheap enough here and needs no spatial reach, since a
     * berth is a place the field already knows the coordinates of.
     */
    private static LongBucket everyoneOnTheField(BattleControl sim) {
        LongBucket nearby = new LongBucket();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) nearby.add(sim.liveUnitAt(i));
        return nearby;
    }
}
