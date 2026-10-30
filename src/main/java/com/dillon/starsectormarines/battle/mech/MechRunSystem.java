package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.HashSet;
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
 *
 * <p>An <b>attached</b> follower ({@link MechAttachmentService}) has no move order to run on, so it
 * catches up on its own: once it falls {@link #CATCH_UP_CELLS} behind its slot it runs, on the same
 * stamina, until it is within {@link #SETTLED_CELLS} of it. A chassis slower than its squad's walk,
 * as a Hound is, otherwise trails it for the whole march.
 */
public final class MechRunSystem {

    /** Accuracy left to a chassis's autonomous fire on a tick it ran: a balance choice. */
    public static final float RUN_ACCURACY = .75f;
    /** An attached follower this many cells behind its slot starts to run: a balance choice. */
    static final float CATCH_UP_CELLS = 6f;
    /** A follower that is catching up runs until it is this near its slot, so it does not flicker at the edge. */
    static final float SETTLED_CELLS = 2f;

    private final MechMoveOrderService orders;
    /** Chassis whose speed carries the run multiplier this tick. Read from any thread. */
    private final Set<Long> boosted = ConcurrentHashMap.newKeySet();
    /** Boosted chassis that actually ran this tick, so their fire pays for it. */
    private final Set<Long> ran = ConcurrentHashMap.newKeySet();
    /** Attached followers running to close on their slot. Serial: touched only by {@link #beforeMovement}. */
    private final Set<Long> catchingUp = new HashSet<>();

    public MechRunSystem(MechMoveOrderService orders) {
        this.orders = orders;
    }

    /** Picks this tick's runners. Serial, after direct control's tick and before the unit update. */
    public void beforeMovement(BattleSimulation sim) {
        boosted.clear();
        ran.clear();
        MarineSprint stamina = sim.directControl().marineStamina();
        long controlled = sim.directControl().activeUnitId();
        if (orders.hasActiveOrders()) {
            for (Map.Entry<Long, MechMoveOrderService.ActiveOrder> entry : orders.activeEntries()) {
                long id = entry.getKey();
                if (id == controlled || !orders.isRunning(id)
                        || sim.resolveUnit(id) == 0L || !sim.movement().has(id)) continue;
                boostIfFit(id, stamina);
            }
        }
        catchUp(sim, stamina, controlled);
    }

    /** Runs each attached follower that has fallen behind its slot, until it has closed on it. */
    private void catchUp(BattleSimulation sim, MarineSprint stamina, long controlled) {
        MechAttachmentService attachments = sim.getMechAttachmentService();
        if (attachments.attachments().isEmpty()) {
            catchingUp.clear();
            return;
        }
        Set<Long> still = new HashSet<>();
        for (int mechSquad : attachments.attachments().keySet()) {
            for (int i = 0, n = sim.squadMemberCount(mechSquad); i < n; i++) {
                long id = sim.squadMemberAt(mechSquad, i);
                int[] slot = attachments.slotOf(id);
                if (id == controlled || slot == null || sim.resolveUnit(id) == 0L || !sim.movement().has(id)) continue;
                float lag = TacticalScoring.cellDistance(sim.world().x(id), sim.world().y(id),
                        slot[0] + .5f, slot[1] + .5f);
                if (!catchesUp(catchingUp.contains(id), lag)) continue;
                still.add(id);
                boostIfFit(id, stamina);
            }
        }
        catchingUp.clear();
        catchingUp.addAll(still);
    }

    /**
     * Whether a follower {@code lagCells} behind its slot runs this tick: it starts past
     * {@link #CATCH_UP_CELLS} and, once running, keeps on until within {@link #SETTLED_CELLS}.
     */
    static boolean catchesUp(boolean running, float lagCells) {
        return lagCells > (running ? SETTLED_CELLS : CATCH_UP_CELLS);
    }

    /** Boosts the chassis for the tick if it has stamina to run on, as a run order does. */
    private void boostIfFit(long id, MarineSprint stamina) {
        if (stamina.status(id).exhausted()) stamina.releaseHold(id);
        MarineSprint.Status status = stamina.status(id);
        if (!status.exhausted() && status.staminaFraction() > 0f) boosted.add(id);
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
