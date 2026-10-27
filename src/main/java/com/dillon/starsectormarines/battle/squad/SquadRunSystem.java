package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A running squad's pace, paid for Marine by Marine out of the same battle stamina a controlled
 * Marine sprints on ({@link MarineSprint}).
 *
 * <p>Before the units move, every Marine of a squad whose order runs and who has stamina left is
 * boosted for the tick by the sprint multiplier ({@link #modifier}). After they move, each boosted
 * Marine is charged for the share of a full sprint tick it actually covered, so one held up by a
 * wall, a crowd or its own fight spends nothing. A Marine who runs dry walks on, catches its
 * breath to the sprint's restart reserve, and runs again while the order still runs: nobody is
 * holding a key down for it to let go of. The Marine under direct control is the player's hand's
 * and never boosted here; recovery for everyone else is {@link MarineSprint#tickInactive}'s.
 */
public final class SquadRunSystem {

    private final SquadMoveOrderService orders;
    /** Marines whose speed carries the run multiplier this tick. Read from any thread. */
    private final Set<Long> boosted = ConcurrentHashMap.newKeySet();

    public SquadRunSystem(SquadMoveOrderService orders) {
        this.orders = orders;
    }

    /** Picks this tick's runners. Serial, after direct control's tick and before the unit update. */
    public void beforeMovement(BattleSimulation sim) {
        boosted.clear();
        MarineSprint stamina = sim.directControl().marineStamina();
        long controlled = sim.directControl().activeUnitId();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.isMechSquad() || !orders.isRunning(squad.id)) continue;
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                long id = sim.resolveUnit(sim.squadMemberAt(squad.id, i));
                if (id == 0L || id == controlled || sim.identity().type(id) != UnitType.MARINE) continue;
                if (stamina.status(id).exhausted()) stamina.releaseHold(id);
                if (fundable(stamina.status(id))) boosted.add(id);
            }
        }
    }

    /** Charges each runner for the boost it actually used. Serial, after the unit update. */
    public void afterMovement(BattleSimulation sim) {
        if (boosted.isEmpty()) return;
        MarineSprint stamina = sim.directControl().marineStamina();
        float dt = BattleSimulation.TICK_DT;
        for (long id : boosted) {
            if (sim.resolveUnit(id) == 0L || !sim.movement().has(id)) continue;
            float moved = (float) Math.hypot(sim.movement().velX(id), sim.movement().velY(id)) * dt;
            if (moved <= 0f) continue;
            // Speed here still carries the boost; the walking pace is that over the multiplier,
            // and the run's share of the distance is what the multiplier added to it.
            float walking = sim.movement().moveSpeed(id) / MarineSprint.SPEED_SCALE;
            float bonus = moved * (MarineSprint.SPEED_SCALE - 1f) / MarineSprint.SPEED_SCALE;
            stamina.finishStep(id, true, bonus, walking, dt);
        }
    }

    /** Whether the Marine runs this tick. */
    public boolean boosted(long id) {
        return boosted.contains(id);
    }

    /** The run's pace as a stat modifier on each runner. Registered once at setup. */
    public UnitModifier modifier() {
        return new UnitModifier() {
            @Override public String name(long unit) { return "unitModifierSquadRun"; }
            @Override public Set<UnitStat> stats() { return Set.of(UnitStat.MOVE_SPEED); }
            @Override public boolean appliesTo(long unit) { return boosted.contains(unit); }
            @Override public float multiplier(long unit, UnitStat stat) { return MarineSprint.SPEED_SCALE; }
        };
    }

    /** Stamina enough to run on: not winded, and not empty. */
    static boolean fundable(MarineSprint.Status status) {
        return !status.exhausted() && status.staminaFraction() > 0f;
    }
}
