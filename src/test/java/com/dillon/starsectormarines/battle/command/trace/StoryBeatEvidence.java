package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Neutral-observer export, including events intentionally withheld from player presentation. */
final class StoryBeatEvidence {
    private StoryBeatEvidence() {}
    static JSONArray snapshot(BattleSimulation sim) throws JSONException {
        JSONArray events = new JSONArray();
        for (var event : sim.getStoryBeats()) events.put(new JSONObject()
                .put("sequence", event.sequence()).put("tick", event.tick())
                .put("id", event.id()).put("audience", event.audience().name()));
        return events;
    }
}
