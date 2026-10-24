package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * Broad Front's supply road (`heart-story-beats.md` F6): the one metalled road, up which the logistics base's fresh
 * armour and ammunition come. The satellite placed beside it ({@link HeartBores#ROAD_REACH}) <b>cuts</b> it whenever a
 * swarm body stands on the road while that satellite stands; a cut holds {@link #HOLD_SECONDS} past the last body seen,
 * so a surge crossing it reads as one cut. While the road is cut the base restocks none of what comes up it
 * ({@code AlliedToolShed.suppliedWhile}), and the gunship counts the satellite as a target worth a sortie. Taking the
 * satellite opens the road for good. Each cut is marked {@code road_cut_<n>}.
 */
public final class HeartSupplyRoad {

    /** Seconds a cut holds past the last swarm body seen on the road. */
    static final float HOLD_SECONDS = 10f;

    private final List<int[]> cells;
    private boolean[] mask;
    private int cutUntil = -1;
    private boolean cut;
    private int cuts;

    /** @param cells the road's cells as the map laid them; none makes a road that is never cut */
    public HeartSupplyRoad(List<int[]> cells) {
        this.cells = cells == null ? List.of() : cells;
    }

    public List<int[]> cells() { return cells; }

    /** Whether the road is open now (as of the last {@link #tick}). */
    public boolean open() { return !cut; }

    /** Whether the road is cut now (as of the last {@link #tick}). */
    public boolean cut() { return cut; }

    /** Cuts so far. */
    public int cuts() { return cuts; }

    /** One tick: reads whether the satellite's swarm is on the road, marking a new cut in {@code beats}. */
    void tick(BattleSimulation sim, List<String> beats) {
        int tick = sim.getSimTickIndex();
        if (satellite(sim) != null && swarmOn(sim)) cutUntil = tick + Math.round(HOLD_SECONDS / BattleSimulation.TICK_DT);
        boolean now = tick <= cutUntil && satellite(sim) != null;
        if (now && !cut) {
            cuts++;
            beats.add("road_cut_" + cuts);
        }
        cut = now;
    }

    /** The standing satellite within {@link HeartBores#ROAD_REACH} of the road, the one that cuts it; null when none is. */
    SwarmBore satellite(BattleSimulation sim) {
        SwarmBore nearest = cells.isEmpty() ? null : HeartObjectives.nearestToRoad(sim, cells);
        if (nearest == null) return null;
        for (int[] cell : cells) {
            if (Math.hypot(cell[0] - nearest.x(), cell[1] - nearest.y()) <= HeartBores.ROAD_REACH) return nearest;
        }
        return null;
    }

    /** Whether a swarm body stands on the road now. */
    boolean swarmOn(BattleSimulation sim) {
        if (cells.isEmpty()) return false;
        int width = sim.getGrid().getWidth();
        if (mask == null) {
            mask = new boolean[width * sim.getGrid().getHeight()];
            for (int[] cell : cells) mask[cell[1] * width + cell[0]] = true;
        }
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.DEFENDER || sim.identity().type(unit).isTurret()) continue;
            int x = (int) sim.world().x(unit), y = (int) sim.world().y(unit);
            if (x >= 0 && x < width && y >= 0 && y * width + x < mask.length && mask[y * width + x]) return true;
        }
        return false;
    }
}
