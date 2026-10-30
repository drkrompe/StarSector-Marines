package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.i18n.Strings;
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
import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
    private final ThunderRunColumnDriver driver = new ThunderRunColumnDriver();
    private final List<Integer> releasedCargo = new ArrayList<>();
    private final int[] gunWork = new int[2];
    private final boolean[] gunsBuilt = new boolean[2];
    private final List<Long> supportGuns = new ArrayList<>();
    /** Raider squads sent at an arm's militia block: squad to {arm, road objective x}. */
    private final Map<Integer, int[]> screened = new LinkedHashMap<>();
    private static final float BLOCK_RADIUS = 12f;
    private int phaseTick, currentTick;
    private final long advanceHauler;
    private int finalRunTick;
    private final long heavyLead;
    private final long technical;
    private boolean heavyExitOrdered;
    private FreightRunProgress.Outcome outcome;
    private ThunderRunPressure pressure;
    /** The checkpoint's reinforcement at the final run: a technical down the outbound road to the gun line. */
    private RoadTechnical checkpointTechnical;
    private final List<Integer> checkpointSquads = new ArrayList<>();
    /** Where the reinforcement comes in, at the freight exit, and where its technical parks, among the exit guns. */
    static final int CHECKPOINT_ENTRY_X = 317, CHECKPOINT_ENTRY_Y = 75, CHECKPOINT_PARK_X = 298, CHECKPOINT_PARK_Y = 71;
    /** The reinforcement's two rifle sections stand beside the exit guns. */
    static final int[][] CHECKPOINT_POSTS = {{302, 62}, {302, 88}};
    static final int CHECKPOINT_SECTION = 4;
    /** On the final run the APCs and the tank push up to the exit gun line to fight it, rather than waiting short of it. */
    static final float ESCORT_ASSAULT_X = 292, HEAVY_ASSAULT_X = 298;

    public ThunderRunSequence(BattleSimulation sim, FreightRunProgress.Attempt attempt, int previouslyDelivered,
                              List<EntitySpec> reserve, StoryBeatLog log) {
        if (attempt == null || previouslyDelivered < 0 || previouslyDelivered + attempt.cargo().size() > FreightRunProgress.COHORT_SIZE
                || reserve == null || reserve.size() != ThunderRunPacing.RESERVE_SIZE)
            throw new IllegalArgumentException("A finite freight attempt and the complete road-raider timetable are required");
        this.attempt = attempt; this.previouslyDelivered = previouslyDelivered;
        this.reserve = List.copyOf(reserve); this.log = log;
        escorts.add(place(sim, sim.convoy().spawn(VehicleStockFit.CONVOY_ESCORT, Faction.ALLY, VehicleMission.deployed(27, 70)), 27, 70));
        escorts.add(place(sim, sim.convoy().spawn(VehicleStockFit.CONVOY_ESCORT, Faction.ALLY, VehicleMission.deployed(27, 80)), 27, 80));
        heavyLead = spawn(sim, VehicleType.BREAKTHROUGH_TANK, 35, 75);
        technical = spawn(sim, VehicleType.MILITIA_TECHNICAL, 34, 83);
        // This earlier load is outside the newly contracted three-load manifest.
        // Its actual fate is witnessed, never scripted or charged to the player's freight award.
        advanceHauler = attempt.ordinal() == 1 ? spawn(sim, VehicleType.CARGO_CARRIER, 117, 75) : 0L;
        for (int i : attempt.cargo()) cargo.put(i, spawn(sim, VehicleType.CARGO_CARRIER, 21 - i * 8, 75));
        for (long id : escorts) driver.register(id, ThunderRunColumnDriver.Role.ESCORT, sim.convoy().body(id).y);
        driver.register(heavyLead, ThunderRunColumnDriver.Role.HEAVY, 75);
        driver.register(technical, ThunderRunColumnDriver.Role.TECHNICAL, 83);
        driver.register(advanceHauler, ThunderRunColumnDriver.Role.CARGO, 75);
        for (long id : cargo.values()) driver.register(id, ThunderRunColumnDriver.Role.CARGO, 75);
    }
    private long spawn(BattleSimulation sim, VehicleType type, float x, float y) {
        return place(sim, sim.convoy().spawn(type, Faction.ALLY, VehicleMission.deployed(x, y)), x, y);
    }
    private static long place(BattleSimulation sim, long id, float x, float y) {
        // Initial map pose, before tick zero; all later motion goes through the chassis controller.
        sim.convoy().body(id).teleport(x, y, -90f);
        return id;
    }
    /** Installs the rising pressure. Setup, once. */
    public void pressure(ThunderRunPressure pressure) {
        if (this.pressure != null) throw new IllegalStateException("Install Thunder Run pressure once");
        this.pressure = pressure;
    }
    public ThunderRunPressure pressure() { return pressure; }
    public long technical() { return technical; }
    /** The checkpoint's technical, 0 before the final run. */
    public long checkpointTechnical() { return checkpointTechnical == null ? 0L : checkpointTechnical.id(); }
    public List<Integer> checkpointSquads() { return List.copyOf(checkpointSquads); }
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
    public int pendingMembers() { return pacing.pendingMembers() + (pressure == null ? 0 : pressure.pendingMembers()); }
    /** Column driving evidence: reactive reroutes, wreck swerves, station-keeping waits, hauler sprints. */
    public int[] drivingCounters() { return new int[]{driver.reroutes(), driver.swerves(), driver.waits(), driver.punches()}; }
    public FreightRunProgress.Outcome outcome() { return outcome; }
    public String status() {
        String beat = switch (phase) {
            case 0 -> Strings.get("thunderRunStatusSecureDispatch");
            case 1 -> holdSite() > 0 ? Strings.format("thunderRunStatusDefendDispatch", (holdRemainingTicks() + 29) / 30)
                    : Strings.get("thunderRunStatusSecureEast");
            case 2 -> holdSite() > 0 ? Strings.format("thunderRunStatusDefendEast", (holdRemainingTicks() + 29) / 30)
                    : Strings.get("thunderRunStatusSecureGate");
            default -> Strings.get("thunderRunStatusRun");
        };
        return Strings.format("thunderRunStatus", beat, deliveredCount(), cargo.size(), lostCount());
    }
    private void beat(BattleSimulation sim, String key) {
        log.emitOnce(sim.getSimTickIndex(), "supply_route.thunder_run." + key, StoryBeatLog.Audience.PLAYER);
    }
    public void tick(BattleSimulation sim) {
        if (outcome != null) return;
        currentTick = sim.getSimTickIndex();
        beat(sim, "started");
        if (advanceHauler != 0L) beat(sim, "advance_distress");
        if (phase > 0 && phase < 3 && holdRemainingTicks() == 0)
            beat(sim, phase == 1 ? "dispatch_window_complete" : "crossing_window_complete");
        releaseBatches(sim);
        if (pressure != null) pressure.tick(sim, phase, leadCargoX(sim));
        releaseBrokenBlocks(sim);
        driver.tick(sim);
        prepareSupportGuns(sim);
        observeDefendOrder(sim);
        if (sim.convoy().isVehicle(advanceHauler)) {
            if (stranded(sim, advanceHauler)) beat(sim, "advance_load_lost");
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
            if (stranded(sim, id)) {
                lost.add(key); beat(sim, "cargo_" + key + "_lost");
            } else if (phase == 3 && sim.convoy().body(id).x > ThunderRunCompound.WIDTH + 2) {
                delivered.add(key); beat(sim, "cargo_" + key + "_delivered"); sim.convoy().despawn(id);
            }
        }
        if (resolved()) return;
        if (phase == 3) {
            // Armed vehicles establish the crossing before the cargo column commits, every hauler together.
            if (ThunderRunPacing.freightDue(currentTick - finalRunTick))
                for (var entry : cargo.entrySet()) {
                    if (releasedCargo.contains(entry.getKey())) continue;
                    releasedCargo.add(entry.getKey());
                    if (lost.contains(entry.getKey())) continue;
                    order(sim, entry.getValue());
                    freightReleased = true;
                    beat(sim, "freight_committed");
                    beat(sim, "load_" + entry.getKey() + "_released");
                }
            if (!heavyExitOrdered && freightReleased) {
                boolean leadApproaching = cargo.values().stream().anyMatch(id ->
                        !stranded(sim, id) && sim.convoy().body(id).x >= 280);
                if (leadApproaching) {
                    heavyExitOrdered = true;
                    order(sim, heavyLead);
                    beat(sim, "heavy_lead_advancing");
                }
            }
            boolean rearPassed = releasedCargo.size() == cargo.size();
            for (long id : cargo.values()) if (!stranded(sim, id) && sim.convoy().body(id).x < 282) rearPassed = false;
            if (rearPassed && !escortExitOrdered) {
                escortExitOrdered = true;
                for (long id : escorts) order(sim, id);
                beat(sim, "rear_clear");
            }
        }
        // Militia must retain earlier gains when a new leg is released. A release is not revocable mid-road, and a
        // hull still on its last leg is simply sent on to the next goal rather than waited for.
        if (phase < 3 && heldThrough(sim, phase + 1) && holdRemainingTicks() == 0) {
            phase++;
            phaseTick = currentTick;
            beat(sim, phase == 1 ? "column_released" : phase == 2 ? "crossing_secured" : "final_run");
            if (phase < 3) {
                int ordinal = 0;
                for (long id : cargo.values()) {
                    if (ordinal++ == 0 || phase == 2) order(sim, id);
                }
                order(sim, advanceHauler);
                // The armor goes up with the freight it guards, to stand over the site through its hold.
                for (long id : escorts) order(sim, id);
                order(sim, heavyLead);
                // The technical goes forward with the first release, to block its arm for both holds.
                if (phase == 1) order(sim, technical);
                beat(sim, phase == 1 ? "defend_dispatch" : "defend_crossing");
            } else {
                finalRunTick = currentTick;
                reinforceCheckpoint(sim);
                for (long id : escorts) order(sim, id);
                order(sim, heavyLead);
                order(sim, advanceHauler);
            }
        }
    }
    /** The x of the leading hauler still on the road, or -1 for none. */
    private float leadCargoX(BattleSimulation sim) {
        float lead = -1;
        for (var entry : cargo.entrySet()) {
            long id = entry.getValue();
            if (delivered.contains(entry.getKey()) || stranded(sim, id)) continue;
            lead = Math.max(lead, sim.convoy().body(id).x);
        }
        return lead;
    }
    /**
     * The checkpoint is reinforced as the final run goes: a technical drives in from the freight exit and parks among
     * the exit guns, and two rifle sections, each with a scrap rocket, take post beside them.
     */
    private void reinforceCheckpoint(BattleSimulation sim) {
        VehicleClearance lane = VehicleClearance.erode(sim.getGrid(),
                VehicleClearance.radiusForWidth(RoadTechnical.TYPE.visualWidthCells));
        int[] entry = BattleSetup.nearestClear(lane, new int[]{CHECKPOINT_ENTRY_X, CHECKPOINT_ENTRY_Y});
        int[] park = BattleSetup.nearestClear(lane, new int[]{CHECKPOINT_PARK_X, CHECKPOINT_PARK_Y});
        checkpointTechnical = new RoadTechnical(entry[0], entry[1], park[0], park[1]);
        checkpointTechnical.enter(sim);
        Random rng = new Random(7919L * attempt.ordinal() + 17);
        for (int[] post : CHECKPOINT_POSTS) {
            int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            int member = 0;
            for (int[] cell : BattleSetup.pickSpawnCluster(sim.getGrid(), CHECKPOINT_ENTRY_X, post[1] < 75 ? 68 : 82,
                    CHECKPOINT_SECTION)) {
                var raider = BattleSetup.makeOpeningMilitia("freight-checkpoint-reinforcement-" + post[1] + "-" + member,
                        Faction.DEFENDER, cell[0], cell[1], rng).role(UnitRole.PATROL);
                if (member == 0)
                    raider.specialEquipment(SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.SCRAP_ROCKET_ID), 2);
                sim.spawn(raider.squad(squad));
                member++;
            }
            sim.getSquad(squad).originalSize = member;
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, post[0], post[1], 5),
                    CommandAuthority.SCRIPTED, "thunder-run-checkpoint", "hold the exit gun line");
            checkpointSquads.add(squad);
        }
        log.emitOnce(currentTick, "supply_route.thunder_run.checkpoint_reinforced", StoryBeatLog.Audience.OBSERVER_ONLY);
    }
    private boolean heldThrough(BattleSimulation sim, int lastSite) {
        var sites = List.copyOf(sim.getCompoundService().getRecords());
        if (sites.size() != 4) return false;
        for (int i = 0; i <= lastSite; i++) if (sites.get(i).state != CompoundState.MARINE_HELD) return false;
        return true;
    }
    /** A hauler that will never reach the exit: gone or burnt out. One with its drive shot away limps on. */
    private static boolean stranded(BattleSimulation sim, long id) {
        return !sim.convoy().isVehicle(id) || sim.convoy().mission(id).state == VehicleState.WRECKED;
    }
    private void order(BattleSimulation sim, long id) {
        if (!sim.convoy().isVehicle(id) || sim.convoy().mission(id).state == VehicleState.WRECKED) return;
        // Staggered goals preserve the column's spacing; the driver owns lanes, weave and reactions.
        float offset = id == advanceHauler ? 16 : id == heavyLead ? 20 : escorts.contains(id) ? 8
                : -8 * new ArrayList<>(cargo.values()).indexOf(id);
        float goal = phase == 1 ? 117 + offset : phase == 2 ? 214 + offset : ThunderRunCompound.WIDTH + 40 + offset;
        if (id == technical) {
            var stop = ThunderRunCompound.TECHNICAL_STOP;
            driver.order(sim, id, stop.x(), stop.y(), VehicleLeg.MOVE_ORDER);
            return;
        }
        if (phase == 3 && escorts.contains(id) && !escortExitOrdered) goal = ESCORT_ASSAULT_X;
        if (phase == 3 && id == heavyLead && !heavyExitOrdered) goal = HEAVY_ASSAULT_X;
        boolean departing = id == heavyLead ? heavyExitOrdered : !escorts.contains(id) || escortExitOrdered;
        driver.order(sim, id, goal, phase == 3 && departing ? VehicleLeg.DEPARTURE_RUN : VehicleLeg.MOVE_ORDER);
    }
    private void releaseBatches(BattleSimulation sim) {
        for (int batch = 0; batch < ThunderRunPacing.BATCHES; batch++) {
            if (!pacing.takeBatch(batch, phase, currentTick)) continue;
            int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
            int start = batch * ThunderRunPacing.RAIDERS_PER_BATCH;
            for (int i = start; i < start + ThunderRunPacing.RAIDERS_PER_BATCH; i++) sim.spawn(reserve.get(i).squad(squad));
            sim.getSquad(squad).originalSize = ThunderRunPacing.RAIDERS_PER_BATCH;
            int rearPost = ThunderRunPacing.rearPost(batch);
            int attackPhase = ThunderRunPacing.batchPhase(batch);
            int targetX = attackPhase == 0 ? 117 : attackPhase == 1 ? 133
                    : attackPhase == 2 ? (ThunderRunPacing.usesOutboundRoad(batch) ? 245 : 172) : 275;
            var post = rearPost >= 0 ? ThunderRunCompound.SUPPORT_POSTS.get(rearPost) : null;
            // A wave walking down a side arm during a hold meets that arm's militia block first and
            // fights it out before it reaches the road; the block is what soaks the wave.
            int arm = batch == 0 ? -1 : batch % 2 == 0 ? 1 : 0;
            if (post == null && arm >= 0 && attackPhase <= 2 && !ThunderRunPacing.usesOutboundRoad(batch)
                    && blockHolds(sim, arm)) {
                var block = blockAt(sim, arm);
                sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad, block.x(), block.y(), 8),
                        CommandAuthority.SCRIPTED, "thunder-run-road-raiders", "break the militia block on the arm");
                screened.put(squad, new int[] {arm, targetX});
            } else {
                sim.assignSquadCommand(ObjectiveAssignment.defendArea(squad,
                                post != null ? post.x() : targetX, post != null ? post.y() : 75, post != null ? 6 : 18),
                        CommandAuthority.SCRIPTED, "thunder-run-road-raiders",
                        post != null ? "attack the militia rear post" : "interdict the authored freight crossing");
            }
            log.emitOnce(currentTick, "supply_route.thunder_run.road_batch_" + batch,
                    StoryBeatLog.Audience.OBSERVER_ONLY);
        }
    }

    /** Waves held on an arm's militia block go on to the road once nobody is left holding it. */
    private void releaseBrokenBlocks(BattleSimulation sim) {
        for (var it = screened.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (blockHolds(sim, entry.getValue()[0])) continue;
            it.remove();
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(entry.getKey(), entry.getValue()[1], 75, 18),
                    CommandAuthority.SCRIPTED, "thunder-run-road-raiders", "interdict the authored freight crossing");
        }
    }

    /** True while militia or a militia gun still stands at {@code arm}'s mouth, or the technical at its stop on the south arm. */
    private boolean blockHolds(BattleSimulation sim, int arm) {
        return technicalBlocks(sim, arm) || alliesNear(sim, ThunderRunCompound.SUPPORT_POSTS.get(arm));
    }

    /** Where {@code arm}'s block stands: the technical while it holds the south arm, otherwise the bay. */
    private ThunderRunCompound.Cell blockAt(BattleSimulation sim, int arm) {
        return technicalBlocks(sim, arm) ? ThunderRunCompound.TECHNICAL_STOP : ThunderRunCompound.SUPPORT_POSTS.get(arm);
    }

    private boolean technicalBlocks(BattleSimulation sim, int arm) {
        if (arm != 1 || !sim.convoy().isVehicle(technical)
                || sim.convoy().mission(technical).state == VehicleState.WRECKED) return false;
        var body = sim.convoy().body(technical);
        var stop = ThunderRunCompound.TECHNICAL_STOP;
        float dx = body.x - stop.x(), dy = body.y - stop.y();
        return dx * dx + dy * dy <= BLOCK_RADIUS * BLOCK_RADIUS;
    }

    private static boolean alliesNear(BattleSimulation sim, ThunderRunCompound.Cell block) {
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.ALLY) continue;
            float dx = sim.world().x(unit) - block.x(), dy = sim.world().y(unit) - block.y();
            if (dx * dx + dy * dy <= BLOCK_RADIUS * BLOCK_RADIUS) return true;
        }
        return false;
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
            // The north bay's crew is the picket already holding it; the Crossing bay waits on the Crossing.
            gunWork[post] = crewPresent && (post == 0 || sites.get(post + 1).state == CompoundState.MARINE_HELD)
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
