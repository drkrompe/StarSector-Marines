package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.AlliedCommand;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The allies' light armour behind the line ({@code cordon-nouns.md}, Allied armour): cars and
 * technicals under the allied command, standing behind the line between calls and sent to the
 * redoubt calling nearest them when one calls, to fight from behind its rear wall.
 */
public final class CordonArmour {

    /** Ticks between looks at which redoubts are calling. */
    static final int REVIEW_TICKS = 30;
    /** Cells behind a redoubt's rear wall a hull fights from. */
    static final int BEHIND_WALL = 4;

    private final AlliedCommand command;
    private final int frontward;
    private final Map<Long, int[]> home = new HashMap<>();
    private final Map<Long, CordonRedoubt> answering = new HashMap<>();

    /** @param frontward which way the front lies from the line: +1 toward high y, -1 toward low */
    public CordonArmour(AlliedCommand command, int frontward) {
        this.command = command;
        this.frontward = frontward;
    }

    public AlliedCommand command() { return command; }

    /** Sets a hull down at the nearest open ground to a cell; that place is its home between calls. */
    public long deploy(BattleSimulation sim, VehicleType type, int x, int y) {
        int[] cell = CordonGround.nearestWalkable(sim.getGrid(), x, y);
        if (cell == null) return 0L;
        long hull = command.deploy(sim, type, cell[0], cell[1], frontward > 0 ? 0f : 180f);
        home.put(hull, cell);
        return hull;
    }

    /** One tick: sends each hull to the calling redoubt nearest it, or home when none calls. */
    public void advance(BattleSimulation sim, List<CordonRedoubt> redoubts) {
        command.advance(sim);
        if (sim.getSimTickIndex() % REVIEW_TICKS != 0) return;
        List<Long> hulls = new ArrayList<>(command.hulls());
        home.keySet().retainAll(hulls);
        answering.keySet().retainAll(hulls);
        for (long hull : hulls) {
            if (command.carrying(hull) || !home.containsKey(hull)) continue;
            CordonRedoubt current = answering.get(hull);
            if (current != null && current.state() == CordonRedoubt.State.CALLING) continue;
            CordonRedoubt call = nearestCalling(sim.convoy().body(hull), redoubts);
            if (call == current && (call != null || !command.idle(hull))) continue;
            answering.put(hull, call);
            int[] post = call != null ? behind(call) : home.get(hull);
            command.station(sim, hull, post[0], post[1]);
        }
    }

    private CordonRedoubt nearestCalling(GroundBody body, List<CordonRedoubt> redoubts) {
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

    private int[] behind(CordonRedoubt r) {
        int y = frontward > 0 ? r.top() - BEHIND_WALL : r.bottom() + BEHIND_WALL;
        return new int[]{r.centreX(), y};
    }
}
