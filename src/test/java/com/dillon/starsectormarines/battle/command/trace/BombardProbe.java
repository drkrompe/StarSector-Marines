package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.evacuation.SwarmBombards;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prints one {@code [bombard] <run> <json>} line per battle that fielded a Bombard ({@code bombard.md}, Probe): how
 * many came, how long they stood planted, the globs they landed, who the haze caught and for how long, how many walked
 * out of it, the launches that gave them away, the air calls on them, and how they died. Read off the battle each tick;
 * it draws no random number.
 */
final class BombardProbe {

    /** Cells from a call's point within which an air call counts as one on a planted Bombard. */
    private static final float CALL_REACH = 3f;
    /** Cells within which a dying Bombard counts as killed under an air strike's call. */
    private static final float STRUCK_REACH = 4f;
    /** Ticks after a call within which a Bombard death there counts as the strike's. */
    private static final int STRUCK_TICKS = 900;

    private final Set<Long> seen = new HashSet<>();
    private final Map<Long, Integer> plantedTicks = new HashMap<>();
    private final Set<Long> hazedNow = new HashSet<>();
    private final Set<Long> everHazed = new HashSet<>();
    private final Map<Long, Integer> hazeEntered = new HashMap<>();
    private int hazedUnitTicks, exits, exitTicksTotal, revealsSeen, maxAlive, firstPlantTick = -1, firstGlobTick = -1;
    private int engagedTicks, airCalls, airCallsOnBombard, diedPlanted, diedUnplanted, diedNearStrike, eventsRead;
    private final Set<Long> plantedLastTick = new HashSet<>();
    private final Map<Long, float[]> lastPos = new HashMap<>();
    private final List<float[]> callsOnBombard = new ArrayList<>();

    void afterAdvance(BattleSimulation sim) {
        World world = sim.world();
        int tick = sim.getSimTickIndex();
        int alive = 0;
        Set<Long> livingBombards = new HashSet<>();
        Set<Long> planted = new HashSet<>(sim.swarmBombards().plantedBodies());
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().type(unit) != UnitType.SWARM_BOMBARD) continue;
            alive++;
            livingBombards.add(unit);
            seen.add(unit);
            lastPos.put(unit, new float[]{world.x(unit), world.y(unit)});
            if (planted.contains(unit)) {
                plantedTicks.merge(unit, 1, Integer::sum);
                if (firstPlantTick < 0) firstPlantTick = tick;
            }
        }
        maxAlive = Math.max(maxAlive, alive);
        if (firstGlobTick < 0 && sim.swarmBombards().globsLanded() > 0) firstGlobTick = tick;
        for (long gone : new HashSet<>(lastPos.keySet())) {
            if (livingBombards.contains(gone)) continue;
            float[] p = lastPos.remove(gone);
            if (plantedLastTick.contains(gone)) diedPlanted++;
            else diedUnplanted++;
            for (float[] call : callsOnBombard) {
                if (tick - call[2] <= STRUCK_TICKS && Math.hypot(call[0] - p[0], call[1] - p[1]) <= STRUCK_REACH) {
                    diedNearStrike++;
                    break;
                }
            }
        }
        plantedLastTick.clear();
        plantedLastTick.addAll(planted);

        revealsSeen += newReveals(sim);
        NavigationGrid grid = sim.getGrid();
        Set<Long> hazed = new HashSet<>();
        if (grid.anyHaze()) {
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                Faction f = sim.identity().faction(unit);
                if (f != Faction.MARINE && f != Faction.ALLY) continue;
                if (!sim.identity().type(unit).combatant) continue;
                if (grid.hasHaze(world.cellX(unit), world.cellY(unit))) hazed.add(unit);
            }
        }
        for (long unit : hazed) {
            if (!hazedNow.contains(unit)) hazeEntered.put(unit, tick);
            if (livingBombards.contains(world.targetId(unit))) engagedTicks++;
        }
        for (long unit : hazedNow) {
            if (hazed.contains(unit) || !world.isAlive(unit)) continue;
            exits++;
            exitTicksTotal += tick - hazeEntered.getOrDefault(unit, tick);
        }
        hazedUnitTicks += hazed.size();
        everHazed.addAll(hazed);
        hazedNow.clear();
        hazedNow.addAll(hazed);

        AirPoolService pool = sim.airPool();
        if (pool != null) {
            List<AirPoolService.Event> events = pool.events();
            for (; eventsRead < events.size(); eventsRead++) {
                AirPoolService.Event e = events.get(eventsRead);
                if (e.side() != Faction.ALLY || e.type() != AirPoolService.EventType.CALLED) continue;
                airCalls++;
                for (long b : planted) {
                    if (Math.hypot(world.x(b) - e.x(), world.y(b) - e.y()) <= CALL_REACH) {
                        airCallsOnBombard++;
                        callsOnBombard.add(new float[]{e.x(), e.y(), e.tick()});
                        break;
                    }
                }
            }
        }
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            Faction f = sim.identity().faction(unit);
            if ((f == Faction.MARINE || f == Faction.ALLY) && !hazed.contains(unit)
                    && livingBombards.contains(world.targetId(unit))) {
                engagedTicks++;
            }
        }
    }

    private final Set<String> revealKeys = new HashSet<>();

    private int newReveals(BattleSimulation sim) {
        int fresh = 0;
        for (SwarmBombards.Reveal reveal : sim.swarmBombards().launchReveals()) {
            if (revealKeys.add(reveal.cellX() + ":" + reveal.cellY() + ":" + reveal.untilTick())) fresh++;
        }
        return fresh;
    }

    void print(String run, BattleSimulation sim) throws Exception {
        if (seen.isEmpty()) {
            System.out.println("[bombard] " + run + " {\"fielded\":0}");
            return;
        }
        int plantedTotal = 0;
        for (int t : plantedTicks.values()) plantedTotal += t;
        float secs = BattleSimulation.TICK_DT;
        JSONObject json = new JSONObject()
                .put("fielded", seen.size()).put("maxAlive", maxAlive)
                .put("plantedSeconds", Math.round(plantedTotal * secs))
                .put("firstPlantMinute", firstPlantTick < 0 ? -1 : Math.round(firstPlantTick * secs / 6f) / 10f)
                .put("firstGlobMinute", firstGlobTick < 0 ? -1 : Math.round(firstGlobTick * secs / 6f) / 10f)
                .put("globsLanded", sim.swarmBombards().globsLanded())
                .put("deathBursts", sim.swarmBombards().deathBursts())
                .put("launchReveals", revealsSeen)
                .put("troopsHazed", everHazed.size())
                .put("hazedTroopSeconds", Math.round(hazedUnitTicks * secs))
                .put("stepOuts", exits)
                .put("meanSecondsInHaze", exits == 0 ? -1 : Math.round(exitTicksTotal * secs / exits * 10f) / 10f)
                .put("troopSecondsFiringAtBombards", Math.round(engagedTicks * secs))
                .put("airCalls", airCalls).put("airCallsOnBombards", airCallsOnBombard)
                .put("diedPlanted", diedPlanted).put("diedWalking", diedUnplanted)
                .put("diedUnderAirCall", diedNearStrike);
        System.out.println("[bombard] " + run + " " + json);
    }
}
