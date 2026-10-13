package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.AssignmentKind;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.WaterUnderSealCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress.Phase;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.world.gen.NorthWeir;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Physical repair load, protected switch work and a finite Salt Knife road attack. */
public final class WaterUnderSealSequence implements Objective {
    private final WaterUnderSealProgress progress = new WaterUnderSealProgress();
    private final int technicians;
    private final long carrier;
    private final List<Integer> militia;
    private final List<EntitySpec> reserve;
    private final StoryBeatLog log;
    private final List<Integer> roadSquads = new ArrayList<>();
    private final Set<Long> turretIds = new HashSet<>();
    private final Set<Long> escapedAttackers = new HashSet<>();
    public record AttackReport(int committed, int deployed, int living, int killed, int escaped) {}
    public AttackReport attackReport(BattleSimulation sim) {
        int living = roadSquads.stream().mapToInt(sim::squadMemberCount).sum();
        return new AttackReport(12, released * 6, living, released * 6 - living - escapedAttackers.size(), escapedAttackers.size());
    }
    private Tender tender;
    private int released, escortX = 24, escortY = 59;
    private boolean initialized, constructionExhausted;
    private float[] routeX, routeY;
    public WaterUnderSealSequence(int technicians, long carrier, List<Integer> militia, List<EntitySpec> reserve, StoryBeatLog log) {
        this.technicians = technicians; this.carrier = carrier; this.militia = List.copyOf(militia);
        this.reserve = List.copyOf(reserve); this.log = log;
        if (militia.size() != 2 || reserve.size() != 12) throw new IllegalArgumentException("Weir needs two finite holders and two road elements");
    }
    public WaterUnderSealProgress progress() { return progress; }
    public int pendingMembers() { return progress.terminal() ? 0 : 12 - released * 6; }
    public int technicianSquadId() { return technicians; }
    public long repairCarrierId() { return carrier; }
    public List<Integer> militiaSquadIds() { return militia; }
    public List<Integer> roadSquadIds() { return List.copyOf(roadSquads); }
    public Set<Long> turretEntityIds() { return Set.copyOf(turretIds); }
    public Set<Long> technicianEntityIds(BattleSimulation sim) {
        var ids = new HashSet<Long>();
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i); if (sim.identity().type(id) == UnitType.TECHNICIAN && sim.squad().squadId(id) == technicians) ids.add(id);
        }
        return Set.copyOf(ids);
    }
    public Tender tender() { return tender; }
    public boolean tenderAvailable() {
        return tender != null && tender.anchorAlive() && !tender.disabled() && !tender.ship().departing() && !tender.departed()
                && tender.buildersLost() < tender.builderComplement();
    }
    public int escortRallyX() { return escortX; }
    public int escortRallyY() { return escortY; }
    public int buildX() { return NorthWeir.BUILD_X; }
    public int buildY() { return NorthWeir.BUILD_Y; }
    public int targetX() { return switch (progress.phase()) {
        case ESCORT -> escortX; case NORTH_ROOM -> NorthWeir.NORTH_X; case SOUTH_ROOM -> NorthWeir.SOUTH_X; default -> buildX();
    }; }
    public int targetY() { return switch (progress.phase()) {
        case ESCORT -> escortY; case NORTH_ROOM -> NorthWeir.NORTH_Y; case SOUTH_ROOM -> NorthWeir.SOUTH_Y; default -> buildY();
    }; }
    public WaterUnderSealProgress.Report report(BattleSimulation sim) {
        return progress.report(companyAlive(sim), 2 - sim.squadMemberCount(technicians),
                8 - militia.stream().mapToInt(sim::squadMemberCount).sum());
    }
    public void advance(BattleSimulation sim) {
        if (sim.isComplete() || progress.terminal()) return;
        if (!initialized) { initialized = true; prepareRoute(sim); emit(sim, "mobilization"); }
        for (Tender candidate : sim.tenders().tenders()) {
            var source = candidate.ship().supportSource();
            if (candidate.ship().faction == Faction.MARINE && !candidate.grounded() && source != null
                    && source.tender() && source.memberId() != null && candidate.anchorAlive() && !candidate.disabled()
                    && !candidate.ship().departing() && candidate.buildersLost() < candidate.builderComplement()) {
                tender = candidate; break;
            }
        }
        boolean carrierAlive = sim.convoy().isVehicle(carrier) && sim.convoy().mission(carrier).state != VehicleState.WRECKED;
        boolean arrived = false;
        if (progress.phase() == Phase.ESCORT && carrierAlive) arrived = driveEscort(sim);
        boolean company = false, crew = false, hostile = false;
        Phase before = progress.phase();
        int workX = before == Phase.ESCORT ? NorthWeir.ISOLATION_X : targetX();
        int workY = before == Phase.ESCORT ? NorthWeir.ISOLATION_Y : targetY();
        boolean room = before == Phase.NORTH_ROOM || before == Phase.SOUTH_ROOM;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i); Faction side = sim.identity().faction(id);
            int x = (int) sim.world().x(id), y = (int) sim.world().y(id);
            double distance = Math.hypot(sim.world().x(id) - workX, sim.world().y(id) - workY);
            boolean inside = room ? NorthWeir.insideRoom(sim.getTopology(), x, y, before == Phase.NORTH_ROOM) : distance < 12;
            if (side == Faction.ALLY && sim.squad().squadId(id) == technicians && inside) crew = true;
            if (isCompany(sim, id) && (room ? inside : distance < 20)) company = true;
            if (sim.identity().type(id).combatant && Faction.MARINE.hostileTo(side) && (inside || room && distance < 13)) hostile = true;
            String name = sim.identity().name(id);
            if (side == Faction.MARINE && name != null && name.startsWith("tender-turret-") && distance < 12) turretIds.add(id);
        }
        // Only a completed site owned by the called, campaign-backed tender commissions this position.
        boolean commissioned = tender != null && tender.completedSites().stream()
                .anyMatch(site -> Math.hypot(site.siteX() - buildX(), site.siteY() - buildY()) < 12)
                && turretIds.stream().anyMatch(sim.world()::isAlive);
        if (before == Phase.CONSTRUCTION && tender != null && tender.current() != null && released == 0) release(sim);
        if (commissioned && released == 0) release(sim);
        if (commissioned && released == 1) release(sim);
        observeRoadExits(sim);
        boolean attackResolved = released == 2 && roadSquads.stream().allMatch(id -> sim.squadMemberCount(id) == 0);
        boolean safe = company && !hostile && (room || before == Phase.ESCORT ? crew : true);
        progress.advance(BattleSimulation.TICK_DT, companyAlive(sim), sim.squadMemberCount(technicians) > 0,
                carrierAlive, arrived, safe, commissioned, attackResolved);
        if (progress.positionLost() && progress.phase() == Phase.CONSTRUCTION && !tenderAvailable()
                && sim.getCommandPowerService().getChargesRemaining(ShipOnStation.ID) <= 0) {
            constructionExhausted = true; progress.fail(); emit(sim, "construction_exhausted");
        }
        if (before != progress.phase()) emit(sim, progress.phase().name().toLowerCase(Locale.ROOT));
    }
    private void prepareRoute(BattleSimulation sim) {
        var body = sim.convoy().body(carrier);
        var mask = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(sim.convoy().vehicleType(carrier).visualWidthCells));
        int[] path = GridPathfinder.findPathOnDemand(sim.getGrid(), (int) body.x, (int) body.y,
                NorthWeir.ISOLATION_X, NorthWeir.ISOLATION_Y, index -> 1f, mask::isPassableAt);
        if (path.length < 4) throw new IllegalStateException("North Weir repair load has no cleared route");
        var mission = sim.convoy().mission(carrier);
        mission.authoredRouteX = new float[path.length / 2]; mission.authoredRouteY = new float[path.length / 2];
        for (int i = 0; i < path.length / 2; i++) { mission.authoredRouteX[i] = path[i * 2] + .5f; mission.authoredRouteY[i] = path[i * 2 + 1] + .5f; }
        routeX = mission.authoredRouteX; routeY = mission.authoredRouteY;
        mission.authoredRoutePaused = true;
    }
    private boolean driveEscort(BattleSimulation sim) {
        var body = sim.convoy().body(carrier); var mission = sim.convoy().mission(carrier);
        boolean escort = false, threat = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            double distance = Math.hypot(sim.world().x(id) - body.x, sim.world().y(id) - body.y);
            if (isCompany(sim, id) && distance < 24) escort = true;
            if (sim.identity().type(id).combatant && Faction.ALLY.hostileTo(sim.identity().faction(id)) && distance < 24
                    && sim.getGrid().hasLineOfSight((int) body.x, (int) body.y, (int) sim.world().x(id), (int) sim.world().y(id))) threat = true;
        }
        mission.authoredRoutePaused = !escort || threat;
        int nearest = 0; double best = Double.MAX_VALUE;
        for (int i = 0; i < routeX.length; i++) {
            double d = Math.hypot(routeX[i] - body.x, routeY[i] - body.y);
            if (d < best) { best = d; nearest = i; }
        }
        int ahead = Math.min(routeX.length - 1, nearest + 10);
        escortX = (int) routeX[ahead]; escortY = (int) routeY[ahead];
        return Math.hypot(body.x - NorthWeir.ISOLATION_X, body.y - NorthWeir.ISOLATION_Y) < 4;
    }
    private void release(BattleSimulation sim) {
        int batch = released++; int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        for (int i = batch * 6; i < batch * 6 + 6; i++) sim.spawn(reserve.get(i).squad(squad));
        sim.getSquad(squad).originalSize = 6;
        sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, WaterUnderSealCommand.issuer(Faction.DEFENDER), "Salt Knife committed road element");
        roadSquads.add(squad);
        log.emitOnce(sim.getSimTickIndex(), "water_under_seal.road_element_" + batch, StoryBeatLog.Audience.OBSERVER_ONLY);
    }
    private void observeRoadExits(BattleSimulation sim) {
        var departing = new ArrayList<Long>();
        for (int squadId : roadSquads) {
            var directive = sim.getSquadCommandDirective(squadId);
            if (directive == null || directive.assignment() == null
                    || directive.assignment().kind() != AssignmentKind.WITHDRAW) continue;
            for (int member = 0; member < sim.squadMemberCount(squadId); member++) {
                long id = sim.squadMemberAt(squadId, member);
                if (WaterUnderSealRoadExit.atExit(sim.world().x(id), sim.world().y(id))) departing.add(id);
            }
        }
        for (long id : departing) {
            escapedAttackers.add(id);
            sim.takeOffTheField(id);
            log.emitOnce(sim.getSimTickIndex(), "water_under_seal.road_escape_" + id, StoryBeatLog.Audience.OBSERVER_ONLY);
        }
    }
    private static boolean companyAlive(BattleSimulation sim) {
        return ObjectiveFactionPresence.anyInPlay(sim, Faction.MARINE, id -> isCompany(sim, id));
    }
    private static boolean isCompany(BattleSimulation sim, long id) {
        if (sim.identity().faction(id) != Faction.MARINE) return false;
        return switch (sim.identity().type(id)) { case MARINE, MARINE_BLUE, MARINE_RED, HEAVY_MECH -> true; default -> false; };
    }
    private void emit(BattleSimulation sim, String beat) { log.emitOnce(sim.getSimTickIndex(), "water_under_seal." + beat, StoryBeatLog.Audience.PLAYER); }
    public Faction owningFaction() { return Faction.MARINE; }
    public void tick(BattleView ignored) {}
    public boolean isComplete() { return progress.phase() == Phase.COMPLETE; }
    public boolean isFailed() { return progress.phase() == Phase.FAILED; }
    public String displayName() { return switch (progress.phase()) {
        case ESCORT -> "Escort the repair load to the isolation switch";
        case NORTH_ROOM -> "Protect reconnection inside the north switching room";
        case SOUTH_ROOM -> "Protect reconnection inside the south switching room";
        case CONSTRUCTION -> progress.positionLost() ? "Replace the destroyed road gun using surviving committed tender support"
                : "Call the selected tender; build a road gun at the maintenance road";
        case ATTACK -> "Defend the road gun until Edek confirms handover";
        case COMPLETE -> "North Weir reconnected and defensive position handed over";
        case FAILED -> constructionExhausted ? "Construction support exhausted; withdraw for a new mobilization"
                : "North Weir mobilization lost; replacement load and technicians required";
    }; }
}
