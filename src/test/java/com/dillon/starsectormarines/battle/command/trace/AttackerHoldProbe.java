package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * How the attacking commander held the ground it took (`garrisonables.md`, holding taken ground): every thirty ticks,
 * which marine squads carry a run order their commander gave them to hold a taken fort's belt, how many of their
 * members are dug in, and whether the fort is held by the marines whole, by nobody whole, or has been won back.
 * Readings for the trial ground's comparison against the control, never assertions.
 */
final class AttackerHoldProbe {

    private static final int SAMPLE_TICKS = 30;

    private int holderSamples;
    private int dugInSamples;
    private int maxHolders;
    private int manOrders;
    private int fallBacks;
    private int fortTakenTick = -1;
    private int fortRetakenTick = -1;
    private int heldWholeSamples;
    private final Map<Integer, Integer> lastRun = new HashMap<>();
    private final Map<Integer, Boolean> lastFallBack = new HashMap<>();

    void sample(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick % SAMPLE_TICKS != 0) return;
        boolean any = false;
        boolean whole = true;
        for (DefenceStructureFact fact : sim.defenceStructures()) {
            if (!fact.ofFort()) continue;
            any = true;
            if (fact.holder() != Faction.MARINE) whole = false;
        }
        boolean heldWhole = any && whole;
        if (heldWhole) {
            heldWholeSamples++;
            if (fortTakenTick < 0) fortTakenTick = tick;
        } else if (fortTakenTick >= 0 && fortRetakenTick < 0) {
            fortRetakenTick = tick;
        }
        CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(Faction.MARINE);
        int holders = 0;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.aliveMembers <= 0) continue;
            CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(squad.id);
            String reason = directive == null ? "" : directive.reason();
            boolean fallingBack = reason.equals("HOLD_GROUND_FALL_BACK");
            if (fallingBack && !lastFallBack.getOrDefault(squad.id, false)) fallBacks++;
            lastFallBack.put(squad.id, fallingBack);
            ObjectiveAssignment order = squad.assignedObjective;
            boolean holding = order != null && order.isGarrison()
                    && (reason.equals("HOLD_GROUND_MAN") || reason.equals("HOLD_GROUND_STAND"));
            if (!holding) {
                lastRun.remove(squad.id);
                continue;
            }
            holders++;
            Integer before = lastRun.put(squad.id, order.occupyRunId());
            if (before == null || before != order.occupyRunId()) manOrders++;
            for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
                if (sim.isDugIn(sim.squadMemberAt(squad.id, i))) dugInSamples++;
            }
        }
        holderSamples += holders;
        maxHolders = Math.max(maxHolders, holders);
    }

    String describe() {
        return String.format(Locale.ROOT,
                "- MARINE holds taken ground: fort whole in marine hands from %s (samples %d), won back at %s; manning "
                        + "orders %d, holder squad-samples %d (most at once %d), dug-in member-samples %d, fall backs %d%n",
                fortTakenTick < 0 ? "never" : Integer.toString(fortTakenTick), heldWholeSamples,
                fortRetakenTick < 0 ? "never" : Integer.toString(fortRetakenTick), manOrders, holderSamples, maxHolders,
                dugInSamples, fallBacks);
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("fortTakenTick", fortTakenTick).put("fortRetakenTick", fortRetakenTick)
                .put("heldWholeSamples", heldWholeSamples).put("manOrders", manOrders)
                .put("holderSamples", holderSamples).put("maxHolders", maxHolders)
                .put("dugInSamples", dugInSamples).put("fallBacks", fallBacks);
    }
}
