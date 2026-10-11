package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.audio.BattleRadioChatter.Cue;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/** Company command answers confirmed squad reports; it never observes or changes simulation state. */
public final class CompanyOfficer {
    public static final String PATH = "data/marines/company_officer_narration.json";
    private static List<Line> builtins = List.of();
    private final Map<Cue, List<Line>> lines = new EnumMap<>(Cue.class);
    private final Map<Cue, Integer> lastTake = new EnumMap<>(Cue.class);
    private final Random random;
    private final Function<Line, SoundAPI> play;
    private Cue pending;
    private float age, quiet, cooldown, remaining;
    private Line current;
    private SoundAPI sound;

    public record Line(String id, Cue cue, String text, float durationSeconds) {
        public Line {
            if (id == null || id.isBlank() || cue == null || text == null || text.isBlank()
                    || !Float.isFinite(durationSeconds) || durationSeconds <= 0)
                throw new IllegalArgumentException("Invalid company officer line");
        }
    }

    public CompanyOfficer() {
        this(builtins, new Random(), line -> {
            try {
                return Global.getSoundPlayer().playUISound(line.id(), 1f, 1f);
            } catch (RuntimeException failure) {
                Global.getLogger(CompanyOfficer.class).warn("Cannot play officer " + line.id(), failure);
                return null;
            }
        });
    }

    CompanyOfficer(List<Line> entries, Random random, Function<Line, SoundAPI> play) {
        for (Line line : entries) lines.computeIfAbsent(line.cue(), ignored -> new ArrayList<>()).add(line);
        this.random = random;
        this.play = play;
    }

    public static void loadBuiltins() {
        try {
            builtins = parse(Global.getSettings().loadJSON(PATH));
        } catch (Exception failure) {
            builtins = List.of();
            Global.getLogger(CompanyOfficer.class).warn("Company officer recordings unavailable", failure);
        }
    }

    public static List<Line> parse(JSONObject root) throws JSONException {
        JSONArray entries = root.getJSONArray("lines");
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject line = entries.getJSONObject(i);
            result.add(new Line(line.getString("id"), Cue.valueOf(line.getString("cue")),
                    line.getString("text"), (float) line.getDouble("durationSeconds")));
        }
        return List.copyOf(result);
    }

    /** Called only after the squad report is admitted to the shared voice channel. */
    public void heard(Cue cue) {
        if (cooldown > 0 || busy() || !lines.containsKey(cue)) return;
        pending = cue;
        age = quiet = 0;
    }

    /** Real time: paused or completed battles finish active speech but admit no new response. */
    public Line advance(float dt, boolean running, boolean channelBusy) {
        dt = Math.max(0, dt);
        cooldown = Math.max(0, cooldown - dt);
        if (current != null) {
            remaining -= dt;
            if (remaining <= 0 && (sound == null || !sound.isPlaying())) finish();
        }
        if (pending == null) return null;
        age += dt;
        if (age > 12) { pending = null; return null; }
        if (!running || channelBusy || busy()) { quiet = 0; return null; }
        quiet += dt;
        if (quiet < 1) return null;
        List<Line> pool = lines.get(pending);
        int index = random.nextInt(pool.size());
        if (pool.size() > 1 && index == lastTake.getOrDefault(pending, -1))
            index = (index + 1) % pool.size();
        lastTake.put(pending, index);
        current = pool.get(index);
        pending = null;
        cooldown = 45;
        remaining = current.durationSeconds() + 0.4f;
        sound = play.apply(current);
        return current;
    }

    public Line current() { return current; }
    public boolean busy() { return current != null; }
    public void discardPending() { pending = null; }

    /** Mission speech and leaving the host take priority over incidental command replies. */
    public void stop() { finish(); pending = null; }

    public void reset() {
        stop();
        cooldown = 0;
        lastTake.clear();
    }

    private void finish() {
        if (sound != null) sound.stop();
        sound = null;
        current = null;
        remaining = 0;
    }
}
