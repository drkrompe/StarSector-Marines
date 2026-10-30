package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How long a mech lasts once it is under fire: for every mech that takes the field, the tick it
 * was first damaged, the ticks its sections went ({@code mechs-nouns.md}) and the tick
 * it died. Time-to-kill is death less first damage. A reading, never asserted.
 */
public final class MechTimeToKillProbe {

    private static final class Track {
        final String variant;
        final String faction;
        int firstSeen;
        int firstDamaged = -1;
        int died = -1;
        float structureLeft = 1f;
        float armorLeft = 1f;
        final Map<MechSection, Integer> lost = new LinkedHashMap<>();

        Track(String variant, String faction, int firstSeen) {
            this.variant = variant;
            this.faction = faction;
            this.firstSeen = firstSeen;
        }
    }

    private final Map<Long, Track> tracks = new LinkedHashMap<>();

    public void sample(BattleSimulation sim) {
        World world = sim.world();
        int tick = sim.getSimTickIndex();
        for (Map.Entry<Long, Track> e : tracks.entrySet()) {
            Track t = e.getValue();
            if (t.died < 0 && !sim.getRoster().isAliveById(e.getKey())) t.died = tick;
        }
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!world.hasMechLoadout(id)) continue;
            MechLoadoutComponent loadout = world.mechLoadout(id);
            Track t = tracks.computeIfAbsent(id, k -> new Track(loadout.variant.id,
                    sim.identity().faction(id).name(), tick));
            boolean damaged = world.hp(id) < world.maxHp(id)
                    || world.hasArmor(id) && world.armor(id) < world.maxArmor(id);
            if (damaged && t.firstDamaged < 0) t.firstDamaged = tick;
            t.structureLeft = world.maxHp(id) > 0f ? world.hp(id) / world.maxHp(id) : 0f;
            t.armorLeft = world.hasArmor(id) && world.maxArmor(id) > 0f ? world.armor(id) / world.maxArmor(id) : 0f;
            MechSections sections = loadout.sections();
            if (sections == null) continue;
            for (MechSection section : MechSection.values()) {
                if (section != MechSection.TORSO && sections.destroyed(section) && !t.lost.containsKey(section)) {
                    t.lost.put(section, tick);
                }
            }
        }
    }

    public JSONObject json() throws JSONException {
        JSONArray mechs = new JSONArray();
        List<Integer> ttk = new ArrayList<>();
        for (Track t : tracks.values()) {
            JSONObject lost = new JSONObject();
            for (Map.Entry<MechSection, Integer> e : t.lost.entrySet()) lost.put(e.getKey().name(), e.getValue());
            mechs.put(new JSONObject().put("variant", t.variant).put("faction", t.faction)
                    .put("firstSeen", t.firstSeen).put("firstDamaged", t.firstDamaged).put("died", t.died)
                    .put("structureLeft", t.structureLeft).put("armorLeft", t.armorLeft)
                    .put("sectionsLost", lost));
            if (t.died >= 0 && t.firstDamaged >= 0) ttk.add(t.died - t.firstDamaged);
        }
        ttk.sort(Integer::compare);
        return new JSONObject().put("mechs", mechs)
                .put("killed", ttk.size()).put("timeToKillTicks", new JSONArray(ttk))
                .put("medianTimeToKillTicks", ttk.isEmpty() ? -1 : ttk.get(ttk.size() / 2));
    }

    public String describe() {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "- mechs: %d took the field%n", tracks.size()));
        for (Track t : tracks.values()) {
            out.append(String.format(Locale.ROOT, "  - %s %s: seen %d, first damaged %s, died %s, sections lost %s%n",
                    t.faction, t.variant, t.firstSeen, t.firstDamaged < 0 ? "never" : t.firstDamaged,
                    t.died < 0 ? "no" : t.died, t.lost.isEmpty() ? "none" : t.lost));
        }
        return out.toString();
    }
}
