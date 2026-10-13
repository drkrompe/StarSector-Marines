package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What became of each fort's aspects by the end of a replayed battle, as markdown for the evidence report, and how near
 * the company ever came to each. Read off the simulation, so it describes the outcome and not what either commander knew
 * along the way, except for three questions it asks of the company's fog and the commander's memory: when each fort's
 * platform was first in view, when its works were, and when the marine commander was first actually told of the fort.
 */
final class FortOutcomes {

    /** Ticks between samples of where the company is. */
    private static final int SAMPLE_TICKS = 30;

    private final Map<Integer, Float> nearest = new HashMap<>();
    private final Map<Integer, Float> nearestWorks = new HashMap<>();
    private final Map<Integer, Integer> firstSeen = new HashMap<>();
    private final Map<Integer, Integer> firstWorksSeen = new HashMap<>();
    private final Map<Integer, Integer> firstDisclosed = new HashMap<>();
    private final Map<String, Faction> lastHolder = new HashMap<>();
    private final List<String> holderChanges = new ArrayList<>();

    /** Samples the company's distance to every fort and whether its disclosing cells are in view. */
    void sample(BattleSimulation sim) {
        if (sim.forts().isEmpty() || sim.getSimTickIndex() % SAMPLE_TICKS != 0) return;
        int tick = sim.getSimTickIndex();
        for (Fort fort : sim.forts().forts()) {
            noteHolders(fort, tick);
            float[] at = centre(sim, fort);
            if (at == null) continue;
            int cellX = (int) Math.floor(at[0]);
            int cellY = (int) Math.floor(at[1]);
            if (!firstSeen.containsKey(fort.id()) && sim.playerSeesCell(cellX, cellY)) {
                firstSeen.put(fort.id(), tick);
            }
            if (!firstDisclosed.containsKey(fort.id()) && sim.structureSighted(cellX, cellY)) {
                firstDisclosed.put(fort.id(), tick);
            }
            int[] works = fort.exteriorCells();
            if (!firstWorksSeen.containsKey(fort.id())) {
                for (int i = 0; i + 1 < works.length; i += 2) {
                    if (sim.playerSeesCell(works[i], works[i + 1])) {
                        firstWorksSeen.put(fort.id(), tick);
                        break;
                    }
                }
            }
            float best = nearest.getOrDefault(fort.id(), Float.MAX_VALUE);
            float bestWorks = nearestWorks.getOrDefault(fort.id(), Float.MAX_VALUE);
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE || sim.identity().type(unit) != UnitType.MARINE) continue;
                float ux = sim.world().x(unit);
                float uy = sim.world().y(unit);
                best = Math.min(best, (float) Math.hypot(ux - at[0], uy - at[1]));
                for (int c = 0; c + 1 < works.length; c += 2) {
                    bestWorks = Math.min(bestWorks, (float) Math.hypot(ux - works[c] - 0.5f, uy - works[c + 1] - 0.5f));
                }
            }
            nearest.put(fort.id(), best);
            nearestWorks.put(fort.id(), bestWorks);
        }
    }

    /** Records each change of who holds an aspect, with the tick it was first seen. */
    private void noteHolders(Fort fort, int tick) {
        for (Fort.Place place : fort.places()) {
            String key = fort.id() + ":" + place.aspect();
            Faction holder = place.wrecked() ? null : place.control().holder();
            if (!lastHolder.containsKey(key)) {
                lastHolder.put(key, holder);
                continue;
            }
            Faction before = lastHolder.get(key);
            if (before != holder) {
                holderChanges.add("tick " + tick + ": fort " + fort.id() + " " + place.aspect() + " "
                        + (before == null ? "WRECKED" : before.name()) + " -> " + (holder == null ? "WRECKED" : holder.name()));
                lastHolder.put(key, holder);
            }
        }
    }

    /** The cell whose being seen discloses the fort: the platform or the generator. */
    private static float[] centre(BattleSimulation sim, Fort fort) {
        if (fort.kind() == FortKind.BATTERY_FORT && fort.battery() != null) {
            return new float[] {fort.battery().x(), fort.battery().y()};
        }
        long emitter = fort.emitterId();
        if (emitter != 0L && sim.getRoster().isAliveById(emitter)) {
            return new float[] {sim.world().x(emitter), sim.world().y(emitter)};
        }
        return null;
    }

    private static String at(Map<Integer, Integer> ticks, int fortId) {
        return ticks.containsKey(fortId) ? "at tick " + ticks.get(fortId) : "never";
    }

    /** One block per fort, or a line saying there were none. */
    String describe(BattleSimulation sim) {
        if (sim.forts().isEmpty()) return "No forts stood on this map.\n";
        StringBuilder out = new StringBuilder();
        for (Fort fort : sim.forts().forts()) {
            out.append("- fort ").append(fort.id()).append(' ').append(fort.kind());
            float[] at = centre(sim, fort);
            if (at != null) out.append(String.format(Locale.ROOT, " at (%.0f,%.0f)", at[0], at[1]));
            if (fort.kind() == FortKind.BATTERY_FORT && fort.battery() != null) {
                FortressBattery battery = fort.battery();
                int rounds = 0;
                for (FortressBattery.Gun gun : battery.guns()) rounds += gun.shotsFired;
                out.append(String.format(Locale.ROOT, ": guns held by %s (built for %s), destroyed=%s, rounds fired=%d",
                        battery.owner(), battery.originalOwner(), battery.destroyed(), rounds));
            } else if (fort.kind() == FortKind.SHIELD_FORT) {
                out.append(": dome held by ").append(fort.domeHolderNow());
            }
            Float closest = nearest.get(fort.id());
            Float closestWorks = nearestWorks.get(fort.id());
            out.append(String.format(Locale.ROOT,
                    "; nearest marine %s cells (to the works %s); platform first in the company's view %s,"
                            + " works first in view %s, marine commander first told of the fort %s",
                    closest == null ? "n/a" : String.format(Locale.ROOT, "%.0f", closest),
                    closestWorks == null ? "n/a" : String.format(Locale.ROOT, "%.0f", closestWorks),
                    at(firstSeen, fort.id()), at(firstWorksSeen, fort.id()), at(firstDisclosed, fort.id())));
            out.append('\n');
            for (Fort.Place place : fort.places()) {
                out.append("  - ").append(place.aspect()).append(": holder ").append(place.control().holder())
                        .append(" (built for ").append(place.originalHolder()).append(")")
                        .append(place.wrecked() ? ", WRECKED" : "")
                        .append('\n');
            }
        }
        if (!holderChanges.isEmpty()) {
            out.append("- aspect holder changes:\n");
            for (String change : holderChanges) out.append("  - ").append(change).append('\n');
        }
        return out.toString();
    }
}
