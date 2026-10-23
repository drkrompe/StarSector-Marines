package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleClearance;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The allies' light armour on the line ({@code cordon-nouns.md}, Allied armour): cars and
 * technicals under the allied command, each given a redoubt of its own to fight beside.
 *
 * <p>A hull stands beside its redoubt, out past a side wall and a few cells back from the front:
 * from there its gun reaches across the ground in front of the wall. Behind the rear wall it was out
 * of reach of the front (a redoubt is deeper than a heavy machine gun's range) and shot at nothing.
 * Several on one redoubt take turns on the two sides, each a step further back.
 */
public final class CordonArmour {

    /** Ticks between looks at which redoubts are calling. */
    static final int REVIEW_TICKS = 30;
    /** Cells behind a redoubt's rear wall a carrier sets its squad down. */
    static final int BEHIND_WALL = 4;
    /** Cells out past a redoubt's side wall a hull answering its call stands. */
    static final int FLANK_OUT = 4;
    /** Cells back from the redoubt's front wall the first hull on a flank stands. */
    static final int FLANK_BACK = 5;
    /** Cells further back each later hull on the same flank stands. */
    static final int FLANK_STEP = 4;
    /** Cells a hull set down at setup may be moved to find open ground it fits on. */
    static final int SET_DOWN_REACH = 24;

    private final AlliedCommand command;
    private final int frontward;
    private final Map<Long, int[]> home = new HashMap<>();
    private final Map<Long, CordonRedoubt> answering = new LinkedHashMap<>();

    /** @param frontward which way the front lies from the line: +1 toward high y, -1 toward low */
    public CordonArmour(AlliedCommand command, int frontward) {
        this.command = command;
        this.frontward = frontward;
    }

    public AlliedCommand command() { return command; }

    /**
     * Takes a hull standing on the field under the allied command, with a home it goes back to
     * between calls: a car or technical rolled out of the base's shed.
     */
    public void adopt(BattleSimulation sim, long hull, int homeX, int homeY) {
        command.enlist(sim, hull);
        home.put(hull, new int[]{homeX, homeY});
    }

    /** Sets a hull down at the nearest open ground to a cell; that place is its home between calls. */
    public long deploy(BattleSimulation sim, VehicleType type, int x, int y) {
        int[] cell = drivable(sim, type, x, y);
        if (cell == null) return 0L;
        long hull = command.deploy(sim, type, cell[0], cell[1], frontward > 0 ? 0f : 180f);
        home.put(hull, cell);
        return hull;
    }

    /** The nearest cell to {@code (x, y)} a hull of this type stands clear on, out to {@link #SET_DOWN_REACH}. */
    static int[] drivable(BattleSimulation sim, VehicleType type, int x, int y) {
        int radius = VehicleClearance.radiusForWidth(type.visualWidthCells);
        for (int r = 0; r <= SET_DOWN_REACH; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int cx = x + dx, cy = y + dy;
                    if (sim.getGrid().inBounds(cx, cy) && VehicleClearance.fitsAt(sim.getGrid(), cx, cy, radius)) {
                        return new int[]{cx, cy};
                    }
                }
            }
        }
        return null;
    }

    /**
     * One tick: keeps each hull on the flank of its own redoubt. A hull without one, or whose
     * redoubt fell, is given the standing redoubt with the fewest hulls (a calling one first, then the
     * nearest); with none standing it goes home.
     *
     * <p>Sectors, not a reserve that chases calls. On a front hundreds of cells wide a hull sent to
     * whichever redoubt called last spent the battle driving, mostly to calls that had ended by the
     * time it came; one standing beside a redoubt is there when the swarm comes.
     */
    public void advance(BattleSimulation sim, List<CordonRedoubt> redoubts) {
        command.advance(sim);
        if (sim.getSimTickIndex() % REVIEW_TICKS != 0) return;
        List<Long> hulls = new ArrayList<>(command.hulls());
        home.keySet().retainAll(hulls);
        answering.keySet().retainAll(hulls);
        answering.values().removeIf(r -> r.state() == CordonRedoubt.State.FALLEN);
        for (long hull : hulls) {
            if (command.carrying(hull) || !home.containsKey(hull)) continue;
            CordonRedoubt sector = answering.get(hull);
            if (sector == null) {
                sector = emptiest(sim.convoy().body(hull), redoubts);
                if (sector != null) answering.put(hull, sector);
            }
            int[] post = sector == null ? home.get(hull) : flank(sector, rankIn(hull, sector));
            command.station(sim, hull, post[0], post[1]);
        }
    }

    /** A hull's place among its sector's hulls, in the order they were enlisted. */
    private int rankIn(long hull, CordonRedoubt sector) {
        int rank = 0;
        for (Map.Entry<Long, CordonRedoubt> e : answering.entrySet()) {
            if (e.getKey() == hull) return rank;
            if (e.getValue() == sector) rank++;
        }
        return rank;
    }

    /** The standing redoubt with the fewest hulls; a calling one, then the nearest, breaks a tie. */
    private CordonRedoubt emptiest(GroundBody body, List<CordonRedoubt> redoubts) {
        CordonRedoubt best = null;
        float bestScore = Float.MAX_VALUE;
        for (CordonRedoubt r : redoubts) {
            if (r.state() == CordonRedoubt.State.FALLEN) continue;
            long held = answering.values().stream().filter(x -> x == r).count();
            float score = held * 10_000f - (r.state() == CordonRedoubt.State.CALLING ? 5_000f : 0f)
                    + body.distanceTo(r.centreX(), r.centreY());
            if (score < bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    static CordonRedoubt nearestCalling(GroundBody body, List<CordonRedoubt> redoubts) {
        CordonRedoubt best = null;
        float bestDistance = Float.MAX_VALUE;
        for (CordonRedoubt r : redoubts) {
            if (r.state() != CordonRedoubt.State.CALLING) continue;
            float d = body.distanceTo(r.centreX(), r.centreY());
            if (d < bestDistance) {
                bestDistance = d;
                best = r;
            }
        }
        return best;
    }

    /**
     * The {@code rank}th place on a redoubt's flanks: out past one side wall, a few cells back from
     * the front, the sides taken in turn and each pair a step further back.
     */
    int[] flank(CordonRedoubt r, int rank) {
        int x = rank % 2 == 0 ? r.left() - FLANK_OUT : r.right() + FLANK_OUT;
        int front = frontward > 0 ? r.bottom() : r.top();
        int y = front - frontward * (FLANK_BACK + (rank / 2) * FLANK_STEP);
        return new int[]{x, y};
    }

    /** Where to set a squad down behind a redoubt: its rear wall's middle, a few cells back. */
    int[] behind(CordonRedoubt r) {
        int y = frontward > 0 ? r.top() - BEHIND_WALL : r.bottom() + BEHIND_WALL;
        return new int[]{r.centreX(), y};
    }
}
