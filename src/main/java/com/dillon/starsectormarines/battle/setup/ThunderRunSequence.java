package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
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

/** Finite dispatch windows and employer support; only actual exiting hulls deliver cargo. */
public final class ThunderRunSequence {
    private final FreightRunProgress.Attempt attempt;
    private final int previouslyDelivered;
    private final StoryBeatLog log;
    private final Map<Integer, Long> cargo = new LinkedHashMap<>();
    private final List<Long> escorts = new ArrayList<>();
    private final List<Integer> delivered = new ArrayList<>(), lost = new ArrayList<>();
    private final List<EntitySpec> reserve;
    private int phase;
    private boolean freightReleased, escortExitOrdered;
    private final ThunderRunPacing pacing = new ThunderRunPacing();
    private final List<Integer> releasedCargo = new ArrayList<>();
    private final int[] gunWork = new int[2];
    private final boolean[] gunsBuilt = new boolean[2];
    private final boolean[] armorStaged = new boolean[3];
    private final List<Long> supportGuns = new ArrayList<>();
    private int phaseTick, currentTick;
    private final long advanceHauler;
    private int finalRunTick;
    private final long heavyLead;
    private boolean heavyExitOrdered;
    private FreightRunProgress.Outcome outcome;

    public ThunderRunSequence(BattleSimulation sim, FreightRunProgress.Attempt attempt, int previouslyDelivered,
                              List<EntitySpec> reserve, StoryBeatLog log) {
        if (attempt == null || previouslyDelivered < 0 || previouslyDelivered + attempt.cargo().size() > FreightRunProgress.COHORT_SIZE
                || reserve == null || reserve.size() != ThunderRunPacing.RESERVE_SIZE)
            throw new IllegalArgumentException("A finite freight attempt and the complete road-raider timetable are required");
        this.attempt = attempt; this.previouslyDelivered = previouslyDelivered;
        this.reserve = List.copyOf(reserve); this.log = log;
        escorts.add(spawn(sim, VehicleType.ESCORT_APC, 27, 70));
        escorts.add(spawn(sim, VehicleType.ESCORT_APC, 27, 80));
        heavyLead = spawn(sim, VehicleType.BREAKTHROUGH_TANK, 35, 75);
        // This earlier load is outside the newly contracted three-load manifest.
        // Its actual fate is witnessed, never scripted or charged to the player's freight award.
        advanceHauler = attempt.ordinal() == 1 ? spawn(sim, VehicleType.CARGO_CARRIER, 117, 75) : 0L;
        for (int i : attempt.cargo()) cargo.put(i, spawn(sim, VehicleType.CARGO_CARRIER, 21 - i * 8, 75));
    }
    private long spawn(BattleSimulation sim, VehicleType type, float x, float y) {
        long id = sim.convoy().spawn(type, Faction.ALLY, VehicleMission.deployed(x, y));
        // Initial map pose, before tick zero; all later motion goes through the chassis controller.
        sim.convoy().body(id).teleport(x, y, -90f);
        return id;
    }
    public long heavyLead() { return heavyLead; }
    public long advanceHauler() { return advanceHauler; }
    public List<Long> supportGuns() { return List.copyOf(supportGuns); }
    public int holdSite() { return phase > 0 && phase < 3 && holdRemainingTicks() > 0 ? phase : 0; }
    public int holdRemainingTicks() { return Math.max(0, ThunderRunPacing.holdTicks(phase) - (currentTick - phaseTick)); }
    public int phase() { return phase; }
    public Map<Integer, Long> cargo() { return Map.copyOf(cargo); }
    public List<Integer> cargoIdentities() { return attempt.cargo(); }
    public List<String> cargoStates() {
        return attempt.cargo().stream().map(i -> delivered.contains(i) ? "DELIVERED" : lost.contains(i) ? "LOST"
                : phase >= 2 || attempt.cargo().indexOf(i) < phase || releasedCargo.contains(i) ? "IN_TRANSIT" : "WAITING").toList();
    }
    public int deliveredCount() { return delivered.size(); }
    public int lostCount() { return lost.size(); }
    public boolean resolved() { return delivered.size() + lost.size() == cargo.size(); }
    public boolean successful() { return resolved() && previouslyDelivered + delivered.size() > 0; }
    public int pendingMembers() { return pacing.pendingMembers(); }
    public FreightRunProgress.Outcome outcome() { return outcome; }
    public String status() {
        String beat = switch (phase) {
            case 0 -> "Secure Dispatch Post / release the column";
            case 1 -> holdSite() > 0 ? "DEFEND Dispatch / next dispatch in " + (holdRemainingTicks() + 29) / 30 + "s"
                    : "Secure East Crossing / militia hold Dispatch";
            case 2 -> holdSite() > 0 ? "DEFEND East Crossing / armor dispatch in " + (holdRemainingTicks() + 29) / 30 + "s"
                    : "Secure Outbound Gate / prepare the armored run";
            default -> "THUNDER RUN / cover the column to the exit";
        };
        return beat + "  |  " + deliveredCount() + "/" + cargo.size() + " delivered, " + lostCount() + " lost";
    }
    private void beat(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), "supply_route.thunder_run." + key, StoryBeatLog.Audience.PLAYER);
    }
    public void tick(BattleSimulation sim) {
        if (outcome != null) return;
        currentTick = sim.getSimTickIndex();
        beat(sim, "started");
        if (advanceHauler != 0L) beat(sim, "advance_distress");
        if (phase > 0 && phase < 3 && holdRemainingTicks() == 0) {
            beat(sim, phase == 1 ? "dispatch_window_complete" : "crossing_window_complete");
            if (!armorStaged[phase] && heldThrough(sim, phase)) {
                armorStaged[phase] = true;
                for (long id : escorts) order(sim, id);
                order(sim, heavyLead);
            }
        }
        releaseBatches(sim);
        prepareSupportGuns(sim);
        observeDefendOrder(sim);
        if (sim.convoy().isVehicle(advanceHauler)) {
            if (sim.convoy().mission(advanceHauler).state == VehicleState.WRECKED) beat(sim, "advance_load_lost");
            else if (sim.convoy().body(advanceHauler).x > ThunderRunCompound.WIDTH + 2) {
                beat(sim, "advance_load_clear");
                sim.convoy().despawn(advanceHauler);
            }
        }
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
        if (phase == 3) {
            // Armed vehicles establish the crossing before the long, slower cargo column commits.
            int ordinal = 0;
            for (var entry : cargo.entrySet()) {
                if (!releasedCargo.contains(entry.getKey())
                        && ThunderRunPacing.cargoDue(ordinal, currentTick - finalRunTick)) {
                    releasedCargo.add(entry.getKey());
                    if (lost.contains(entry.getKey())) { ordinal++; continue; }
                    order(sim, entry.getValue());
                    freightReleased = true;
                    beat(sim, "freight_committed");
                    beat(sim, "load_" + entry.getKey() + "_released");
                }
                ordinal++;
            }
            if (!heavyExitOrdered && freightReleased) {
                boolean leadApproaching = cargo.values().stream().anyMatch(id ->
                        sim.convoy().isTargetable(id) && sim.convoy().body(id).x >= 280);
                if (leadApproaching) {
                    heavyExitOrdered = true;
                    order(sim, heavyLead);
                    beat(sim, "heavy_lead_advancing");
                }
            }
            boolean rearPassed = releasedCargo.size() == cargo.size();
            for (long id : cargo.values()) if (sim.convoy().isTargetable(id) && sim.convoy().body(id).x < 282) rearPassed = false;
            if (rearPassed && !escortExitOrdered) {
                escortExitOrdered = true;
                for (long id : escorts) order(sim, id);
                beat(sim, "rear_clear");
            }
        }
        // Militia must retain earlier gains when a new leg is released. A release is not revocable mid-road.
        if (phase < 3 && heldThrough(sim, phase + 1) && holdRemainingTicks() == 0 && columnStopped(sim)) {
            phase++;
            phaseTick = currentTick;
            beat(sim, phase == 1 ? "column_released" : phase == 2 ? "crossing_secured" : "final_run");
            if (phase < 3) {
                int ordinal = 0;
                for (long id : cargo.values()) {
                    if (ordinal++ == 0 || phase == 2) order(sim, id);
                }
                order(sim, advanceHauler);
                beat(sim, phase == 1 ? "defend_dispatch" : "defend_crossing");
            } else {
                finalRunTick = currentTick;
                for (long id : escorts) order(sim, id);
                order(sim, heavyLead);
                order(sim, advanceHauler);
            }
        }
    }
    private boolean heldThrough(BattleSimulation sim, int lastSite) {
        var sites = List.copyOf(sim.getCompoundService().getRecords());
        if (sites.size() != 4) return false;
        for (int i = 0; i <= lastSite; i++) if (sites.get(i).state != CompoundState.MARINE_HELD) return false;
        return true;
    }
    private boolean columnStopped(BattleSimulation sim) {
        for (long id : cargo.values()) if (sim.convoy().isVehicle(id)
                && sim.convoy().mission(id).state != VehicleState.WRECKED && sim.convoy().mission(id).authoredRouteX != null) return false;
        return true;
    }
    private void order(BattleSimulation sim, long id) {
        if (!sim.convoy().isVehicle(id) || sim.convoy().mission(id).state == VehicleState.WRECKED) return;
        var body = sim.convoy().body(id); var mission = sim.convoy().mission(id);
        // Parallel wide road lanes preserve initial spacing, with the armed escorts screening both flanks.
        float offset = id == advanceHauler ? 16 : id == heavyLead ? 20 : escorts.contains(id) ? 8
                : -8 * new ArrayList<>(cargo.values()).indexOf(id);
        float goal = phase == 1 ? 117 + offset : phase == 2 ? 214 + offset : ThunderRunCompound.WIDTH + 40 + offset;
        if (phase == 3 && escorts.contains(id) && !escortExitOrdered) goal = 273;
        if (phase == 3 && id == heavyLead && !heavyExitOrdered) goal = 294;
        mission.authoredRouteX = new float[]{body.x, goal};
        mission.authoredRouteY = new float[]{body.y, body.y};
        boolean departing = id == heavyLead ? heavyExitOrdered : !escorts.contains(id) || escortExitOrdered;
        mission.authoredLeg = phase == 3 && departing ? VehicleLeg.DEPARTURE_RUN : VehicleLeg.MOVE_ORDER;
    }
    private void releaseBatches(BattleSimulation sim) {
        for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++) {
            if (!pacing.takeBatch(batch, phase, currentTick)) continue;
            int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            int start = batch * ThunderRunPacing.RAIDERS_PER_BATCH;
            for (int i = start; i < start + ThunderRunPacing.RAIDERS_PER_BATCH; i++) sim.spawn(reserve.get(i).squad(squad));
            sim.getSquad(squad).originalSize = ThunderRunPacing.RAIDERS_PER_BATCH;
            int attackPhase = ThunderRunPacing.batchPhase(batch);
            int targetX = attackPhase == 0 ? 117 : attackPhase == 1 ? 133
                    : attackPhase == 2 ? (ThunderRunPacing.usesOutboundRoad(batch) ? 245 : 172) : 275;
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, targetX, 75, 18),
                    CommandAuthority.SCRIPTED, "thunder-run-road-raiders", "interdict the authored freight crossing");
            log.emitOnce(currentTick, "supply_route.thunder_run.road_batch_" + batch,
                    StoryBeatLog.Audience.OBSERVER_ONLY);
        }
    }

    private void prepareSupportGuns(BattleSimulation sim) {
        var sites = List.copyOf(sim.getCompoundService().getRecords());
        if (sites.size() != 4) return;
        for (int post = 0; post < 2; post++) {
            if (gunsBuilt[post]) continue;
            var cell = ThunderRunCompound.SUPPORT_POSTS.get(post);
            boolean crewPresent = false;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.ALLY || sim.identity().type(unit).isTurret()) continue;
                float dx = sim.world().x(unit) - cell.x(), dy = sim.world().y(unit) - cell.y();
                if (dx * dx + dy * dy <= 16) { crewPresent = true; break; }
            }
            gunWork[post] = crewPresent && sites.get(post + 1).state == CompoundState.MARINE_HELD
                    ? gunWork[post] + 1 : 0;
            if (gunWork[post] < 8 * 30) continue;
            gunsBuilt[post] = true;
            // Prepared roadside mounts do not close navigation beneath their crew.
            supportGuns.add(sim.spawn(MapTurret.create("wayline-field-gun-" + post, Faction.ALLY,
                    TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID, cell.x(), cell.y())));
            beat(sim, "militia_gun_" + post + "_ready");
        }
    }

    private void observeDefendOrder(BattleSimulation sim) {
        if (holdSite() == 0) return;
        var post = ThunderRunCompound.SUPPORT_POSTS.get(holdSite() - 1);
        for (var squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || !squad.hasPlayerOrder(AssignmentKind.DEFEND_AREA)) continue;
            var a = squad.assignedObjective;
            int dx = a.targetCellX() - post.x(), dy = a.targetCellY() - post.y();
            if (dx * dx + dy * dy <= 30 * 30) beat(sim, "defend_order_placed");
        }
    }
    /** Results/retreat freeze once; UI reads cannot manufacture or replay deliveries. */
    public void freeze(BattleSimulation sim, boolean retreat) {
        if (outcome != null) return;
        boolean victory = !retreat && sim.isComplete() && sim.getWinner() == Faction.MARINE;
        outcome = new FreightRunProgress.Outcome(attempt, delivered, lost, victory);
        beat(sim, retreat ? "retreat" : victory ? "victory" : "defeat");
    }
}
