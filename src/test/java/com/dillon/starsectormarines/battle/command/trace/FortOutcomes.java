package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What became of each fort's aspects by the end of a replayed battle, as markdown for the evidence report, and how near
 * the company ever came to each. Read off the simulation, so it describes the outcome and not what either commander knew
 * along the way, except for the one question it asks of the company's fog: when each fort's disclosing cell was first seen.
 */
final class FortOutcomes {

    /** Ticks between samples of where the company is. */
    private static final int SAMPLE_TICKS = 30;

    private final Map<Integer, Float> nearest = new HashMap<>();
    private final Map<Integer, Integer> firstSeen = new HashMap<>();

    /** Samples the company's distance to every fort and whether its disclosing cell is in view. */
    void sample(BattleSimulation sim) {
        if (sim.forts().isEmpty() || sim.getSimTickIndex() % SAMPLE_TICKS != 0) return;
        for (Fort fort : sim.forts().forts()) {
            float[] at = centre(sim, fort);
            if (at == null) continue;
            if (!firstSeen.containsKey(fort.id()) && sim.playerSeesCell((int) at[0], (int) at[1])) {
                firstSeen.put(fort.id(), sim.getSimTickIndex());
            }
            float best = nearest.getOrDefault(fort.id(), Float.MAX_VALUE);
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.MARINE || sim.identity().type(unit) != UnitType.MARINE) continue;
                best = Math.min(best, (float) Math.hypot(sim.world().x(unit) - at[0], sim.world().y(unit) - at[1]));
            }
            nearest.put(fort.id(), best);
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
            out.append(String.format(Locale.ROOT, "; nearest marine %s cells, disclosing cell first in the company's view %s",
                    closest == null ? "n/a" : String.format(Locale.ROOT, "%.0f", closest),
                    firstSeen.containsKey(fort.id()) ? "at tick " + firstSeen.get(fort.id()) : "never"));
            out.append('\n');
            for (Fort.Place place : fort.places()) {
                out.append("  - ").append(place.aspect()).append(": holder ").append(place.control().holder())
                        .append(" (built for ").append(place.originalHolder()).append(")")
                        .append(place.wrecked() ? ", WRECKED" : "")
                        .append('\n');
            }
        }
        return out.toString();
    }
}
