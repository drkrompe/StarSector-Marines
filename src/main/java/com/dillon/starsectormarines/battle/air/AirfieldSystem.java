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
     * The geometry — the footprint, the see-through obstacle, the step-clear,
     * the refusal to seal anybody in — is {@link GroundWreckFootprint}'s,
     * shared with the taxiway kill {@link AirSystem} settles the same way.
     * This gathers the candidate units the cheap way already at hand here:
     * every live unit on the field, exactly as before this was pulled out.
     */
    private void settleWreck(BattleControl sim, AirfieldService.Berth berth) {
        LongBucket nearby = new LongBucket();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) nearby.add(sim.liveUnitAt(i));
        GroundWreckFootprint.settle(sim.getGrid(), sim.getTopology(), sim.world(),
                nearby, berth.centerX, berth.centerY);
    }

    /** Stands an airframe on its hardstand, carrying whatever hull the berth is holding. */
    private void place(BattleControl sim, AirfieldService service,
                       AirfieldService.Berth berth) {
        berth.airframeId = sim.spawn(BasedAircraft.create(
                "af" + (nextAirframeId++), faction, berth.airframe,
                berth.centerX, berth.centerY, berth.hullHp));
    }
}
