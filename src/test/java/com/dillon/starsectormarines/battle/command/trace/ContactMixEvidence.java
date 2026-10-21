package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * The mix of fights a battle produced (`garrisonables.md`, not every fight is into prepared positions): every
 * {@link #EVERY_TICKS} ticks, each squad in actionable contact is counted as fighting <b>on a post</b> when its leader
 * stands on a garrisonable (a building envelope's footprint or posts, or a fighting-position stretch) and <b>in the
 * open</b> otherwise, per side. Counts any squad on a post, whoever sent it there — an authored garrison in its
 * building is on a post — so a run is read against its control, never as an absolute.
 */
public final class ContactMixEvidence {

    static final int EVERY_TICKS = 15;

    private final Map<Faction, int[]> counts = new EnumMap<>(Faction.class);

    public void sample(BattleSimulation sim) {
        if (sim.getSimTickIndex() % EVERY_TICKS != 0) return;
        GarrisonableRegistry places = sim.garrisonables();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction == Faction.CIVILIAN || sim.squadMemberCount(squad.id) <= 0) continue;
            if (!WorldStateBuilder.hasActionableContact(squad, sim)) continue;
            long leader = sim.resolveUnit(squad.leaderId);
            if (leader == 0L) leader = sim.squadMemberAt(squad.id, 0);
            int[] row = counts.computeIfAbsent(squad.faction, f -> new int[2]);
            boolean onPost = places != null && places.idAt(sim.world().cellX(leader), sim.world().cellY(leader)) >= 0;
            row[onPost ? 0 : 1]++;
        }
    }

    /** {@code {"DEFENDER": {"onPost": n, "open": m, "onPostShare": s}, ...}} */
    public JSONObject json() throws JSONException {
        JSONObject out = new JSONObject();
        for (Map.Entry<Faction, int[]> entry : counts.entrySet()) {
            int[] row = entry.getValue();
            int total = row[0] + row[1];
            out.put(entry.getKey().name(), new JSONObject().put("onPost", row[0]).put("open", row[1])
                    .put("onPostShare", total == 0 ? 0.0 : Math.round(1000.0 * row[0] / total) / 1000.0));
        }
        return out;
    }

    /** One line's worth: {@code DEFENDER=onPost/open ...}. */
    public String line() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Faction, int[]> entry : counts.entrySet()) {
            out.append(' ').append("mix.").append(entry.getKey().name()).append('=')
                    .append(entry.getValue()[0]).append('/').append(entry.getValue()[1]);
        }
        return out.toString();
    }
}
