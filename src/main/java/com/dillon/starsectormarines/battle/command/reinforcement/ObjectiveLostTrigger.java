package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.nav.zone.NavigationZone;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * V2 reinforcement trigger: posts a
 * {@link ReinforcementRequest.Reason#OBJECTIVE_LOST} for any zone the
 * defender previously held that has now flipped to marine occupation
 * (≥ 1 marine present, 0 defenders). Rally hint = the lost zone's own
 * centroid — the defender comes from off-map and tries to retake it.
 *
 * <p>The "previously held" memory is built up incrementally: any zone
 * observed with at least one defender at any tick is recorded in
 * {@link #wasDefenderHeld}. This avoids snapshotting at battle start
 * (when defenders may still be settling into spawn positions) and
 * naturally handles late-acquired ground.
 *
 * <p>One-shot per zone (the lost zone is added to {@link #postedZones}
 * on fire and never cleared) — v1 has no recovery story. A flip-back
 * after retake won't refire; the second slice can replace the boolean
 * with a cooldown if needed.
 */
public final class ObjectiveLostTrigger implements ReinforcementTrigger {

    private final Set<Integer> wasDefenderHeld = new HashSet<>();
    private final Set<Integer> postedZones = new HashSet<>();

    @Override
    public void check(BattleView sim, Consumer<ReinforcementRequest> out) {
        ZoneGraph graph = sim.getZoneGraph();
        if (graph == null) return;

        Map<Integer, int[]> tally = new HashMap<>();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            Unit u = sim.liveUnitAt(i);
            int zoneId = graph.zoneIdAt(sim.world().cellX(u.entityId), sim.world().cellY(u.entityId));
            if (zoneId < 0) continue;
            int[] counts = tally.computeIfAbsent(zoneId, k -> new int[2]);
            if (u.faction == Faction.DEFENDER) counts[0]++;
            else if (u.faction == Faction.MARINE) counts[1]++;
        }

        for (Map.Entry<Integer, int[]> e : tally.entrySet()) {
            if (e.getValue()[0] > 0) wasDefenderHeld.add(e.getKey());
        }

        int gw = sim.getGrid().getWidth();
        for (Map.Entry<Integer, int[]> e : tally.entrySet()) {
            int zoneId = e.getKey();
            int defenders = e.getValue()[0];
            int marines = e.getValue()[1];
            if (defenders > 0) continue;
            if (marines <= 0) continue;
            if (!wasDefenderHeld.contains(zoneId)) continue;
            if (!postedZones.add(zoneId)) continue;

            NavigationZone zone = graph.zoneById(zoneId);
            if (zone == null) continue;
            int[] rally = zoneCentroid(zone, gw);
            if (rally[0] < 0) continue;

            out.accept(new ReinforcementRequest(
                    Faction.DEFENDER,
                    ReinforcementRequest.Reason.OBJECTIVE_LOST,
                    ReinforcementRequest.Strength.SMALL,
                    rally[0], rally[1]));
        }
    }

    /** Mean cell position over the zone's cells. {@code {-1,-1}} when the zone has no cells. Same shape as {@code ConquestCommand}'s centroid computation. */
    private static int[] zoneCentroid(NavigationZone zone, int gridWidth) {
        int[] cells = zone.getCellIndices();
        if (cells.length == 0) return new int[]{-1, -1};
        long sx = 0, sy = 0;
        for (int idx : cells) {
            sx += idx % gridWidth;
            sy += idx / gridWidth;
        }
        return new int[]{(int) (sx / cells.length), (int) (sy / cells.length)};
    }
}
