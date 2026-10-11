package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleLeg;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Finite employer column. Captures release legs; actual hulls crossing the boundary deliver cargo. */
public final class ThunderRunSequence {
    private final FreightRunProgress.Attempt attempt;
    private final int previouslyDelivered;
    private final StoryBeatLog log;
    private final Map<Integer, Long> cargo = new LinkedHashMap<>();
    private final List<Long> escorts = new ArrayList<>();
    private final List<Integer> delivered = new ArrayList<>(), lost = new ArrayList<>();
    private final List<EntitySpec> reserve;
    private int phase;
    private boolean ambush;
    private FreightRunProgress.Outcome outcome;

    public ThunderRunSequence(BattleSimulation sim, FreightRunProgress.Attempt attempt, int previouslyDelivered,
                              List<EntitySpec> reserve, StoryBeatLog log) {
        if (attempt == null || previouslyDelivered < 0 || previouslyDelivered + attempt.cargo().size() > FreightRunProgress.COHORT_SIZE
                || reserve == null || reserve.size() != 12) throw new IllegalArgumentException("A finite freight attempt and twelve committed raiders are required");
        this.attempt = attempt; this.previouslyDelivered = previouslyDelivered;
        this.reserve = List.copyOf(reserve); this.log = log;
        escorts.add(spawn(sim, VehicleType.HEAVY_APC, 26, 72));
        escorts.add(spawn(sim, VehicleType.HEAVY_APC, 6, 78));
        for (int i : attempt.cargo()) cargo.put(i, spawn(sim, VehicleType.CARGO_CARRIER, 21 - i * 6, 75));
    }
    private long spawn(BattleSimulation sim, VehicleType type, float x, float y) {
        long id = sim.convoy().spawn(type, Faction.ALLY, VehicleMission.deployed(x, y));
        // Initial map pose, before tick zero; all later motion goes through the chassis controller.
        sim.convoy().body(id).teleport(x, y, 90f);
        return id;
    }
    public int phase() { return phase; }
    public Map<Integer, Long> cargo() { return Map.copyOf(cargo); }
    public List<Integer> cargoIdentities() { return attempt.cargo(); }
    public List<String> cargoStates() {
        return attempt.cargo().stream().map(i -> delivered.contains(i) ? "DELIVERED" : lost.contains(i) ? "LOST" : "IN_TRANSIT").toList();
    }
    public int deliveredCount() { return delivered.size(); }
    public int lostCount() { return lost.size(); }
    public boolean resolved() { return delivered.size() + lost.size() == cargo.size(); }
    public boolean successful() { return resolved() && previouslyDelivered + delivered.size() > 0; }
    public int pendingMembers() { return ambush ? 0 : reserve.size(); }
    public FreightRunProgress.Outcome outcome() { return outcome; }
    public String status() {
        String beat = switch (phase) {
            case 0 -> "Secure Dispatch Post / release the column";
            case 1 -> "Secure East Crossing / cover the freight";
            case 2 -> "Secure Outbound Gate / prepare the final run";
            default -> "THUNDER RUN / cover the column to the exit";
        };
        return beat + "  |  " + deliveredCount() + "/" + cargo.size() + " delivered, " + lostCount() + " lost";
    }
    private void beat(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), "supply_route.thunder_run." + key, StoryBeatLog.Audience.PLAYER);
    }
    public void tick(BattleSimulation sim) {
        if (outcome != null) return;
        beat(sim, "started");
        for (int i = 0; i < sim.liveUnitCount(); i++) if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.MARINE) {
            beat(sim, "player_landed"); break;
        }
        for (var entry : cargo.entrySet()) {
            int key = entry.getKey(); long id = entry.getValue();
            if (delivered.contains(key) || lost.contains(key)) continue;
            if (!sim.convoy().isVehicle(id) || sim.convoy().mission(id).state == VehicleState.WRECKED) {
                lost.add(key); beat(sim, "cargo_" + key + "_lost");
            } else if (phase == 3 && sim.convoy().body(id).x > ThunderRunCompound.WIDTH + 2) {
                delivered.add(key); beat(sim, "cargo_" + key + "_delivered"); sim.convoy().despawn(id);
            }
        }
        if (resolved()) return;
        var sites = new ArrayList<>(sim.getCompoundService().getRecords());
        // Militia must retain earlier gains when a new leg is released. A release is not revocable mid-road.
        boolean held = sites.size() == 4;
        for (int i = 0; i <= Math.min(phase + 1, 3) && held; i++) held &= sites.get(i).state == CompoundState.MARINE_HELD;
        if (phase < 3 && held && columnStopped(sim)) {
            phase++;
            beat(sim, phase == 1 ? "column_released" : phase == 2 ? "crossing_secured" : "final_run");
            for (long id : escorts) order(sim, id);
            for (long id : cargo.values()) order(sim, id);
            if (phase == 3) releaseAmbush(sim);
        }
    }
    private boolean columnStopped(BattleSimulation sim) {
        for (long id : cargo.values()) if (sim.convoy().isVehicle(id)
                && sim.convoy().mission(id).state != VehicleState.WRECKED && sim.convoy().mission(id).authoredRouteX != null) return false;
        return true;
    }
    private void order(BattleSimulation sim, long id) {
        if (!sim.convoy().isVehicle(id) || sim.convoy().mission(id).state == VehicleState.WRECKED) return;
        var body = sim.convoy().body(id); var mission = sim.convoy().mission(id);
        // Parallel wide road lanes preserve initial spacing, including the armed lead and rear escorts.
        float offset = escorts.indexOf(id) == 0 ? 12 : escorts.indexOf(id) == 1 ? -12 :
                6 - 6 * new ArrayList<>(cargo.values()).indexOf(id);
        float goal = phase == 1 ? 117 + offset : phase == 2 ? 214 + offset : ThunderRunCompound.WIDTH + 18 + offset;
        mission.authoredRouteX = new float[]{body.x, goal};
        mission.authoredRouteY = new float[]{body.y, body.y};
        mission.authoredLeg = phase == 3 ? VehicleLeg.DEPARTURE_RUN : VehicleLeg.MOVE_ORDER;
    }
    private void releaseAmbush(BattleSimulation sim) {
        ambush = true;
        for (int side = 0; side < 2; side++) {
            int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            for (int i = side * (reserve.size() / 2); i < (side + 1) * (reserve.size() / 2); i++) sim.spawn(reserve.get(i).squad(squad));
            sim.getSquad(squad).originalSize = reserve.size() / 2;
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, 275, 75),
                    CommandAuthority.SCRIPTED, "thunder-run-ambush", "cut off the freight at the outbound road");
        }
        log.emitOnce(sim.getSimTickIndex(), "supply_route.thunder_run.road_reserve_committed", StoryBeatLog.Audience.OBSERVER_ONLY);
    }
    /** Results/retreat freeze once; UI reads cannot manufacture or replay deliveries. */
    public void freeze(BattleSimulation sim, boolean retreat) {
        if (outcome != null) return;
        boolean victory = !retreat && sim.isComplete() && sim.getWinner() == Faction.MARINE;
        outcome = new FreightRunProgress.Outcome(attempt, delivered, lost, victory);
        beat(sim, retreat ? "retreat" : victory ? "victory" : "defeat");
    }
}
