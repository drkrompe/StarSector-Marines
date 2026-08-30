package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Stateless tick consumer that puts a field's airframes on the map, notices
 * when one has been destroyed on its pad, and counts down a turnaround.
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
                case PARKED -> {
                    if (berth.airframeId != 0L && !sim.world().isAlive(berth.airframeId)) {
                        // Burned where it stood. The berth is written off for
                        // the battle, nothing replaces it, and the hulk stays
                        // on the concrete — this is the one death path that
                        // leaves wreckage on a pad, so it is the one that says
                        // so.
                        service.burnedOnPad(berth);
                    } else if (berth.airframeId == 0L) {
                        place(sim, service, berth);
                    }
                }
                case REFITTING -> {
                    berth.refitRemaining -= dt;
                    if (berth.refitRemaining <= 0f) {
                        berth.refitRemaining = 0f;
                        berth.hullHp = service.repaired(berth);
                        berth.state = AirfieldService.BerthState.PARKED;
                        // Placed on the next pass through PARKED above, so
                        // there is one rule for "a parked berth has an
                        // aircraft on it" rather than two.
                    }
                }
                case AWAY -> {
                    // The aircraft is in the air now, so the unit that was
                    // standing in for it comes off the map. Released rather
                    // than killed: it has stopped being a unit, not stopped
                    // existing, and a death here would owe a wreck and a
                    // casualty nobody took.
                    if (berth.airframeId != 0L) {
                        sim.releaseFromRegistry(berth.airframeId);
                        berth.airframeId = 0L;
                    }
                }
                case DESTROYED -> {
                    // Terminal. Nothing to place, nothing to count down.
                }
            }
        }
    }

    /** Stands an airframe on its hardstand, carrying whatever hull the berth is holding. */
    private void place(BattleControl sim, AirfieldService service,
                       AirfieldService.Berth berth) {
        berth.airframeId = sim.spawn(BasedAircraft.create(
                "af" + (nextAirframeId++), faction, berth.type,
                berth.pad.centerX, berth.pad.centerY, berth.hullHp));
    }
}
