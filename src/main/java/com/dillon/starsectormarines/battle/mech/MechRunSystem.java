package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A running mech order's pace ({@link MechMoveOrderService#toggleRun}), paid out of the same
 * battle stamina pool, keyed by the exact chassis, that a squad's run and a controlled body's
 * sprint spend ({@link MarineSprint}).
 *
 * <p>As a squad's run ({@code SquadRunSystem}): before the units move, each chassis whose order
 * runs and who has stamina left is boosted for the tick by the sprint multiplier; after they move,
 * each is charged for the share of a full sprint tick it actually covered, so one held up spends
 * nothing, and one that runs dry walks on, recovers to the restart reserve and runs again. The
 * chassis under direct control is the player's hand's ({@code DirectControlSession}) and never
 * boosted here.
 *
 * <p>Unlike a Marine, a running mech keeps its weapons up: the torso swivels and every mount fires
 * as ever. Its autonomous fire pays for the pace instead, at {@link #RUN_ACCURACY} on a tick it
 * actually ran; the player's own aim under Shift is not penalised.
 */
public final class MechRunSystem {

    /** Accuracy left to a chassis's autonomous fire on a tick it ran: a balance choice. */
    public static final float RUN_ACCURACY = .75f;

    private final MechMoveOrderService orders;
    /** Chassis whose speed carries the run multiplier this tick. Read from any thread. */
    private final Set<Long> boosted = ConcurrentHashMap.newKeySet();
    /** Boosted chassis that actually ran this tick, so their fire pays for it. */
    private final Set<Long> ran = ConcurrentHashMap.newKeySet();

    public MechRunSystem(MechMoveOrderService orders) {
        this.orders = orders;
    }

    /** Picks this tick's runners. Serial, after direct control's tick and before the unit update. */
    public void beforeMovement(BattleSimulation sim) {
        boosted.clear();
        ran.clear();
        if (!orders.hasActiveOrders()) return;
        MarineSprint stamina = sim.directControl().marineStamina();
        long controlled = sim.directControl().activeUnitId();
        for (Map.Entry<Long, MechMoveOrderService.ActiveOrder> entry : orders.activeEntries()) {
            long id = entry.getKey();
            if (id == controlled || !orders.isRunning(id)
                    || sim.resolveUnit(id) == 0L || !sim.movement().has(id)) continue;
            if (stamina.status(id).exhausted()) stamina.releaseHold(id);
            MarineSprint.Status status = stamina.status(id);
            if (!status.exhausted() && status.staminaFraction() > 0f) boosted.add(id);
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
            // As a squad's run: the speed still carries the boost, so the walking pace is that over
            // the multiplier and the run's share of the distance is what the multiplier added.
            float walking = sim.movement().moveSpeed(id) / MarineSprint.SPEED_SCALE;
            float bonus = moved * (MarineSprint.SPEED_SCALE - 1f) / MarineSprint.SPEED_SCALE;
            if (stamina.finishStep(id, true, bonus, walking, dt)) ran.add(id);
        }
    }

    /** Whether the chassis runs this tick. */
    public boolean boosted(long id) {
        return boosted.contains(id);
    }

    /** The multiplier on the chassis's autonomous fire this tick: {@link #RUN_ACCURACY} if it ran. */
    public float accuracy(long id) {
        return ran.contains(id) ? RUN_ACCURACY : 1f;
    }

    /** The run's pace as a stat modifier on each runner. Registered once at setup. */
    public UnitModifier modifier() {
        return new UnitModifier() {
            @Override public String name(long unit) { return "unitModifierMechRun"; }
            @Override public Set<UnitStat> stats() { return Set.of(UnitStat.MOVE_SPEED); }
            @Override public boolean appliesTo(long unit) { return boosted.contains(unit); }
            @Override public float multiplier(long unit, UnitStat stat) { return MarineSprint.SPEED_SCALE; }
        };
    }
}
