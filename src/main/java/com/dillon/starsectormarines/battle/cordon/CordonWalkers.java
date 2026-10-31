package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * A group of people a beat walks (L1, L5, R2): stood up together, walked to a place as the Line's
 * refugees are walked ({@link CordonRefugees}), and taken off the field once they are safe. They are
 * prey: the swarm harvests one it reaches.
 */
final class CordonWalkers {

    /** Whether a cell is safe for this group: they leave the battle there. */
    interface Safe {
        boolean at(int x, int y);
    }

    private final List<Long> members = new ArrayList<>();
    private int spawned, out, lost;

    /** Stands {@code count} people up round {@code (x, y)}. */
    static CordonWalkers stand(BattleSimulation sim, String name, int x, int y, int count) {
        CordonWalkers group = new CordonWalkers();
        List<int[]> cells = CordonGround.clusterNear(sim, x, y, count);
        for (int i = 0; i < cells.size(); i++) {
            int[] cell = cells.get(i);
            group.members.add(sim.spawn(new EntitySpec(name + " " + (i + 1), Faction.CIVILIAN, UnitType.CIVILIAN,
                    cell[0], cell[1]).role(UnitRole.VIP)));
        }
        group.spawned = group.members.size();
        return group;
    }

    int spawned() { return spawned; }
    /** Left the field safe. */
    int out() { return out; }
    /** Killed. */
    int lost() { return lost; }
    /** Still on the field and alive, as of the last {@link #walk} or {@link #count}. */
    int onField() { return members.size(); }

    /** Drops the dead, without moving anyone. */
    void count(BattleSimulation sim) {
        for (int i = 0; i < members.size(); ) {
            long id = members.get(i);
            if (sim.resolveUnit(id) == 0L || !sim.world().isAlive(id)) {
                members.remove(i);
                lost++;
            } else {
                i++;
            }
        }
    }

    /** One step toward {@code (destX, destY)}; anyone standing safe leaves the field. */
    void walk(BattleSimulation sim, int destX, int destY, Safe safe) {
        walk(sim, destX, destY, safe, id -> {
            sim.clearPath(id);
            sim.takeOffTheField(id);
        });
    }

    /** One step toward {@code (destX, destY)}; anyone standing safe is out of this group and handed to {@code arrived}. */
    void walk(BattleSimulation sim, int destX, int destY, Safe safe, LongConsumer arrived) {
        count(sim);
        for (int i = 0; i < members.size(); ) {
            long id = members.get(i);
            int x = sim.world().cellX(id), y = sim.world().cellY(id);
            if (safe.at(x, y)) {
                arrived.accept(id);
                members.remove(i);
                out++;
                continue;
            }
            int[] path = sim.movement().path(id);
            boolean elsewhere = Paths.isEmpty(path) || Paths.destX(path) != destX || Paths.destY(path) != destY;
            boolean spent = sim.movement().pathIdx(id) >= Paths.cellCount(path);
            if ((elsewhere || spent) && sim.movement().mayRepath(id) && sim.getGrid().inBounds(x, y)) {
                sim.setPath(id, sim.findPath(x, y, destX, destY));
            }
            sim.advanceMovement(id);
            i++;
        }
    }

    /** Whether any of them stands within {@code reach} cells of a Marine. */
    boolean reachedByMarines(BattleSimulation sim, int reach) {
        if (members.isEmpty()) return false;
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (!sim.world().hasPosition(unit) || sim.isRiding(unit)
                    || sim.identity().faction(unit) != Faction.MARINE) continue;
            float ux = sim.world().x(unit), uy = sim.world().y(unit);
            for (long m : members) {
                if (Math.abs(sim.world().x(m) - ux) <= reach && Math.abs(sim.world().y(m) - uy) <= reach) return true;
            }
        }
        return false;
    }

    /** Where the group stands: the first member's cell, or {@code null} when nobody is left on the field. */
    int[] where(BattleSimulation sim) {
        if (members.isEmpty()) return null;
        long m = members.get(0);
        return new int[]{sim.world().cellX(m), sim.world().cellY(m)};
    }
}
