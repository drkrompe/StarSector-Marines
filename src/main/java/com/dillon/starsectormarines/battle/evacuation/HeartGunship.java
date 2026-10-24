package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.allied.AlliedRequest;
import com.dillon.starsectormarines.battle.command.allied.AlliedTool;
import com.dillon.starsectormarines.battle.command.allied.AlliedToolShed;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.List;

/**
 * The Broad Front's gunship on call (`heart-allied-commander.md`): the lanes' commander asks the logistics base for
 * one sortie at a time, only while the spore colonies over the company's approach are dead (the air is open), and
 * only onto something worth it: a Matriarch near a lane's armour first, then the bore a broken lane is on. A sortie
 * comes off station at half structure.
 */
public final class HeartGunship {

    /** The place the gunship's requests name. */
    static final String PLACE = "air";
    /** Seconds a sortie works over its target. */
    static final float ORBIT_SECONDS = 150f;
    /** Cells from a lane's machine within which a Matriarch is on its armour. */
    static final float ON_ARMOUR_CELLS = 20f;

    private HeartGunship() { }

    /** Whether the air over the approach is open: no spore colony stands on the satellites nearest the company. */
    public static boolean airOpen(BattleSimulation sim) {
        for (SwarmBore bore : HeartObjectives.approachSatellites(sim)) {
            if (bore.collapsed()) continue;
            for (long colony : bore.colonies()) {
                if (sim.resolveUnit(colony) != 0L && sim.world().isAlive(colony)) return false;
            }
        }
        return true;
    }

    /** Sets a sortie down over the request's cell, flying in and out over the depot; done once it is gone. */
    public static AlliedToolShed.Maker maker() {
        return (sim, from, request) -> {
            if (from == null) return null;
            long craft = sim.spawnGunship(ShuttleType.MUDSKIPPER, Faction.ALLY, request.x() + .5f, request.y() + .5f,
                    from[0], from[1], from[0], from[1]);
            ShuttleMission sortie = sim.world().mission(craft);
            if (sortie == null) return null;
            sortie.orbitSecondsLeft = ORBIT_SECONDS;
            return s -> {
                if (sortie.destroyed || sortie.state == ShuttleState.GONE) return true;
                // Off station at half structure: it turns for home and the next sortie waits for the base.
                if (s.world().hp(craft) < s.world().maxHp(craft) * .5f) sortie.orbitSecondsLeft = 0f;
                return false;
            };
        };
    }

    /**
     * The commander's call: with no sortie up, asks the shed for one over the most pressing target, and adds
     * {@code gunship_called_<n>} to {@code beats} when it is granted. Returns how many sorties have been called.
     */
    static int call(BattleSimulation sim, AlliedToolShed shed, HeartLanes lanes, List<String> beats, int called) {
        if (shed.onRoad(AlliedTool.GUNSHIP, PLACE) > 0) return called;
        int[] target = target(sim, lanes);
        if (target == null) return called;
        if (!shed.request(sim, new AlliedRequest(AlliedTool.GUNSHIP, PLACE, target[0], target[1], "target"))) return called;
        beats.add("gunship_called_" + (called + 1));
        return called + 1;
    }

    /** The most pressing target: a living Matriarch on a lane's armour, else the bore a broken lane is on. */
    static int[] target(BattleSimulation sim, HeartLanes lanes) {
        List<float[]> armour = lanes.machinePositions(sim);
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) != UnitType.SWARM_MATRIARCH || !sim.world().isAlive(unit)) continue;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            for (float[] m : armour) {
                if (Math.abs(m[0] - x) <= ON_ARMOUR_CELLS && Math.abs(m[1] - y) <= ON_ARMOUR_CELLS) {
                    return new int[]{(int) x, (int) y};
                }
            }
        }
        return lanes.brokenLaneTarget();
    }
}
