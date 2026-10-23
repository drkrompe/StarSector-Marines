package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How long the battle's ground vehicles spent against geometry, read off each hull's
 * {@code wallStuckTime} (seconds its body's sweep has been refused without moving clear) every
 * tick. A <b>pinned window</b> is thirty seconds in which a hull touched geometry and moved less
 * than a cell: a truck held on a fence, not one parked at its post. A reading, not a verdict;
 * printed as one {@code [vehicle-pins]} line per fixture for a control run to be read against.
 */
final class VehiclePinLedger {

    private static final int WINDOW_TICKS = Math.round(30f / BattleSimulation.TICK_DT);

    private final Map<Long, Hull> hulls = new HashMap<>();
    private final Set<Long> pinnedHulls = new HashSet<>();
    private int ticks;
    private int contactTicks;
    private int pinnedWindows;

    private static final class Hull {
        float windowX, windowY;
        boolean touched;
        int windowStart;
        int contactTicks;
    }

    void afterAdvance(BattleSimulation sim) {
        ConvoyService convoy = sim.convoy();
        int tick = sim.getSimTickIndex();
        ticks++;
        for (long id : convoy.entityIds()) {
            if (!convoy.isPresent(id)) continue;
            GroundBody body = convoy.body(id);
            Hull hull = hulls.get(id);
            if (hull == null) {
                hull = new Hull();
                hull.windowX = body.x;
                hull.windowY = body.y;
                hull.windowStart = tick;
                hulls.put(id, hull);
            }
            if (convoy.control(id).wallStuckTime() > 0f) {
                hull.touched = true;
                hull.contactTicks++;
                contactTicks++;
            }
            if (tick - hull.windowStart < WINDOW_TICKS) continue;
            float dx = body.x - hull.windowX, dy = body.y - hull.windowY;
            if (hull.touched && dx * dx + dy * dy < 1f) {
                pinnedWindows++;
                pinnedHulls.add(id);
            }
            hull.windowX = body.x;
            hull.windowY = body.y;
            hull.windowStart = tick;
            hull.touched = false;
        }
    }

    String line(String fixtureId) {
        int touchedFiveSeconds = 0;
        float threshold = 5f / BattleSimulation.TICK_DT;
        for (Hull hull : hulls.values()) if (hull.contactTicks >= threshold) touchedFiveSeconds++;
        return String.format(Locale.ROOT,
                "[vehicle-pins] %s ticks=%d hulls=%d contactSeconds=%.1f hullsInContact5s=%d"
                        + " pinnedWindows30s=%d pinnedHulls=%d",
                fixtureId, ticks, hulls.size(), contactTicks * BattleSimulation.TICK_DT,
                touchedFiveSeconds, pinnedWindows, pinnedHulls.size());
    }
}
