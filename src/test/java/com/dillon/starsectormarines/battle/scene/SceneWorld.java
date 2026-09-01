package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A built scene: the simulation, and the keys a scene reads it back through.
 *
 * <p>Keys rather than ids because a scene's own text is about "the squad" and
 * "the far defender", and an int that came out of {@code mintSquad} four
 * statements ago is the one thing in a scene nobody can check by eye. An
 * unknown key names the ones that exist rather than returning nothing, since a
 * typo would otherwise surface as a null squad several ticks later.
 */
public final class SceneWorld {

    private final BattleSimulation sim;
    private final int width;
    private final int height;
    private final Map<String, Integer> squadIds;
    private final Map<String, long[]> members;
    private final Map<String, Long> unitIds;

    SceneWorld(BattleSimulation sim, int width, int height,
               Map<String, Integer> squadIds, Map<String, long[]> members,
               Map<String, Long> unitIds) {
        this.sim = sim;
        this.width = width;
        this.height = height;
        this.squadIds = new LinkedHashMap<>(squadIds);
        this.members = new LinkedHashMap<>(members);
        this.unitIds = new LinkedHashMap<>(unitIds);
    }

    public BattleSimulation sim() { return sim; }

    public int width() { return width; }

    public int height() { return height; }

    public int squadId(String key) {
        Integer id = squadIds.get(key);
        if (id == null) throw unknown("squad", key, squadIds.keySet());
        return id;
    }

    public Squad squad(String key) {
        return sim.getSquad(squadId(key));
    }

    /** Every member spawned into the squad, alive or not, in spawn order. */
    public long[] members(String key) {
        long[] ids = members.get(key);
        if (ids == null) throw unknown("squad", key, squadIds.keySet());
        return ids.clone();
    }

    public long unit(String key) {
        Long id = unitIds.get(key);
        if (id == null) throw unknown("unit", key, unitIds.keySet());
        return id;
    }

    /** Zone id at a cell, or the detector's own "no zone" answer for a doorway. */
    public int zoneAt(int x, int y) {
        return sim.getZoneGraph().zoneIdAt(x, y);
    }

    private static IllegalArgumentException unknown(String kind, String key,
                                                    Iterable<String> known) {
        StringBuilder sb = new StringBuilder("No ").append(kind).append(" keyed '")
                .append(key).append("' in this scene; known: ");
        boolean first = true;
        for (String k : known) {
            if (!first) sb.append(", ");
            sb.append(k);
            first = false;
        }
        if (first) sb.append("(none)");
        return new IllegalArgumentException(sb.toString());
    }
}
