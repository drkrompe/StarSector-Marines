package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.BoreCues;
import com.dillon.starsectormarines.battle.evacuation.BoreTier;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The bores' sound (`breach-audio.md`, hook points): an eruption, a tier
 * reached, a collapse and a surge leaving the mouth, played positionally from
 * {@link BoreCues}, and a breathing ambience loop under every living bore whose
 * clip follows its tier. A bore is the swarm's, so it is heard only where the
 * map has been seen. Spore-colony bursts need nothing here: a burst is a shot and
 * the vent weapon names its own fire sound.
 */
public final class BoreAudio {

    static final String ERUPTION = "marines_breach_bore_eruption";
    static final String COLLAPSE = "marines_breach_bore_collapse";
    static final String SURGE = "marines_breach_bore_surge";
    private static final float PITCH_JITTER = 0.06f;
    private static final float AMBIENCE_VOLUME = 0.45f;

    // Presentation-only: the last tick whose cues were played, per battle. Weak keys release abandoned battles.
    private static final Map<BattleSimulation, int[]> LAST_TICK = new WeakHashMap<>();

    private BoreAudio() { }

    /** Called every rendered frame: the loops must be refreshed each frame, the cues once per tick. */
    public static void play(BattleSimulation sim) {
        Vector2f still = new Vector2f(0f, 0f);
        for (SwarmBore bore : sim.swarmBores().bores()) {
            if (bore.collapsed() || !heard(sim, bore.x(), bore.y())) continue;
            Global.getSoundPlayer().playLoop(ambience(bore.tier()), bore, 1f, AMBIENCE_VOLUME,
                    where(bore.x() + 0.5f, bore.y() + 0.5f), still);
        }
        int[] last = LAST_TICK.computeIfAbsent(sim, ignored -> new int[]{-1});
        int tick = sim.getSimTickIndex();
        if (tick == last[0]) return;
        last[0] = tick;
        List<BoreCues.Cue> cues = sim.swarmBores().cues().cuesThisFrame();
        for (BoreCues.Cue cue : cues) {
            if (!heard(sim, (int) Math.floor(cue.x()), (int) Math.floor(cue.y()))) continue;
            String id = soundId(cue.kind());
            if (id == null) continue;
            float pitch = 1f + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER);
            Global.getSoundPlayer().playSound(id, pitch, volume(cue.kind()), where(cue.x(), cue.y()), still);
        }
    }

    /** The one-shot clip for a cue; a tier reached has none of its own, the ambience changing is the sound. */
    static String soundId(BoreCues.Kind kind) {
        return switch (kind) {
            case ERUPTED -> ERUPTION;
            case COLLAPSED -> COLLAPSE;
            case SURGE -> SURGE;
            case GREW -> null;
        };
    }

    static float volume(BoreCues.Kind kind) {
        return switch (kind) {
            case ERUPTED -> 1f;
            case COLLAPSED -> 0.95f;
            case SURGE -> 0.7f;
            case GREW -> 0f;
        };
    }

    static String ambience(BoreTier tier) {
        return "marines_breach_bore_ambience_" + tier.beatName;
    }

    private static Vector2f where(float x, float y) {
        return new Vector2f(x * BattleShotAudio.WORLD_UNITS_PER_CELL, y * BattleShotAudio.WORLD_UNITS_PER_CELL);
    }

    private static boolean heard(BattleSimulation sim, int x, int y) {
        return sim.getFogOfWar().isCellRevealed(x, y);
    }
}
