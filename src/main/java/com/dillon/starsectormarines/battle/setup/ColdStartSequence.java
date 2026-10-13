package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.ColdStartCommand;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.power.YardConstruction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.ColdStartProgress;
import com.dillon.starsectormarines.battle.story.ColdStartPicket;
import com.dillon.starsectormarines.battle.story.ColdStartRoadEntry;
import com.dillon.starsectormarines.battle.story.ColdStartProgress.Phase;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Physical repair convoy, safe technician work and the grounded Shepherd's first construction sortie. */
public final class ColdStartSequence implements Objective {
    public static final int WORKSHOP_X = 82, WORKSHOP_Y = 119, PUMP_X = 171, PUMP_Y = 117;
    public static final int BUILD_X = 154, BUILD_Y = 106, BAY_X = 147, BAY_Y = 115;
    private static final String ROAD = "cold-start-road";
    private final ColdStartProgress progress;
    private final int engineers;
    private final long carrier;
    private final List<EntitySpec> reserve;
    private final StoryBeatLog log;
    private final List<Integer> roadSquads = new ArrayList<>();
    private final List<Integer> roadBatches = new ArrayList<>();
    private final ColdStartRoadEntry[] roadEntries = {new ColdStartRoadEntry(), new ColdStartRoadEntry(), new ColdStartRoadEntry(), new ColdStartRoadEntry()};
    private final Set<Integer> deniedRoadTeams = new HashSet<>();
    private final Set<Integer> passedRoadMouth = new HashSet<>();
    private final Set<Integer> handedOff = new HashSet<>();
    private final Set<Long> turretIds = new HashSet<>();
    private final List<ColdStartPicket> pickets = new ArrayList<>();
    private Tender tender;
    private float[] routeX, routeY;
    private int escortX = 20, escortY = 78, released;
    private boolean initialized, pumpStarted, finalResponseReached;
    private long pumpStartTick;

    public ColdStartSequence(int engineers, long carrier, List<EntitySpec> reserve,
                             boolean delivered, boolean powered, StoryBeatLog log) {
        this.engineers = engineers; this.carrier = carrier; this.reserve = List.copyOf(reserve); this.log = log;
        if (reserve.size() != 24) throw new IllegalArgumentException("Cold Start needs four six-person road teams");
        progress = new ColdStartProgress(delivered, powered);
    }
    public ColdStartProgress progress() { return progress; }
    public void registerPicket(String key, int squadId, int outerX, int outerY, int innerX, int innerY, int members) {
        if (initialized || pickets.stream().anyMatch(p -> p.report().squadId() == squadId || p.report().key().equals(key)))
            throw new IllegalStateException("Pickets must be unique and registered before advance");
        pickets.add(new ColdStartPicket(key, squadId, outerX, outerY, innerX, innerY, members));
    }
    public List<ColdStartPicket.Report> pickets() { return pickets.stream().map(ColdStartPicket::report).toList(); }
    public int pendingMembers() { return progress.terminal() ? 0 : reserve.size() - released * 6; }
    /** Off-map entrants refused by an actual sustained friendly boundary screen; these are not casualties. */
    public int deniedRoadMembers() { return deniedRoadTeams.size() * 6; }
    public long repairCarrierId() { return carrier; }
    public int engineersSquadId() { return engineers; }
    public Tender tender() { return tender; }
    public boolean finalResponseReached() { return finalResponseReached; }
    public Set<Long> turretEntityIds() { return Set.copyOf(turretIds); }
    public int buildX() { return BUILD_X; }
    public int buildY() { return BUILD_Y; }
    public ColdStartProgress.Report report(BattleSimulation sim) { return progress.report(2 - sim.squadMemberCount(engineers)); }
    public int targetX() { return switch (progress.phase()) {
        case ESCORT -> escortX; case POWER -> PUMP_X; case CONSTRUCTION -> BUILD_X; default -> BAY_X;
    }; }
    public int targetY() { return switch (progress.phase()) {
        case ESCORT -> escortY; case POWER -> PUMP_Y; case CONSTRUCTION -> BUILD_Y; default -> BAY_Y;
    }; }
    public void advance(BattleSimulation sim) {
        if (sim.isComplete() || progress.terminal()) return;
        if (!ObjectiveFactionPresence.anyInPlay(sim, Faction.MARINE, id -> isCompany(sim, id))) {
            progress.fail();
            emit(sim, "company_lost");
            return;
        }
        if (!initialized) {
            initialized = true;
            if (progress.phase() == Phase.ESCORT) prepareRoute(sim);
            if (progress.phase() == Phase.ESCORT) emit(sim, "convoy_arrived");
        }
        var report = report(sim);
        if (report.shorePower() && tender == null) powerYard(sim);
        boolean carrierAlive = carrier != 0 && sim.convoy().isVehicle(carrier)
                && sim.convoy().mission(carrier).state != VehicleState.WRECKED;
        boolean arrived = false;
        if (progress.phase() == Phase.ESCORT && carrierAlive) arrived = driveEscort(sim);
        int workX = progress.phase() == Phase.ESCORT ? WORKSHOP_X : progress.phase() == Phase.POWER ? PUMP_X : BAY_X;
        int workY = progress.phase() == Phase.ESCORT ? WORKSHOP_Y : progress.phase() == Phase.POWER ? PUMP_Y : BAY_Y;
        boolean company = false, crew = false, hostile = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            float distance = (float) Math.hypot(sim.world().x(id) - workX, sim.world().y(id) - workY);
            Faction side = sim.identity().faction(id);
            if (side == Faction.ALLY && sim.squad().squadId(id) == engineers && distance < 9) crew = true;
            if (!sim.identity().type(id).combatant) continue;
            if (distance < 17 && isCompany(sim, id)) company = true;
            if (distance < 13 && Faction.MARINE.hostileTo(side)) hostile = true;
            String name = sim.identity().name(id);
            if (side == Faction.MARINE && name != null && name.startsWith("tender-turret-")) turretIds.add(id);
        }
        Phase before = progress.phase();
        observePickets(sim);
        if (before == Phase.CONSTRUCTION && tender != null && tender.buildersLost() >= tender.builderComplement() && tender.built() == 0) {
            progress.fail();
            emit(sim, "builders_lost");
            return;
        }
        if (!pumpStarted && company && (before == Phase.POWER && crew || report.shorePower())) {
            pumpStarted = true; pumpStartTick = sim.getSimTickIndex();
            if (!report.shorePower()) emit(sim, "pump_started");
        }
        // Only public stage transitions commit finite attackers. Their travel and combat remain ordinary.
        if (released == 0 && (report.cargoDelivered() && company
                || carrierAlive && sim.convoy().body(carrier).x >= 50)) release(sim);
        if (pumpStarted && released == 1) release(sim);
        if (pumpStarted && sim.getSimTickIndex() - pumpStartTick >= 450 && released == 2) release(sim);
        if (before == Phase.REPAIR && released == 3) release(sim);
        handoffRoadTeams(sim);
        boolean built = tender != null && tender.built() > 0;
        progress.advance(BattleSimulation.TICK_DT, sim.squadMemberCount(engineers) > 0,
                carrierAlive || report.cargoDelivered(), arrived, company, crew, hostile, built, finalResponseReached);
        if (progress.phase() != before) {
            switch (progress.phase()) {
                case POWER -> { emit(sim, "workshop"); if (released == 0) release(sim); }
                case CONSTRUCTION -> { powerYard(sim); emit(sim, "power_restored"); }
                case REPAIR -> emit(sim, "turret_ready");
                case COMPLETE -> emit(sim, "complete");
                case FAILED -> emit(sim, sim.squadMemberCount(engineers) == 0 ? "crew_lost" : "carrier_lost");
                default -> { }
            }
        }
    }
    private void prepareRoute(BattleSimulation sim) {
        var body = sim.convoy().body(carrier);
        var mask = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(sim.convoy().vehicleType(carrier).visualWidthCells));
        int[] path = GridPathfinder.findPathOnDemand(sim.getGrid(), (int) body.x, (int) body.y,
                WORKSHOP_X, WORKSHOP_Y, index -> 1f, mask::isPassableAt);
        if (path.length < 4) throw new IllegalStateException("Repair carrier has no cleared workshop route");
        routeX = new float[path.length / 2]; routeY = new float[routeX.length];
        for (int i = 0; i < routeX.length; i++) { routeX[i] = path[i * 2] + .5f; routeY[i] = path[i * 2 + 1] + .5f; }
        var mission = sim.convoy().mission(carrier);
        mission.authoredRouteX = routeX; mission.authoredRouteY = routeY; mission.authoredRoutePaused = true;
    }
    private boolean driveEscort(BattleSimulation sim) {
        var body = sim.convoy().body(carrier);
        boolean escort = false, threat = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.identity().type(id).combatant) continue;
            float distance = (float) Math.hypot(sim.world().x(id) - body.x, sim.world().y(id) - body.y);
            if (distance < 30 && isCompany(sim, id)) escort = true;
            if (distance < 24 && Faction.ALLY.hostileTo(sim.identity().faction(id))
                    && sim.getGrid().hasLineOfSight((int) body.x, (int) body.y, (int) sim.world().x(id), (int) sim.world().y(id))) threat = true;
        }
        sim.convoy().mission(carrier).authoredRoutePaused = !escort || threat;
        int nearest = 0; double best = Double.MAX_VALUE;
        for (int i = 0; i < routeX.length; i++) {
            double d = Math.hypot(routeX[i] - body.x, routeY[i] - body.y);
            if (d < best) { best = d; nearest = i; }
        }
        int ahead = Math.min(routeX.length - 1, nearest + 12);
        escortX = (int) routeX[ahead]; escortY = (int) routeY[ahead];
        return Math.hypot(body.x - WORKSHOP_X, body.y - WORKSHOP_Y) < 4;
    }
    private void powerYard(BattleSimulation sim) {
        if (tender != null) return;
        var lighting = sim.getTopology().nightLighting();
        if (lighting != null) sim.getTopology().setNightLighting(lighting.withPower("yard"));
        try { tender = Tender.grounded(Faction.MARINE, 131, 115, BAY_X, BAY_Y, -90); }
        catch (Exception ex) { throw new IllegalStateException("Unable to commission Morrow's builder bay", ex); }
        for (Doodad prop : sim.getDoodads()) {
            if ("doodad.shepherd-recovery-hull".equals(prop.definitionId())) {
                tender.groundDisplay(new Tender.GroundDisplay(
                        prop.cellX + prop.footprintCellsX * 0.5f,
                        prop.cellY + prop.footprintCellsY * 0.5f,
                        prop.footprintCellsX, prop.footprintCellsY));
                break;
            }
        }
        sim.tenders().add(sim, tender);
        sim.getCommandPowerService().register(new YardConstruction(tender));
        if (!tender.groundSiteReachable(BUILD_X + .5f, BUILD_Y + .5f, sim.getGrid()))
            throw new IllegalStateException("Cold Start recommended turret site is unreachable");
    }
    private void release(BattleSimulation sim) {
        boolean visible = false;
        for (int i = released * 6; i < released * 6 + 6; i++) {
            var entry = reserve.get(i);
            for (int y = entry.cellY - 1; y <= entry.cellY + 1; y++)
                for (int x = entry.cellX - 1; x <= entry.cellX + 1; x++) visible |= sim.playerSeesCell(x, y);
        }
        var entry = reserve.get(released * 6);
        boolean screened = false;
        if (visible) for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (sim.identity().type(id).combatant && Faction.MARINE.friendlyTo(sim.identity().faction(id))
                    && Math.hypot(sim.world().x(id) - entry.cellX, sim.world().y(id) - entry.cellY) <= 40
                    && sim.getGrid().hasLineOfSight((int)sim.world().x(id), (int)sim.world().y(id), entry.cellX, entry.cellY)) screened = true;
        }
        if (roadEntries[released].denied(sim.getSimTickIndex(), visible, screened)) {
            int batch = released++;
            deniedRoadTeams.add(batch);
            emit(sim, batch == 2 ? "quarry_entry_denied" : "south_entry_denied");
            log.emitOnce(sim.getSimTickIndex(), "cold_start.road_team_denied_" + batch, StoryBeatLog.Audience.OBSERVER_ONLY);
            if (batch == 3) finalResponseReached = true;
            return;
        }
        // Visible entries remain off-map; denied entry records no killed or spawned soldiers.
        if (visible) return;
        int batch = released++;
        if (batch == 0) emit(sim, "approach");
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        for (int i = batch * 6; i < batch * 6 + 6; i++) sim.spawn(reserve.get(i).squad(squad));
        sim.getSquad(squad).originalSize = 6;
        int[] ingress = roadMouth(batch);
        int x = ingress[0], y = ingress[1];
        sim.assignSquadCommand(ObjectiveAssignment.attackMove(squad, x, y), CommandAuthority.SCRIPTED, ROAD, "Scrappers enter along the haul road");
        roadSquads.add(squad);
        roadBatches.add(batch);
        log.emitOnce(sim.getSimTickIndex(), "cold_start.road_team_" + batch, StoryBeatLog.Audience.OBSERVER_ONLY);
    }
    private void handoffRoadTeams(BattleSimulation sim) {
        for (int i = 0; i < roadSquads.size(); i++) {
            int id = roadSquads.get(i);
            int batch = roadBatches.get(i);
            var squad = sim.getSquad(id);
            int[] mouth = roadMouth(batch);
            if (!passedRoadMouth.contains(id) && sim.squadMemberCount(id) > 0
                    && Math.hypot(squad.centroidX - mouth[0], squad.centroidY - mouth[1]) < 4) {
                int[] post = roadIngress(batch);
                sim.assignSquadCommand(ObjectiveAssignment.attackMove(id, post[0], post[1]),
                        CommandAuthority.SCRIPTED, ROAD, "Scrappers cross the outer picket approach");
                passedRoadMouth.add(id);
            }
            int[] ingress = roadIngress(batch);
            int x = ingress[0], y = ingress[1];
            if (passedRoadMouth.contains(id) && !handedOff.contains(id) && sim.squadMemberCount(id) > 0
                    && Math.hypot(squad.centroidX - x, squad.centroidY - y) < 4) {
                if (sim.handoffSquadCommand(id, ROAD, CommandAuthority.MISSION_COMMAND, ColdStartCommand.issuer(Faction.DEFENDER), null, "Raid the repair yard")) handedOff.add(id);
            }
            if (batch == 3 && (sim.squadMemberCount(id) == 0 || Math.hypot(squad.centroidX - BUILD_X, squad.centroidY - BUILD_Y) < 36)) finalResponseReached = true;
        }
    }
    private static int[] roadMouth(int batch) {
        return batch == 2 ? new int[]{283,135} : new int[]{194,14};
    }
    private int[] roadIngress(int batch) {
        int lane = batch == 2 ? 1 : 0;
        if (pickets.size() > lane) {
            var post = pickets.get(lane).report();
            return new int[]{post.outerX(), post.outerY()};
        }
        return batch == 2 ? new int[]{270,145} : new int[]{194,45};
    }
    private void observePickets(BattleSimulation sim) {
        for (var picket : pickets) {
            var before = picket.report();
            int inside = 0;
            boolean breached = false;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long id = sim.liveUnitAt(i);
                if (sim.squad().hasSquad(id) && sim.squad().squadId(id) == before.squadId()
                        && Math.hypot(sim.world().x(id) - before.innerX(), sim.world().y(id) - before.innerY()) <= 5) inside++;
                if (sim.identity().type(id).combatant && Faction.ALLY.hostileTo(sim.identity().faction(id))
                        && Math.hypot(sim.world().x(id) - before.outerX(), sim.world().y(id) - before.outerY()) <= 3) breached = true;
            }
            var squad = sim.getSquad(before.squadId());
            picket.observe(sim.getSimTickIndex(), sim.squadMemberCount(before.squadId()), inside,
                    squad != null && squad.hasDirectContactThisTick(), breached);
            var after = picket.report();
            if (after.contacted()) emit(sim, after.key() + "_contact");
            if (after.withdrawing()) emit(sim, after.key() + "_withdrawing");
            if (after.overrun()) emit(sim, after.key() + "_overrun");
            if (after.state() == ColdStartPicket.State.INNER_HOLD) emit(sim, after.key() + "_inner_hold");
        }
        if (tender != null && tender.built() > 0 && turretIds.stream().anyMatch(sim.world()::isAlive))
            emit(sim, "inner_line_fortified");
    }
    private void emit(BattleSimulation sim, String beat) { log.emitOnce(sim.getSimTickIndex(), "cold_start." + beat, StoryBeatLog.Audience.PLAYER); }
    private static boolean isCompany(BattleSimulation sim, long id) {
        if (sim.identity().faction(id) != Faction.MARINE) return false;
        return switch (sim.identity().type(id)) {
            case MARINE, MARINE_BLUE, MARINE_RED, HEAVY_MECH -> true;
            default -> false;
        };
    }
    public Faction owningFaction() { return Faction.MARINE; }
    public void tick(BattleView ignored) { }
    public boolean isComplete() { return progress.phase() == Phase.COMPLETE; }
    public boolean isFailed() { return progress.phase() == Phase.FAILED; }
    public String displayName() { return switch (progress.phase()) {
        case ESCORT -> "Escort the repair carrier to the workshop";
        case POWER -> "Protect the pump crew / shore power " + (int) (100 * progress.work() / ColdStartProgress.POWER_SECONDS) + "%";
        case CONSTRUCTION -> "Select Build Vulcan Turret; place it outside Morrow's east bay";
        case REPAIR -> "Hold Morrow's work site / repairs " + (int) (100 * progress.work() / ColdStartProgress.REPAIR_SECONDS) + "%";
        case COMPLETE -> "Morrow powered and repaired; departure clearance still required";
        case FAILED -> "Repair operation lost; completed deliveries and power retained";
    }; }
}
