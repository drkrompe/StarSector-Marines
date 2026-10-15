package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Whether anybody ever stood inside a fort's fire-control bunker, read off the simulation: the capture rule is presence in
 * the bunker's room, so a company that cleared the courtyard and never entered the room is a different finding from one that
 * died outside it. Sampled every 30 ticks; reports the room's size, when a marine and a defender were first inside, and the
 * most marines inside at once.
 */
final class BunkerProbe {

    private static final int SAMPLE_TICKS = 30;
    private static final int SCAN_CELLS = 16;

    private int cells = -1;
    private int firstMarine = -1;
    private int firstDefender = -1;
    private int peakMarines;
    private int lastDefender = -1;
    private int defendersInsideAtEnd;
    private int marinesInsideAtEnd;

    void sample(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (sim.forts().isEmpty() || tick % SAMPLE_TICKS != 0) return;
        for (Fort fort : sim.forts().forts()) {
            Fort.Place bunker = fort.place(FortAspect.FIRE_CONTROL);
            if (bunker == null || !bunker.resolved()) continue;
            if (cells < 0) {
                cells = 0;
                int cx = (int) bunker.x();
                int cy = (int) bunker.y();
                for (int y = cy - SCAN_CELLS; y <= cy + SCAN_CELLS; y++) {
                    for (int x = cx - SCAN_CELLS; x <= cx + SCAN_CELLS; x++) if (bunker.contains(x, y)) cells++;
                }
            }
            int marines = 0;
            int defenders = 0;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                Faction faction = sim.identity().faction(unit);
                if (faction != Faction.MARINE && faction != Faction.DEFENDER) continue;
                if (sim.identity().type(unit).isStatic() || sim.identity().type(unit).isDrone()) continue;
                if (!bunker.contains((int) Math.floor(sim.world().x(unit)), (int) Math.floor(sim.world().y(unit)))) continue;
                if (faction == Faction.MARINE) marines++;
                else {
                    if (firstDefender < 0) firstDefender = tick;
                    lastDefender = tick;
                    defenders++;
                }
            }
            if (marines > 0 && firstMarine < 0) firstMarine = tick;
            peakMarines = Math.max(peakMarines, marines);
            defendersInsideAtEnd = defenders;
            marinesInsideAtEnd = marines;
        }
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("roomCells", cells).put("firstMarineInsideTick", firstMarine)
                .put("firstDefenderInsideTick", firstDefender).put("peakMarinesInside", peakMarines)
                .put("lastDefenderInsideTick", lastDefender).put("defendersInsideAtEnd", defendersInsideAtEnd)
                .put("marinesInsideAtEnd", marinesInsideAtEnd);
    }
}
