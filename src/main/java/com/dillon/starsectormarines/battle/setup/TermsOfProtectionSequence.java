package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.TermsOfProtectionCommand;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.ObjectiveFactionPresence;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Phase;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Route;
import com.dillon.starsectormarines.marine.ArmorClass;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Referee for Terms of Protection: a physical custody party under escort, a signing at the
 * dispatch handover bay, technician work in the calibration room, and Berth Four's finite assault.
 *
 * <p>Every enemy group is authored before launch with its ingress and commitment condition. Reed's
 * two assault sections and his mortar team commit when the commissioning work is first seen to
 * be under way; the prize-crew party comes down the Cinder Steps lane a fixed interval later. A
 * group resolves through confirmed losses or its survivors' physical withdrawal off the Cinder
 * Steps lane; the mortar team withdraws only once both assaults it supports have been spent.
 * Warnings are story beats raised by actual sightings and reports, never by commitment itself.
 */
public final class TermsOfProtectionSequence implements Objective {
    /** Commissioning seconds before Reed's people commit: the work has to be under way to be worth stopping. */
    public static final float PELL_COMMITS_AFTER_WORK = 10f;
    /** Ticks between Reed's commitment and the prize crews on the Cinder Steps lane. */
    public static final int LANE_PARTY_DELAY_TICKS = 900;
    /** Fire-mission radius the mortar team serves around the tooling hall. */
    public static final int MORTAR_MISSION_RADIUS = 18;

    /** The authored enemy force, in commitment order. */
    public record Reserve(List<List<EntitySpec>> pellSections, List<EntitySpec> mortarTeam, List<EntitySpec> laneParty) {
        public Reserve {
            pellSections = pellSections.stream().map(List::copyOf).toList();
            mortarTeam = List.copyOf(mortarTeam); laneParty = List.copyOf(laneParty);
        }
        public int size() { return pellSections.stream().mapToInt(List::size).sum() + mortarTeam.size() + laneParty.size(); }
    }

    /** Employer parties by squad id; a route's absent party is {@code -1}. */
    public record Parties(int representatives, int technicians, int fitters, int guards, int constables) { }

    private final TermsOfProtectionProgress progress;
    private final long carrier;
    private final Parties parties;
    private final Reserve reserve;
    private final StoryBeatLog log;
    private final List<Integer> pellSquads = new ArrayList<>();
    private int mortarSquad = -1, laneSquad = -1, pellReleasedTick = -1;
    private final Set<Long> escaped = new HashSet<>();
    private boolean initialized, mortarReported, mortarSighted, mortarWithdrawing, pellSighted, laneSighted, fieldClear;
    private int originalFitters, originalGuards, originalConstables;
    private float[] routeX, routeY;
    private int escortX = LatchkeyYard.PARTY_START.x(), escortY = LatchkeyYard.PARTY_START.y();

    public TermsOfProtectionSequence(Route route, long carrier, Parties parties, Reserve reserve, StoryBeatLog log) {
        this.progress = new TermsOfProtectionProgress(route);
        this.carrier = carrier; this.parties = parties; this.reserve = reserve; this.log = log;
        if (reserve.pellSections().size() != 2) throw new IllegalArgumentException("Reed commits two assault sections");
        if ((route == Route.HOUSE_CUSTODY) != (parties.guards() >= 0) || (route == Route.YARD_COMPACT) != (parties.fitters() >= 0))
            throw new IllegalArgumentException("Each route brings its own party and no other");
    }

    public TermsOfProtectionProgress progress() { return progress; }
    public Route route() { return progress.route(); }
    public long carrierId() { return carrier; }
    public Parties parties() { return parties; }
    public List<Integer> pellSquadIds() { return List.copyOf(pellSquads); }
    public int mortarSquadId() { return mortarSquad; }
    public int laneSquadId() { return laneSquad; }
    /** Whether incoming mortar rounds have actually been observed falling. */
    public boolean mortarReported() { return mortarReported; }
    /** Whether the player's own observation has found the mortar team. */
    public boolean mortarSighted() { return mortarSighted; }
    public boolean mortarWithdrawing() { return mortarWithdrawing; }
    /** Whether the player has actually seen Reed's assault, or the prize crews on the lane. */
    public boolean pellSighted() { return pellSighted; }
    public boolean laneSighted() { return laneSighted; }
    /** Public referee fact shown on the mission picture: every committed group has been resolved. */
    public boolean fieldClear() { return fieldClear; }
    public int escortRallyX() { return escortX; }
    public int escortRallyY() { return escortY; }
    public boolean pellCommitted() { return pellReleasedTick >= 0; }
    public boolean laneCommitted() { return laneSquad >= 0; }
    public int escapedCount() { return escaped.size(); }

    public int pendingMembers() {
        if (progress.terminal()) return 0;
        int pending = 0;
        if (pellReleasedTick < 0) pending += reserve.pellSections().stream().mapToInt(List::size).sum() + reserve.mortarTeam().size();
        if (laneSquad < 0) pending += reserve.laneParty().size();
        return pending;
    }

    /** Whether every committed group is spent: all released, and nobody of theirs left on the field. */
    public boolean assaultsResolved(BattleSimulation sim) {
        if (pellReleasedTick < 0 || laneSquad < 0) return false;
        return reserveSquads().stream().allMatch(id -> sim.squadMemberCount(id) == 0);
    }

    private List<Integer> reserveSquads() {
        var all = new ArrayList<>(pellSquads);
        if (mortarSquad >= 0) all.add(mortarSquad);
        if (laneSquad >= 0) all.add(laneSquad);
        return all;
    }

    public TermsOfProtectionProgress.Report report(BattleSimulation sim) {
        if (sim.isForcedVictory()) return progress.wonReport();
        boolean carrierLost = !carrierAlive(sim);
        return progress.report(carrierLost,
                TermsOfProtectionProgress.REPRESENTATIVES - sim.squadMemberCount(parties.representatives()),
                TermsOfProtectionProgress.TECHNICIANS - sim.squadMemberCount(parties.technicians()),
                parties.fitters() < 0 ? 0 : originalFitters - sim.squadMemberCount(parties.fitters()),
                parties.guards() < 0 ? 0 : originalGuards - sim.squadMemberCount(parties.guards()),
                originalConstables - sim.squadMemberCount(parties.constables()),
                assaultsResolved(sim));
    }

    public void advance(BattleSimulation sim) {
        if (sim.isComplete() || progress.terminal()) return;
        if (!initialized) {
            initialized = true;
            originalFitters = parties.fitters() < 0 ? 0 : sim.squadMemberCount(parties.fitters());
            originalGuards = parties.guards() < 0 ? 0 : sim.squadMemberCount(parties.guards());
            originalConstables = sim.squadMemberCount(parties.constables());
            prepareRoute(sim);
            emit(sim, "mobilization");
        }
        Phase before = progress.phase();
        boolean carrierAlive = carrierAlive(sim);
        boolean carrierAtHandover = false;
        if (carrierAlive) carrierAtHandover = driveEscort(sim, before == Phase.ESCORT);
        boolean repAtHandover = false, techAtWork = false, companyAtDispatch = false, dispatchContested = false;
        boolean companyInHall = false, hallContested = false;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            Faction side = sim.identity().faction(id);
            int x = sim.world().cellX(id), y = sim.world().cellY(id);
            boolean dispatch = LatchkeyYard.inside(sim.getTopology(), x, y, 0)
                    || Math.hypot(sim.world().x(id) - LatchkeyYard.HANDOVER.x(), sim.world().y(id) - LatchkeyYard.HANDOVER.y()) < 6;
            boolean hall = LatchkeyYard.inside(sim.getTopology(), x, y, 1);
            int squad = sim.squad().hasSquad(id) ? sim.squad().squadId(id) : -1;
            if (squad == parties.representatives() && dispatch) repAtHandover = true;
            if (squad == parties.technicians() && LatchkeyYard.insideCalibrationRoom(x, y)) techAtWork = true;
            if (isCompany(sim, id)) { companyAtDispatch |= dispatch; companyInHall |= hall; }
            if (Faction.MARINE.hostileTo(side) && sim.identity().type(id).combatant) {
                dispatchContested |= dispatch;
                hallContested |= hall;
            }
        }
        observeWarnings(sim);
        observeExits(sim);
        boolean resolved = assaultsResolved(sim);
        fieldClear = resolved;
        progress.advance(BattleSimulation.TICK_DT, new TermsOfProtectionProgress.Observation(
                ObjectiveFactionPresence.anyInPlay(sim, Faction.MARINE, id -> isCompany(sim, id)), carrierAlive, carrierAtHandover,
                sim.squadMemberCount(parties.representatives()), repAtHandover, sim.squadMemberCount(parties.technicians()),
                techAtWork, companyAtDispatch, dispatchContested, companyInHall, hallContested, resolved));
        if (!progress.terminal()) {
            if (pellReleasedTick < 0 && progress.work() >= PELL_COMMITS_AFTER_WORK) releasePell(sim);
            if (pellReleasedTick >= 0 && laneSquad < 0 && sim.getSimTickIndex() - pellReleasedTick >= LANE_PARTY_DELAY_TICKS) releaseLane(sim);
            boolean supported = pellSquads.stream().anyMatch(id -> sim.squadMemberCount(id) > 0)
                    || laneSquad >= 0 && sim.squadMemberCount(laneSquad) > 0 || laneSquad < 0;
            if (mortarSquad >= 0 && !mortarWithdrawing && !supported && sim.squadMemberCount(mortarSquad) > 0) {
                mortarWithdrawing = true;
                log.emitOnce(sim.getSimTickIndex(), "terms_of_protection.mortar_withdraws", StoryBeatLog.Audience.OBSERVER_ONLY);
            }
        }
        Phase after = progress.phase();
        if (before != after) emit(sim, after.name().toLowerCase(Locale.ROOT));
    }

    /** Raises a warning only on what was actually seen or reported, and only once. */
    private void observeWarnings(BattleSimulation sim) {
        for (int squad : pellSquads) for (int m = 0; m < sim.squadMemberCount(squad); m++) {
            long id = sim.squadMemberAt(squad, m);
            if (!sim.playerSeesCell(sim.world().cellX(id), sim.world().cellY(id))) continue;
            pellSighted = true; emit(sim, "pell_sighted");
            if (sim.identity().armorClass(id) == ArmorClass.POWER_SUIT) emit(sim, "rigs_sighted");
        }
        if (laneSquad >= 0) for (int m = 0; m < sim.squadMemberCount(laneSquad); m++) {
            long id = sim.squadMemberAt(laneSquad, m);
            if (sim.playerSeesCell(sim.world().cellX(id), sim.world().cellY(id))) { laneSighted = true; emit(sim, "lane_sighted"); break; }
        }
        if (parties.guards() >= 0 && laneSquad >= 0 && sim.squadMemberCount(parties.guards()) > 0
                && sim.getSquad(parties.guards()).timeSinceUnderFire < 1f) emit(sim, "guards_contact");
        if (mortarSquad >= 0) {
            if (!mortarReported && sim.fieldMortars().tubes().stream()
                    .anyMatch(tube -> tube.squadId == mortarSquad && tube.roundsFired() > 0)) {
                mortarReported = true; emit(sim, "mortar_incoming");
            }
            if (!mortarSighted) for (int m = 0; m < sim.squadMemberCount(mortarSquad); m++) {
                long id = sim.squadMemberAt(mortarSquad, m);
                if (sim.playerSeesCell(sim.world().cellX(id), sim.world().cellY(id))) { mortarSighted = true; emit(sim, "mortar_sighted"); break; }
            }
        }
    }

    /** Survivors who reach the Cinder Steps lane's map edge under a withdrawal leave the field, not the record. */
    private void observeExits(BattleSimulation sim) {
        var leaving = new ArrayList<Long>();
        for (int squad : reserveSquads()) {
            boolean withdrawing = squad == mortarSquad ? mortarWithdrawing : false;
            if (!withdrawing) continue;
            for (int m = 0; m < sim.squadMemberCount(squad); m++) {
                long id = sim.squadMemberAt(squad, m);
                if (atLaneExit(sim.world().x(id), sim.world().y(id))) leaving.add(id);
            }
        }
        for (long id : leaving) { escaped.add(id); sim.takeOffTheField(id); }
    }

    public static boolean atLaneExit(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y) && x >= LatchkeyYard.WIDTH - 6 && y >= 38 && y <= 48;
    }

    private void releasePell(BattleSimulation sim) {
        pellReleasedTick = sim.getSimTickIndex();
        for (var section : reserve.pellSections()) pellSquads.add(commit(sim, section, "Berth Four assault section"));
        mortarSquad = commit(sim, reserve.mortarTeam(), "Berth Four mortar team");
        log.emitOnce(sim.getSimTickIndex(), "terms_of_protection.pell_commits", StoryBeatLog.Audience.OBSERVER_ONLY);
    }

    private void releaseLane(BattleSimulation sim) {
        laneSquad = commit(sim, reserve.laneParty(), "Prize crew party on the Cinder Steps lane");
        log.emitOnce(sim.getSimTickIndex(), "terms_of_protection.lane_commits", StoryBeatLog.Audience.OBSERVER_ONLY);
    }

    private static int commit(BattleSimulation sim, List<EntitySpec> group, String why) {
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.MILITIA);
        for (var member : group) sim.spawn(member.squad(squad));
        sim.getSquad(squad).originalSize = group.size();
        sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, TermsOfProtectionCommand.issuer(Faction.DEFENDER), why);
        return squad;
    }

    private boolean carrierAlive(BattleSimulation sim) {
        return sim.convoy().isVehicle(carrier) && sim.convoy().mission(carrier).state != VehicleState.WRECKED;
    }

    private void prepareRoute(BattleSimulation sim) {
        var body = sim.convoy().body(carrier);
        var mask = VehicleClearance.erode(sim.getGrid(), VehicleClearance.radiusForWidth(sim.convoy().vehicleType(carrier).visualWidthCells));
        int[] path = GridPathfinder.findPathOnDemand(sim.getGrid(), (int) body.x, (int) body.y,
                LatchkeyYard.HANDOVER.x(), HANDOVER_ROAD_Y, index -> 1f, mask::isPassableAt);
        if (path.length < 4) throw new IllegalStateException("Latchkey custody carrier has no cleared route to dispatch");
        var mission = sim.convoy().mission(carrier);
        mission.authoredRouteX = new float[path.length / 2]; mission.authoredRouteY = new float[path.length / 2];
        for (int i = 0; i < path.length / 2; i++) { mission.authoredRouteX[i] = path[i * 2] + .5f; mission.authoredRouteY[i] = path[i * 2 + 1] + .5f; }
        routeX = mission.authoredRouteX; routeY = mission.authoredRouteY;
        mission.authoredRoutePaused = true;
    }

    /** Freight-road cell beside the handover bay where the carrier stops. */
    public static final int HANDOVER_ROAD_Y = 59;

    /** Ordinary convoy movement: the carrier rolls only with company escort and no hostile in sight of it. */
    private boolean driveEscort(BattleSimulation sim, boolean escorting) {
        var body = sim.convoy().body(carrier); var mission = sim.convoy().mission(carrier);
        boolean arrived = Math.hypot(body.x - LatchkeyYard.HANDOVER.x(), body.y - HANDOVER_ROAD_Y) < 4;
        if (!escorting) { mission.authoredRoutePaused = true; return arrived; }
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
        return arrived;
    }

    static boolean isCompany(BattleSimulation sim, long id) {
        if (sim.identity().faction(id) != Faction.MARINE) return false;
        return switch (sim.identity().type(id)) { case MARINE, MARINE_BLUE, MARINE_RED, HEAVY_MECH -> true; default -> false; };
    }

    private void emit(BattleSimulation sim, String beat) {
        log.emitOnce(sim.getSimTickIndex(), "terms_of_protection." + beat, StoryBeatLog.Audience.PLAYER);
    }

    public Faction owningFaction() { return Faction.MARINE; }
    public void tick(BattleView ignored) { }
    public boolean isComplete() { return progress.phase() == Phase.COMPLETE; }
    public boolean isFailed() { return progress.phase() == Phase.FAILED; }
    public String displayName() { return switch (progress.phase()) {
        case ESCORT -> Strings.get("termsOfProtectionEscort");
        case SIGNING -> Strings.get("termsOfProtectionSigning");
        case COMMISSIONING -> Strings.get("termsOfProtectionCommissioning");
        case DEFENCE -> Strings.get("termsOfProtectionDefence");
        case COMPLETE -> Strings.get("termsOfProtectionComplete");
        case FAILED -> switch (progress.failure()) {
            case REPRESENTATIVES_LOST -> Strings.get("termsOfProtectionRepresentativesLost");
            case CARRIER_LOST -> Strings.get("termsOfProtectionCarrierLost");
            case TECHNICIANS_LOST -> Strings.get("termsOfProtectionTechniciansLost");
            default -> Strings.get("termsOfProtectionFailed");
        };
    }; }
}
