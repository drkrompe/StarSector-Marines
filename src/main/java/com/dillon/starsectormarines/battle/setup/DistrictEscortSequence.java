package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.DistrictEscortCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.DistrictEscortProgress;
import com.dillon.starsectormarines.battle.story.DistrictEscortProgress.Phase;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.world.gen.LowQuay;
import java.util.List;
import java.util.Locale;

/**
 * A finite two-squad escort over Low Quay's ordinary freight lane: link up with a fresh consignment
 * at the loading lane, then cover it and its handover party to the western apron. The route is the
 * carrier's own clearance path, solved once at setup, so the map may change without changing this.
 */
public final class DistrictEscortSequence implements Objective {
    /** Company and party within this distance of the apron count as arrived. */
    static final double APRON_RADIUS = 14;
    private final DistrictEscortProgress progress = new DistrictEscortProgress();
    private final int party, constables;
    private final long carrier;
    private final List<EntitySpec> reserve;
    private final float[] routeX, routeY;
    private final StoryBeatLog log;
    private boolean initialized, released;
    private int reserveSquad = -1, escortX, escortY, cargoX, cargoY, trailX, trailY;

    public DistrictEscortSequence(int party, int constables, long carrier, List<EntitySpec> reserve,
                                  float[] routeX, float[] routeY, StoryBeatLog log) {
        if (routeX == null || routeY == null || routeX.length != routeY.length || routeX.length < 2)
            throw new IllegalArgumentException("District Escort needs a solved carrier route");
        this.party = party; this.constables = constables; this.carrier = carrier;
        this.reserve = List.copyOf(reserve); this.log = log;
        this.routeX = routeX.clone(); this.routeY = routeY.clone();
        escortX = cargoX = trailX = Math.round(routeX[0]); escortY = cargoY = trailY = Math.round(routeY[0]);
    }

    /**
     * The carrier's clearance route from the loading lane to the western apron, as cell centres
     * {@code [x0, y0, x1, y1, ...]}; empty when the lane is not passable for the carrier.
     */
    public static float[] route(NavigationGrid grid, float visualWidthCells) {
        var mask = VehicleClearance.erode(grid, VehicleClearance.radiusForWidth(visualWidthCells));
        int[] path = GridPathfinder.findPathOnDemand(grid, LowQuay.LOAD_X, LowQuay.LOAD_Y,
                LowQuay.LANDING_X, LowQuay.LANDING_Y, index -> 1f, mask::isPassableAt);
        float[] out = new float[path.length];
        for (int i = 0; i < path.length; i++) out[i] = path[i] + .5f;
        return out;
    }

    public DistrictEscortProgress progress() { return progress; }
    public int partySquadId() { return party; }
    public int constableSquadId() { return constables; }
    public long carrierId() { return carrier; }
    public int reserveSquadId() { return reserveSquad; }
    public int pendingMembers() { return released || progress.terminal() ? 0 : reserve.size(); }
    /** Where the company should be: the carrier at link-up, a point ahead of it in escort. */
    public int targetX() { return escortX; }
    public int targetY() { return escortY; }
    /**
     * The lane's heading at the company's point, as a unit step {@code [dx, dy]}; squads stand
     * either side of it so the carrier is never driving into its own escort.
     */
    public float[] laneHeading() {
        int nearest = 0; double best = Double.MAX_VALUE;
        for (int i = 0; i < routeX.length; i++) {
            double distance = Math.hypot(routeX[i] - escortX, routeY[i] - escortY);
            if (distance < best) { best = distance; nearest = i; }
        }
        int from = Math.max(0, nearest - 3), to = Math.min(routeX.length - 1, nearest + 3);
        float dx = routeX[to] - routeX[from], dy = routeY[to] - routeY[from];
        float length = (float) Math.hypot(dx, dy);
        return length < 1e-3f ? new float[]{-1, 0} : new float[]{dx / length, dy / length};
    }
    /** Where the handover party walks: behind the carrier on its own route, never in its path. */
    public int trailX() { return trailX; }
    public int trailY() { return trailY; }
    /** The carrier's cell as of the last advance; disclosed to the company and its allies only. */
    public int cargoX() { return cargoX; }
    public int cargoY() { return cargoY; }
    /**
     * The raiders' ambush ground, the lane's exposed crossing: what they know, never the
     * carrier's live position or the company's.
     */
    public int ambushX() { return LowQuay.CROSS_X; }
    public int ambushY() { return LowQuay.CROSS_Y; }

    public DistrictEscortProgress.Report report(BattleSimulation sim) {
        if (sim.isForcedVictory()) return progress.wonReport();
        return progress.report(DistrictEscortProgress.PARTY - sim.squadMemberCount(party),
                DistrictEscortProgress.CONSTABLES - sim.squadMemberCount(constables));
    }

    public void advance(BattleSimulation sim) {
        if (sim.isComplete() || progress.terminal()) return;
        var mission = sim.convoy().mission(carrier);
        if (!initialized) {
            initialized = true;
            mission.authoredRouteX = routeX; mission.authoredRouteY = routeY; mission.authoredRoutePaused = true;
            emit(sim, "mobilization");
        }
        Phase before = progress.phase();
        boolean cargoAlive = mission.state != VehicleState.WRECKED;
        var body = sim.convoy().body(carrier);
        cargoX = Math.round(body.x); cargoY = Math.round(body.y);
        boolean cover = false, threat = false, companyAtApron = false, partyAtApron = false, ashore = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            double toCargo = Math.hypot(sim.world().x(id) - body.x, sim.world().y(id) - body.y);
            double toApron = Math.hypot(sim.world().x(id) - LowQuay.LANDING_X, sim.world().y(id) - LowQuay.LANDING_Y);
            if (isCompany(sim, id)) {
                ashore = true;
                if (toCargo < 22) cover = true;
                if (toApron < APRON_RADIUS) companyAtApron = true;
            }
            if (sim.squad().squadId(id) == party && toApron < APRON_RADIUS) partyAtApron = true;
            if (toCargo < 20 && sim.identity().type(id).combatant && Faction.MARINE.hostileTo(sim.identity().faction(id))
                    && sim.getGrid().hasLineOfSight((int) body.x, (int) body.y, sim.world().cellX(id), sim.world().cellY(id)))
                threat = true;
        }
        boolean safe = cover && !threat;
        if (before == Phase.ESCORT) {
            mission.authoredRoutePaused = !safe;
            int nearest = 0; double best = Double.MAX_VALUE;
            for (int i = 0; i < routeX.length; i++) {
                double distance = Math.hypot(routeX[i] - body.x, routeY[i] - body.y);
                if (distance < best) { best = distance; nearest = i; }
            }
            int ahead = Math.min(routeX.length - 1, nearest + 12), behind = Math.max(0, nearest - 5);
            escortX = Math.round(routeX[ahead]); escortY = Math.round(routeY[ahead]);
            trailX = Math.round(routeX[behind]); trailY = Math.round(routeY[behind]);
        } else {
            escortX = trailX = Math.round(body.x); escortY = trailY = Math.round(body.y);
        }
        boolean companyInPlay = ObjectiveFactionPresence.anyInPlay(sim, Faction.MARINE, id -> isCompany(sim, id));
        boolean cargoAtEgress = Math.hypot(body.x - LowQuay.LANDING_X, body.y - LowQuay.LANDING_Y) < 4;
        progress.advance(BattleSimulation.TICK_DT, companyInPlay, Math.min(DistrictEscortProgress.PARTY, sim.squadMemberCount(party)),
                cargoAlive, safe, cargoAtEgress, partyAtApron, companyAtApron);
        // The raiders see the company come down and move onto the lane; they never learn the load.
        if (ashore && !released && !progress.terminal()) release(sim);
        if (before != progress.phase()) emit(sim, progress.phase().name().toLowerCase(Locale.ROOT));
    }

    private void release(BattleSimulation sim) {
        released = true;
        reserveSquad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        for (var member : reserve) sim.spawn(member.squad(reserveSquad));
        sim.getSquad(reserveSquad).originalSize = reserve.size();
        sim.claimSquadCommand(reserveSquad, CommandAuthority.MISSION_COMMAND, DistrictEscortCommand.issuer(Faction.DEFENDER),
                "Freight-lane raiders commit");
        log.emitOnce(sim.getSimTickIndex(), "district_escort.raiders", StoryBeatLog.Audience.OBSERVER_ONLY);
    }

    private static boolean isCompany(BattleSimulation sim, long id) {
        return sim.identity().faction(id) == Faction.MARINE && switch (sim.identity().type(id)) {
            case MARINE, MARINE_BLUE, MARINE_RED -> true; default -> false;
        };
    }
    private void emit(BattleSimulation sim, String beat) {
        log.emitOnce(sim.getSimTickIndex(), "district_escort." + beat, StoryBeatLog.Audience.PLAYER);
    }
    public Faction owningFaction() { return Faction.MARINE; }
    public void tick(BattleView ignored) { }
    public boolean isComplete() { return progress.phase() == Phase.COMPLETE; }
    public boolean isFailed() { return progress.phase() == Phase.FAILED; }
    public String displayName() { return switch (progress.phase()) {
        case LINK_UP -> Strings.get("districtEscortLinkUp");
        case ESCORT -> Strings.get("districtEscortEscort");
        case COMPLETE -> Strings.get("districtEscortComplete");
        case FAILED -> Strings.get("districtEscortFailed");
    }; }
}
