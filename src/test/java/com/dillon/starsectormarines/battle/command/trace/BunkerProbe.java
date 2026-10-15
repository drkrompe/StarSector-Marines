package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortSystem;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Whether anybody ever stood inside a fort's fire-control bunker, read off the simulation: the capture rule is presence in
 * the bunker's room, so a company that cleared the courtyard and never entered the room is a different finding from one that
 * died outside it. Sampled every 30 ticks; reports the room's size and bounds, when a marine and a defender were first
 * inside, the most marines inside at once, when the guns changed hands and when the gun platform died. A timeline every
 * 300 ticks reads {@code tick:inside/near} (marines on the room's floor / within {@value #NEAR_CELLS} cells of the core),
 * then the fire control's state ({@code C} contested and its progress) and what the capture scan saw ({@code H} the
 * holder's side present, {@code X} a hostile side present).
 */
final class BunkerProbe {

    private static final int SAMPLE_TICKS = 30;
    private static final int SCAN_CELLS = 16;
    /** Marines within this many cells of the bunker core count as near it, for the timeline. */
    private static final int NEAR_CELLS = 8;
    private static final int TIMELINE_TICKS = 300;
    private final StringBuilder timeline = new StringBuilder();

    private int cells = -1;
    private int firstMarine = -1;
    private int firstDefender = -1;
    private int peakMarines;
    private int lastDefender = -1;
    private int defendersInsideAtEnd;
    private int marinesInsideAtEnd;
    private int platformDestroyedTick = -1;
    private int takenTick = -1;
    private int[] roomBounds;

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
                roomBounds = new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
                for (int y = cy - SCAN_CELLS; y <= cy + SCAN_CELLS; y++) {
                    for (int x = cx - SCAN_CELLS; x <= cx + SCAN_CELLS; x++) {
                        if (!bunker.contains(x, y)) continue;
                        cells++;
                        roomBounds[0] = Math.min(roomBounds[0], x);
                        roomBounds[1] = Math.min(roomBounds[1], y);
                        roomBounds[2] = Math.max(roomBounds[2], x);
                        roomBounds[3] = Math.max(roomBounds[3], y);
                    }
                }
            }
            if (platformDestroyedTick < 0 && fort.battery() != null && fort.battery().destroyed()) platformDestroyedTick = tick;
            if (takenTick < 0 && fort.battery() != null && fort.battery().owner() != fort.battery().originalOwner()) takenTick = tick;
            int marines = 0;
            int defenders = 0;
            int near = 0;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                Faction faction = sim.identity().faction(unit);
                if (faction != Faction.MARINE && faction != Faction.DEFENDER) continue;
                if (sim.identity().type(unit).isStatic() || sim.identity().type(unit).isDrone()) continue;
                if (faction == Faction.MARINE && Math.max(Math.abs(sim.world().x(unit) - bunker.x()),
                        Math.abs(sim.world().y(unit) - bunker.y())) <= NEAR_CELLS) near++;
                if (!bunker.contains((int) Math.floor(sim.world().x(unit)), (int) Math.floor(sim.world().y(unit)))) continue;
                if (faction == Faction.MARINE) marines++;
                else {
                    if (firstDefender < 0) firstDefender = tick;
                    lastDefender = tick;
                    defenders++;
                }
            }
            if (marines > 0 && firstMarine < 0) firstMarine = tick;
            if (tick % TIMELINE_TICKS == 0) {
                timeline.append(timeline.length() == 0 ? "" : " ").append(tick).append(':').append(marines).append('/').append(near);
                if (fort.battery() != null) {
                    FortSystem.Presence there = FortSystem.scan(sim, fort.battery().owner(), bunker);
                    timeline.append("[fc ").append(fort.battery().fireControl().contested() ? "C" : "-")
                            .append(String.format(Locale.ROOT, "%.2f", fort.battery().fireControl().captureProgress()))
                            .append(there.holderPresent() ? " H" : " -").append(there.hostile() == null ? "-" : "X")
                            .append(fort.battery().destroyed() ? " dead" : "").append(']');
                }
            }
            peakMarines = Math.max(peakMarines, marines);
            defendersInsideAtEnd = defenders;
            marinesInsideAtEnd = marines;
        }
    }

    JSONObject json() throws JSONException {
        return new JSONObject().put("roomCells", cells).put("firstMarineInsideTick", firstMarine)
                .put("firstDefenderInsideTick", firstDefender).put("peakMarinesInside", peakMarines)
                .put("lastDefenderInsideTick", lastDefender).put("defendersInsideAtEnd", defendersInsideAtEnd)
                .put("marinesInsideAtEnd", marinesInsideAtEnd)
                .put("takenTick", takenTick)
                .put("platformDestroyedTick", platformDestroyedTick)
                .put("insideNearTimeline", timeline.toString())
                .put("roomBounds", roomBounds == null ? "-" : roomBounds[0] + "," + roomBounds[1] + ".." + roomBounds[2] + "," + roomBounds[3]);
    }
}
