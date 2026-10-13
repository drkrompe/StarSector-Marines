package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.LimitedWarrantCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.LimitedWarrantProgress;
import com.dillon.starsectormarines.battle.story.LimitedWarrantProgress.Phase;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.world.gen.LowQuay;
import java.util.List;
import java.util.Locale;

/** Physical technician and vulnerable calibration carrier; no reward is inferred from enemy casualties. */
public final class LimitedWarrantSequence implements Objective {
    private final LimitedWarrantProgress progress;
    private final int technician, constables;
    private final long carrier;
    private final List<EntitySpec> reserve;
    private final StoryBeatLog log;
    private boolean initialized, released;
    private int reserveSquad = -1, escortX = LowQuay.LOAD_X, escortY = LowQuay.LOAD_Y;
    private float[] routeX, routeY;
    public LimitedWarrantSequence(int technician, int constables, long carrier, List<EntitySpec> reserve,
                                 boolean records, StoryBeatLog log) {
        this.technician = technician; this.constables = constables; this.carrier = carrier;
        this.reserve = List.copyOf(reserve); this.log = log; progress = new LimitedWarrantProgress(records);
    }
    public LimitedWarrantProgress progress() { return progress; }
    public int technicianSquadId() { return technician; }
    public long carrierId() { return carrier; }
    public int pendingMembers() { return released || progress.terminal() ? 0 : reserve.size(); }
    public int reserveSquadId() { return reserveSquad; }
    public int targetX() { return switch (progress.phase()) {
        case RECORDS -> LowQuay.RECORDS_X; case CALIBRATION -> LowQuay.KIT_X; default -> escortX;
    }; }
    public int targetY() { return switch (progress.phase()) {
        case RECORDS -> LowQuay.RECORDS_Y; case CALIBRATION -> LowQuay.KIT_Y; default -> escortY;
    }; }
    public LimitedWarrantProgress.Report report(BattleSimulation sim) {
        return progress.report(1 - sim.squadMemberCount(technician), 4 - sim.squadMemberCount(constables));
    }
    public void advance(BattleSimulation sim) {
        if (sim.isComplete() || progress.terminal()) return;
        if (!initialized) { initialized = true; prepareRoute(sim); emit(sim, "mobilization"); }
        Phase before = progress.phase();
        boolean cargoAlive = sim.convoy().mission(carrier).state != VehicleState.WRECKED;
        var body = sim.convoy().body(carrier);
        int x = targetX(), y = targetY();
        boolean company = false, crew = false, hostile = false, atEgress = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            int ux = sim.world().cellX(id), uy = sim.world().cellY(id);
            double distance = Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y);
            boolean inside = before == Phase.EGRESS ? distance < 18
                    : LowQuay.insideRoom(sim.getTopology(), ux, uy, before == Phase.RECORDS);
            if (isCompany(sim, id)) {
                if (inside) company = true;
                if (Math.hypot(sim.world().x(id) - LowQuay.LANDING_X, sim.world().y(id) - LowQuay.LANDING_Y) < 14) atEgress = true;
            }
            if (sim.squad().squadId(id) == technician && inside) crew = true;
            if (sim.identity().type(id).combatant && Faction.MARINE.hostileTo(sim.identity().faction(id))
                    && (inside || distance < 13)) hostile = true;
        }
        boolean safe = company && !hostile && (before != Phase.CALIBRATION || crew);
        if (before == Phase.EGRESS && cargoAlive) {
            boolean cover = false, threat = false;
            for (int i = 0; i < sim.liveUnitCount(); i++) {
                long id = sim.liveUnitAt(i);
                double distance = Math.hypot(sim.world().x(id) - body.x, sim.world().y(id) - body.y);
                if (isCompany(sim, id) && distance < 22) cover = true;
                if (distance < 20 && sim.identity().type(id).combatant && Faction.MARINE.hostileTo(sim.identity().faction(id))
                        && sim.getGrid().hasLineOfSight((int)body.x, (int)body.y, sim.world().cellX(id), sim.world().cellY(id))) threat = true;
            }
            sim.convoy().mission(carrier).authoredRoutePaused = !cover || threat;
            int nearest = 0; double best = Double.MAX_VALUE;
            for (int i = 0; i < routeX.length; i++) {
                double distance = Math.hypot(routeX[i] - body.x, routeY[i] - body.y);
                if (distance < best) { best = distance; nearest = i; }
            }
            int ahead = Math.min(routeX.length - 1, nearest + 8);
            escortX = (int)routeX[ahead]; escortY = (int)routeY[ahead];
            safe = cover && !threat;
        }
        boolean companyInPlay = ObjectiveFactionPresence.anyInPlay(sim, Faction.MARINE, id -> isCompany(sim, id));
        boolean cargoAtEgress = Math.hypot(body.x - LowQuay.LANDING_X, body.y - LowQuay.LANDING_Y) < 4;
        progress.advance(BattleSimulation.TICK_DT, companyInPlay, sim.squadMemberCount(technician) > 0,
                cargoAlive, safe, cargoAtEgress, atEgress);
        if (progress.recordsRecovered() && !released && !progress.terminal()) release(sim);
        if (before != progress.phase()) emit(sim, progress.phase().name().toLowerCase(Locale.ROOT));
    }
    private void prepareRoute(BattleSimulation sim) {
        var mask = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(sim.convoy().vehicleType(carrier).visualWidthCells));
        int[] path = GridPathfinder.findPathOnDemand(sim.getGrid(), LowQuay.LOAD_X, LowQuay.LOAD_Y,
                LowQuay.LANDING_X, LowQuay.LANDING_Y, index -> 1f, mask::isPassableAt);
        if (path.length < 4) throw new IllegalStateException("Low Quay calibration load has no route to egress");
        routeX = new float[path.length / 2]; routeY = new float[path.length / 2];
        for (int i = 0; i < routeX.length; i++) { routeX[i] = path[2*i] + .5f; routeY[i] = path[2*i+1] + .5f; }
        var mission = sim.convoy().mission(carrier);
        mission.authoredRouteX = routeX; mission.authoredRouteY = routeY; mission.authoredRoutePaused = true;
    }
    private void release(BattleSimulation sim) {
        released = true; reserveSquad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        for (var member : reserve) sim.spawn(member.squad(reserveSquad));
        sim.getSquad(reserveSquad).originalSize = reserve.size();
        sim.claimSquadCommand(reserveSquad, CommandAuthority.MISSION_COMMAND, LimitedWarrantCommand.issuer(Faction.DEFENDER), "Committed Runner rearguard");
        log.emitOnce(sim.getSimTickIndex(), "limited_warrant.rearguard", StoryBeatLog.Audience.OBSERVER_ONLY);
    }
    private static boolean isCompany(BattleSimulation sim, long id) {
        return sim.identity().faction(id) == Faction.MARINE && switch (sim.identity().type(id)) {
            case MARINE, MARINE_BLUE, MARINE_RED -> true; default -> false;
        };
    }
    private void emit(BattleSimulation sim, String beat) { log.emitOnce(sim.getSimTickIndex(), "limited_warrant." + beat, StoryBeatLog.Audience.PLAYER); }
    public Faction owningFaction() { return Faction.MARINE; }
    public void tick(BattleView ignored) { }
    public boolean isComplete() { return progress.phase() == Phase.COMPLETE; }
    public boolean isFailed() { return progress.phase() == Phase.FAILED; }
    public String displayName() { return switch (progress.phase()) {
        case RECORDS -> "Authenticate the service archive inside the records office";
        case CALIBRATION -> "Escort the supplier's technician into the calibration cage";
        case EGRESS -> "Cover the calibration load back to the western egress";
        case COMPLETE -> "Service archive and calibration kit recovered";
        case FAILED -> "Recovery incomplete; replenish and commission a new mobilization";
    }; }
}
