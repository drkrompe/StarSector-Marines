package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.JunctionCommand;
import com.dillon.starsectormarines.battle.command.JunctionTactics;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.List;

/**
 * The Junction's counterattacks after the first gain (`early-operation-nouns.md`, Open the Junction), as two blows
 * rather than a trickle of squads, scripted on the same site triggers the raiders' commander reads
 * ({@link JunctionTactics}).
 *
 * <p><b>The first blow.</b> A garrison waits inside the bonded warehouse north of the main road, fighting whatever it
 * sees from there. When the reserve commits on the first gain, the garrison holds until the reserve reaches its
 * approach ({@link JunctionCommand.Picture#reserveArrived()}), or {@link #MUSTER_TICKS} at most, then sprints (on the
 * shared stamina, holding fire while it runs) into the first gain, so both hit it together, and digs in there.
 *
 * <p><b>The second blow.</b> When the Rill road column commits ({@link JunctionTactics#roadColumnCommitted()}), two
 * technicals wait at the main road's east mouth until the column closes on its target ({@link #RELEASE_RANGE}, or
 * {@link #MUSTER_TICKS} at most), then drive in behind it and park on the road beside the site ({@link RoadTechnical});
 * the garrison's survivors sprint from wherever they are to join it.
 */
public final class JunctionPressure {
    static final String ISSUER = "junction-pressure";
    /** Within this of its target's capture cell a sprinting raider stops sprinting and its squad digs in. */
    static final float ARRIVAL = 8f;
    /** The longest a blow waits for the slower group before going without it. */
    static final int MUSTER_TICKS = 30 * 30;
    /** The Rill column within this of its target releases the technicals and the garrison's survivors. */
    static final float RELEASE_RANGE = 25f;
    /** Ticks between the two technicals entering, so the second does not spawn on the first. */
    static final int TECHNICAL_STAGGER_TICKS = 150;
    /** The main road's mouth at the east edge, where the technicals enter. */
    static final int ROAD_MOUTH_X = 189, ROAD_MOUTH_Y = 81;
    /** The main road's row past the sites, where the technicals park. */
    static final int MAIN_ROAD_Y = 59;

    private final PointOfInterest warehouse;
    private final List<Integer> garrison;
    private final JunctionTactics tactics = new JunctionTactics();
    private final List<RoadTechnical> technicals = new ArrayList<>();
    private final List<Integer> dugIn = new ArrayList<>();
    private int targetX, targetY;
    private int reserveTick = -1, roadTick = -1, firstBlowTick = -1, secondBlowTick = -1;

    /** @param garrison the warehouse squads, already holding inside under {@link #ISSUER} */
    JunctionPressure(PointOfInterest warehouse, List<Integer> garrison) {
        this.warehouse = warehouse;
        this.garrison = List.copyOf(garrison);
    }

    /** Holds the warehouse garrison inside the hall. Setup, once each squad is spawned. */
    static void holdWarehouse(BattleSimulation sim, int squadId, PointOfInterest warehouse) {
        sim.assignSquadCommand(ObjectiveAssignment.defendArea(squadId, warehouse.interiorAnchorX, warehouse.interiorAnchorY,
                        Math.max(4, Math.min(warehouse.right - warehouse.left, warehouse.bottom - warehouse.top) / 2 - 1)),
                CommandAuthority.SCRIPTED, ISSUER, "hold the bonded warehouse");
    }

    public List<Integer> garrison() { return garrison; }
    /** Whether the warehouse garrison has gone out with the first blow. */
    public boolean sallied() { return firstBlowTick >= 0; }
    /** The tick the first blow launched, -1 before. */
    public int firstBlowTick() { return firstBlowTick; }
    /** The tick the second blow launched (technicals and garrison survivors released), -1 before. */
    public int secondBlowTick() { return secondBlowTick; }
    /** The technicals' hull ids, 0 for one not yet entered; empty before the second blow commits. */
    public List<Long> technicalIds() { return technicals.stream().map(RoadTechnical::id).toList(); }
    /** Each technical's {entryX, entryY, parkX, parkY}; empty before the second blow commits. */
    public List<int[]> technicalRoutes() {
        return technicals.stream().map(t -> new int[]{t.entryX(), t.entryY(), t.parkX(), t.parkY()}).toList();
    }
    public PointOfInterest warehouse() { return warehouse; }

    public void advance(BattleSimulation sim) {
        if (sim.isComplete()) return;
        var records = new ArrayList<>(sim.getCompoundService().getRecords());
        if (records.size() != JunctionCompound.SITE_NAMES.size()) return;
        // Capture cells resolve on first ask; ask before reading them.
        for (var record : records) sim.getCompoundService().captureZoneId(record, sim);
        int now = sim.getSimTickIndex();
        tactics.observe(records.stream().map(r -> r.state).toList(), now);
        if (reserveTick < 0 && tactics.reserveCommitted()) reserveTick = now;
        if (firstBlowTick < 0 && reserveTick >= 0 && (reserveArrived(sim) || now - reserveTick >= MUSTER_TICKS)) {
            var gain = records.get(tactics.firstSecured());
            strike(sim, gain.captureCellX, gain.captureCellY, "first blow: retake the first gain with the reserve");
            firstBlowTick = now;
        }
        if (roadTick < 0 && tactics.roadColumnCommitted()) {
            plan(sim, records.get(tactics.roadTarget()).captureCellX);
            roadTick = now;
        }
        if (secondBlowTick < 0 && roadTick >= 0) {
            var target = records.get(tactics.roadTarget());
            if (columnClosing(sim, target.captureCellX, target.captureCellY) || now - roadTick >= MUSTER_TICKS) {
                strike(sim, target.captureCellX, target.captureCellY, "second blow: join the Rill column");
                secondBlowTick = now;
            }
        }
        if (firstBlowTick >= 0) digIn(sim);
        for (int i = 0; i < technicals.size(); i++)
            if (secondBlowTick >= 0 && technicals.get(i).id() == 0L && now >= secondBlowTick + i * TECHNICAL_STAGGER_TICKS)
                technicals.get(i).enter(sim);
    }

    private static boolean reserveArrived(BattleSimulation sim) {
        var picture = sim.getCommander(Faction.DEFENDER) instanceof JunctionCommand command ? command.picture() : null;
        return picture != null && picture.reserveArrived();
    }

    /** Whether any squad the raiders' commander is sending at this cell is within release range of it. */
    private static boolean columnClosing(BattleSimulation sim, int x, int y) {
        String issuer = JunctionCommand.issuer(Faction.DEFENDER);
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.DEFENDER || sim.squadMemberCount(squad.id) == 0) continue;
            CommandDirective directive = sim.getSquadCommandDirective(squad.id);
            if (directive == null || !issuer.equals(directive.issuer()) || directive.assignment() == null) continue;
            var assignment = directive.assignment();
            if (assignment.targetCellX() != x || assignment.targetCellY() != y) continue;
            if (Math.hypot(squad.centroidX - x, squad.centroidY - y) <= RELEASE_RANGE) return true;
        }
        return false;
    }

    /** Sends every live garrison squad sprinting at the cell; each digs in on arrival. */
    private void strike(BattleSimulation sim, int x, int y, String reason) {
        targetX = x;
        targetY = y;
        dugIn.clear();
        for (int squadId : garrison) {
            if (sim.getSquad(squadId) == null || sim.squadMemberCount(squadId) == 0) continue;
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squadId, x, y), CommandAuthority.SCRIPTED, ISSUER, reason);
            sim.squadRun().runScripted(squadId, id -> Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y) > ARRIVAL);
        }
    }

    /** A striking squad that has arrived holds its target. */
    private void digIn(BattleSimulation sim) {
        for (int squadId : garrison) {
            if (dugIn.contains(squadId)) continue;
            Squad squad = sim.getSquad(squadId);
            if (squad == null || sim.squadMemberCount(squadId) == 0) { dugIn.add(squadId); continue; }
            if (Math.hypot(squad.centroidX - targetX, squad.centroidY - targetY) > ARRIVAL) continue;
            sim.squadRun().stopScripted(squadId);
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squadId, targetX, targetY, 6),
                    CommandAuthority.SCRIPTED, ISSUER, "hold the retaken site");
            dugIn.add(squadId);
        }
    }

    /** Two parks on the main road either side of the target site's column, each the nearest a hull clears. */
    private void plan(BattleSimulation sim, int siteX) {
        VehicleClearance lane = VehicleClearance.erode(sim.getGrid(),
                VehicleClearance.radiusForWidth(RoadTechnical.TYPE.visualWidthCells));
        int[] entry = BattleSetup.nearestClear(lane, new int[]{ROAD_MOUTH_X, ROAD_MOUTH_Y});
        for (int offset : new int[]{6, -6}) {
            int[] park = BattleSetup.nearestClear(lane, new int[]{siteX + offset, MAIN_ROAD_Y});
            technicals.add(new RoadTechnical(entry[0], entry[1], park[0], park[1]));
        }
    }
}
