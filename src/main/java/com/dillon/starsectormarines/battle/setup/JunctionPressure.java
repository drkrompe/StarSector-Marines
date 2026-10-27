package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.JunctionTactics;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.world.gen.JunctionCompound;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.List;

/**
 * The Junction's added resistance (`early-operation-nouns.md`, Open the Junction), scripted on the same site triggers
 * the raiders' commander reads ({@link JunctionTactics}).
 *
 * <p><b>The bonded warehouse.</b> A garrison waits inside the impound hall north of the main road, fighting whatever
 * it sees from there. When the company secures its first site it sallies: it sprints across the road (on the shared
 * stamina, holding fire while it runs) into the Freight Yard opposite, and defends it from there, retaking it if the
 * company holds it.
 *
 * <p><b>The road counterattack's armor.</b> When the company secures its second site, the column off the Rill road
 * goes in ({@link JunctionTactics#roadColumnCommitted()}) and two technicals drive in from the east edge down the main
 * road, parking on it beside the site the column is retaking ({@link RoadTechnical}).
 */
public final class JunctionPressure {
    static final String ISSUER = "junction-pressure";
    /** The site across the main road from the bonded warehouse: the Freight Yard. */
    static final int SALLY_SITE = 2;
    /** Within this of the Freight Yard's capture cell a sallying raider stops sprinting and its squad digs in. */
    static final float SALLY_ARRIVAL = 8f;
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
    private int sallyX, sallyY;
    private boolean sallied;
    private int technicalsSentTick = -1;

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
    /** Whether the warehouse garrison has gone across the road. */
    public boolean sallied() { return sallied; }
    /** The technicals' hull ids, 0 for one not yet entered; empty before the counterattack. */
    public List<Long> technicalIds() { return technicals.stream().map(RoadTechnical::id).toList(); }
    /** Each technical's {entryX, entryY, parkX, parkY}; empty before the counterattack. */
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
        tactics.observe(records.stream().map(r -> r.state).toList());
        if (!sallied && tactics.firstSecured() >= 0) sally(sim, records.get(SALLY_SITE).captureCellX, records.get(SALLY_SITE).captureCellY);
        if (sallied) digIn(sim);
        if (technicalsSentTick < 0 && tactics.roadColumnCommitted()) {
            var target = records.get(tactics.secondSecured());
            plan(sim, target.captureCellX);
            technicalsSentTick = sim.getSimTickIndex();
        }
        for (int i = 0; i < technicals.size(); i++)
            if (technicals.get(i).id() == 0L && sim.getSimTickIndex() >= technicalsSentTick + i * TECHNICAL_STAGGER_TICKS)
                technicals.get(i).enter(sim);
    }

    private void sally(BattleSimulation sim, int x, int y) {
        sallied = true;
        sallyX = x;
        sallyY = y;
        for (int squadId : garrison) {
            if (sim.getSquad(squadId) == null || sim.squadMemberCount(squadId) == 0) continue;
            sim.assignSquadCommand(ObjectiveAssignment.attackMove(squadId, x, y),
                    CommandAuthority.SCRIPTED, ISSUER, "sally across the road into the Freight Yard");
            sim.squadRun().runScripted(squadId, id -> Math.hypot(sim.world().x(id) - x, sim.world().y(id) - y) > SALLY_ARRIVAL);
        }
    }

    /** A sallying squad that has crossed holds the Freight Yard. */
    private void digIn(BattleSimulation sim) {
        for (int squadId : garrison) {
            if (dugIn.contains(squadId)) continue;
            Squad squad = sim.getSquad(squadId);
            if (squad == null || sim.squadMemberCount(squadId) == 0) { dugIn.add(squadId); continue; }
            if (Math.hypot(squad.centroidX - sallyX, squad.centroidY - sallyY) > SALLY_ARRIVAL) continue;
            sim.squadRun().stopScripted(squadId);
            sim.assignSquadCommand(ObjectiveAssignment.defendArea(squadId, sallyX, sallyY, 6),
                    CommandAuthority.SCRIPTED, ISSUER, "hold the Freight Yard");
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
