package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Action;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.scoring.RoleAssigner;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>Story M — Room breach.</b> Attacker-side maneuver for a squad that has
 * a target across an adjacent zone's portal and no in-zone targets to deal
 * with first. Two phases, no per-step plan churn:
 *
 * <ol>
 *   <li><b>Stack-up.</b> Each member paths to their assigned stack-up cell
 *       (cells on the friendly side of the doorway). The squad is considered
 *       "stacked" once at least {@link #STACKUP_MIN_FRACTION} of alive members
 *       are within {@link #STACKUP_ARRIVAL_RADIUS} cells of any stack-up cell,
 *       OR after {@link #STACKUP_TIMEOUT_SECONDS} sim-seconds — whichever
 *       comes first. The timeout prevents one stuck straggler from halting
 *       the breach indefinitely.</li>
 *   <li><b>Advance.</b> Each member paths to their assigned forward cell
 *       (cells in a search box past the portal, picked at customPlan time
 *       via cover-aware scoring). The path is allowed to cross the portal;
 *       in-room cells on the original side don't reverse-attract the squad
 *       because the destination is fixed past the portal.</li>
 * </ol>
 *
 * <p>The phase check is squad-wide — re-evaluated each tick. Once the squad
 * has flipped to advance, individual late arrivers also head for their
 * forward cell directly (no need to re-stack). Members don't fire during
 * the breach: the maneuver's whole point is committing to forward movement,
 * not trading shots from doorway cover; once at the forward cell, the
 * next replan re-postures to {@code EngagePosture} normally.
 *
 * <p>Per-instance parameterized like {@link EnterZone} — the portal id +
 * the per-slot cell arrays are baked at construction; the planner never
 * sees this action's preconditions/effects (custom-plan goal only).
 */
public final class BreachAndAdvance implements Action {

    /** Stack-up arrival tolerance — within this many cells of the assigned stack-up cell counts as "arrived." Slightly loose so a unit pathing through occupied neighbors still registers. */
    public static final float STACKUP_ARRIVAL_RADIUS = 1.5f;
    /** Fraction of alive squad members that must be near any stack-up cell before the breach commits. 0.6 ≈ "majority" — keeps the squad coordinated without waiting forever on stragglers. */
    public static final float STACKUP_MIN_FRACTION = 0.6f;
    /** Sim-seconds after which the breach commits regardless of how many members have stacked. Prevents one stuck member from halting the maneuver. */
    public static final float STACKUP_TIMEOUT_SECONDS = 2.0f;

    private final int portalId;
    /** Stack-up cell per slot index — friendly-side near-doorway cells. */
    private final int[] stackUpX;
    private final int[] stackUpY;
    /** Forward cover cell per slot index — search-box cells past the portal. */
    private final int[] forwardX;
    private final int[] forwardY;

    public BreachAndAdvance(int portalId, int[] stackUpX, int[] stackUpY, int[] forwardX, int[] forwardY) {
        if (stackUpX.length != stackUpY.length || stackUpX.length != forwardX.length
                || stackUpX.length != forwardY.length) {
            throw new IllegalArgumentException("BreachAndAdvance: cell arrays must all be same length");
        }
        this.portalId = portalId;
        this.stackUpX = stackUpX;
        this.stackUpY = stackUpY;
        this.forwardX = forwardX;
        this.forwardY = forwardY;
    }

    public int portalId() { return portalId; }
    public int slotCount() { return stackUpX.length; }
    public int stackUpCellX(int slot) { return stackUpX[slot]; }
    public int stackUpCellY(int slot) { return stackUpY[slot]; }
    public int forwardCellX(int slot) { return forwardX[slot]; }
    public int forwardCellY(int slot) { return forwardY[slot]; }

    @Override public String name() { return "BreachAndAdvance[portal=" + portalId + "]"; }
    @Override public WorldState preconditions() { return WorldState.EMPTY; }
    @Override public WorldState effects() { return WorldState.EMPTY; }
    @Override public float cost(WorldState s, Squad squad, BattleView sim) { return 1f; }
    @Override public int requiredMembers() { return Math.max(1, stackUpX.length); }

    @Override
    public java.util.List<int[]> highlightCells(Squad squad, BattleView sim) {
        // Stack-up + forward cells together — the player sees the full breach
        // path: where the squad pools and where they're pushing through to.
        java.util.List<int[]> out = new java.util.ArrayList<>(stackUpX.length * 2);
        for (int i = 0; i < stackUpX.length; i++) {
            out.add(new int[]{stackUpX[i], stackUpY[i]});
            out.add(new int[]{forwardX[i], forwardY[i]});
        }
        return out;
    }

    /** One slot per breach position, named "breacher:N". Members are scored by negated distance to their slot's stack-up cell — closest member wins, so the squad's natural order at the door is preserved. */
    @Override
    public List<RoleAssigner.Slot<Entity>> roles(Squad squad, BattleView sim) {
        List<RoleAssigner.Slot<Entity>> slots = new ArrayList<>(stackUpX.length);
        for (int i = 0; i < stackUpX.length; i++) {
            final int sx = stackUpX[i];
            final int sy = stackUpY[i];
            slots.add(new RoleAssigner.Slot<>(
                    "breacher:" + i,
                    1,
                    c -> -TacticalScoring.cellDistance(sim.world().cellX(c.entityId), sim.world().cellY(c.entityId), sx, sy)));
        }
        return slots;
    }

    @Override
    public ActionStatus execute(Entity member, Squad squad, BattleControl sim) {
        SquadPlan plan = squad.currentPlan;
        SquadPlan.Step step = plan != null && !plan.isComplete() ? plan.currentStep() : null;
        String slotName = step != null ? step.slotOf(member) : null;
        if (slotName == null) return ActionStatus.RUNNING;
        int slot = parseSlotIndex(slotName);
        if (slot < 0 || slot >= stackUpX.length) return ActionStatus.RUNNING;

        // Phase check: is the squad still stacking up, or have they committed
        // to the advance? Squad-wide: count members near any stack-up cell.
        // Hysteresis isn't needed — once advancing, no member checks back at
        // the stack-up cells (their destination is the forward cell).
        boolean stacked = squadIsStacked(squad, sim);
        boolean timedOut = squad.breachStackupTimer >= STACKUP_TIMEOUT_SECONDS;
        boolean advancing = stacked || timedOut;

        int destX, destY;
        if (advancing) {
            destX = forwardX[slot];
            destY = forwardY[slot];
        } else {
            destX = stackUpX[slot];
            destY = stackUpY[slot];
            // Leader-gate the timer accumulation: each member sees TICK_DT
            // once per tick and an N-member RMW race would inflate the
            // timer by ~N× (or drop increments under torn writes), either
            // way mis-tripping the timeout. One canonical writer per tick
            // gives a deterministic timer regardless of worker count.
            if (member.entityId == squad.leaderId) {
                squad.breachStackupTimer += BattleSimulation.TICK_DT;
            }
        }

        if (sim.world().cellX(member.entityId) == destX && sim.world().cellY(member.entityId) == destY) {
            if (!Paths.isEmpty(sim.world().path(member.entityId))) sim.clearPath(member);
            sim.world().setMoveProgress(member.entityId, 0f);
            member.setRenderPos(sim.world().cellX(member.entityId), sim.world().cellY(member.entityId));
            // Squad-wide success: all members are at their forward cells.
            // Per-member success would advance the plan after the first
            // arrives, which would let the rest scramble independently.
            // Lock the success check + reset so the timer clear pairs
            // atomically with the SUCCESS return — otherwise a sibling
            // worker might re-enter the stack-up branch and re-arm the
            // timer between our reset and the plan advance.
            if (advancing && allMembersAtForward(squad, sim)) {
                synchronized (squad.lock) {
                    squad.breachStackupTimer = 0f;
                }
                return ActionStatus.SUCCESS;
            }
            return ActionStatus.RUNNING;
        }

        if (sim.world().moveProgress(member.entityId) == 0f) {
            sim.setPath(member, GridPathfinder.findPath(sim.getGrid(),
                    sim.world().cellX(member.entityId), sim.world().cellY(member.entityId), destX, destY, sim.getOccupancyMap()));
        }
        sim.advanceMovement(member);
        return ActionStatus.RUNNING;
    }

    /**
     * True iff at least {@link #STACKUP_MIN_FRACTION} of alive squad members
     * are within {@link #STACKUP_ARRIVAL_RADIUS} cells of any stack-up cell.
     * Squad-scope decision: a single member arriving doesn't flip the squad
     * to advance — we want the visible "stacked" beat before commitment.
     */
    private boolean squadIsStacked(Squad squad, BattleView sim) {
        if (squad.aliveMembers <= 0) return false;
        int alive = 0;
        int near = 0;
        float r2 = STACKUP_ARRIVAL_RADIUS * STACKUP_ARRIVAL_RADIUS;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) { Entity u = sim.liveUnitAt(i);
            if (u.squadId != squad.id) continue;
            alive++;
            for (int i2 = 0; i2 < stackUpX.length; i2++) {
                float dx = sim.world().cellX(u.entityId) - stackUpX[i2];
                float dy = sim.world().cellY(u.entityId) - stackUpY[i2];
                if (dx * dx + dy * dy <= r2) { near++; break; }
            }
        }
        if (alive == 0) return false;
        return (float) near / alive >= STACKUP_MIN_FRACTION;
    }

    /** True iff every alive squad member is at their assigned forward cell. Plan SUCCESS condition. */
    private boolean allMembersAtForward(Squad squad, BattleView sim) {
        SquadPlan plan = squad.currentPlan;
        if (plan == null || plan.isComplete()) return false;
        SquadPlan.Step step = plan.currentStep();
        // Sibling worker may have advanced past the end between isComplete()
        // and currentStep() under parallel dispatch.
        if (step == null) return false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) { Entity u = sim.liveUnitAt(i);
            if (u.squadId != squad.id) continue;
            String name = step.slotOf(u);
            if (name == null) continue;
            int s = parseSlotIndex(name);
            if (s < 0 || s >= forwardX.length) continue;
            if (sim.world().cellX(u.entityId) != forwardX[s] || sim.world().cellY(u.entityId) != forwardY[s]) return false;
        }
        return true;
    }

    private static int parseSlotIndex(String slotName) {
        if (slotName == null) return -1;
        int colon = slotName.indexOf(':');
        if (colon < 0) return -1;
        try {
            return Integer.parseInt(slotName.substring(colon + 1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
