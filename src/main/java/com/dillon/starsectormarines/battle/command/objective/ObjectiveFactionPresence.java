package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.air.LiftQueue;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.CommandPowerService;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Set;
import java.util.function.LongPredicate;

/** Shared objective-side definition of a faction that still has forces in play. */
public final class ObjectiveFactionPresence {

    private ObjectiveFactionPresence() {}

    static boolean anyInPlay(BattleView sim, Faction faction) {
        return anyInPlay(sim, Set.of(faction));
    }

    /**
     * Counts eligible live ground members while retaining ordinary committed arrivals,
     * survivor pickup and current/future lift. Useful when a deployable cannot replace
     * the personnel required by a mission's work task.
     */
    public static boolean anyInPlay(BattleView sim, Faction faction, LongPredicate groundEligible) {
        return anyInPlay(sim, Set.of(faction), groundEligible);
    }

    /**
     * True while any of {@code factions} still has forces in play. A terminal
     * check names the sides it counts, so a side present on the map but absent
     * from the set does not keep the check open — an allied militia standing
     * beside a wiped company neither ends nor prolongs the company's battle.
     */
    static boolean anyInPlay(BattleView sim, Set<Faction> factions) {
        return anyInPlay(sim, factions, unit -> true);
    }

    /**
     * True while the company can still fight: a soldier, vehicle or machine on the
     * map, or a reserve with a way onto it (a boat still carrying or still to fly a
     * sortie, a pending arrival, a delivery power that can still field someone). Its own
     * emplacements and deployables are not forces: guns alone hold nothing.
     */
    public static boolean companyInPlay(BattleView sim) {
        return anyInPlay(sim, Set.of(Faction.MARINE), unit -> !sim.identity().type(unit).isStatic()
                && !sim.identity().type(unit).isDrone());
    }

    static boolean anyInPlay(BattleView sim, Set<Faction> factions, LongPredicate groundEligible) {
        if (factions == null || factions.isEmpty()) return false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (factions.contains(sim.identity().faction(unit)) && !sim.isCapturedStructure(unit)
                    && groundEligible.test(unit)) return true;
        }
        for (Faction faction : factions) if (sim.pendingGroundArrivals(faction) > 0) return true;
        if (sim.survivorPickup() != null && factions.stream().anyMatch(f -> sim.survivorPickup().survivingCount(f) > 0)) return true;
        World world = sim.world();
        for (long id : sim.getAirEntityIds()) {
            if (!factions.contains(world.airFaction(id))) continue;
            ShuttleMission mission = world.mission(id);
            if (mission == null || mission.destroyed || mission.state == ShuttleState.GONE) continue;
            boolean currentPayloadInbound = mission.marinesRemaining > 0 && mission.payload.fieldsForces()
                    && mission.state != ShuttleState.DEPARTING;
            boolean futureSortieCommitted =
                    mission.currentCycle + 1 < mission.totalCycles;
            if (currentPayloadInbound || futureSortieCommitted) return true;
        }
        return factions.contains(Faction.MARINE)
                && (queuedDropCanFly(sim) || reserveCanField(sim.getCommandPowerService(), sim));
    }

    /** A machine drop still waiting on the lift queue that a live ferry craft can carry down. */
    static boolean queuedDropCanFly(BattleView sim) {
        LiftQueue queue = sim.liftQueue();
        if (queue == null) return false;
        for (LiftQueue.MachineDrop job : queue.queued()) {
            if (LiftQueue.ferryAdmits(sim.world(), sim.getAirEntityIds(), job.liftClass())) return true;
        }
        return false;
    }

    /** A company power with a charge, the supplies to pay for it, and someone left to send. */
    static boolean reserveCanField(CommandPowerService powers, BattleView sim) {
        if (powers == null) return false;
        for (CommandPower power : powers.getAvailablePowers()) {
            if (powers.getChargesRemaining(power.id) != 0 && powers.getAvailableSupplies() >= power.supplyCost
                    && power.canStillField(sim)) return true;
        }
        return false;
    }
}
