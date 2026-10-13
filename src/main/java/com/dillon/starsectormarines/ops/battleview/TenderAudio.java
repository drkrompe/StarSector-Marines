package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.TenderCues;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays the tender's authored machine effects for this frame,
 * positionally, as ground weapons are. A hostile tender's work is heard only where the map has
 * been seen, matching how {@code TenderSiteRenderSystem} gates its picture.
 */
public final class TenderAudio {

    static final String WELD = "marines_tender_weld";
    static final String STUN = "marines_tender_hit";
    static final String ALERT = "marines_tender_alert";
    static final String COMPLETE = "marines_tender_complete";

    // Presentation-only state. Weak keys release abandoned battles; no loop handles survive
    // pause, cancellation or destruction. Wall-time gaps bound the mix even at accelerated time.
    private static final Map<BattleSimulation, Mix> MIXES = new WeakHashMap<>();

    static final class Mix {
        private int tick = -1;
        private final EnumMap<TenderCues.Kind, Long> last = new EnumMap<>(TenderCues.Kind.class);

        boolean frame(int current) {
            if (current == tick) return false;
            if (current < tick) last.clear();
            tick = current;
            return true;
        }

        boolean admit(TenderCues.Kind kind, long now) {
            long gap = switch (kind) {
                case WELD -> 300_000_000L;
                case LAUNCH, DOCK -> 280_000_000L;
                case STUN -> 140_000_000L;
                case ALERT -> 600_000_000L;
                case COMPLETE -> 250_000_000L;
            };
            Long prior = last.get(kind);
            if (prior != null && now - prior < gap) return false;
            last.put(kind, now);
            return true;
        }
    }

    private static final float PITCH_JITTER = 0.08f;

    private TenderAudio() { }

    public static void play(BattleSimulation sim) {
        Mix mix = MIXES.computeIfAbsent(sim, ignored -> new Mix());
        if (!mix.frame(sim.getSimTickIndex())) return;
        List<TenderCues.Cue> cues = sim.tenders().cues().cuesThisFrame();
        if (cues.isEmpty()) return;
        Vector2f zeroVelocity = new Vector2f(0f, 0f);
        for (TenderCues.Cue cue : cues) {
            if (!heard(sim, cue)) continue;
            if (!mix.admit(cue.kind(), System.nanoTime())) continue;
            Vector2f where = new Vector2f(
                    cue.x() * BattleShotAudio.WORLD_UNITS_PER_CELL,
                    cue.y() * BattleShotAudio.WORLD_UNITS_PER_CELL);
            float pitch = 1f + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER);
            Global.getSoundPlayer().playSound(soundId(cue.kind()), pitch, volume(cue.kind()), where, zeroVelocity);
        }
    }

    static String soundId(TenderCues.Kind kind) {
        return switch (kind) {
            case LAUNCH -> "marines_tender_launch";
            case DOCK -> "marines_tender_dock";
            case WELD -> WELD;
            case STUN -> STUN;
            case ALERT -> ALERT;
            case COMPLETE -> COMPLETE;
        };
    }

    static float volume(TenderCues.Kind kind) {
        return switch (kind) {
            case LAUNCH -> 0.55f;
            case DOCK -> 0.6f;
            case WELD -> 0.35f;
            case STUN -> 0.8f;
            case ALERT -> 0.45f;
            case COMPLETE -> 0.7f;
        };
    }

    /** The player's own side is always heard; a hostile one only where the cell has been revealed. */
    static boolean heard(BattleSimulation sim, TenderCues.Cue cue) {
        return Allegiance.of(cue.faction()).friendly()
                || sim.getFogOfWar().isCellRevealed((int) Math.floor(cue.x()), (int) Math.floor(cue.y()));
    }
}
