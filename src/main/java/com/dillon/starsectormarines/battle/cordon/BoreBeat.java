package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.evacuation.BoreTier;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.optional.ObjectiveRuntime.Status;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;

import java.util.List;

/**
 * A bore that opens where a beat says (L4, A bore behind the line; R5, A maw on the road;
 * {@code cordon-nouns.md}, Optional objectives), through the ordinary bore seam the sequence's eruptions
 * use: it opens as a fissure and grows as any bore does. Succeeds when it is collapsed while still a
 * fissure; fails once it grows to a bore, the second tier, about three minutes on. On the Road a failed
 * maw closes the corridor beside it: the convoys that set out after take a detour that keeps clear of it,
 * through the second road where that is the way.
 */
final class BoreBeat extends CordonBeat {

    /** L4's bore behind the line: between the second and third tides. */
    static final int BEHIND_SECONDS = 540;
    /** R5's maw beside the road: between the second and third convoys. */
    static final int ROAD_SECONDS = 330;
    /** Corridor cells past the gap R5's maw stands beside, and how far off the road. */
    static final int ROAD_PAST_GAP = 25, ROAD_ASIDE = 16;
    /** Cells round a grown maw the detour keeps clear of. */
    static final int DETOUR_CLEAR = 22;

    private final boolean road;
    private final int openSeconds;
    private int number = -1;
    private int[] site;
    private boolean opened;

    BoreBeat(CordonBeats beats, boolean road) {
        super(beats);
        this.road = road;
        this.openSeconds = road ? ROAD_SECONDS : BEHIND_SECONDS;
    }

    /** The rule: collapsed while a fissure succeeds, grown past it fails. */
    static Status rule(boolean opened, boolean collapsed, BoreTier tier) {
        if (!opened) return Status.ACTIVE;
        if (tier != null && tier.ordinal() >= BoreTier.BORE.ordinal()) return Status.FAILED;
        return collapsed ? Status.SUCCEEDED : Status.ACTIVE;
    }

    @Override
    protected Status step(BattleSimulation sim, int tick) {
        if (number < 0) {
            if (tick < CordonSequence.ticks(openSeconds)) return Status.ACTIVE;
            site = road ? roadSite(sim) : beats.centre(CordonPlan.ERUPTION_SITE);
            if (site == null) return Status.LAPSED;
            number = beats.sequence().openBeatBore(sim, site[0], site[1]);
        }
        SwarmBore bore = null;
        for (SwarmBore b : sim.swarmBores().bores()) if (!b.mother() && b.number() == number) bore = b;
        opened = bore != null;
        Status status = rule(opened, opened && bore.collapsed(), opened ? bore.tier() : null);
        if (status == Status.FAILED && road) detour(sim, bore);
        return status;
    }

    /** Beside the corridor a stretch past the gap, on the side the second road crosses. */
    private int[] roadSite(BattleSimulation sim) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null) return null;
        List<int[]> corridor = convoys.corridor();
        int at = Math.min(corridor.size() - 1, convoys.gapIndex() + ROAD_PAST_GAP);
        int[] a = corridor.get(Math.max(0, at - 3)), b = corridor.get(Math.min(corridor.size() - 1, at + 3));
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float length = (float) Math.max(1e-3, Math.sqrt(dx * dx + dy * dy));
        int[] second = beats.centre(CordonPlan.SECOND_ROAD);
        int[] c = corridor.get(at);
        int side = 1;
        if (second != null) {
            float px = -dy / length, py = dx / length;
            side = (second[0] - c[0]) * px + (second[1] - c[1]) * py >= 0 ? 1 : -1;
        }
        int x = Math.round(c[0] - dy / length * ROAD_ASIDE * side), y = Math.round(c[1] + dx / length * ROAD_ASIDE * side);
        int[] open = CordonGround.nearestWalkable(sim.getGrid(), x, y);
        return open;
    }

    /** The convoys' road round the grown maw, when there is one. */
    private void detour(BattleSimulation sim, SwarmBore bore) {
        CordonConvoys convoys = beats.sequence().convoys();
        if (convoys == null || bore == null) return;
        List<int[]> road = detourAround(sim.getGrid(), sim, bore.x(), bore.y());
        if (road == null) return;
        int[] oldEntry = convoys.corridor().get(convoys.entry());
        convoys.detour(road, gapIndex(road), nearestIndex(road, oldEntry));
    }

    /** The corridor found again with the ground round {@code (x, y)} closed, or {@code null}. */
    private List<int[]> detourAround(NavigationGrid grid, BattleSimulation sim, int x, int y) {
        NavigationGrid copy = grid.copyRoutingTopology();
        for (int cy = y - DETOUR_CLEAR; cy <= y + DETOUR_CLEAR; cy++) {
            for (int cx = x - DETOUR_CLEAR; cx <= x + DETOUR_CLEAR; cx++) {
                if (copy.inBounds(cx, cy) && (cx - x) * (cx - x) + (cy - y) * (cy - y) <= DETOUR_CLEAR * DETOUR_CLEAR) {
                    copy.setWalkable(cx, cy, false);
                }
            }
        }
        return CordonCorridor.find(copy, sim.getTopology(), beats.plan());
    }

    private int gapIndex(List<int[]> road) {
        float line = CordonFront.lineY(beats.plan());
        int frontward = CordonFront.frontward(beats.plan());
        for (int i = 0; i < road.size(); i++) if ((road.get(i)[1] - line) * frontward >= 0) return i;
        return road.size() - 1;
    }

    private static int nearestIndex(List<int[]> road, int[] cell) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < road.size(); i++) {
            long dx = road.get(i)[0] - cell[0], dy = road.get(i)[1] - cell[1];
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                best = i;
            }
        }
        return best;
    }

    @Override
    String describe(BattleSimulation sim) {
        if (site == null) return "";
        for (SwarmBore b : sim.swarmBores().bores()) {
            if (!b.mother() && b.number() == number) {
                return "bore " + number + " at " + b.x() + "," + b.y() + " " + b.tier().beatName
                        + (b.collapsed() ? " collapsed" : "");
            }
        }
        return "bore " + number + " waiting for clear ground at " + site[0] + "," + site[1];
    }

    @Override
    int[] call(BattleSimulation sim) {
        return site == null ? null : new int[]{site[0], site[1], 3};
    }

    /** The bore's site once chosen; before that, the set piece it opens on. */
    @Override
    int[] place(BattleSimulation sim) {
        return site == null ? null : new int[]{site[0], site[1]};
    }
}
