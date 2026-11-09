package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The network's power branches (`derelict-security-network.md`, slice 8; `ruins-expedition.md`, Works). Each zone is fed through
 * the junction of the layer its node stands in, on that junction's way-on branch: the branch that also powers the
 * layer's gates, and at the last layer the vault's seal. A company that throws the junction to its store powers the
 * zone down; throwing it back powers it up, dormant.
 *
 * <p>The network's feed runs through the junctions whether or not the company has restarted the hall: the hall is the
 * company's way into the works, the network was never without power.
 */
public final class DerelictPower {

    private DerelictPower() {}

    /**
     * The junction feeding a zone whose node stands at {@code (x, y)}: the layer whose ground holds it, else the
     * layer whose ground is nearest (the vault's zone lies past the last layer).
     */
    public static int junctionOf(int x, int y, List<RuinsDelve.Layer> layers) {
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (RuinsDelve.Layer layer : layers) {
            RuinsDelve.Area ground = layer.ground();
            int dx = Math.max(0, Math.max(ground.left() - x, x - ground.right()));
            int dy = Math.max(0, Math.max(ground.bottom() - y, y - ground.top()));
            int distance = Math.max(dx, dy);
            if (distance < bestDistance) {
                best = layer.index();
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Powers each zone down whose junction is thrown off the way-on branch, and up again when it is thrown back.
     *
     * @param junctions each zone's junction, by zone id
     * @param position  a junction's branch now
     * @return the zones whose power changed on this call, in zone order
     */
    public static List<Integer> apply(SecurityNetwork network, Map<Integer, Integer> junctions,
                                      IntFunction<Branch> position) {
        List<Integer> changed = new ArrayList<>();
        for (SecurityZone zone : network.zones()) {
            Integer junction = junctions.get(zone.id);
            if (junction == null || junction < 0) continue;
            boolean fed = position.apply(junction) == Branch.ONWARD;
            if (fed == zone.powered()) continue;
            if (fed) zone.powerUp();
            else zone.powerDown();
            changed.add(zone.id);
        }
        return changed;
    }
}
