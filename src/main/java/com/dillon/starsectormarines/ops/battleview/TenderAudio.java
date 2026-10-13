package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.tender.TenderCues;
import com.fs.starfarer.api.Global;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Plays the sounds a tender's builders asked for this frame, with the game's own sound ids and
 * positionally, as ground weapons are. A hostile tender's work is heard only where the map has
 * been seen, matching how {@code TenderSiteRenderSystem} gates its picture.
 */
public final class TenderAudio {

    /** A quiet electrical crackle, the game's EMP-arc impact. */
    static final String WELD = "mote_attractor_impact_emp_arc";
    /** A short zap, the shock repeater's EMP impact. */
    static final String STUN = "shock_repeater_emp_impact";
    /** A soft ping: the site has been stood down. */
    static final String ALERT = "mine_ping";
    /** The game's own build-complete chime. */
    static final String COMPLETE = "ui_build_industry";

    private static final float PITCH_JITTER = 0.08f;

    private TenderAudio() { }

    public static void play(BattleSimulation sim) {
        List<TenderCues.Cue> cues = sim.tenders().cues().cuesThisFrame();
        if (cues.isEmpty()) return;
        Vector2f zeroVelocity = new Vector2f(0f, 0f);
        for (TenderCues.Cue cue : cues) {
            if (!heard(sim, cue)) continue;
            Vector2f where = new Vector2f(
                    cue.x() * BattleShotAudio.WORLD_UNITS_PER_CELL,
                    cue.y() * BattleShotAudio.WORLD_UNITS_PER_CELL);
            float pitch = 1f + ThreadLocalRandom.current().nextFloat(-PITCH_JITTER, PITCH_JITTER);
            Global.getSoundPlayer().playSound(soundId(cue.kind()), pitch, volume(cue.kind()), where, zeroVelocity);
        }
    }

    static String soundId(TenderCues.Kind kind) {
        return switch (kind) {
            case WELD -> WELD;
            case STUN -> STUN;
            case ALERT -> ALERT;
            case COMPLETE -> COMPLETE;
        };
    }

    static float volume(TenderCues.Kind kind) {
        return switch (kind) {
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
